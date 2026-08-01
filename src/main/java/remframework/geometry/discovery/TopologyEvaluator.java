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

public final class TopologyEvaluator {
    private final GeometryDiscoveryEngine geometryEngine = new GeometryDiscoveryEngine();
    private final TopologyMetricsCalculator topologyCalculator = new TopologyMetricsCalculator();
    private final DirectionalBalanceCalculator directionalCalculator = new DirectionalBalanceCalculator();

    public TopologyCandidate evaluate(long candidateId, TopologyCandidate parent,
                                      GeometryDiscoveryModel model, TopologyMutation mutation,
                                      TopologySearchSettings settings) {
        GeometryDiscoveryResult geometry = geometryEngine.relax(model.copy(), settings.geometrySettings());
        TopologyMetrics topology = topologyCalculator.calculate(geometry.model());
        DirectionalBalanceMetrics directional = directionalCalculator.calculate(
                geometry.model(), settings.diagnosticSettings());
        TopologyScore score = TopologyScore.calculate(geometry, topology, directional, settings);
        String history = parent == null ? "initial"
                : parent.mutationHistorySummary() + " -> " + mutation.description();
        return new TopologyCandidate(candidateId, parent == null ? null : parent.candidateId(),
                geometry, topology, directional, score, mutation,
                TopologySignature.of(model), history);
    }
}
