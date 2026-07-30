package remframework.geometry.discovery;

public record TopologyScoreWeights(
        double edgeLengthError,
        double edgeLengthStandardDeviation,
        double collisionPenalty,
        double nonConvergencePenalty,
        double directionalImbalance,
        double tensorAnisotropy,
        double degreeVariance,
        double bridgePenalty,
        double articulationPenalty,
        double excessiveDegreePenalty,
        double graphDiameterPreference) {
    public static TopologyScoreWeights defaults() {
        return new TopologyScoreWeights(1, 2, 100, 1000, 3, 5, 0.5,
                2, 3, 100, 0.1);
    }

    public TopologyScoreWeights scaled(double scale) {
        return new TopologyScoreWeights(edgeLengthError * scale,
                edgeLengthStandardDeviation * scale, collisionPenalty * scale,
                nonConvergencePenalty * scale, directionalImbalance * scale,
                tensorAnisotropy * scale, degreeVariance * scale, bridgePenalty * scale,
                articulationPenalty * scale, excessiveDegreePenalty * scale,
                graphDiameterPreference * scale);
    }
}
