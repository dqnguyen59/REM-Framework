package remframework.lattice;

import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.List;
import java.util.Map;

public interface LatticeModel {
    String type();
    double equilibriumDistance();
    Vector3i dimensions(Vector3i projectDimensions);
    List<NodeDefinition> nodes(Vector3i projectDimensions);

    record NodeDefinition(String id, Vector3i index, Vector3d position,
                          Map<Integer, String> neighbors) {
    }
}
