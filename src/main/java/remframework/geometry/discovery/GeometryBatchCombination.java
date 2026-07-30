package remframework.geometry.discovery;

public record GeometryBatchCombination(int runId, long seed, double learningRate,
                                       double repulsionStrength, double maximumCorrection) {
}
