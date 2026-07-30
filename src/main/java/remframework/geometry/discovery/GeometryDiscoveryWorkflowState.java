package remframework.geometry.discovery;

public enum GeometryDiscoveryWorkflowState {
    NO_CANDIDATE,
    CANDIDATE_LOADED,
    RELAXATION_REQUIRED,
    RELAXING,
    GEOMETRY_CONVERGED,
    READY_FOR_EVALUATION,
    READY_FOR_SEARCH,
    SEARCHING,
    SEARCH_PAUSED,
    BEST_CANDIDATE_AVAILABLE,
    ERROR
}
