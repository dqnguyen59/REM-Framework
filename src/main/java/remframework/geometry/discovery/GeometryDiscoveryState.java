package remframework.geometry.discovery;

public record GeometryDiscoveryState(Status status, int iteration,
                                     GeometryDiscoveryMetrics metrics,
                                     boolean valid) {
    public enum Status { READY, RUNNING, PAUSED, CONVERGED, STOPPED, INVALID }
}
