package remframework.geometry.discovery;

import java.util.List;

public record DynamicElasticMetrics(
        int iteration,
        int connectionsSatisfied,
        int connectionCount,
        double meanDistanceAccuracy,
        double satisfiedConnectionPercentage,
        double maximumNodeSpeed,
        int stableIterations,
        int requiredStableIterations,
        DynamicElasticState state,
        List<DynamicElasticEdgeMetric> edges) {
    public DynamicElasticMetrics {
        edges = List.copyOf(edges);
    }
    public boolean allFinite() {
        return Double.isFinite(meanDistanceAccuracy)
                && Double.isFinite(satisfiedConnectionPercentage)
                && Double.isFinite(maximumNodeSpeed)
                && edges.stream().allMatch(edge -> Double.isFinite(edge.distance())
                && Double.isFinite(edge.accuracy()));
    }
}
