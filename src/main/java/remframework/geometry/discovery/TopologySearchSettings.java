package remframework.geometry.discovery;

public record TopologySearchSettings(
        TopologySearchMode searchMode,
        long randomSeed,
        int maximumSearchIterations,
        int mutationsAttemptedPerIteration,
        int minimumDegree,
        int maximumDegree,
        int requiredEdgeCount,
        int archiveSize,
        double initialTemperature,
        double coolingRate,
        double minimumTemperature,
        double scoreImprovementTolerance,
        GeometryDiscoverySettings geometrySettings,
        TopologyScoreWeights scoreWeights,
        int checkpointInterval) {
    public static TopologySearchSettings defaults(long seed,
                                                  GeometryDiscoverySettings geometry) {
        return new TopologySearchSettings(TopologySearchMode.HILL_CLIMBING, seed,
                1000, 1, 1, 6, -1, 20, 1, 0.997, 1e-6,
                1e-9, geometry, TopologyScoreWeights.defaults(), 100);
    }
}
