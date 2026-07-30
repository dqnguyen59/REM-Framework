package remframework.geometry.discovery;

import java.util.Comparator;

public final class TopologySignature {
    private TopologySignature() { }

    public static String of(GeometryDiscoveryModel model) {
        String edges = model.connections().stream()
                .sorted(Comparator.comparingInt(DiscoveryConnection::lowerId)
                        .thenComparingInt(DiscoveryConnection::upperId))
                .map(edge -> edge.lowerId() + "-" + edge.upperId())
                .reduce((a, b) -> a + "," + b).orElse("");
        return model.nodes().size() + ":" + edges;
    }
}
