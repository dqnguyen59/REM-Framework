package remframework.geometry.discovery;

public final class TopologyEvaluator {
    private final GeometryDiscoveryEngine geometryEngine = new GeometryDiscoveryEngine();
    private final TopologyMetricsCalculator topologyCalculator = new TopologyMetricsCalculator();
    private final DirectionalBalanceCalculator directionalCalculator = new DirectionalBalanceCalculator();

    public TopologyCandidate evaluate(long candidateId, TopologyCandidate parent,
                                      GeometryDiscoveryModel model, TopologyMutation mutation,
                                      TopologySearchSettings settings) {
        GeometryDiscoveryResult geometry = geometryEngine.relax(model.copy(), settings.geometrySettings());
        TopologyMetrics topology = topologyCalculator.calculate(geometry.model());
        DirectionalBalanceMetrics directional = directionalCalculator.calculate(geometry.model());
        TopologyScore score = TopologyScore.calculate(geometry.metrics(), topology, directional,
                settings.scoreWeights(), settings.maximumDegree());
        String history = parent == null ? "initial"
                : parent.mutationHistorySummary() + " -> " + mutation.description();
        return new TopologyCandidate(candidateId, parent == null ? null : parent.candidateId(),
                geometry, topology, directional, score, mutation,
                TopologySignature.of(model), history);
    }
}
