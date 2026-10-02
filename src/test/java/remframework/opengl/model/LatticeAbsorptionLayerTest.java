package remframework.opengl.model;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.opengl.model.entity.Node;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LatticeAbsorptionLayerTest {

    @Test
    void runtimeLayerChangeReappliesExistingNodeFactors() {
        Lattice lattice = new Lattice();
        Node[][][] nodes = new Node[1][1][7];
        for (int i = 0; i < nodes[0][0].length; i++) {
            nodes[0][0][i] = lattice.createNode(
                    null, null, new Vector3d(i, 0, 0), new Vector3d(), 1.0);
        }
        lattice.setNodeMatrix(nodes);

        lattice.setNumEdgeAbsorptionLayers(2);
        lattice.applyPendingAbsorptionLayerChange();

        assertEquals(0.25f, nodes[0][0][0].getAbsorptionFactor(), 1.0e-6f);
        assertEquals(0.75f, nodes[0][0][1].getAbsorptionFactor(), 1.0e-6f);
        assertEquals(1.0f, nodes[0][0][2].getAbsorptionFactor(), 1.0e-6f);

        lattice.setNumEdgeAbsorptionLayers(1);
        lattice.applyPendingAbsorptionLayerChange();

        assertEquals(0.5f, nodes[0][0][0].getAbsorptionFactor(), 1.0e-6f);
        assertEquals(1.0f, nodes[0][0][1].getAbsorptionFactor(), 1.0e-6f);

        lattice.setNumEdgeAbsorptionLayers(-1);
        lattice.applyPendingAbsorptionLayerChange();

        assertEquals(0, lattice.getNumEdgeAbsorptionLayers());
        for (Node node : nodes[0][0]) {
            assertEquals(1.0f, node.getAbsorptionFactor(), 1.0e-6f);
        }
    }
}
