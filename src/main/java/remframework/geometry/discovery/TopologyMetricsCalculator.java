package remframework.geometry.discovery;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class TopologyMetricsCalculator {
    public TopologyMetrics calculate(GeometryDiscoveryModel model) {
        Map<Integer, Set<Integer>> graph = graph(model);
        int n = graph.size();
        if (n == 0) return new TopologyMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of());
        int min = Integer.MAX_VALUE, max = 0, sum = 0;
        Map<Integer, Integer> histogram = new TreeMap<>();
        for (Set<Integer> neighbors : graph.values()) {
            int degree = neighbors.size();
            min = Math.min(min, degree);
            max = Math.max(max, degree);
            sum += degree;
            histogram.merge(degree, 1, Integer::sum);
        }
        double mean = (double) sum / n;
        double variance = graph.values().stream()
                .mapToDouble(v -> Math.pow(v.size() - mean, 2)).sum() / n;
        long distanceSum = 0;
        long pairCount = 0;
        int diameter = 0;
        for (int source : graph.keySet()) {
            Map<Integer, Integer> distances = distances(source, graph);
            for (var entry : distances.entrySet()) {
                if (source < entry.getKey()) {
                    distanceSum += entry.getValue();
                    pairCount++;
                    diameter = Math.max(diameter, entry.getValue());
                }
            }
        }
        int triangles = 0;
        double clusteringSum = 0;
        for (int node : graph.keySet()) {
            List<Integer> neighbors = new ArrayList<>(graph.get(node));
            int links = 0;
            for (int i = 0; i < neighbors.size(); i++)
                for (int j = i + 1; j < neighbors.size(); j++)
                    if (graph.get(neighbors.get(i)).contains(neighbors.get(j))) links++;
            if (neighbors.size() >= 2)
                clusteringSum += (2.0 * links) / (neighbors.size() * (neighbors.size() - 1));
            triangles += links;
        }
        Tarjan result = new Tarjan(graph);
        result.run();
        return new TopologyMetrics(n, model.connections().size(), min, max, mean, Math.sqrt(variance),
                TopologyConstraintValidator.components(model), diameter,
                pairCount == 0 ? 0 : (double) distanceSum / pairCount,
                clusteringSum / n, triangles / 3, result.bridges, result.articulations.size(),
                Map.copyOf(histogram));
    }

    static Map<Integer, Set<Integer>> graph(GeometryDiscoveryModel model) {
        Map<Integer, Set<Integer>> graph = new LinkedHashMap<>();
        model.sortedNodes().forEach(n -> graph.put(n.id(), new HashSet<>()));
        for (DiscoveryConnection edge : model.connections()) {
            if (graph.containsKey(edge.firstNodeId()) && graph.containsKey(edge.secondNodeId())) {
                graph.get(edge.firstNodeId()).add(edge.secondNodeId());
                graph.get(edge.secondNodeId()).add(edge.firstNodeId());
            }
        }
        return graph;
    }

    private static Map<Integer, Integer> distances(int source, Map<Integer, Set<Integer>> graph) {
        Map<Integer, Integer> result = new HashMap<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        result.put(source, 0);
        queue.add(source);
        while (!queue.isEmpty()) {
            int current = queue.remove();
            for (int next : graph.get(current).stream().sorted().toList())
                if (!result.containsKey(next)) {
                    result.put(next, result.get(current) + 1);
                    queue.add(next);
                }
        }
        return result;
    }

    private static final class Tarjan {
        private final Map<Integer, Set<Integer>> graph;
        private final Map<Integer, Integer> discovery = new HashMap<>();
        private final Map<Integer, Integer> low = new HashMap<>();
        private final Set<Integer> articulations = new HashSet<>();
        private int time;
        private int bridges;

        private Tarjan(Map<Integer, Set<Integer>> graph) { this.graph = graph; }
        private void run() {
            for (int node : graph.keySet()) if (!discovery.containsKey(node)) visit(node, -1);
        }
        private void visit(int node, int parent) {
            discovery.put(node, ++time);
            low.put(node, time);
            int children = 0;
            for (int next : graph.get(node).stream().sorted().toList()) {
                if (next == parent) continue;
                if (!discovery.containsKey(next)) {
                    children++;
                    visit(next, node);
                    low.put(node, Math.min(low.get(node), low.get(next)));
                    if (low.get(next) > discovery.get(node)) bridges++;
                    if (parent != -1 && low.get(next) >= discovery.get(node)) articulations.add(node);
                } else low.put(node, Math.min(low.get(node), discovery.get(next)));
            }
            if (parent == -1 && children > 1) articulations.add(node);
        }
    }
}
