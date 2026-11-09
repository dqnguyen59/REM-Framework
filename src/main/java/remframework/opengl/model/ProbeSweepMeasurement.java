package remframework.opengl.model;

import org.joml.Vector3d;

/** Constant-memory vector statistics over whole source periods after local detection. */
public final class ProbeSweepMeasurement {
    public record Result(long startUpdate, long endUpdate, int requestedCycles, int completedCycles,
                         int updatesPerCycle, long samples, double meanX, double meanY, double meanZ,
                         double rms, double radialMean, double perpendicularRms, String status) {
        public boolean complete() { return status.equals("complete"); }
    }
    private final int period, cycles;
    private final Vector3d radial;
    private final Vector3d sum = new Vector3d();
    private long start = -1, last, samples;
    private double squares, radialSum, perpendicularSquares;
    private String terminal;

    public ProbeSweepMeasurement(int period, int cycles, Vector3d radial) {
        if (period < 1 || cycles < 1 || !radial.isFinite() || radial.lengthSquared() == 0)
            throw new IllegalArgumentException("Positive period, cycle count and finite radial direction required");
        this.period = period; this.cycles = cycles; this.radial = new Vector3d(radial).normalize();
    }
    /** Shared source-clock window; detection does not choose different cycles per probe. */
    public ProbeSweepMeasurement(int period, int cycles, Vector3d radial, long startBoundary) {
        this(period, cycles, radial);
        if(startBoundary<0)throw new IllegalArgumentException("Nonnegative window boundary required");
        start=startBoundary;
    }

    /** Samples (start, end], once per update. Detection must qualify both norm signals. */
    public void sample(long update, boolean detected, Vector3d imbalanceChange) {
        if (terminal != null || update == last) return;
        if (update < 1 || (last > 0 && update != last + 1) || !imbalanceChange.isFinite()) {
            terminal = "invalid_or_missing_sample"; return;
        }
        last = update;
        if (start < 0 && detected) start = Math.multiplyExact(Math.floorDiv(update + period - 1, period), period);
        if (start < 0 || update <= start) return;
        sum.add(imbalanceChange); samples++;
        double radialValue = imbalanceChange.dot(radial);
        squares += imbalanceChange.lengthSquared(); radialSum += radialValue;
        perpendicularSquares += Math.max(0, imbalanceChange.lengthSquared() - radialValue * radialValue);
        if (samples == (long) period * cycles) terminal = "complete";
    }
    public void finish() { if (terminal == null) terminal = "incomplete"; }
    public Result snapshot() {
        return new Result(start, start < 0 ? -1 : start + (long) period * cycles, cycles,
                (int) (samples / period), period, samples,
                samples == 0 ? Double.NaN : sum.x / samples, samples == 0 ? Double.NaN : sum.y / samples,
                samples == 0 ? Double.NaN : sum.z / samples, samples == 0 ? Double.NaN : Math.sqrt(squares / samples),
                samples == 0 ? Double.NaN : radialSum / samples,
                samples == 0 ? Double.NaN : Math.sqrt(perpendicularSquares / samples),
                terminal != null ? terminal : start < 0 ? "waiting_for_detection" : "recording");
    }
}
