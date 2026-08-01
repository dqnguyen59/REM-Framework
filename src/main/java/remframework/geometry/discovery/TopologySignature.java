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
