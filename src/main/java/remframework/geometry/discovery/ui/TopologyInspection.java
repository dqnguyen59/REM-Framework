package remframework.geometry.discovery.ui;

import remframework.geometry.discovery.DiscoveryConnection;
import remframework.geometry.discovery.configuration.Configuration;

import java.util.List;

/** Immutable topology inspection data derived from the selected configuration. */
public record TopologyInspection(int vertexCount, int expectedDegree, int edgeCount,
        List<List<Integer>> adjacency, List<DiscoveryConnection> edges,
        List<Integer> degreeMismatches) {
    public TopologyInspection {
        adjacency = adjacency.stream().map(List::copyOf).toList();
        edges = edges.stream().map(e -> new DiscoveryConnection(e.lowerId(), e.upperId())).toList();
        degreeMismatches = List.copyOf(degreeMismatches);
    }

    public static TopologyInspection from(Configuration configuration) {
        List<Integer> mismatches = java.util.stream.IntStream.range(0, configuration.nodeCount())
                .filter(i -> configuration.adjacency().get(i).size() != configuration.exactDegree())
                .boxed().toList();
        return new TopologyInspection(configuration.nodeCount(), configuration.exactDegree(),
                configuration.edges().size(), configuration.adjacency(), configuration.edges(), mismatches);
    }

    public boolean adjacencySymmetric() {
        for (int node = 0; node < adjacency.size(); node++)
            for (int neighbor : adjacency.get(node))
                if (neighbor < 0 || neighbor >= adjacency.size() || !adjacency.get(neighbor).contains(node)) return false;
        return true;
    }

    public boolean simpleGraph() {
        return edges.stream().allMatch(e -> e.firstNodeId() != e.secondNodeId())
                && edges.stream().map(DiscoveryConnection::undirectedKey).distinct().count() == edges.size();
    }

    public List<String> adjacencyLines() {
        return java.util.stream.IntStream.range(0, adjacency.size())
                .mapToObj(i -> "Node " + i + ": " + adjacency.get(i).stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", ")))
                .toList();
    }

    public List<String> edgeLines() {
        return edges.stream().map(e -> e.lowerId() + " -- " + e.upperId()).toList();
    }
}
