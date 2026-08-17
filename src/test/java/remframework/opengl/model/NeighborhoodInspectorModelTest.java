package remframework.opengl.model;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import remframework.opengl.helpers.Neighbor;
import remframework.opengl.model.entity.Node;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NeighborhoodInspectorModelTest {
    @Test
    void snapshotUsesLiteralNeighborGeometryAndSubtractionOrder() {
        Node source = node(0, 0, 0, 0, 0, 0);
        Node right = node(1, 0, 0, 3, 0, 0);
        Node left = node(-1, 0, 0, -1, 0, 0);
        source.setNeighbor(Neighbor.RIGHT, right);
        source.setNeighbor(Neighbor.LEFT, left);

        NeighborhoodInspectorModel.Snapshot snapshot = NeighborhoodInspectorModel.read(source);

        assertEquals(3.0, snapshot.neighbors().getFirst().distance(), 1.0e-12);
        assertEquals(2.0, snapshot.gradient().x, 1.0e-12);
        assertEquals(4.0, snapshot.pairX().x, 1.0e-12);
    }

    @Test
    void weightedGradientIsCalculatedSampleFromEachSource() {
        Node first = node(0, 0, 0, 0, 0, 0);
        Node firstRight = node(1, 0, 0, 2, 0, 0);
        first.setNeighbor(Neighbor.RIGHT, firstRight);
        Node second = node(0, 0, 0, 0, 0, 0);
        Node secondRight = node(1, 0, 0, 5, 0, 0);
        second.setNeighbor(Neighbor.RIGHT, secondRight);

        Vector3d weighted = NeighborhoodInspectorModel.weightedGradient(
                List.of(first, second), List.of(
                        new ProbeDefinition.InterpolationSource(new Vector3i(0), 0.25),
                        new ProbeDefinition.InterpolationSource(new Vector3i(1, 0, 0), 0.75)));

        assertEquals(4.25, weighted.x, 1.0e-12);
    }

    private static Node node(int x, int y, int z, double px, double py, double pz) {
        Node node = new Node(null, null, new Vector3d(px, py, pz), new Vector3d(), 1.0);
        node.setIndex(x, y, z);
        return node;
    }
}
