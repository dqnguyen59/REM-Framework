package remframework.measurement.detector;

import java.util.Arrays;

/**
 * Pure numerical helpers used to validate Experiment 3E analysis semantics.
 */
public final class Experiment3EAnalysis {
    private Experiment3EAnalysis() {
    }

    public static double pooledRms(double... values) {
        requireFinite(values);
        return Math.sqrt(Arrays.stream(values)
                .map(value -> value * value).average().orElseThrow());
    }

    public static double mean(double... values) {
        requireFinite(values);
        return Arrays.stream(values).average().orElseThrow();
    }

    public static double directionalCv(double... values) {
        double mean = mean(values);
        double variance = Arrays.stream(values)
                .map(value -> (value - mean) * (value - mean))
                .average().orElseThrow();
        return Math.sqrt(variance) / Math.max(Math.abs(mean), Math.ulp(1.0));
    }

    public static double directionalSpread(double... values) {
        requireFinite(values);
        double mean = mean(values);
        return (Arrays.stream(values).max().orElseThrow()
                - Arrays.stream(values).min().orElseThrow())
                / Math.max(Math.abs(mean), Math.ulp(1.0));
    }

    public static double symmetricDifference(double first, double second) {
        requireFinite(first, second);
        return Math.abs(first - second)
                / Math.max((Math.abs(first) + Math.abs(second)) / 2,
                Math.ulp(1.0));
    }

    public static String classifyTrend(double first, double middle,
                                       double last) {
        requireFinite(first, middle, last);
        if (middle < first && last < middle) return "decreasing";
        if (middle > first && last > middle) return "increasing";
        double scale = Math.max(Math.abs(first), Math.ulp(1.0));
        if (Math.abs(last - first) / scale < 1e-12) {
            return "approximately stable";
        }
        return "non-monotonic";
    }

    private static void requireFinite(double... values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("Values must not be empty");
        }
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Non-finite analysis value: " + value);
            }
        }
    }
}
