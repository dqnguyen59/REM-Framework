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
        List<DynamicElasticEdgeMetric> edges,
        double minimumObservedNonNeighborDistance,
        int edgeClearanceViolations,
        double meanLocalSpread,
        double minimumLocalSpread,
        boolean elasticEquilibrium,
        boolean spatiallyValid) {
    public DynamicElasticMetrics {
        edges = List.copyOf(edges);
    }
    public boolean allFinite() {
        return Double.isFinite(meanDistanceAccuracy)
                && Double.isFinite(satisfiedConnectionPercentage)
                && Double.isFinite(maximumNodeSpeed)
                && Double.isFinite(minimumObservedNonNeighborDistance)
                && Double.isFinite(meanLocalSpread)
                && Double.isFinite(minimumLocalSpread)
                && edges.stream().allMatch(edge -> Double.isFinite(edge.distance())
                && Double.isFinite(edge.accuracy()));
    }
}
