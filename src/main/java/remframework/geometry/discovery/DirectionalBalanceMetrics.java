package remframework.geometry.discovery;

public record DirectionalBalanceMetrics(
        double meanLocalImbalance, double maximumLocalImbalance,
        double standardDeviationLocalImbalance, int worstNodeId,
        double meanTensorAnisotropy, int insufficientDegreeNodeCount) {
}
