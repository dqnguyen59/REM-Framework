package remframework.opengl.model;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import remframework.opengl.helpers.Neighbor;
import remframework.opengl.model.entity.EntityType;
import remframework.opengl.model.entity.Node;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbePlacementTest {

    @Test
    void twoDimensionalOddRaysIncludeBothAxesAndFaceDiagonal() {
        Node[][][] nodes = positiveGrid(15, 15, 0);

        ProbePlacement.Result result = ProbePlacement.place(
                nodes[0][0][0], 10, 5, 2, 5, 0, false,
                new Vector4f(1.0f), Set.of(), "test");

        assertTrue(result.valid(), result.status());
        assertEquals(List.of(
                new Vector3i(10, 0, 0),
                new Vector3i(9, 4, 0),
                new Vector3i(7, 7, 0),
                new Vector3i(4, 9, 0),
                new Vector3i(0, 10, 0),
                new Vector3i(15, 0, 0),
                new Vector3i(11, 11, 0),
                new Vector3i(0, 15, 0)), result.definitions().stream()
                .map(ProbeDefinition::getNodeIndex).toList());
        assertEquals(List.of(10, 10, 10, 10, 10, 15, 15, 15, 15, 15),
                result.probeReports().stream().map(ProbePlacement.ProbeReport::requestedRadius)
                        .toList());
        assertEquals(10, result.probeReports().getFirst().graphDistance());
        assertEquals(10.0, result.probeReports().getFirst().realizedRadius(), 1.0e-12);
        assertEquals("X axis", result.probeReports().getFirst().rayPlane());
        assertEquals("Active probe space: XY plane (Z extent insufficient for requested radii)",
                     result.activeProbeSpace());
        assertEquals(2, result.probeReports().stream()
                .filter(report -> !report.available()).count());
        assertAvailableLaterShellsFollowFirstShellDirection(nodes[0][0][0], result);
    }

    @Test
    void threeDimensionalOddPlaneSetDeduplicatesAxesAndAddsBodyDiagonal() {
        Node[][][] nodes = positiveGrid(10, 10, 10);

        ProbePlacement.Result result = ProbePlacement.place(
                nodes[0][0][0], 10, 5, 1, 5, 0, true,
                new Vector4f(1.0f), Set.of(), "test");

        assertTrue(result.valid(), result.status());
        assertEquals("Active probe space: XYZ octant", result.activeProbeSpace());
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
    void laterThreeDimensionalShellsAreIntegerMultiplesOfFirstShellDirections() {
        Node[][][] nodes = positiveGrid(30, 30, 30);
        Node center = nodes[0][0][0];

        ProbePlacement.Result result = ProbePlacement.place(
                center, 10, 10, 3, 5, 0, true,
                new Vector4f(1.0f), Set.of(), "test");

        assertTrue(result.valid(), result.status());
        assertEquals(39, result.probeReports().size());
        assertTrue(result.probeReports().stream().allMatch(ProbePlacement.ProbeReport::available),
                   result.status());
        assertAvailableLaterShellsFollowFirstShellDirection(center, result);
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
    void twoDimensionalEvenProbeCountIsRejected() {
        Node[][][] nodes = positiveGrid(4, 4, 0);

        ProbePlacement.Result result = ProbePlacement.place(
                nodes[0][0][0], 2, 1, 1, 4, 0, false,
                new Vector4f(1.0f), Set.of(), "test");

        assertFalse(result.valid());
        assertTrue(result.status().contains("must be odd"), result.status());
    }

    @Test
    void duplicateSnapsAreMergedWithoutRedirectingEitherRay() {
        Node[][][] nodes = positiveGrid(3, 3, 3);

        ProbePlacement.Result result = ProbePlacement.place(
                nodes[0][0][0], 1, 1, 1, 5, 0, true,
                new Vector4f(1.0f), Set.of(), "test");

        assertTrue(result.valid(), result.status());
        assertTrue(result.definitions().size() < 13);
        assertEquals(13, result.probeReports().size());
        assertEquals(13, result.definitions().stream()
                .map(ProbeDefinition::getPlacement)
                .mapToInt(placement -> placement.targets().size()).sum());
        assertEquals(result.definitions().size(), result.definitions().stream()
                .map(ProbeDefinition::getNodeIndex).distinct().count());
        assertTrue(result.probeReports().stream().anyMatch(ProbePlacement.ProbeReport::merged));
        assertTrue(result.status().contains("merged collisions"), result.status());
    }

    @Test
    void thinThreeDimensionalLatticeProducesOnlyXyTargets() {
        Node[][][] nodes = thinGrid(300, 300, 2, 1);
        Node center = nodes[150][150][1];

        ProbePlacement.Result result = ProbePlacement.place(
                center, 20, 20, 3, 5, 0, true,
                new Vector4f(1.0f), Set.of(), "test");

        assertTrue(result.valid(), result.status());
        assertEquals("Active probe space: XY plane (Z extent insufficient for requested radii)",
                     result.activeProbeSpace());
        assertEquals(15, result.probeReports().size());
        assertTrue(result.definitions().stream()
                .flatMap(definition -> definition.getPlacement().targets().stream())
                .allMatch(target -> target.rayPlane().equals("X axis")
                        || target.rayPlane().equals("Y axis")
                        || target.rayPlane().startsWith("XY ")));
    }

    @Test
    void flatLatticeSelectedQuadrantIncludesNegativeXAndPositiveYAxes() {
        Node[][][] nodes = thinGrid(300, 300, 0, 0);

        ProbePlacement.Result result = ProbePlacement.place(
                nodes[150][150][0], 50, 5, 1, 5, 1, false,
                new Vector4f(1.0f), Set.of(), "test");

        assertTrue(result.valid(), result.status());
        assertTrue(result.definitions().stream()
                .anyMatch(definition -> definition.getNodeIndex().equals(new Vector3i(100, 150, 0))));
        assertTrue(result.definitions().stream()
                .anyMatch(definition -> definition.getNodeIndex().equals(new Vector3i(150, 200, 0))));
    }

    @Test
    void effectiveSpaceChangeInvalidatesExistingGeneratedDefinitions() {
        Node[][][] thin = thinGrid(20, 20, 2, 1);
        ProbePlacement.Result xy = ProbePlacement.place(
                thin[10][10][1], 5, 1, 1, 5, 0, true,
                new Vector4f(1.0f), Set.of(), "test");

        assertTrue(ProbePlacement.generatedDefinitionsCompatible(
                thin[10][10][1], xy.definitions(), true, ProbePlacement.DEFAULT_TOLERANCES));

        Node[][][] deep = positiveGrid(20, 20, 10);
        assertFalse(ProbePlacement.generatedDefinitionsCompatible(
                deep[10][10][1], xy.definitions(), true, ProbePlacement.DEFAULT_TOLERANCES));
    }

    @Test
    void interpolatedTwoDimensionalPlacementRetainsExactTargetsAndFourSources() {
        Node[][][] nodes = positiveGrid(20, 20, 0);

        ProbePlacement.Result result = ProbePlacement.place(
                nodes[0][0][0], 10, 5, 1, 5, 0, false,
                new Vector4f(1.0f), Set.of(), "virtual",
                ProbeDetectorType.INTERPOLATED_2D);

        assertTrue(result.valid(), result.status());
        assertEquals(5, result.definitions().size());
        assertTrue(result.definitions().stream().allMatch(definition ->
                definition.getDetectorType() == ProbeDetectorType.INTERPOLATED_2D
                        && definition.getNodeIndex() == null
                        && definition.getInterpolationSources().size() == 4));
        ProbeDefinition.Target diagonal = result.definitions().get(2)
                .getPlacement().targets().getFirst();
        double component = 10.0 / Math.sqrt(2.0);
        assertTrue(diagonal.requestedCoordinate().distance(
                new Vector3d(component, component, 0.0)) < 1.0e-12);
        assertTrue(diagonal.requestedDirection().distance(
                new Vector3d(1.0 / Math.sqrt(2.0),
                             1.0 / Math.sqrt(2.0), 0.0)) < 1.0e-12);
        assertTrue(Double.isNaN(diagonal.realizedRadius()));
    }

    @Test
    void interpolatedThreeDimensionalPlacementUsesEightSourcesPerExactTarget() {
        Node[][][] nodes = positiveGrid(20, 20, 20);

        ProbePlacement.Result result = ProbePlacement.place(
                nodes[0][0][0], 10, 5, 1, 5, 0, true,
                new Vector4f(1.0f), Set.of(), "virtual",
                ProbeDetectorType.INTERPOLATED_3D);

        assertTrue(result.valid(), result.status());
        assertEquals(13, result.definitions().size());
        assertTrue(result.definitions().stream().allMatch(definition ->
                definition.getDetectorType() == ProbeDetectorType.INTERPOLATED_3D
                        && definition.getNodeIndex() == null
                        && definition.getInterpolationSources().size() == 8));
        assertTrue(result.definitions().stream().flatMap(definition ->
                definition.getPlacement().targets().stream()).anyMatch(target ->
                target.rayPlane().equals("XYZ body diagonal")));
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

    private static void assertAvailableLaterShellsFollowFirstShellDirection(
            Node center, ProbePlacement.Result result) {
        Map<Integer, Vector3i> firstShellDirections = new HashMap<>();
        Vector3i centerIndex = center.getIndex();
        for (ProbePlacement.ProbeReport report : result.probeReports()) {
            if (!report.available()) continue;
            Vector3i offset = new Vector3i(report.coordinate()).sub(centerIndex);
            if (report.shell() == 0) {
                Vector3i primitive = primitiveDirection(offset);
                firstShellDirections.put(report.ray(), primitive);
                assertEquals(primitive, report.realizedDirection());
                assertEquals(0.0, report.angularDeviationFromFirstShellDegrees(), 0.0);
                continue;
            }
            Vector3i firstShellDirection = firstShellDirections.get(report.ray());
            assertNotNull(firstShellDirection, "Missing first-shell direction for ray " + report.ray());
            assertPositiveIntegerMultiple(offset, firstShellDirection);
            assertEquals(firstShellDirection, report.realizedDirection());
            assertEquals(0.0, report.angularDeviationFromFirstShellDegrees(), 0.0);
        }
    }

    private static void assertPositiveIntegerMultiple(Vector3i offset, Vector3i direction) {
        Integer multiple = null;
        int[] offsets = {offset.x, offset.y, offset.z};
        int[] directions = {direction.x, direction.y, direction.z};
        for (int axis = 0; axis < directions.length; axis++) {
            if (directions[axis] == 0) {
                assertEquals(0, offsets[axis]);
                continue;
            }
            assertEquals(0, offsets[axis] % directions[axis]);
            int axisMultiple = offsets[axis] / directions[axis];
            assertTrue(axisMultiple > 0);
            if (multiple == null) multiple = axisMultiple;
            else assertEquals(multiple.intValue(), axisMultiple);
        }
        assertNotNull(multiple);
    }

    private static Vector3i primitiveDirection(Vector3i offset) {
        int divisor = gcd(gcd(Math.abs(offset.x), Math.abs(offset.y)), Math.abs(offset.z));
        assertTrue(divisor > 0);
        return new Vector3i(offset.x / divisor, offset.y / divisor, offset.z / divisor);
    }

    private static int gcd(int left, int right) {
        while (right != 0) {
            int remainder = left % right;
            left = right;
            right = remainder;
        }
        return left;
    }

    private static Node[][][] thinGrid(int maximumX, int maximumY, int maximumZ, int planeZ) {
        Node[][][] nodes = new Node[maximumX + 1][maximumY + 1][maximumZ + 1];
        for (int x = 0; x <= maximumX; x++) {
            for (int y = 0; y <= maximumY; y++) {
                nodes[x][y][planeZ] = node(x, y, planeZ);
            }
        }
        int centerX = maximumX / 2;
        int centerY = maximumY / 2;
        for (int z = 0; z <= maximumZ; z++) {
            if (nodes[centerX][centerY][z] == null) {
                nodes[centerX][centerY][z] = node(centerX, centerY, z);
            }
        }
        for (int x = 0; x <= maximumX; x++) {
            for (int y = 0; y <= maximumY; y++) {
                Node node = nodes[x][y][planeZ];
                if (x < maximumX) node.setNeighbor(Neighbor.RIGHT, nodes[x + 1][y][planeZ]);
                if (x > 0) node.setNeighbor(Neighbor.LEFT, nodes[x - 1][y][planeZ]);
                if (y < maximumY) node.setNeighbor(Neighbor.TOP, nodes[x][y + 1][planeZ]);
                if (y > 0) node.setNeighbor(Neighbor.BOTTOM, nodes[x][y - 1][planeZ]);
            }
        }
        for (int z = 0; z <= maximumZ; z++) {
            Node node = nodes[centerX][centerY][z];
            if (z < maximumZ) node.setNeighbor(Neighbor.FRONT, nodes[centerX][centerY][z + 1]);
            if (z > 0) node.setNeighbor(Neighbor.BACK, nodes[centerX][centerY][z - 1]);
        }
        return nodes;
    }

    private static Node node(int x, int y, int z) {
        Node node = new Node(null, null, new Vector3d(x, y, z), new Vector3d(), 1.0);
        node.setType(EntityType.NODE);
        node.setIndex(x, y, z);
        return node;
    }
}
