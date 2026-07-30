package remframework.geometry.discovery;

import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GeometryRuntimeCompatibilityAnalyzer {
    public RuntimeCompatibility analyze(GeometryDiscoveryModel model, double tolerance) {
        if (model.nodes().isEmpty())
            return incompatible("Candidate contains no nodes.");
        for (DiscoveryNode node : model.nodes()) {
            if (node.neighborIds().size() > 6)
                return incompatible("At least one node has more than six neighbors.");
        }
        List<Double> xs = clusters(model.nodes(), 0, tolerance);
        List<Double> ys = clusters(model.nodes(), 1, tolerance);
        List<Double> zs = clusters(model.nodes(), 2, tolerance);
        long expected = (long) xs.size() * ys.size() * zs.size();
        if (expected != model.nodes().size())
            return incompatible("Candidate does not form a complete rectangular indexed lattice.");

        Map<Integer, Vector3i> indices = new LinkedHashMap<>();
        Set<Vector3i> occupied = new HashSet<>();
        for (DiscoveryNode node : model.nodes()) {
            Vector3d p = node.position();
            Vector3i index = new Vector3i(nearest(xs, p.x), nearest(ys, p.y), nearest(zs, p.z));
            if (!occupied.add(new Vector3i(index)))
                return incompatible("Multiple nodes map to the same rectangular grid index.");
            indices.put(node.id(), index);
        }
        Set<Long> actualEdges = new HashSet<>();
        for (DiscoveryConnection connection : model.connections())
            actualEdges.add(connection.undirectedKey());
        Map<Vector3i, Integer> idByIndex = new HashMap<>();
        indices.forEach((id, index) -> idByIndex.put(new Vector3i(index), id));
        Set<Long> expectedEdges = new HashSet<>();
        int[][] positiveOffsets = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        for (Map.Entry<Integer, Vector3i> entry : indices.entrySet()) {
            for (int[] offset : positiveOffsets) {
                Integer neighborId = idByIndex.get(new Vector3i(entry.getValue()).add(
                        offset[0], offset[1], offset[2]));
                if (neighborId != null)
                    expectedEdges.add(new DiscoveryConnection(entry.getKey(), neighborId).undirectedKey());
            }
        }
        if (!actualEdges.equals(expectedEdges))
            return incompatible("Connections are not exactly the open-boundary six-neighbor rectangular topology.");
        return new RuntimeCompatibility(true,
                "Candidate forms a complete rectangular six-neighbor indexed lattice.",
                new Vector3i(xs.size(), ys.size(), zs.size()), Map.copyOf(indices));
    }

    private RuntimeCompatibility incompatible(String reason) {
        return new RuntimeCompatibility(false, reason, new Vector3i(), Map.of());
    }

    private List<Double> clusters(List<DiscoveryNode> nodes, int axis, double tolerance) {
        List<Double> values = new ArrayList<>();
        for (DiscoveryNode node : nodes) {
            double value = node.position().get(axis);
            if (values.stream().noneMatch(existing -> Math.abs(existing - value) <= tolerance))
                values.add(value);
        }
        values.sort(Comparator.naturalOrder());
        return values;
    }

    private int nearest(List<Double> values, double target) {
        int best = 0;
        for (int i = 1; i < values.size(); i++)
            if (Math.abs(values.get(i) - target) < Math.abs(values.get(best) - target)) best = i;
        return best;
    }
}
