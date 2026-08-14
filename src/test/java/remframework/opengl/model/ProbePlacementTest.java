package remframework.opengl.model;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import remframework.opengl.helpers.Neighbor;
import remframework.opengl.model.entity.EntityType;
import remframework.opengl.model.entity.Node;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbePlacementTest {

    @Test
    void twoDimensionalRaysUseAdditiveRadiiAndNearestIndexNodes() {
        Node[][][] nodes = positiveGrid(15, 15, 0);

        ProbePlacement.Result result = ProbePlacement.place(
                nodes[0][0][0], 10, 5, 2, 4, 0, false,
                new Vector4f(1.0f), Set.of(), "test");

        assertTrue(result.valid(), result.status());
        assertEquals(List.of(
                new Vector3i(9, 4, 0),
                new Vector3i(7, 7, 0),
                new Vector3i(4, 9, 0),
                new Vector3i(0, 10, 0),
                new Vector3i(14, 6, 0),
                new Vector3i(11, 11, 0),
                new Vector3i(6, 14, 0),
                new Vector3i(0, 15, 0)), result.definitions().stream()
                .map(ProbeDefinition::getNodeIndex).toList());
        assertEquals(List.of(10, 10, 10, 10, 15, 15, 15, 15),
                result.probeReports().stream().map(ProbePlacement.ProbeReport::requestedRadius)
                        .toList());
        assertEquals(13, result.probeReports().getFirst().graphDistance());
        assertEquals(Math.sqrt(97.0), result.probeReports().getFirst().realizedRadius(), 1.0e-12);
        assertEquals("XY 22.500°", result.probeReports().getFirst().rayPlane());
    }

    @Test
    void threeDimensionalOddPlaneSetDeduplicatesAxesAndAddsBodyDiagonal() {
        Node[][][] nodes = positiveGrid(10, 10, 10);

        ProbePlacement.Result result = ProbePlacement.place(
                nodes[0][0][0], 10, 5, 1, 5, 0, true,
                new Vector4f(1.0f), Set.of(), "test");

        assertTrue(result.valid(), result.status());
        assertEquals(13, result.definitions().size());
        assertEquals(List.of(
                new Vector3i(10, 0, 0),
                new Vector3i(9, 4, 0),
                new Vector3i(7, 7, 0),
                new Vector3i(4, 9, 0),
                new Vector3i(0, 10, 0),
                new Vector3i(9, 0, 4),
                new Vector3i(7, 0, 7),
                new Vector3i(4, 0, 9),
                new Vector3i(0, 0, 10),
                new Vector3i(0, 9, 4),
                new Vector3i(0, 7, 7),
                new Vector3i(0, 4, 9),
                new Vector3i(6, 6, 6)), result.definitions().stream()
                .map(ProbeDefinition::getNodeIndex).toList());
        assertEquals(List.of("X axis", "XY 22.500°", "XY 45.000°", "XY 67.500°",
                             "Y axis", "XZ 22.500°", "XZ 45.000°", "XZ 67.500°",
                             "Z axis", "YZ 22.500°", "YZ 45.000°", "YZ 67.500°",
                             "XYZ body diagonal"), result.probeReports().stream()
                .map(ProbePlacement.ProbeReport::rayPlane).toList());
    }

    @Test
    void threeDimensionalEvenPlaneCountIsRejected() {
        Node[][][] nodes = positiveGrid(4, 4, 4);

        ProbePlacement.Result result = ProbePlacement.place(
                nodes[0][0][0], 2, 1, 1, 4, 0, true,
                new Vector4f(1.0f), Set.of(), "test");

        assertFalse(result.valid());
        assertTrue(result.status().contains("must be odd"), result.status());
    }

    @Test
    void duplicateSnapsAreReportedWithoutRedirectingEitherRay() {
        Node[][][] nodes = positiveGrid(3, 3, 3);

        ProbePlacement.Result result = ProbePlacement.place(
                nodes[0][0][0], 1, 1, 1, 5, 0, true,
                new Vector4f(1.0f), Set.of(), "test");

        assertFalse(result.valid());
        assertTrue(result.definitions().isEmpty());
        assertEquals(13, result.probeReports().size());
        assertTrue(result.probeReports().stream().anyMatch(ProbePlacement.ProbeReport::collision));
        assertTrue(result.status().contains("Snapped-node collision"), result.status());
    }

    private static Node[][][] positiveGrid(int maximumX, int maximumY, int maximumZ) {
        Node[][][] nodes = new Node[maximumX + 1][maximumY + 1][maximumZ + 1];
        for (int x = 0; x <= maximumX; x++) {
            for (int y = 0; y <= maximumY; y++) {
                for (int z = 0; z <= maximumZ; z++) {
                    Node node = new Node(null, null, new Vector3d(x, y, z),
                                         new Vector3d(), 1.0);
                    node.setType(EntityType.NODE);
                    node.setIndex(x, y, z);
                    nodes[x][y][z] = node;
                }
            }
        }
        for (int x = 0; x <= maximumX; x++) {
            for (int y = 0; y <= maximumY; y++) {
                for (int z = 0; z <= maximumZ; z++) {
                    Node node = nodes[x][y][z];
                    if (x < maximumX) node.setNeighbor(Neighbor.RIGHT, nodes[x + 1][y][z]);
                    if (x > 0) node.setNeighbor(Neighbor.LEFT, nodes[x - 1][y][z]);
                    if (y < maximumY) node.setNeighbor(Neighbor.TOP, nodes[x][y + 1][z]);
                    if (y > 0) node.setNeighbor(Neighbor.BOTTOM, nodes[x][y - 1][z]);
                    if (z < maximumZ) node.setNeighbor(Neighbor.FRONT, nodes[x][y][z + 1]);
                    if (z > 0) node.setNeighbor(Neighbor.BACK, nodes[x][y][z - 1]);
                }
            }
        }
        return nodes;
    }
}
