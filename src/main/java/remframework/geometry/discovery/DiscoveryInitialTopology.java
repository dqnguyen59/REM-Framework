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

public enum DiscoveryInitialTopology {
    LINE, RING, CUBIC_GRID, RANDOM_TREE, RANDOM_CONNECTED_GRAPH,
    COMPLETE_GRAPH, LOAD_CANDIDATE, LOAD_TOML
}
