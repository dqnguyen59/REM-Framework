package remframework.opengl.model;

import org.joml.Vector3d;
import remframework.opengl.helpers.Neighbor;
import remframework.opengl.model.entity.Node;

import java.util.ArrayList;
import java.util.List;

/** Read-only numerical view of the values used by Node.measureGradientImbalance(). */
public final class NeighborhoodInspectorModel {
    private NeighborhoodInspectorModel() { }

    public record NeighborValue(Neighbor direction, Node node, Vector3d position,
                                Vector3d delta, double distance,
                                Vector3d velocityDifference, Vector3d accelerationDifference) {
        public NeighborValue {
            position = new Vector3d(position);
            delta = new Vector3d(delta);
            velocityDifference = new Vector3d(velocityDifference);
            accelerationDifference = new Vector3d(accelerationDifference);
        }
    }

    public record Snapshot(Node source, Vector3d sourcePosition, List<NeighborValue> neighbors,
                           Vector3d gradient, Vector3d pairX, Vector3d pairY, Vector3d pairZ,
                           Vector3d weightedGradient) {
        public Snapshot {
            sourcePosition = new Vector3d(sourcePosition);
            neighbors = List.copyOf(neighbors);
            gradient = new Vector3d(gradient);
            pairX = new Vector3d(pairX);
            pairY = new Vector3d(pairY);
            pairZ = new Vector3d(pairZ);
            weightedGradient = new Vector3d(weightedGradient);
        }
    }

    public static Snapshot read(Node source) {
        if (source == null) return null;
        Vector3d sourcePosition = new Vector3d(source.getPosition());
        List<NeighborValue> values = new ArrayList<>();
        for (Neighbor direction : Neighbor.values()) {
            Node neighbor = source.getNeighbor(direction);
            if (neighbor == null) continue;
            Vector3d position = new Vector3d(neighbor.getPosition());
            Vector3d delta = new Vector3d(position).sub(sourcePosition);
            values.add(new NeighborValue(direction, neighbor, position, delta, delta.length(),
                    new Vector3d(neighbor.getVelocity()).sub(source.getVelocity()),
                    new Vector3d(neighbor.getAcceleration()).sub(source.getAcceleration())));
        }
        Vector3d right = deltaFor(values, Neighbor.RIGHT);
        Vector3d left = deltaFor(values, Neighbor.LEFT);
        Vector3d top = deltaFor(values, Neighbor.TOP);
        Vector3d bottom = deltaFor(values, Neighbor.BOTTOM);
        Vector3d front = deltaFor(values, Neighbor.FRONT);
        Vector3d back = deltaFor(values, Neighbor.BACK);
        return new Snapshot(source, sourcePosition, values, source.readGradientImbalance(),
                new Vector3d(right).sub(left), new Vector3d(top).sub(bottom),
                new Vector3d(front).sub(back), new Vector3d());
    }

    private static Vector3d deltaFor(List<NeighborValue> values, Neighbor direction) {
        return values.stream().filter(value -> value.direction() == direction)
                .map(NeighborValue::delta).findFirst().orElseGet(Vector3d::new);
    }

    /** Weighted sum used by InterpolatedProbe for current source gradients. */
    public static Vector3d weightedGradient(List<Node> nodes,
                                            List<ProbeDefinition.InterpolationSource> sources) {
        Vector3d result = new Vector3d();
        for (int i = 0; i < nodes.size(); i++)
            result.fma(sources.get(i).weight(), nodes.get(i).readGradientImbalance());
        return result;
    }
}
