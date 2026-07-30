package remframework.geometry.discovery;

import java.util.Map;

public record SelectedNodeMetrics(int nodeId, Map<Integer, Double> neighborDistances,
                                  double meanLocalEdgeDistance,
                                  double maximumLocalEdgeError) {
}
