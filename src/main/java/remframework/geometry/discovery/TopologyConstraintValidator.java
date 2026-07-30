package remframework.geometry.discovery;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class TopologyConstraintValidator {
    public DiscoveryValidationResult validate(GeometryDiscoveryModel model,
                                              TopologySearchSettings settings) {
        DiscoveryValidationResult result = new DiscoveryValidationResult();
        if (settings.maximumSearchIterations() < 0 || settings.mutationsAttemptedPerIteration() < 1)
            result.error("SEARCH_ITERATIONS", "Search iteration settings are invalid.");
        if (settings.minimumDegree() < 0 || settings.maximumDegree() < settings.minimumDegree())
            result.error("DEGREE_LIMITS", "Degree limits are invalid.");
        if (settings.archiveSize() < 1 || !Double.isFinite(settings.initialTemperature())
                || settings.initialTemperature() < 0 || !Double.isFinite(settings.coolingRate())
                || settings.coolingRate() <= 0 || settings.coolingRate() > 1)
            result.error("SEARCH_NUMERICS", "Search numerical settings are invalid.");
        if (model.nodes().size() < 4 || model.nodes().size() > 32)
            result.error("NODE_LIMIT", "Topology search supports 4 to 32 nodes.");
        Map<Integer, Integer> degree = new HashMap<>();
        for (DiscoveryNode node : model.nodes()) degree.put(node.id(), 0);
        Set<Long> edges = new HashSet<>();
        for (DiscoveryConnection edge : model.connections()) {
            if (edge.firstNodeId() == edge.secondNodeId())
                result.error("SELF_EDGE", "Self-edge at node " + edge.firstNodeId());
            if (!degree.containsKey(edge.firstNodeId()) || !degree.containsKey(edge.secondNodeId()))
                result.error("MISSING_ENDPOINT", "Edge endpoint does not exist.");
            if (!edges.add(edge.undirectedKey()))
                result.error("DUPLICATE_EDGE", "Duplicate edge " + edge.lowerId() + "-" + edge.upperId());
            degree.computeIfPresent(edge.firstNodeId(), (id, value) -> value + 1);
            degree.computeIfPresent(edge.secondNodeId(), (id, value) -> value + 1);
        }
        degree.forEach((id, value) -> {
            if (value < settings.minimumDegree())
                result.error("MINIMUM_DEGREE", "Node " + id + " degree " + value
                        + " is below " + settings.minimumDegree());
            if (value > settings.maximumDegree())
                result.error("MAXIMUM_DEGREE", "Node " + id + " degree " + value
                        + " exceeds " + settings.maximumDegree());
        });
        if (settings.requiredEdgeCount() >= 0
                && model.connections().size() != settings.requiredEdgeCount())
            result.error("EDGE_COUNT", "Edge count does not match the configured requirement.");
        if (components(model) != 1)
            result.error("DISCONNECTED_GRAPH", "Topology must remain connected.");
        return result;
    }

    static int components(GeometryDiscoveryModel model) {
        if (model.nodes().isEmpty()) return 0;
        Map<Integer, Set<Integer>> adjacency = adjacency(model);
        Set<Integer> visited = new HashSet<>();
        int components = 0;
        for (Integer start : adjacency.keySet()) {
            if (visited.contains(start)) continue;
            components++;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                int node = queue.remove();
                if (visited.add(node)) queue.addAll(adjacency.get(node));
            }
        }
        return components;
    }

    static Map<Integer, Set<Integer>> adjacency(GeometryDiscoveryModel model) {
        Map<Integer, Set<Integer>> result = new java.util.TreeMap<>();
        for (DiscoveryNode node : model.nodes()) result.put(node.id(), new java.util.TreeSet<>());
        for (DiscoveryConnection edge : model.connections()) {
            if (result.containsKey(edge.firstNodeId()) && result.containsKey(edge.secondNodeId())) {
                result.get(edge.firstNodeId()).add(edge.secondNodeId());
                result.get(edge.secondNodeId()).add(edge.firstNodeId());
            }
        }
        return result;
    }
}
