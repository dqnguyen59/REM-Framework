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
    private static final int TRACE_STRIDE = 6;
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
                         double maximumPositionChange, double maximumGradientChange,
                         double positionEstimatedArrival, double gradientEstimatedArrival) {
        public Result(int ordinal,String probeId,String representedRays,long positionArrivalUpdate,long gradientArrivalUpdate,
                      double maximumPositionChange,double maximumGradientChange) {
            this(ordinal,probeId,representedRays,positionArrivalUpdate,gradientArrivalUpdate,maximumPositionChange,maximumGradientChange,
                    positionArrivalUpdate>0?positionArrivalUpdate:Double.NaN,gradientArrivalUpdate>0?gradientArrivalUpdate:Double.NaN);
        }
        public long combinedArrivalUpdate() {
            if (positionArrivalUpdate < 0 || gradientArrivalUpdate < 0) return -1;
            return Math.max(positionArrivalUpdate, gradientArrivalUpdate);
        }
        public double combinedEstimatedArrival() {
            return Double.isFinite(positionEstimatedArrival)&&Double.isFinite(gradientEstimatedArrival)
                    ?Math.max(positionEstimatedArrival,gradientEstimatedArrival):Double.NaN;
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
        private final Vector3d traceVector = new Vector3d();
        private final long baselineSampleCount;
        private long positionArrivalUpdate = -1;
        private long gradientArrivalUpdate = -1;
        private double positionEstimatedArrival = Double.NaN, gradientEstimatedArrival = Double.NaN;
        private double previousPositionChange, previousGradientChange;
        private long previousArrivalSampleUpdate;
        private double maximumPositionChange;
        private double maximumGradientChange;
        private double[] trace;
        private int traceSize;
        private boolean traceTruncated;
        private long lastTraceUpdate;
        private ProbeSweepMeasurement sweep;
        private ProbeCycleMeasurement velocityCycle;
        private ProbeCycleMeasurement imbalanceCycle;

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
    private boolean traceRecording;
    private boolean cyclesRunning;
    public record CycleResult(String probeId, String signal, ProbeCycleMeasurement.Result cycle) {}
    public synchronized List<CycleResult> cycleResults() {
        var results = new ArrayList<CycleResult>();
        for (var tracked : trackedProbes) {
            results.add(new CycleResult(tracked.probe.getId(), "velocity_z", tracked.velocityCycle.snapshot()));
            results.add(new CycleResult(tracked.probe.getId(), "imbalance_z", tracked.imbalanceCycle.snapshot()));
        }
        return List.copyOf(results);
    }

    public record SweepResult(String probeId, ProbeSweepMeasurement.Result measurement) {}
    public synchronized void enableSweep(int period,int cycles,Vector3d sourceCenter) {
        for(var tracked:trackedProbes) tracked.sweep=new ProbeSweepMeasurement(period,cycles,
            new Vector3d(tracked.baselinePosition).sub(sourceCenter));
    }
    public synchronized List<SweepResult> sweepResults() {
        return trackedProbes.stream().filter(t->t.sweep!=null)
            .map(t->new SweepResult(t.probe.getId(),t.sweep.snapshot())).toList();
    }
    public synchronized void enableSharedSweep(int period,int cycles,Vector3d sourceCenter,long start) {
        for(var tracked:trackedProbes) tracked.sweep=new ProbeSweepMeasurement(period,cycles,
                readPosition(tracked.probe,new Vector3d()).sub(sourceCenter),start);
    }

    /** Optional bounded diagnostics, enabled before the first update of a new measurement. */
    public synchronized void enableTrace(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Trace capacity must be positive");
        for (var tracked : trackedProbes) tracked.trace = new double[Math.multiplyExact(capacity, TRACE_STRIDE)];
        traceRecording = true;
    }

    public synchronized boolean hasTrace() {
        return trackedProbes.stream().anyMatch(t -> t.trace != null);
    }

    public synchronized boolean isTraceRecording() { return traceRecording; }

    /** Linear temporal estimate only for a consecutive pair bracketing the threshold. */
    public static double thresholdCrossing(long previousUpdate,long update,double before,double after,double threshold) {
        if(previousUpdate<0 || update!=previousUpdate+1 || !Double.isFinite(before) || !Double.isFinite(after)
                || !Double.isFinite(threshold) || threshold<=0 || before<0 || before>=threshold || after<threshold)
            return Double.NaN;
        return previousUpdate+(threshold-before)/(after-before);
    }

    public synchronized ProbePeakAnalysis.Result peakAnalysis(boolean gradient) {
        if (traceRecording) return new ProbePeakAnalysis.Result(Double.NaN, Double.NaN, List.of(), "Finish recording first");
        long earliest = trackedProbes.stream().mapToLong(t -> gradient ? t.gradientArrivalUpdate : t.positionArrivalUpdate)
                .filter(t -> t > 0).min().orElse(-1);
        var traces = trackedProbes.stream().filter(t -> t.trace != null)
                .map(t -> new ProbePeakAnalysis.Trace(t.probe.getId(), normTrace(t))).toList();
        return ProbePeakAnalysis.analyze(traces, gradient, earliest);
    }

    private static double[] normTrace(TrackedProbe tracked) {
        double[] samples = new double[tracked.traceSize * 3];
        for (int i = 0; i < tracked.traceSize; i++)
            System.arraycopy(tracked.trace, i * TRACE_STRIDE, samples, i * 3, 3);
        return samples;
    }

    /** channel 0: velocity Z; 1: measured imbalance Z; 2: cycle-summary imbalance Z. */
    public synchronized ProbeReversalAnalysis.Result reversalAnalysis(int channel) {
        if (channel < 0 || channel > 2) throw new IllegalArgumentException("Unknown signed trace channel");
        if (traceRecording) return new ProbeReversalAnalysis.Result(List.of(), "Finish recording first");
        if (trackedProbes.stream().anyMatch(t -> t.traceTruncated))
            return new ProbeReversalAnalysis.Result(List.of(), "Trace truncated; reduce probe count or recording length");
        var traces = new ArrayList<ProbeReversalAnalysis.Trace>();
        for (var tracked : trackedProbes) {
            if (tracked.trace == null) continue;
            double[] samples = new double[tracked.traceSize * 2];
            for (int i = 0; i < tracked.traceSize; i++) {
                samples[i * 2] = tracked.trace[i * TRACE_STRIDE];
                samples[i * 2 + 1] = tracked.trace[i * TRACE_STRIDE + 3 + channel];
            }
            traces.add(new ProbeReversalAnalysis.Trace(tracked.probe.getId(), samples));
        }
        return ProbeReversalAnalysis.analyze(traces, channel != 2);
    }

    /** Values are the exact baseline-relative norms used by the arrival detector. */
    public synchronized void writeTrace(java.io.Writer writer, double radius) throws java.io.IOException {
        for (var tracked : trackedProbes) {
            if (tracked.trace == null) continue;
            for (int i = 0; i < tracked.traceSize; i++) {
                int offset = i * TRACE_STRIDE;
                writer.write(String.format(Locale.ROOT,
                        "%d,%.17g,\"%s\",%d,%.17g,%.17g,%.17g,%.17g,%d,%d,%s,%.17g,%.17g,%.17g,%.17g,%.17g%n",
                        shell + 1, radius, tracked.probe.getId().replace("\"", "\"\""),
                        (long) tracked.trace[offset], tracked.trace[offset + 1], tracked.trace[offset + 2],
                        positionThreshold, gradientThreshold, tracked.positionArrivalUpdate,
                        tracked.gradientArrivalUpdate, tracked.traceTruncated,
                        tracked.trace[offset + 3], tracked.trace[offset + 4], tracked.trace[offset + 5],
                        tracked.positionEstimatedArrival, tracked.gradientEstimatedArrival));
            }
        }
    }

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
        return start(probes, selectedShell, selectedPositionThreshold, selectedGradientThreshold, 1e-9);
    }
    public synchronized boolean start(List<? extends PassiveObserver> probes, int selectedShell,
                                      double selectedPositionThreshold, double selectedGradientThreshold,
                                      double cycleDeadband) {
        validateThreshold(selectedPositionThreshold, "position");
        validateThreshold(selectedGradientThreshold, "gradient");
        validateThreshold(cycleDeadband, "cycle deadband");
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
        for (var tracked : trackedProbes) {
            tracked.velocityCycle = new ProbeCycleMeasurement(cycleDeadband);
            tracked.imbalanceCycle = new ProbeCycleMeasurement(cycleDeadband);
        }
        cyclesRunning = true;
        traceRecording = false;
        shell = selectedShell;
        positionThreshold = selectedPositionThreshold;
        gradientThreshold = selectedGradientThreshold;
        elapsedUpdates = 0;
        running = true;
        return true;
    }

    /** Samples once after all committed probes have completed one measurement update. */
    public synchronized void sample() {
        if (!running && !traceRecording && !cyclesRunning && trackedProbes.stream().noneMatch(t->t.sweep!=null && !t.sweep.snapshot().complete())) return;
        boolean detecting = running;
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
            double velocityZ = 0;
            if (traceRecording || !tracked.velocityCycle.finished())
                velocityZ = ProbeArrowQuantity.VELOCITY.read(tracked.probe, tracked.traceVector).z;
            tracked.velocityCycle.sample(probeElapsed, velocityZ);
            tracked.imbalanceCycle.sample(probeElapsed, tracked.currentGradient.z);
            if (traceRecording && probeElapsed > tracked.lastTraceUpdate) {
                tracked.lastTraceUpdate = probeElapsed;
                if (tracked.traceSize < tracked.trace.length / TRACE_STRIDE) {
                    int offset = tracked.traceSize++ * TRACE_STRIDE;
                    tracked.trace[offset] = probeElapsed;
                    tracked.trace[offset + 1] = positionChange;
                    tracked.trace[offset + 2] = gradientChange;
                    tracked.trace[offset + 3] = velocityZ;
                    tracked.trace[offset + 4] = tracked.currentGradient.z;
                    tracked.trace[offset + 5] = ProbeArrowQuantity.CYCLE_IMBALANCE.read(tracked.probe, tracked.traceVector).z;
                } else tracked.traceTruncated = true;
            }
            if(tracked.sweep!=null) {
                boolean detected=(tracked.positionArrivalUpdate>=0 || positionChange>=positionThreshold)
                    && (tracked.gradientArrivalUpdate>=0 || gradientChange>=gradientThreshold);
                tracked.sweep.sample(probeElapsed,detected,tracked.traceVector.set(tracked.currentGradient).sub(tracked.baselineGradient));
            }
            if (!detecting) continue;
            tracked.maximumPositionChange = Math.max(tracked.maximumPositionChange, positionChange);
            tracked.maximumGradientChange = Math.max(tracked.maximumGradientChange, gradientChange);
            if (tracked.positionArrivalUpdate < 0 && positionChange >= positionThreshold) {
                tracked.positionArrivalUpdate = probeElapsed;
                tracked.positionEstimatedArrival=thresholdCrossing(tracked.previousArrivalSampleUpdate,probeElapsed,
                        tracked.previousPositionChange,positionChange,positionThreshold);
            }
            if (tracked.gradientArrivalUpdate < 0 && gradientChange >= gradientThreshold) {
                tracked.gradientArrivalUpdate = probeElapsed;
                tracked.gradientEstimatedArrival=thresholdCrossing(tracked.previousArrivalSampleUpdate,probeElapsed,
                        tracked.previousGradientChange,gradientChange,gradientThreshold);
            }
            if(probeElapsed>tracked.previousArrivalSampleUpdate) {
                tracked.previousArrivalSampleUpdate=probeElapsed;
                tracked.previousPositionChange=positionChange;tracked.previousGradientChange=gradientChange;
            }
            if (tracked.positionArrivalUpdate < 0 || tracked.gradientArrivalUpdate < 0) {
                allArrived = false;
            }
        }
        if (detecting) elapsedUpdates = largestElapsed;
        cyclesRunning = trackedProbes.stream().anyMatch(t -> !t.velocityCycle.finished() || !t.imbalanceCycle.finished());
        if (allArrived) running = false;
    }

    public synchronized Snapshot snapshot() {
        List<Result> results = new ArrayList<>(trackedProbes.size());
        for (int index = 0; index < trackedProbes.size(); index++) {
            TrackedProbe tracked = trackedProbes.get(index);
            results.add(new Result(index + 1, tracked.probe.getId(), tracked.representedRays,
                    tracked.positionArrivalUpdate, tracked.gradientArrivalUpdate,
                    tracked.maximumPositionChange, tracked.maximumGradientChange,
                    tracked.positionEstimatedArrival, tracked.gradientEstimatedArrival));
        }
        return new Snapshot(shell, positionThreshold, gradientThreshold,
                running, elapsedUpdates, results);
    }

    /** Freezes a recording at its causal cutoff, preserving incomplete arrivals. */
    public synchronized void finish() {
        running = false; traceRecording = false; cyclesRunning = false;
        for (var tracked : trackedProbes) { tracked.velocityCycle.finish(); tracked.imbalanceCycle.finish(); if(tracked.sweep!=null)tracked.sweep.finish(); }
    }

    public synchronized void clear() {
        trackedProbes = List.of();
        traceRecording = false;
        cyclesRunning = false;
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
            double arrival=gradient?result.gradientEstimatedArrival():result.positionEstimatedArrival();
            if(!Double.isFinite(arrival)||arrival<=0)return Double.NaN;
            double inverse=1.0/arrival;
            min=Math.min(min,inverse);max=Math.max(max,inverse);sum+=inverse;
        }
        return 100*(max-min)/(sum/results.size());
    }
}
