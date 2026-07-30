package remframework.geometry.discovery;

import java.util.List;

public record LocalDirectionalDiagnostics(
        int nodeId,
        int degree,
        int localDimensionality,
        List<Double> eigenvalues,
        double normalizedDirectionalBias,
        double unnormalizedDirectionalBias,
        double tensorIsotropyError,
        List<Double> pairwiseDotProducts,
        double meanPairwiseDotProduct,
        double pairwiseDotProductVariance,
        boolean angularReferenceApplicable,
        AngularReferenceType angularReferenceType,
        double knownReferenceAngularError,
        boolean sphericalCoverageApplicable,
        double meanNearestDirectionAngle,
        double maximumUncoveredAngle,
        double angularCoverageVariance,
        boolean oppositeDirectionApplicable,
        double meanOppositeDirectionMismatch,
        double maximumOppositeDirectionMismatch,
        int missingOppositeDirectionCount) {
}
