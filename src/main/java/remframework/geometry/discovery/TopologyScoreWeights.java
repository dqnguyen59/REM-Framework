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

public record TopologyScoreWeights(
        double edgeLengthError,
        double edgeLengthVariance,
        double separationCollision,
        double nonConvergence,
        double directionalBias,
        double tensorIsotropy,
        double knownReferenceAngular,
        double sphericalCoverage,
        double oppositeDirection,
        double localDimensionality,
        double degreeVariance,
        double bridgePenalty,
        double articulationPenalty,
        double graphDiameterPreference,
        double runtimeCompatibility,
        double profileDegree) {

    public static TopologyScoreWeights forProfile(TopologyScoringProfile profile) {
        return switch (profile) {
            case GENERAL_GEOMETRY -> new TopologyScoreWeights(
                    5, 2, 10, 10, 0, 3, 1, 2, 0, 1, .5, 2, 2, .2, 0, 0);
            case WAVE_PROPAGATION -> new TopologyScoreWeights(
                    5, 2, 10, 10, .5, 2, .5, 4, 3, 3, .5, 2, 2, .2, 0, 0);
            case CENTROSYMMETRIC -> new TopologyScoreWeights(
                    5, 2, 10, 10, 4, 3, .5, 1, 6, 1, .5, 1, 1, .1, 0, 0);
            case TETRAHEDRAL -> new TopologyScoreWeights(
                    6, 3, 12, 10, 0, 5, 8, 2, 0, 2, 1, 1, 1, .1, 0, 6);
            case CUBIC_RUNTIME -> new TopologyScoreWeights(
                    6, 3, 12, 10, 3, 4, 3, 3, 8, 3, 1, 2, 2, .2, 20, 6);
        };
    }

    public static TopologyScoreWeights defaults() {
        return forProfile(TopologyScoringProfile.GENERAL_GEOMETRY);
    }

    public TopologyScoreWeights scaled(double scale) {
        return new TopologyScoreWeights(edgeLengthError * scale, edgeLengthVariance * scale,
                separationCollision * scale, nonConvergence * scale, directionalBias * scale,
                tensorIsotropy * scale, knownReferenceAngular * scale, sphericalCoverage * scale,
                oppositeDirection * scale, localDimensionality * scale, degreeVariance * scale,
                bridgePenalty * scale, articulationPenalty * scale,
                graphDiameterPreference * scale, runtimeCompatibility * scale,
                profileDegree * scale);
    }

    public TopologyScoreWeights override(String name, double value) {
        return new TopologyScoreWeights(
                name.equals("edge-length-error") ? value : edgeLengthError,
                name.equals("edge-length-variance") ? value : edgeLengthVariance,
                name.equals("separation") ? value : separationCollision,
                name.equals("non-convergence") ? value : nonConvergence,
                name.equals("directional-bias") ? value : directionalBias,
                name.equals("tensor-isotropy") ? value : tensorIsotropy,
                name.equals("angular-reference") ? value : knownReferenceAngular,
                name.equals("spherical-coverage") ? value : sphericalCoverage,
                name.equals("opposite-direction") ? value : oppositeDirection,
                name.equals("local-dimensionality") ? value : localDimensionality,
                name.equals("degree-variance") ? value : degreeVariance,
                name.equals("bridges") ? value : bridgePenalty,
                name.equals("articulation") ? value : articulationPenalty,
                name.equals("diameter") ? value : graphDiameterPreference,
                name.equals("runtime-compatibility") ? value : runtimeCompatibility,
                name.equals("profile-degree") ? value : profileDegree);
    }
}
