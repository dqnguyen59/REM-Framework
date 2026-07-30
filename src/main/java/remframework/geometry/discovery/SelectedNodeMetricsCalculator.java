package remframework.geometry.discovery;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SelectedNodeMetricsCalculator {
    public SelectedNodeMetrics calculate(GeometryDiscoveryModel model, int nodeId,
                                         double targetDistance) {
        Map<Integer, DiscoveryNode> byId = new LinkedHashMap<>();
        for (DiscoveryNode node : model.sortedNodes()) byId.put(node.id(), node);
        DiscoveryNode selected = byId.get(nodeId);
        if (selected == null) throw new IllegalArgumentException("Unknown node ID: " + nodeId);
        Map<Integer, Double> distances = new LinkedHashMap<>();
        double sum = 0;
        double maximumError = 0;
        for (Integer neighborId : selected.neighborIds().stream().sorted().toList()) {
            DiscoveryNode neighbor = byId.get(neighborId);
            if (neighbor == null) continue;
            double distance = selected.position().distance(neighbor.position());
            distances.put(neighborId, distance);
            sum += distance;
            maximumError = Math.max(maximumError, Math.abs(distance - targetDistance));
        }
        return new SelectedNodeMetrics(nodeId, Map.copyOf(distances),
                distances.isEmpty() ? 0 : sum / distances.size(), maximumError);
    }
}
