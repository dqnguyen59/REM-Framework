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

public record TopologyCandidate(
        long candidateId, Long parentCandidateId, GeometryDiscoveryResult geometryResult,
        TopologyMetrics topologyMetrics, DirectionalBalanceMetrics directionalMetrics,
        TopologyScore score, TopologyMutation mutation, String topologySignature,
        String mutationHistorySummary) {
    public GeometryDiscoveryModel model() { return geometryResult.model(); }
}
