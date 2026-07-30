package remframework.geometry.discovery;

public record TopologyCandidate(
        long candidateId, Long parentCandidateId, GeometryDiscoveryResult geometryResult,
        TopologyMetrics topologyMetrics, DirectionalBalanceMetrics directionalMetrics,
        TopologyScore score, TopologyMutation mutation, String topologySignature,
        String mutationHistorySummary) {
    public GeometryDiscoveryModel model() { return geometryResult.model(); }
}
