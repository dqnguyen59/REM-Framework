package remframework.opengl.model;

import java.util.ArrayList;
import java.util.List;

/** Signed-Z reversal diagnostics; thresholds suppress tiny sign chatter without changing samples. */
public final class ProbeReversalAnalysis {
    public static final double DEADBAND_FRACTION = .01;
    private ProbeReversalAnalysis() {}
    /** Alternating update and signed value. */
    public record Trace(String id, double[] samples) {}
    public record Crossing(double update, int direction) {}
    public record Match(String id, double update, String status) {}
    public record Event(int ordinal, int direction, double referenceUpdate, double windowStart,
                        double windowEnd, double deadband, List<Match> matches) {
        public Event { matches = List.copyOf(matches); }
        public boolean complete() { return !matches.isEmpty() && matches.stream().allMatch(m -> Double.isFinite(m.update)); }
        public double minimum() { return complete() ? matches.stream().mapToDouble(Match::update).min().orElseThrow() : Double.NaN; }
        public double maximum() { return complete() ? matches.stream().mapToDouble(Match::update).max().orElseThrow() : Double.NaN; }
        public double range() { return maximum() - minimum(); }
    }
    public record Result(List<Event> events, String status) {
        public Result { events = List.copyOf(events); }
    }

    /** Detects opposite qualified signs, retaining the zero crossing between them. */
    public static List<Crossing> crossings(double[] samples, double deadband, boolean interpolate) {
        if (!Double.isFinite(deadband) || deadband <= 0 || samples.length % 2 != 0)
            throw new IllegalArgumentException("Positive finite deadband and update/value pairs required");
        var result = new ArrayList<Crossing>();
        int state = 0, previousSign = 0, zeros = 0, pendingDirection = 0;
        double previousValue = 0, previousTime = 0, zeroTime = 0, pending = Double.NaN;
        for (int i = 0; i < samples.length; i += 2) {
            double time = samples[i], value = samples[i + 1];
            int sign = Double.compare(value, 0);
            if (value == 0) sign = 0;
            if (sign == 0) { if (zeros++ == 0) zeroTime = time; }
            else {
                if (previousSign != 0 && sign != previousSign) {
                    pending = zeros > 1 ? Double.NaN : zeros == 1 ? zeroTime
                            : previousTime + (time - previousTime) * (-previousValue) / (value - previousValue);
                    pendingDirection = sign;
                }
                previousSign = sign; previousTime = time; previousValue = value; zeros = 0;
            }
            int qualified = value > deadband ? 1 : value < -deadband ? -1 : 0;
            if (qualified == 0) continue;
            if (state != 0 && qualified != state) {
                result.add(new Crossing(interpolate ? pendingDirection == qualified ? pending : Double.NaN : time, qualified));
            }
            state = qualified;
        }
        return List.copyOf(result);
    }

    /**
     * Matches equal-direction crossings in windows halfway between adjacent shell-mean
     * reversals. Missing/multiple/zero-plateau crossings are not forced into alignment.
     * <br><br>
     * Deadband = 1% of the largest absolute source signal in this shell and channel.
     * It is common to all directions, independent of the arrival threshold.
     */
    public static Result analyze(List<Trace> traces, boolean interpolate) {
        if (traces.isEmpty()) return new Result(List.of(), "No signed traces");
        int length = traces.getFirst().samples.length;
        if (length < 6 || length % 2 != 0) return new Result(List.of(), "Insufficient signed samples");
        double[] mean = new double[length]; double maximum = 0;
        var first = traces.getFirst().samples;
        for (var trace : traces) {
            if (trace.samples.length != length) return new Result(List.of(), "Unequal trace lengths");
            for (int i = 0; i < length; i += 2) {
                double time = trace.samples[i], value = trace.samples[i + 1];
                if (!Double.isFinite(time) || !Double.isFinite(value) || time != first[i]
                        || (i > 0 && time != first[i - 2] + 1))
                    return new Result(List.of(), "Missing or unaligned signed samples");
                mean[i] = time; mean[i + 1] += value / traces.size();
                maximum = Math.max(maximum, Math.abs(value));
            }
        }
        if (maximum == 0) return new Result(List.of(), "No nonzero signed signal");
        double deadband = maximum * DEADBAND_FRACTION;
        if (!(deadband > 0)) return new Result(List.of(), "Signal below numeric range");
        var reference = crossings(mean, deadband, interpolate).stream().filter(c -> Double.isFinite(c.update)).toList();
        if (reference.isEmpty()) return new Result(List.of(), "No qualified shell-mean reversal");
        var detected = traces.stream().map(t -> crossings(t.samples, deadband, interpolate)).toList();
        var events = new ArrayList<Event>();
        for (int e = 0; e < reference.size(); e++) {
            var current = reference.get(e);
            double left = e == 0 ? first[0] : (reference.get(e - 1).update + current.update) / 2;
            double right = e == reference.size() - 1 ? first[length - 2] : (current.update + reference.get(e + 1).update) / 2;
            var matches = new ArrayList<Match>();
            for (int p = 0; p < traces.size(); p++) {
                var candidates = detected.get(p).stream().filter(c -> c.direction == current.direction
                        && c.update >= left && c.update < right).toList();
                matches.add(new Match(traces.get(p).id, candidates.size() == 1 ? candidates.getFirst().update : Double.NaN,
                        candidates.size() == 1 ? "complete" : candidates.isEmpty() ? "Missing or ambiguous reversal" : "Multiple reversals in window"));
            }
            events.add(new Event(e + 1, current.direction, current.update, left, right, deadband, matches));
        }
        return new Result(events, "Recorded signed-Z reversals; compare within each shell and channel");
    }
}
