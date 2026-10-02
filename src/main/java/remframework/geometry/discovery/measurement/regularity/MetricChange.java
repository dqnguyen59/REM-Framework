package remframework.geometry.discovery.measurement.regularity;

public record MetricChange(double baseline, double optimized, double absoluteChange,
        Double relativeChange) {
    public static MetricChange of(double baseline, double optimized) {
        return new MetricChange(baseline, optimized, optimized-baseline,
                baseline == 0 || !Double.isFinite(baseline) ? null : (optimized-baseline)/Math.abs(baseline));
    }
}
