package remframework.geometry.discovery;

import java.util.Map;

public record DirectionalBalanceMetrics(
        double meanNormalizedDirectionalBias,
        double maximumNormalizedDirectionalBias,
        double standardDeviationNormalizedDirectionalBias,
        int worstBiasNodeId,
        double meanTensorIsotropyError,
        int tensorApplicableNodeCount,
        double meanKnownReferenceAngularError,
        int angularReferenceApplicableNodeCount,
        double meanPairwiseDotProduct,
        double meanPairwiseDotProductVariance,
        double meanNearestDirectionAngle,
        double maximumUncoveredAngle,
        double meanAngularCoverageVariance,
        int sphericalCoverageApplicableNodeCount,
        double meanOppositeDirectionMismatch,
        double maximumOppositeDirectionMismatch,
        int missingOppositeDirectionCount,
        int oppositeDirectionApplicableNodeCount,
        int dimensionDeficientNodeCount,
        int localGlobalDimensionMismatchCount,
        int globalDimensionality,
        Map<Integer, LocalDirectionalDiagnostics> localDiagnostics) {

    // Backward-compatible names for version 0.1 callers and files.
    public double meanLocalImbalance() { return meanNormalizedDirectionalBias; }
    public double maximumLocalImbalance() { return maximumNormalizedDirectionalBias; }
    public double standardDeviationLocalImbalance() {
        return standardDeviationNormalizedDirectionalBias;
    }
    public int worstNodeId() { return worstBiasNodeId; }
    public double meanTensorAnisotropy() { return meanTensorIsotropyError; }
    public int insufficientDegreeNodeCount() { return dimensionDeficientNodeCount; }
}
