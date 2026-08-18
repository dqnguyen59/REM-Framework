package remframework.opengl.model;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import remframework.opengl.helpers.Neighbor;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.EntityType;
import remframework.opengl.model.entity.Node;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterpolatedNeighborhoodInspectionTest {
    private final Scene scene = new Scene();

    @AfterEach
    void leaveInspection() {
        scene.exitNeighborhoodInspection();
        scene.updateCameraTransition(1.0);
        scene.clearInspectionState();
    }

    @Test
    void twoDimensionalInspectionExposesFourSources() {
        InterpolatedProbe probe = createProbe(ProbeDetectorType.INTERPOLATED_2D, 4);
        scene.selectProbe(probe);
        scene.enterNeighborhoodInspection();

        assertEquals(4, scene.getInspectionSourceNodes().size());
    }

    @Test
    void threeDimensionalInspectionExposesEightSources() {
        InterpolatedProbe probe = createProbe(ProbeDetectorType.INTERPOLATED_3D, 8);
        scene.selectProbe(probe);
        scene.enterNeighborhoodInspection();

        assertEquals(8, scene.getInspectionSourceNodes().size());
    }

    @Test
    void selectingSourceChangesFocusAndDirectNeighborSet() {
        InterpolatedProbe probe = createProbe(ProbeDetectorType.INTERPOLATED_2D, 4);
        Node first = probe.getSourceNodes().get(0);
        Node second = probe.getSourceNodes().get(1);
        Node firstNeighbor = newNode(10, 0, 0);
        Node secondNeighbor = newNode(11, 0, 0);
        first.setNeighbor(Neighbor.RIGHT, firstNeighbor);
        second.setNeighbor(Neighbor.RIGHT, secondNeighbor);
        scene.selectProbe(probe);
        scene.enterNeighborhoodInspection();
        scene.updateCameraTransition(1.0);

        assertEquals(0, scene.getInspectedSourceIndex());
        assertTrue(firstNeighbor.isInspectionVisible());
        assertFalse(secondNeighbor.isInspectionVisible());

        assertTrue(scene.selectInspectionSource(1));
        scene.updateCameraTransition(1.0);

        assertEquals(1, scene.getInspectedSourceIndex());
        assertEquals(second.toWorldPosition(), scene.getActiveCameraTarget());
        assertFalse(firstNeighbor.isInspectionVisible());
        assertTrue(secondNeighbor.isInspectionVisible());
        assertTrue(first.isInspectionVisible());
        assertTrue(second.isInspectionVisible());
    }

    @Test
    void sourceColorHasPriorityWhenSourceIsAlsoSelectedSourceNeighbor() {
        InterpolatedProbe probe = createProbe(ProbeDetectorType.INTERPOLATED_2D, 4);
        Node first = probe.getSourceNodes().get(0);
        Node second = probe.getSourceNodes().get(1);
        Node ordinaryNeighbor = newNode(-1, 0, 0);
        first.setNeighbor(Neighbor.RIGHT, second);
        first.setNeighbor(Neighbor.LEFT, ordinaryNeighbor);
        scene.selectProbe(probe);
        scene.enterNeighborhoodInspection();
        scene.updateCameraTransition(1.0);

        assertNotEquals(first.getInspectionColor(), second.getInspectionColor());
        assertEquals(second.getInspectionColor(), probe.getSourceNodes().get(2).getInspectionColor());
        assertNotEquals(ordinaryNeighbor.getInspectionColor(), second.getInspectionColor());
        assertTrue(second.isInspectionVisible());
    }

    @Test
    void exitClearsTemporaryInspectionStateWithoutChangingOrdinaryVisibility() {
        InterpolatedProbe probe = createProbe(ProbeDetectorType.INTERPOLATED_2D, 4);
        Node source = probe.getSourceNodes().get(0);
        source.setVisible(false);
        scene.selectProbe(probe);
        scene.enterNeighborhoodInspection();
        scene.updateCameraTransition(1.0);
        assertTrue(source.isInspectionVisible());

        scene.exitNeighborhoodInspection();
        scene.updateCameraTransition(1.0);

        assertFalse(source.isInspectionVisible());
        assertFalse(source.isVisible());
        assertNull(source.getInspectionColor());
        assertNull(probe.getSourceNodes().get(1).getInspectionColor());
    }

    private InterpolatedProbe createProbe(ProbeDetectorType detectorType, int sourceCount) {
        List<ProbeDefinition.InterpolationSource> definitions = new ArrayList<>();
        List<Node> nodes = new ArrayList<>();
        for (int index = 0; index < sourceCount; index++) {
            Vector3i coordinate = new Vector3i(index, 0, 0);
            definitions.add(new ProbeDefinition.InterpolationSource(coordinate, 1.0 / sourceCount));
            nodes.add(newNode(coordinate.x, coordinate.y, coordinate.z));
        }
        ProbeDefinition.Target target = new ProbeDefinition.Target(
                0, 0, detectorType == ProbeDetectorType.INTERPOLATED_2D ? "XY" : "XYZ", 1,
                new Vector3d(), new Vector3d(1, 0, 0), 0.0, 0.0, null, 0.0, -1);
        ProbeDefinition definition = ProbeDefinition.interpolated(
                "interpolated-inspection", detectorType, new Vector4f(0.2f, 0.8f, 1.0f, 1.0f),
                new ProbeDefinition.Placement(List.of(target), 0, target.rayPlane()), definitions);
        Entity marker = new Entity(null, null, new Vector3d(), new Vector3d(), 1.0, EntityType.ENTITY);
        return new InterpolatedProbe(definition, nodes, marker);
    }

    private Node newNode(int x, int y, int z) {
        Node node = new Node(null, null, new Vector3d(x, y, z), new Vector3d(), 1.0);
        node.setIndex(new Vector3i(x, y, z));
        return node;
    }
}
