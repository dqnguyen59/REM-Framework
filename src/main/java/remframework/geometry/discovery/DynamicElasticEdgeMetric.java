package remframework.geometry.discovery;

public record DynamicElasticEdgeMetric(
        int firstNodeId, int secondNodeId, double distance,
        double targetDistance, double relativeError, double accuracy,
        boolean satisfied) {
}
