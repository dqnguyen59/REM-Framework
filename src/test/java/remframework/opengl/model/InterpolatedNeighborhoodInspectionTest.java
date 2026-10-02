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
    void selectingSourceChangesFocusAndKeepsAllSourceNeighborsVisible() {
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
        assertTrue(secondNeighbor.isInspectionVisible());

        assertTrue(scene.selectInspectionSource(1));
        scene.updateCameraTransition(1.0);

        assertEquals(1, scene.getInspectedSourceIndex());
        assertEquals(second.toWorldPosition(), scene.getActiveCameraTarget());
        assertTrue(firstNeighbor.isInspectionVisible());
        assertTrue(secondNeighbor.isInspectionVisible());
        assertEquals(0.45f, secondNeighbor.getInspectionColor().w, 1.0e-6f);
        assertTrue(first.isInspectionVisible());
        assertTrue(second.isInspectionVisible());
        assertEquals(new Vector4f(0.0f, 1.0f, 0.0f, 1.0f),
                     second.getInspectionColor());
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

        assertEquals(new Vector4f(0.0f, 1.0f, 0.0f, 1.0f), first.getInspectionColor());
        assertEquals(probe.getSourceNodes().get(2).getInspectionColor(), second.getInspectionColor());
        assertNotEquals(ordinaryNeighbor.getInspectionColor(), second.getInspectionColor());
        assertTrue(second.isInspectionVisible());
        assertEquals(new Vector4f(0.5f, 0.5f, 0.5f, 0.45f), ordinaryNeighbor.getInspectionColor());
    }

    @Test
    void inspectionUsesDeduplicatedUnionOfAllSourceNeighbors() {
        InterpolatedProbe probe = createProbe(ProbeDetectorType.INTERPOLATED_2D, 4);
        Node sharedNeighbor = newNode(10, 0, 0);
        Node uniqueNeighbor = newNode(11, 0, 0);
        probe.getSourceNodes().get(0).setNeighbor(Neighbor.RIGHT, sharedNeighbor);
        probe.getSourceNodes().get(1).setNeighbor(Neighbor.RIGHT, sharedNeighbor);
        probe.getSourceNodes().get(2).setNeighbor(Neighbor.RIGHT, uniqueNeighbor);
        scene.selectProbe(probe);
        scene.enterNeighborhoodInspection();
        scene.updateCameraTransition(1.0);

        for (Node source : probe.getSourceNodes()) assertTrue(source.isInspectionVisible());
        assertTrue(sharedNeighbor.isInspectionVisible());
        assertTrue(uniqueNeighbor.isInspectionVisible());
        assertEquals(6, scene.getInspectionTemporaryNodeCount());
        assertEquals(sharedNeighbor.getInspectionColor(), uniqueNeighbor.getInspectionColor());
    }

    @Test
    void nonSelectedSourcesAndProbeUseConfiguredAlphaAndProbeCanBeHidden() {
        InterpolatedProbe probe = createProbe(ProbeDetectorType.INTERPOLATED_2D, 4);
        Node neighbor = newNode(9, 0, 0);
        probe.getSourceNodes().get(2).setNeighbor(Neighbor.RIGHT, neighbor);
        scene.selectProbe(probe);
        scene.enterNeighborhoodInspection();
        scene.updateCameraTransition(1.0);

        scene.setInspectionSourceAlpha(0.25);
        assertEquals(0.25f, probe.getSourceNodes().get(1).getInspectionColor().w, 1.0e-6f);
        assertEquals(0.25f, neighbor.getInspectionColor().w, 1.0e-6f);
        assertEquals(0.25f, probe.getMarker().getInspectionColor().w, 1.0e-6f);
        assertTrue(probe.getMarker().isInspectionVisible());
        assertEquals(1.0f, probe.getSourceNodes().get(0).getInspectionColor().w, 1.0e-6f);

        scene.setShowInspectionProbe(false);
        assertFalse(probe.getMarker().isInspectionVisible());
        assertTrue(probe.getSourceNodes().get(0).isInspectionVisible());
        scene.setShowInspectionProbe(true);
        assertTrue(probe.getMarker().isInspectionVisible());
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
        assertFalse(probe.getMarker().isInspectionVisible());
        assertNull(probe.getMarker().getInspectionColor());
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
