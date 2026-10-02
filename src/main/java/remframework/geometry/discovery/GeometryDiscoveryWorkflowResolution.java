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

import java.util.Map;

public record GeometryDiscoveryWorkflowResolution(
        GeometryDiscoveryWorkflowStage stage,
        GeometryDiscoveryWorkflowState state,
        GeometryDiscoveryRecommendation recommendation,
        Map<String, GeometryDiscoveryActionAvailability> actions) {
    public GeometryDiscoveryActionAvailability action(String name) {
        return actions.getOrDefault(name,
                GeometryDiscoveryActionAvailability.disabled("Action is unavailable."));
    }
}
