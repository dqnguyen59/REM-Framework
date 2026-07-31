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
        boolean spatiallyValid,
        int localAngleCount,
        int localAnglesSatisfied,
        double meanAngleErrorDegrees,
        double maximumAngleErrorDegrees,
        double targetAngleDegrees,
        double angleToleranceDegrees,
        boolean canonicalGeometryValid) {
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
                && Double.isFinite(meanAngleErrorDegrees)
                && Double.isFinite(maximumAngleErrorDegrees)
                && Double.isFinite(targetAngleDegrees)
                && Double.isFinite(angleToleranceDegrees)
                && edges.stream().allMatch(edge -> Double.isFinite(edge.distance())
                && Double.isFinite(edge.accuracy()));
    }
}
