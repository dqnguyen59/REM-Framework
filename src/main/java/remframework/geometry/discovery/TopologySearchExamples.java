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

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

public final class TopologySearchExamples {
    private TopologySearchExamples() { }

    public static GeometryDiscoveryModel create(String name, int nodes, double distance, long seed) {
        if (!"generated".equalsIgnoreCase(name))
            return GeometryDiscoveryExamples.create(name, distance);
        if (nodes < 4 || nodes > 32) throw new IllegalArgumentException("nodes must be 4..32");
        SplittableRandom random = new SplittableRandom(seed);
        List<DiscoveryNode> nodeList = new ArrayList<>();
        List<DiscoveryConnection> edges = new ArrayList<>();
        for (int i = 0; i < nodes; i++) {
            double angle = 2 * Math.PI * i / nodes;
            nodeList.add(new DiscoveryNode(i, new Vector3d(Math.cos(angle) * distance,
                    Math.sin(angle) * distance, (random.nextDouble() - .5) * distance), i == 0));
            edges.add(new DiscoveryConnection(i, (i + 1) % nodes));
        }
        for (int i = 0; i < nodes / 2; i++) {
            int other = (i + nodes / 2) % nodes;
            if (i != other) edges.add(new DiscoveryConnection(i, other));
        }
        return new GeometryDiscoveryModel("generated-" + nodes,
                "Deterministic connected topology-search seed graph", nodeList, edges);
    }
}
