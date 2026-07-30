package remframework.geometry.discovery;

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
