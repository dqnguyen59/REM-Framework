package remframework.geometry.discovery.measurement.regularity;

import java.util.List;

public record Statistics(List<Double> values, double mean, double variance,
        double standardDeviation, double minimum, double maximum) {
    public Statistics { values = List.copyOf(values); }
    public static Statistics of(List<Double> values) {
        List<Double> copy = List.copyOf(values);
        if (copy.isEmpty()) return new Statistics(copy, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        double mean = copy.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double variance = copy.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0);
        return new Statistics(copy, mean, variance, Math.sqrt(variance),
                copy.stream().mapToDouble(Double::doubleValue).min().orElseThrow(),
                copy.stream().mapToDouble(Double::doubleValue).max().orElseThrow());
    }
    public double range() { return maximum - minimum; }
}
