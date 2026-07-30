package remframework.geometry.discovery;

public record GeometryDiscoverySettings(
        double targetDistance,
        double learningRate,
        int maximumIterations,
        double convergenceTolerance,
        double minimumNodeSeparation,
        double repulsionStrength,
        long randomSeed,
        double maximumCorrection,
        double dimensionalityTolerance,
        int progressInterval) {

    public static GeometryDiscoverySettings defaults(double targetDistance) {
        return new GeometryDiscoverySettings(targetDistance, 0.08, 20_000, 1.0e-7,
                targetDistance * 0.35, 0.25, 1L, targetDistance * 0.2,
                targetDistance * 1.0e-6, 1_000);
    }

    public GeometryDiscoverySettings withTargetDistance(double value) {
        return new GeometryDiscoverySettings(value, learningRate, maximumIterations,
                convergenceTolerance, minimumNodeSeparation, repulsionStrength, randomSeed,
                maximumCorrection, dimensionalityTolerance, progressInterval);
    }

    public GeometryDiscoverySettings withMaximumIterations(int value) {
        return new GeometryDiscoverySettings(targetDistance, learningRate, value,
                convergenceTolerance, minimumNodeSeparation, repulsionStrength, randomSeed,
                maximumCorrection, dimensionalityTolerance, progressInterval);
    }

    public GeometryDiscoverySettings withRandomSeed(long value) {
        return new GeometryDiscoverySettings(targetDistance, learningRate, maximumIterations,
                convergenceTolerance, minimumNodeSeparation, repulsionStrength, value,
                maximumCorrection, dimensionalityTolerance, progressInterval);
    }
}
