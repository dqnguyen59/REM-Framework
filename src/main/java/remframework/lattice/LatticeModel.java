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

    default boolean mechanicsOnly() {return false;}
    default double mechanicalDivisor() {return 6;}
    default boolean fixedBoundary(NodeDefinition node,Vector3i dimensions) {
        var i=node.index();
        return (dimensions.x>1 && (i.x==0||i.x==dimensions.x-1))
            || (dimensions.y>1 && (i.y==0||i.y==dimensions.y-1))
            || (dimensions.z>1 && (i.z==0||i.z==dimensions.z-1));
    }
    record NodeDefinition(String id, Vector3i index, Vector3d position,
                          Map<Integer, String> neighbors) {
    }
}
