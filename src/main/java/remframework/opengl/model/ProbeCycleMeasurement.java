package remframework.opengl.model;

/** Constant-memory, per-probe signed-signal cycle detector. No shell reference is used. */
public final class ProbeCycleMeasurement {
    public record Result(double startUpdate, double middleUpdate, double endUpdate,
                         double periodUpdates, long arrivalUpdate, double deadband, String status) {
        public boolean complete() { return status.equals("complete"); }
    }
    private final double deadband;
    private long lastUpdate;
    private int state, previousSign, zeroCount, pendingDirection, stage;
    private double previousValue, previousTime, zeroTime, pending = Double.NaN;
    private double start = Double.NaN, middle = Double.NaN, end = Double.NaN;
    private long arrival = -1;
    private String terminalStatus;

    public ProbeCycleMeasurement(double deadband) {
        if (!Double.isFinite(deadband) || deadband <= 0)
            throw new IllegalArgumentException("Cycle deadband must be finite and positive");
        this.deadband = deadband;
    }
    public boolean finished() { return terminalStatus != null; }

    /** Samples consecutive completed updates; repeated calls for an update are ignored. */
    public void sample(long update, double value) {
        if (finished() || update == lastUpdate) return;
        if (update < 1 || (lastUpdate > 0 && update != lastUpdate + 1) || !Double.isFinite(value)) {
            terminalStatus = "invalid_or_missing_sample"; return;
        }
        lastUpdate = update;
        int sign = value > 0 ? 1 : value < 0 ? -1 : 0;
        if (sign == 0) { if (zeroCount++ == 0) zeroTime = update; }
        else {
            if (previousSign != 0 && sign != previousSign) {
                pending = zeroCount > 1 ? Double.NaN : zeroCount == 1 ? zeroTime
                        : previousTime + (update - previousTime) * (-previousValue) / (value - previousValue);
                pendingDirection = sign;
            }
            previousSign = sign; previousTime = update; previousValue = value; zeroCount = 0;
        }
        int qualified = value > deadband ? 1 : value < -deadband ? -1 : 0;
        if (qualified == 0) return;
        if (state != 0 && qualified != state) {
            if (pendingDirection != qualified || !Double.isFinite(pending)) {
                terminalStatus = "ambiguous_zero_crossing"; return;
            }
            if (stage == 0 && qualified > 0) { start = pending; stage = 1; }
            else if (stage == 1 && qualified < 0) { middle = pending; stage = 2; }
            else if (stage == 2 && qualified > 0) {
                end = pending; arrival = update; terminalStatus = "complete";
            }
        }
        state = qualified;
    }
    public void finish() { if (!finished()) terminalStatus = "incomplete"; }
    public Result snapshot() {
        return new Result(start, middle, end, Double.isFinite(end) ? end - start : Double.NaN,
                arrival, deadband, terminalStatus != null ? terminalStatus : stage == 0 ? "waiting_for_cycle_start" : "recording_cycle");
    }
}
