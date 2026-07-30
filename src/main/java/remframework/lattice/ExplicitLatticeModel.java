package remframework.lattice;

import org.joml.Vector3i;

import java.util.List;

public record ExplicitLatticeModel(double equilibriumDistance, Vector3i fixedDimensions,
                                   List<NodeDefinition> nodeDefinitions) implements LatticeModel {
    @Override public String type() { return "explicit"; }
    @Override public Vector3i dimensions(Vector3i ignored) { return new Vector3i(fixedDimensions); }
    @Override public List<NodeDefinition> nodes(Vector3i ignored) { return nodeDefinitions; }
}
