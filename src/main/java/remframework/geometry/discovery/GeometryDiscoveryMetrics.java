package remframework.geometry.discovery;

public record GeometryDiscoveryMetrics(
        int iterationCount,
        boolean converged,
        double totalEdgeLengthError,
        double meanConnectedNeighborDistance,
        double minimumConnectedNeighborDistance,
        double maximumConnectedNeighborDistance,
        double standardDeviationConnectedNeighborDistance,
        double minimumDistinctNodeDistance,
        int collisionViolationCount,
        int connectedComponentCount,
        double extentX,
        double extentY,
        double extentZ,
        String effectiveDimensionality) {

    public boolean allFinite() {
        return Double.isFinite(totalEdgeLengthError)
                && Double.isFinite(meanConnectedNeighborDistance)
                && Double.isFinite(minimumConnectedNeighborDistance)
                && Double.isFinite(maximumConnectedNeighborDistance)
                && Double.isFinite(standardDeviationConnectedNeighborDistance)
                && Double.isFinite(minimumDistinctNodeDistance)
                && Double.isFinite(extentX) && Double.isFinite(extentY)
                && Double.isFinite(extentZ);
    }
}
