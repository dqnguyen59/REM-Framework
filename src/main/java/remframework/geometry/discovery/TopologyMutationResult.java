package remframework.geometry.discovery;

public record TopologyMutationResult(
        GeometryDiscoveryModel model, TopologyMutation mutation,
        boolean valid, String rejectionReason) {
}
