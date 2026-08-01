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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GeometryDiscoveryModel {
    private final String name;
    private final String description;
    private final List<DiscoveryNode> nodes;
    private final List<DiscoveryConnection> connections;

    public GeometryDiscoveryModel(String name, String description, List<DiscoveryNode> nodes,
                                  List<DiscoveryConnection> connections) {
        this.name = name;
        this.description = description;
        this.nodes = new ArrayList<>(nodes);
        this.connections = new ArrayList<>(connections);
        populateNeighbors();
    }

    private void populateNeighbors() {
        Map<Integer, DiscoveryNode> byId = new LinkedHashMap<>();
        for (DiscoveryNode node : nodes) byId.putIfAbsent(node.id(), node);
        for (DiscoveryConnection connection : connections) {
            DiscoveryNode first = byId.get(connection.firstNodeId());
            DiscoveryNode second = byId.get(connection.secondNodeId());
            if (first != null) first.addNeighbor(connection.secondNodeId());
            if (second != null) second.addNeighbor(connection.firstNodeId());
        }
    }

    public GeometryDiscoveryModel copy() {
        return new GeometryDiscoveryModel(name, description,
                nodes.stream().map(DiscoveryNode::copy).toList(), connections);
    }

    public String name() { return name; }
    public String description() { return description; }
    public List<DiscoveryNode> nodes() { return List.copyOf(nodes); }
    public List<DiscoveryConnection> connections() { return List.copyOf(connections); }
    public List<DiscoveryNode> sortedNodes() {
        return nodes.stream().sorted(Comparator.comparingInt(DiscoveryNode::id)).toList();
    }
}
