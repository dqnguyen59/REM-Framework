package remframework.opengl.model;

import org.joml.Vector3d;
import remframework.opengl.model.entity.Occupancy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Measures first-change arrival times for one generated probe shell.
 * <br><br>
 * Time is counted in probe measurement updates, so rendering rate and wall-clock
 * performance cannot change the result. The measurement is diagnostic only and
 * does not alter the REM Framework mechanics.
 */
public final class ProbeArrivalMeasurement {
    public static final double DEFAULT_POSITION_THRESHOLD = 1.0e-12;
    public static final double DEFAULT_GRADIENT_THRESHOLD = 1.0e-12;
    public static final double DEFAULT_SPEED_SPREAD_TOLERANCE_PERCENT = 1.0;

    /** A single-run tolerance check; convergence is assessed separately. */
    public static String toleranceResult(double spread, double tolerancePercent) {
        if (!Double.isFinite(tolerancePercent) || tolerancePercent <= 0)
            throw new IllegalArgumentException("Tolerance must be finite and positive");
        if (!Double.isFinite(spread) || spread < 0) return "Incomplete";
        return spread <= tolerancePercent ? "Within tolerance (single run)" : "Above tolerance";
    }

    /** Immutable chart/table result for one physical passive probe. */
    public record Result(int ordinal, String probeId, String representedRays,
                         long positionArrivalUpdate, long gradientArrivalUpdate,
                         double maximumPositionChange, double maximumGradientChange) {
        public long combinedArrivalUpdate() {
            if (positionArrivalUpdate < 0 || gradientArrivalUpdate < 0) return -1;
            return Math.max(positionArrivalUpdate, gradientArrivalUpdate);
        }
    }

    /** Immutable UI snapshot. Shell is zero-based; negative means no active measurement. */
    public record Snapshot(int shell, double positionThreshold, double gradientThreshold,
                           boolean running, long elapsedUpdates, List<Result> results) {
        public Snapshot {
            results = List.copyOf(results);
        }

        public boolean hasMeasurement() { return shell >= 0 && !results.isEmpty(); }

        public boolean complete() {
            return hasMeasurement() && results.stream()
                    .allMatch(result -> result.combinedArrivalUpdate() >= 0);
        }
    }

    private static final class TrackedProbe {
        private final PassiveObserver probe;
        private final String representedRays;
        private final Vector3d baselinePosition = new Vector3d();
        private final Vector3d baselineGradient = new Vector3d();
        private final Vector3d currentPosition = new Vector3d();
        private final Vector3d currentGradient = new Vector3d();
        private final long baselineSampleCount;
        private long positionArrivalUpdate = -1;
        private long gradientArrivalUpdate = -1;
        private double maximumPositionChange;
        private double maximumGradientChange;

        private TrackedProbe(PassiveObserver probe, String representedRays) {
            this.probe = probe;
            this.representedRays = representedRays;
            readPosition(probe, baselinePosition);
            readLatestMeasuredGradient(probe, baselineGradient);
            baselineSampleCount = sampleCount(probe);
        }
    }

    private List<TrackedProbe> trackedProbes = List.of();
    private int shell = -1;
    private double positionThreshold = DEFAULT_POSITION_THRESHOLD;
    private double gradientThreshold = DEFAULT_GRADIENT_THRESHOLD;
    private boolean running;
    private long elapsedUpdates;

    /** Returns the generated shell indices currently represented by committed probes. */
    public static List<Integer> availableShells(List<? extends PassiveObserver> probes) {
        return probes.stream()
                .flatMap(probe -> targets(probe).stream())
                .map(ProbeDefinition.Target::shell)
                .distinct()
                .sorted()
                .toList();
    }

    /** Starts from the latest committed position and per-update gradient samples. */
    public synchronized boolean start(List<? extends PassiveObserver> probes, int selectedShell,
                                      double selectedPositionThreshold,
                                      double selectedGradientThreshold) {
        validateThreshold(selectedPositionThreshold, "position");
        validateThreshold(selectedGradientThreshold, "gradient");
        List<TrackedProbe> selected = probes.stream()
                .filter(probe -> targets(probe).stream()
                        .anyMatch(target -> target.shell() == selectedShell))
                .sorted(Comparator
                        .comparingInt((PassiveObserver probe) -> firstRay(probe, selectedShell))
                        .thenComparing(PassiveObserver::getId))
                .map(probe -> new TrackedProbe(probe, representedRays(probe, selectedShell)))
                .toList();
        if (selected.isEmpty()) {
            clear();
            return false;
        }
        trackedProbes = selected;
        shell = selectedShell;
        positionThreshold = selectedPositionThreshold;
        gradientThreshold = selectedGradientThreshold;
        elapsedUpdates = 0;
        running = true;
        return true;
    }

    /** Samples once after all committed probes have completed one measurement update. */
    public synchronized void sample() {
        if (!running) return;
        boolean allArrived = true;
        long largestElapsed = elapsedUpdates;
        for (TrackedProbe tracked : trackedProbes) {
            long probeElapsed = Math.max(0, sampleCount(tracked.probe) - tracked.baselineSampleCount);
            largestElapsed = Math.max(largestElapsed, probeElapsed);
            if (probeElapsed <= 0) {
                allArrived = false;
                continue;
            }

            double positionChange = readPosition(tracked.probe, tracked.currentPosition)
                    .distance(tracked.baselinePosition);
            double gradientChange = readLatestMeasuredGradient(
                    tracked.probe, tracked.currentGradient).distance(tracked.baselineGradient);
            tracked.maximumPositionChange = Math.max(tracked.maximumPositionChange, positionChange);
            tracked.maximumGradientChange = Math.max(tracked.maximumGradientChange, gradientChange);
            if (tracked.positionArrivalUpdate < 0 && positionChange >= positionThreshold) {
                tracked.positionArrivalUpdate = probeElapsed;
            }
            if (tracked.gradientArrivalUpdate < 0 && gradientChange >= gradientThreshold) {
                tracked.gradientArrivalUpdate = probeElapsed;
            }
            if (tracked.positionArrivalUpdate < 0 || tracked.gradientArrivalUpdate < 0) {
                allArrived = false;
            }
        }
        elapsedUpdates = largestElapsed;
        if (allArrived) running = false;
    }

    public synchronized Snapshot snapshot() {
        List<Result> results = new ArrayList<>(trackedProbes.size());
        for (int index = 0; index < trackedProbes.size(); index++) {
            TrackedProbe tracked = trackedProbes.get(index);
            results.add(new Result(index + 1, tracked.probe.getId(), tracked.representedRays,
                    tracked.positionArrivalUpdate, tracked.gradientArrivalUpdate,
                    tracked.maximumPositionChange, tracked.maximumGradientChange));
        }
        return new Snapshot(shell, positionThreshold, gradientThreshold,
                running, elapsedUpdates, results);
    }

    /** Freezes a recording at its causal cutoff, preserving incomplete arrivals. */
    public synchronized void finish() { running = false; }

    public synchronized void clear() {
        trackedProbes = List.of();
        shell = -1;
        running = false;
        elapsedUpdates = 0;
    }

    private static List<ProbeDefinition.Target> targets(PassiveObserver probe) {
        ProbeDefinition.Placement placement = probe.getDefinition().getPlacement();
        return placement == null ? List.of() : placement.targets();
    }

    private static int firstRay(PassiveObserver probe, int selectedShell) {
        return targets(probe).stream()
                .filter(target -> target.shell() == selectedShell)
                .mapToInt(ProbeDefinition.Target::ray)
                .min().orElse(Integer.MAX_VALUE);
    }

    private static String representedRays(PassiveObserver probe, int selectedShell) {
        return targets(probe).stream()
                .filter(target -> target.shell() == selectedShell)
                .sorted(Comparator.comparingInt(ProbeDefinition.Target::ray))
                .map(target -> target.rayPlane() + " (ray " + (target.ray() + 1) + ")")
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private static long sampleCount(PassiveObserver probe) {
        List<Occupancy> occupancies = probe.getOccupancies();
        return occupancies.isEmpty() ? 0
                                     : occupancies.getFirst().getCumulativeSampleCountForAudit();
    }

    /** Reads the current raw or weighted position without allocating in the physics loop. */
    private static Vector3d readPosition(PassiveObserver probe, Vector3d destination) {
        if (probe instanceof Probe rawProbe) {
            return destination.set(rawProbe.getNode().getPosition());
        }
        if (probe instanceof InterpolatedProbe interpolatedProbe) {
            destination.zero();
            List<ProbeDefinition.InterpolationSource> sources =
                    probe.getDefinition().getInterpolationSources();
            for (int index = 0; index < interpolatedProbe.getSourceNodes().size(); index++) {
                destination.fma(sources.get(index).weight(),
                        interpolatedProbe.getSourceNodes().get(index).getPosition());
            }
            return destination;
        }
        return destination.set(probe.getObservation().position());
    }

    /** g = g_0 for raw probes, or g = sum(w_i * g_i) for interpolated probes. */
    private static Vector3d readLatestMeasuredGradient(
            PassiveObserver probe, Vector3d destination) {
        List<Occupancy> occupancies = probe.getOccupancies();
        if (occupancies.isEmpty()) {
            return destination.set(probe.getObservation().gradientImbalance());
        }
        if (probe.getDetectorType() == ProbeDetectorType.RAW_NODE) {
            return occupancies.getFirst().copyLastMeasuredGradientForAudit(destination);
        }
        destination.zero();
        List<ProbeDefinition.InterpolationSource> sources =
                probe.getDefinition().getInterpolationSources();
        for (int index = 0; index < occupancies.size(); index++) {
            occupancies.get(index).accumulateLastMeasuredGradientForAudit(
                    destination, sources.get(index).weight());
        }
        return destination;
    }

    private static void validateThreshold(double threshold, String name) {
        if (!Double.isFinite(threshold) || threshold <= 0.0) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "%s threshold must be finite and positive: %s", name, threshold));
        }
    }

    /** Equal-radius first-arrival diagnostic; NaN means incomplete, not zero spread. */
    public static double equalRadiusApparentSpread(List<Result> results, boolean gradient) {
        if(results.size()<2)return Double.NaN;
        double min=Double.POSITIVE_INFINITY,max=0,sum=0;
        for(Result result:results) {
            long arrival=gradient?result.gradientArrivalUpdate():result.positionArrivalUpdate();
            if(arrival<=0)return Double.NaN;
            double inverse=1.0/arrival;
            min=Math.min(min,inverse);max=Math.max(max,inverse);sum+=inverse;
        }
        return 100*(max-min)/(sum/results.size());
    }
}
