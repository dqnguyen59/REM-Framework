package remframework.geometry.discovery.measurement.regularity;

import java.util.Map;

public record OptimizationComparison(Map<String, MetricChange> changes, String interpretation) {
    public OptimizationComparison { changes = Map.copyOf(changes); }
}
