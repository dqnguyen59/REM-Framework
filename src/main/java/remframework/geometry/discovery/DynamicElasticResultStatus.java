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

public enum DynamicElasticResultStatus {
    STABLE_CANDIDATE_FOUND,
    NO_EQUILIBRIUM_FOUND,
    NO_VALID_TOPOLOGY_GENERATED,
    IMPOSSIBLE_GRAPH_PARAMETERS,
    NUMERICAL_FAILURE
}
