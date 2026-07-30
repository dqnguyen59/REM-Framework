package remframework.geometry.discovery;

import java.util.List;

public record TopologyMutation(TopologyMutationType type,
                               List<DiscoveryConnection> removedEdges,
                               List<DiscoveryConnection> addedEdges,
                               String description) {
}
