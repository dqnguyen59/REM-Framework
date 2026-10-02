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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

public final class TopologyScore {
    public static final double NUMERICAL_FAILURE_SCORE = 1_000_000;
    private final Map<String, TopologyScoreComponent> details;
    private final double total;
    private final boolean numericalFailure;

    private TopologyScore(Map<String, TopologyScoreComponent> details, boolean numericalFailure) {
        this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
        this.numericalFailure = numericalFailure;
        this.total = numericalFailure ? NUMERICAL_FAILURE_SCORE
                : details.values().stream().mapToDouble(TopologyScoreComponent::weightedContribution).sum();
    }

    public static TopologyScore calculate(GeometryDiscoveryResult geometry,
                                          TopologyMetrics topology,
                                          DirectionalBalanceMetrics directional,
                                          TopologySearchSettings settings) {
        if (!geometry.metrics().allFinite() || !finite(directional))
            return failure();
        GeometryDiscoveryMetrics g = geometry.metrics();
        TopologyScoreWeights w = settings.scoreWeights();
        double target = Math.max(Math.abs(geometry.settings().targetDistance()), 1e-12);
        double pairs = Math.max(1, topology.nodeCount() * (topology.nodeCount() - 1.0) / 2);
        double edges = Math.max(1, topology.edgeCount());
        Map<String, TopologyScoreComponent> values = new LinkedHashMap<>();
        add(values, "edgeLengthError", g.totalEdgeLengthError(),
                g.totalEdgeLengthError() / (edges * target * target), w.edgeLengthError(), true);
        add(values, "edgeLengthVariance",
                g.standardDeviationConnectedNeighborDistance()
                        * g.standardDeviationConnectedNeighborDistance(),
                Math.pow(g.standardDeviationConnectedNeighborDistance() / target, 2),
                w.edgeLengthVariance(), true);
        add(values, "separationCollision", g.collisionViolationCount(),
                g.collisionViolationCount() / pairs, w.separationCollision(), true);
        add(values, "nonConvergence", g.converged() ? 0 : 1,
                g.converged() ? 0 : 1, w.nonConvergence(), true);
        add(values, "directionalBias", directional.meanNormalizedDirectionalBias(),
                directional.meanNormalizedDirectionalBias(), w.directionalBias(), true);
        add(values, "tensorIsotropy", directional.meanTensorIsotropyError(),
                directional.meanTensorIsotropyError(), w.tensorIsotropy(),
                directional.tensorApplicableNodeCount() > 0);
        add(values, "knownReferenceAngular", directional.meanKnownReferenceAngularError(),
                directional.meanKnownReferenceAngularError(), w.knownReferenceAngular(),
                directional.angularReferenceApplicableNodeCount() > 0);
        add(values, "sphericalCoverage", directional.maximumUncoveredAngle(),
                directional.maximumUncoveredAngle() / Math.PI, w.sphericalCoverage(),
                directional.sphericalCoverageApplicableNodeCount() > 0);
        add(values, "oppositeDirection", directional.meanOppositeDirectionMismatch(),
                directional.meanOppositeDirectionMismatch() / Math.PI, w.oppositeDirection(),
                directional.oppositeDirectionApplicableNodeCount() > 0);
        double dimensionRaw = directional.localGlobalDimensionMismatchCount();
        add(values, "localDimensionality", dimensionRaw,
                dimensionRaw / Math.max(1, topology.nodeCount()), w.localDimensionality(), true);
        add(values, "degreeVariance", topology.degreeStandardDeviation(),
                topology.degreeStandardDeviation() / Math.max(1, topology.meanDegree()),
                w.degreeVariance(), true);
        add(values, "bridge", topology.bridgeEdgeCount(),
                topology.bridgeEdgeCount() / edges, w.bridgePenalty(), true);
        add(values, "articulationPoint", topology.articulationPointCount(),
                topology.articulationPointCount() / Math.max(1.0, topology.nodeCount()),
                w.articulationPenalty(), true);
        add(values, "graphDiameter", topology.graphDiameter(),
                topology.graphDiameter() / Math.max(1.0, topology.nodeCount() - 1),
                w.graphDiameterPreference(), true);
        boolean runtimeEnabled = w.runtimeCompatibility() != 0;
        add(values, "runtimeCompatibility", geometry.runtimeCompatibility().compatible() ? 0 : 1,
                geometry.runtimeCompatibility().compatible() ? 0 : 1,
                w.runtimeCompatibility(), runtimeEnabled);
        int targetDegree = switch (settings.scoringProfile()) {
            case TETRAHEDRAL -> 4;
            case CUBIC_RUNTIME -> 6;
            default -> 0;
        };
        double degreeDeviation = targetDegree == 0 ? 0
                : topology.degreeDistribution().entrySet().stream()
                .mapToDouble(e -> Math.abs(e.getKey() - targetDegree) * e.getValue())
                .sum() / Math.max(1, topology.nodeCount());
        add(values, "profileDegree", degreeDeviation,
                targetDegree == 0 ? 0 : degreeDeviation / targetDegree,
                w.profileDegree(), targetDegree != 0);
        return new TopologyScore(values, false);
    }

    /** Compatibility overload for version 0.1 tests and integrations. */
    public static TopologyScore calculate(GeometryDiscoveryMetrics geometry, TopologyMetrics topology,
                                          DirectionalBalanceMetrics directional,
                                          TopologyScoreWeights weights, int maximumDegree) {
        Map<String, TopologyScoreComponent> values = new LinkedHashMap<>();
        add(values, "edgeLengthError", geometry.totalEdgeLengthError(),
                geometry.totalEdgeLengthError(), weights.edgeLengthError(), true);
        add(values, "edgeLengthVariance", geometry.standardDeviationConnectedNeighborDistance(),
                geometry.standardDeviationConnectedNeighborDistance(),
                weights.edgeLengthVariance(), true);
        add(values, "directionalBias", directional.meanNormalizedDirectionalBias(),
                directional.meanNormalizedDirectionalBias(), weights.directionalBias(), true);
        add(values, "tensorIsotropy", directional.meanTensorIsotropyError(),
                directional.meanTensorIsotropyError(), weights.tensorIsotropy(), true);
        return new TopologyScore(values, !geometry.allFinite() || !finite(directional));
    }

    private static void add(Map<String, TopologyScoreComponent> values, String name, double raw,
                            double normalized, double weight, boolean applicable) {
        double contribution = applicable && weight != 0 ? normalized * weight : 0;
        values.put(name, new TopologyScoreComponent(name, raw, normalized, weight,
                contribution, applicable));
    }

    private static boolean finite(DirectionalBalanceMetrics d) {
        return Double.isFinite(d.meanNormalizedDirectionalBias())
                && Double.isFinite(d.meanTensorIsotropyError())
                && Double.isFinite(d.meanKnownReferenceAngularError())
                && Double.isFinite(d.maximumUncoveredAngle())
                && Double.isFinite(d.meanOppositeDirectionMismatch());
    }

    public static TopologyScore failure() {
        return new TopologyScore(Map.of("numericalFailure",
                new TopologyScoreComponent("numericalFailure", 1, 1,
                        NUMERICAL_FAILURE_SCORE, NUMERICAL_FAILURE_SCORE, true)), true);
    }

    public double total() { return total; }
    public boolean numericalFailure() { return numericalFailure; }
    public Map<String, TopologyScoreComponent> details() { return details; }
    public Map<String, Double> components() {
        Map<String, Double> result = new LinkedHashMap<>();
        details.forEach((name, value) -> result.put(name, value.weightedContribution()));
        return result;
    }

    // Version 0.1 accessor aliases.
    public double edgeLengthError() { return contribution("edgeLengthError"); }
    public double edgeLengthStandardDeviation() { return contribution("edgeLengthVariance"); }
    public double collisionPenalty() { return contribution("separationCollision"); }
    public double nonConvergencePenalty() { return contribution("nonConvergence"); }
    public double directionalImbalancePenalty() { return contribution("directionalBias"); }
    public double tensorAnisotropyPenalty() { return contribution("tensorIsotropy"); }
    public double degreeVariancePenalty() { return contribution("degreeVariance"); }
    public double bridgePenalty() { return contribution("bridge"); }
    public double articulationPointPenalty() { return contribution("articulationPoint"); }
    public double excessiveDegreePenalty() { return contribution("profileDegree"); }
    public double graphDiameterPreference() { return contribution("graphDiameter"); }
    private double contribution(String name) {
        TopologyScoreComponent value = details.get(name);
        return value == null ? 0 : value.weightedContribution();
    }
}
