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

public enum GeometryDiscoveryDetailTab {
    OVERVIEW("Overview"),
    GEOMETRY("Geometry"),
    TOPOLOGY("Topology"),
    DIRECTIONAL("Directional"),
    SCORE("Score"),
    SELECTED_NODE("Selected Node"),
    HISTORY("History");

    private final String label;
    GeometryDiscoveryDetailTab(String label) { this.label = label; }
    public String label() { return label; }
}
