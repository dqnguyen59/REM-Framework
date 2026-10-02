package remframework.lattice;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.tomlj.TomlParseResult;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LatticeModelValidator {
    private static final double POSITION_TOLERANCE = 1.0e-9;

    public ValidationResult validate(LatticeModel model) {
        ValidationResult result = new ValidationResult();
        validate(model, null, result);
        return result;
    }

    void validate(LatticeModel model, TomlParseResult toml, ValidationResult result) {
        if (!Double.isFinite(model.equilibriumDistance()) || model.equilibriumDistance() <= 0)
            result.error("Equilibrium distance must be finite and greater than zero.");
        Vector3i dimensions = model.dimensions(new Vector3i(2, 2, 2));
        if (dimensions.x <= 0 || dimensions.y <= 0 || dimensions.z <= 0)
            result.error("All three lattice dimensions must be positive.");
        if (toml != null) {
            Long dimensionality = toml.getLong("model.dimensionality");
            if (dimensionality != null && dimensionality != 3)
                result.error("model.dimensionality must be 3.");
        }
        List<LatticeModel.NodeDefinition> nodes = model.nodes(new Vector3i(2, 2, 2));
        validateNodes(nodes, dimensions, model.equilibriumDistance(),
                toml == null || toml.getDouble("model.distanceTolerance") == null
                        ? POSITION_TOLERANCE : toml.getDouble("model.distanceTolerance"), result);
    }

    private void validateNodes(List<LatticeModel.NodeDefinition> nodes, Vector3i dimensions,
                               double equilibriumDistance,
                               double distanceTolerance, ValidationResult result) {
        Map<String, LatticeModel.NodeDefinition> byId = new HashMap<>();
        Set<String> positions = new HashSet<>();
        Set<Vector3i> indices = new HashSet<>();
        for (LatticeModel.NodeDefinition node : nodes) {
            if (node.id() == null || node.id().isBlank()) result.error("A node has a missing ID.");
            else if (byId.put(node.id(), node) != null) result.error("Duplicate node ID: " + node.id());
            if (node.index() == null) result.error("Node " + node.id() + " has no index.");
            else if (!indices.add(new Vector3i(node.index())))
                result.error("Duplicate node index: " + node.index());
            Vector3d p = node.position();
            if (p == null || !p.isFinite()) result.error("Node " + node.id() + " has invalid coordinates.");
            else if (!positions.add(positionKey(p)))
                result.error("Duplicate node position near: " + p);
            if (node.neighbors().size() > 6)
                result.error("Node " + node.id() + " has more than the six slots supported by Node 0.1.");
            Set<String> unique = new HashSet<>();
            for (String neighbor : node.neighbors().values()) {
                if (node.id() != null && node.id().equals(neighbor))
                    result.error("Node " + node.id() + " references itself.");
                if (!unique.add(neighbor))
                    result.error("Node " + node.id() + " has duplicate neighbor ID: " + neighbor);
            }
        }
        if (nodes.stream().allMatch(n -> n.index() != null)) {
            int maxX = nodes.stream().mapToInt(n -> n.index().x).max().orElse(-1);
            int maxY = nodes.stream().mapToInt(n -> n.index().y).max().orElse(-1);
            int maxZ = nodes.stream().mapToInt(n -> n.index().z).max().orElse(-1);
            if (nodes.stream().anyMatch(n -> n.index().x < 0 || n.index().y < 0 || n.index().z < 0))
                result.error("Node indices must be non-negative.");
            long rectangularSize = (long) (maxX + 1) * (maxY + 1) * (maxZ + 1);
            if (rectangularSize != nodes.size())
                result.error("Node indices must fill the rectangular runtime lattice without gaps.");
            if (nodes.size() != (long) dimensions.x * dimensions.y * dimensions.z
                    || maxX >= dimensions.x || maxY >= dimensions.y || maxZ >= dimensions.z)
                result.error("Node indices and count must match model.dimensions.");
        }
        for (LatticeModel.NodeDefinition node : nodes) {
            for (String neighborId : node.neighbors().values()) {
                LatticeModel.NodeDefinition neighbor = byId.get(neighborId);
                if (neighbor == null) {
                    result.error("Node " + node.id() + " references missing neighbor: " + neighborId);
                    continue;
                }
                if (!neighbor.neighbors().containsValue(node.id()))
                    result.error("Nonreciprocal neighbors: " + node.id() + " -> " + neighborId);
                if (node.position() != null && neighbor.position() != null
                        && Math.abs(node.position().distance(neighbor.position()) - equilibriumDistance)
                        > distanceTolerance)
                    result.error("Connected-node distance differs from equilibrium distance: "
                            + node.id() + " -> " + neighborId);
            }
        }
        if (!nodes.isEmpty() && byId.size() == nodes.size()) {
            Set<String> visited = new HashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(nodes.getFirst().id());
            while (!queue.isEmpty()) {
                String id = queue.remove();
                if (!visited.add(id)) continue;
                LatticeModel.NodeDefinition node = byId.get(id);
                if (node != null) queue.addAll(node.neighbors().values());
            }
            if (visited.size() != nodes.size())
                result.error("Lattice graph is disconnected: reached " + visited.size()
                        + " of " + nodes.size() + " nodes.");
        }
    }

    private String positionKey(Vector3d p) {
        return Math.round(p.x / POSITION_TOLERANCE) + ":"
                + Math.round(p.y / POSITION_TOLERANCE) + ":"
                + Math.round(p.z / POSITION_TOLERANCE);
    }
}
