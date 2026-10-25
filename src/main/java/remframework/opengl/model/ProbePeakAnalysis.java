package remframework.opengl.model;

import java.util.ArrayList;
import java.util.List;

/** Compares one common waveform lobe without requiring every probe to cross a threshold. */
public final class ProbePeakAnalysis {
    private ProbePeakAnalysis() {}

    /** Packed update, position norm, gradient norm triples. Used only during analysis. */
    public record Trace(String id, double[] samples) {}
    public record Peak(String id, double update, double amplitude, String status, double halfPeakUpdate) {
        public Peak(String id, double update, double amplitude, String status) {
            this(id, update, amplitude, status, Double.NaN);
        }
        public boolean valid() { return status.equals("complete"); }
    }
    public record Result(double windowStart, double windowEnd, List<Peak> peaks, String status) {
        public Result { peaks = List.copyOf(peaks); }
        public boolean complete() { return !peaks.isEmpty() && peaks.stream().allMatch(Peak::valid); }
        public double timingRange() {
            return complete() ? peaks.stream().mapToDouble(Peak::update).max().orElseThrow()
                    - peaks.stream().mapToDouble(Peak::update).min().orElseThrow() : Double.NaN;
        }
        public double minimumAmplitude() { return complete() ? peaks.stream().mapToDouble(Peak::amplitude).min().orElseThrow() : Double.NaN; }
        public double maximumAmplitude() { return complete() ? peaks.stream().mapToDouble(Peak::amplitude).max().orElseThrow() : Double.NaN; }
        public boolean halfPeakComplete() { return complete() && peaks.stream().allMatch(p -> Double.isFinite(p.halfPeakUpdate)); }
        public double halfPeakTimingRange() {
            return halfPeakComplete() ? peaks.stream().mapToDouble(Peak::halfPeakUpdate).max().orElseThrow()
                    - peaks.stream().mapToDouble(Peak::halfPeakUpdate).min().orElseThrow() : Double.NaN;
        }
    }

    private static Result unavailable(String status) { return new Result(Double.NaN, Double.NaN, List.of(), status); }

    /**
     * Selects the first local maximum of the shell-mean norm at or after its earliest
     * threshold arrival. Neighboring mean minima define one shared window for all probes.
     * <br><br>
     * Each probe must have exactly one strict interior maximum in that window. Ambiguous
     * or unfinished lobes remain unavailable. Selection is threshold-anchored, so changing
     * the threshold can select a different lobe; this is a peak-timing diagnostic, not a front speed.
     */
    public static Result analyze(List<Trace> traces, boolean gradient, long earliestArrival) {
        if (traces.isEmpty() || earliestArrival < 1) return unavailable("No traces or arrivals");
        int count = traces.getFirst().samples.length / 3;
        if (count < 5) return unavailable("Insufficient trace samples");
        int column = gradient ? 2 : 1;
        double[] mean = new double[count];
        double[] first = traces.getFirst().samples;
        for (var trace : traces) {
            if (trace.samples.length != count * 3) return unavailable("Unequal trace lengths");
            for (int i = 0; i < count; i++) {
                if (trace.samples[i * 3] != first[i * 3]
                        || (i > 0 && first[i * 3] != first[(i - 1) * 3] + 1))
                    return unavailable("Missing or unaligned updates");
                double value = trace.samples[i * 3 + column];
                if (!Double.isFinite(value) || value < 0) return unavailable("Invalid detector norm");
                mean[i] += value / traces.size();
            }
        }
        int peak = -1;
        for (int i = 1; i < count - 1; i++) {
            if (first[i * 3] >= earliestArrival && mean[i] > mean[i - 1] && mean[i] > mean[i + 1]) {
                peak = i; break;
            }
        }
        if (peak < 0) return unavailable("Selected peak not fully recorded");
        int left = peak - 1, right = peak + 1;
        while (left > 0 && mean[left - 1] < mean[left]) left--;
        while (right < count - 1 && mean[right + 1] < mean[right]) right++;
        if (left == 0 || right == count - 1) return unavailable("Lobe boundaries not fully recorded");
        var peaks = new ArrayList<Peak>();
        for (var trace : traces) {
            int candidate = -1, maxima = 0;
            for (int i = left + 1; i < right; i++) {
                double value = trace.samples[i * 3 + column];
                if (value > trace.samples[(i - 1) * 3 + column]
                        && value > trace.samples[(i + 1) * 3 + column]) { candidate = i; maxima++; }
            }
            if (maxima != 1) {
                peaks.add(new Peak(trace.id, Double.NaN, Double.NaN, "Ambiguous or missing peak")); continue;
            }
            double a = trace.samples[(candidate - 1) * 3 + column];
            double b = trace.samples[candidate * 3 + column];
            double c = trace.samples[(candidate + 1) * 3 + column];
            // Vertex of the parabola through three equally spaced samples.
            double delta = .5 * (a - c) / (a - 2 * b + c);
            double amplitude = b - .25 * (a - c) * delta;
            boolean interior = b > trace.samples[left * 3 + column] && b > trace.samples[right * 3 + column];
            // Same lobe and same fraction for every direction: amplitude scaling alone
            // cannot switch this detector to a later oscillation. No waveform is modified.
            double level = .5 * amplitude, crossing = Double.NaN;
            int crossings = 0;
            for (int i = left; i < candidate; i++) {
                double low = trace.samples[i * 3 + column], high = trace.samples[(i + 1) * 3 + column];
                if (low < level && high >= level) {
                    crossing = first[i * 3] + (level - low) / (high - low);
                    crossings++;
                }
            }
            peaks.add(new Peak(trace.id, interior ? first[candidate * 3] + delta : Double.NaN,
                    interior ? amplitude : Double.NaN, interior ? "complete" : "Peak touches window boundary",
                    interior && crossings == 1 ? crossing : Double.NaN));
        }
        return new Result(first[left * 3], first[right * 3], peaks,
                peaks.stream().allMatch(Peak::valid) ? "complete" : "Some probe peaks are ambiguous");
    }
}
