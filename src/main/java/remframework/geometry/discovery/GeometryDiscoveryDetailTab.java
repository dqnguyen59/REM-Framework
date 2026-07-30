package remframework.geometry.discovery;

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
