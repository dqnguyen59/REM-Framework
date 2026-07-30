package remframework.geometry.discovery;

import java.util.LinkedHashMap;
import java.util.Map;

public record TopologyScore(
        double edgeLengthError, double edgeLengthStandardDeviation,
        double collisionPenalty, double nonConvergencePenalty,
        double directionalImbalancePenalty, double tensorAnisotropyPenalty,
        double degreeVariancePenalty, double bridgePenalty,
        double articulationPointPenalty, double excessiveDegreePenalty,
        double graphDiameterPreference, double total) {
    public static TopologyScore calculate(GeometryDiscoveryMetrics geometry, TopologyMetrics topology,
                                          DirectionalBalanceMetrics directional,
                                          TopologyScoreWeights weights, int maximumDegree) {
        double excessive = Math.max(0, topology.maximumDegree() - maximumDegree);
        double a = weights.edgeLengthError() * geometry.totalEdgeLengthError();
        double b = weights.edgeLengthStandardDeviation()
                * geometry.standardDeviationConnectedNeighborDistance();
        double c = weights.collisionPenalty() * geometry.collisionViolationCount();
        double d = geometry.converged() ? 0 : weights.nonConvergencePenalty();
        double e = weights.directionalImbalance() * directional.meanLocalImbalance();
        double f = weights.tensorAnisotropy() * directional.meanTensorAnisotropy();
        double g = weights.degreeVariance() * topology.degreeStandardDeviation();
        double h = weights.bridgePenalty() * topology.bridgeEdgeCount();
        double i = weights.articulationPenalty() * topology.articulationPointCount();
        double j = weights.excessiveDegreePenalty() * excessive;
        double k = weights.graphDiameterPreference() * topology.graphDiameter();
        return new TopologyScore(a, b, c, d, e, f, g, h, i, j, k,
                a + b + c + d + e + f + g + h + i + j + k);
    }

    public Map<String, Double> components() {
        Map<String, Double> result = new LinkedHashMap<>();
        result.put("edgeLengthError", edgeLengthError);
        result.put("edgeLengthStandardDeviation", edgeLengthStandardDeviation);
        result.put("collisionPenalty", collisionPenalty);
        result.put("nonConvergencePenalty", nonConvergencePenalty);
        result.put("directionalImbalancePenalty", directionalImbalancePenalty);
        result.put("tensorAnisotropyPenalty", tensorAnisotropyPenalty);
        result.put("degreeVariancePenalty", degreeVariancePenalty);
        result.put("bridgePenalty", bridgePenalty);
        result.put("articulationPointPenalty", articulationPointPenalty);
        result.put("excessiveDegreePenalty", excessiveDegreePenalty);
        result.put("graphDiameterPreference", graphDiameterPreference);
        return result;
    }
}
