package remframework.opengl.model;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.EntityType;
import remframework.opengl.model.entity.Node;
import remframework.settings.Project;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class InterpolatedProbeTest {

    @Test
    void requestedCoordinateIsImmutableWhileMarkerUsesWeightedLivePosition() {
        Vector3d requestedCoordinate = new Vector3d(0.5, 0.5, 0.0);
        Vector3d requestedDirection = new Vector3d(1.0, 1.0, 0.0).normalize();
        ProbeDefinition.Target target = new ProbeDefinition.Target(
                0, 0, "XY 45.000°", 1, requestedCoordinate, requestedDirection,
                Double.NaN, 0.0, null, Double.NaN, -1);
        List<ProbeDefinition.InterpolationSource> sources =
                ProbeInterpolation.createBilinearStencil(requestedCoordinate, "XY",
                        new Vector3i(0, 0, 0), new Vector3i(1, 1, 0));
        ProbeDefinition definition = ProbeDefinition.interpolated(
                "virtual", ProbeDetectorType.INTERPOLATED_2D, new Vector4f(0.2f, 0.8f, 1.0f, 1.0f),
                new ProbeDefinition.Placement(List.of(target), 0, "XY"), sources);
        List<Node> nodes = new ArrayList<>();
        for (ProbeDefinition.InterpolationSource source : sources) {
            Vector3i index = source.nodeIndex();
            Node node = new Node(null, null, new Vector3d(index), new Vector3d(), 1.0);
            node.setIndex(index);
            nodes.add(node);
        }
        Entity marker = new Entity(null, null, new Vector3d(), new Vector3d(), 1.0,
                                   EntityType.ENTITY);
        InterpolatedProbe probe = new InterpolatedProbe(definition, nodes, marker);

        requestedCoordinate.set(99.0, 99.0, 99.0);
        target.requestedCoordinate().set(-1.0, -1.0, -1.0);
        nodes.get(0).setPosition(new Vector3d(4.0, 8.0, 12.0));
        nodes.get(0).updatePosition();
        probe.updateMarker();

        assertEquals(new Vector3d(0.5, 0.5, 0.0), target.requestedCoordinate());
        assertEquals(new Vector3d(1.5, 2.5, 3.0), marker.getPosition());
        assertEquals(ProbeDetectorType.INTERPOLATED_2D, probe.getDetectorType());
        assertNotSame(marker.getPosition(), probe.getObservation().position());
    }

    @Test
    void hoverUsesNodeHoverColourWithoutOverridingSelectionOrProbeColour() {
        Vector4f probeColour = new Vector4f(0.2f, 0.8f, 1.0f, 1.0f);
        ProbeDefinition.Target target = new ProbeDefinition.Target(
                0, 0, "XY 45.000°", 1, new Vector3d(0.5, 0.5, 0.0),
                new Vector3d(1.0, 1.0, 0.0).normalize(), 0.0, 0.0, null, 0.0, -1);
        ProbeDefinition definition = ProbeDefinition.interpolated(
                "hover", ProbeDetectorType.INTERPOLATED_2D, probeColour,
                new ProbeDefinition.Placement(List.of(target), 0, "XY"),
                ProbeInterpolation.createBilinearStencil(new Vector3d(0.5, 0.5, 0.0), "XY",
                        new Vector3i(0, 0, 0), new Vector3i(1, 1, 0)));
        List<Node> nodes = definition.getInterpolationSources().stream()
                .map(source -> {
                    Node node = new Node(null, null, new Vector3d(source.nodeIndex()), new Vector3d(), 1.0);
                    node.setIndex(source.nodeIndex());
                    return node;
                }).toList();
        Entity marker = new Entity(null, null, new Vector3d(), new Vector3d(), 1.0,
                                   EntityType.ENTITY);
        InterpolatedProbe probe = new InterpolatedProbe(definition, nodes, marker);

        probe.setHovered(true);
        probe.updateMarker();
        Vector4f expectedHover = new Vector4f(Project.getInstance().settings.selectedColor)
                .mul(2.0f);
        expectedHover.w = 1.0f;
        assertEquals(expectedHover, marker.getMaterial().getDiffuseColor());
        assertEquals(probeColour, definition.getDisplayColor());

        probe.setSelected(true);
        probe.updateMarker();
        Vector4f expectedSelected = new Vector4f(1.0f, 0.35f, 0.0f, 1.0f).mul(2.0f);
        expectedSelected.w = 1.0f;
        assertEquals(expectedSelected, marker.getMaterial().getDiffuseColor());

        probe.setComparisonSlot(0);
        probe.updateMarker();
        Vector4f expectedComparison = new Vector4f(0.0f, 1.0f, 0.0f, 1.0f).mul(2.0f);
        expectedComparison.w = 1.0f;
        assertEquals(expectedComparison, marker.getMaterial().getDiffuseColor());

        probe.setSelected(false);
        probe.setComparisonSlot(-1);
        probe.setHovered(false);
        probe.updateMarker();
        Vector4f expectedNormal = new Vector4f(probeColour).mul(2.0f);
        expectedNormal.w = 1.0f;
        assertEquals(expectedNormal, marker.getMaterial().getDiffuseColor());
        assertEquals(new Vector4f(0.2f, 0.8f, 1.0f, 1.0f), definition.getDisplayColor());
    }
}
