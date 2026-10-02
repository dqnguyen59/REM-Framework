package remframework.geometry.discovery;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;

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
