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

public enum DynamicElasticSearchPolicy {
    STOP_AT_FIRST_STABLE_CANDIDATE,
    FIND_ALL_STABLE_CANDIDATES,
    FIND_FIRST_STABLE_CANDIDATE_PER_NODE_COUNT
}
