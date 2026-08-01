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
