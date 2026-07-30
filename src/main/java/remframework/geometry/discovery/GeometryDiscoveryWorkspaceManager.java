package remframework.geometry.discovery;

public final class GeometryDiscoveryWorkspaceManager {
    private static final GeometryDiscoveryWorkspaceController INSTANCE =
            new GeometryDiscoveryWorkspaceController();
    private GeometryDiscoveryWorkspaceManager() { }
    public static GeometryDiscoveryWorkspaceController getInstance() { return INSTANCE; }
}
