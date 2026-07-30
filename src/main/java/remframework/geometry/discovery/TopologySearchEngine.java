package remframework.geometry.discovery;

public final class TopologySearchEngine {
    public TopologySearchResult search(GeometryDiscoveryModel model, TopologySearchSettings settings) {
        return new TopologySearchSession(model, settings).run();
    }
}
