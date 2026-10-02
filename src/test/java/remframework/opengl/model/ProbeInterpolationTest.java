package remframework.opengl.model;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import remframework.opengl.model.entity.Node;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbeInterpolationTest {

    @Test
    void bilinearStencilUsesFourEnclosingNodesInDeterministicOrder() {
        List<ProbeDefinition.InterpolationSource> sources =
                ProbeInterpolation.createBilinearStencil(
                        new Vector3d(1.25, 2.5, 0.0), "XY",
                        new Vector3i(0, 0, 0), new Vector3i(3, 3, 0));

        assertEquals(List.of(
                new Vector3i(1, 2, 0),
                new Vector3i(2, 2, 0),
                new Vector3i(1, 3, 0),
                new Vector3i(2, 3, 0)), sources.stream()
                .map(ProbeDefinition.InterpolationSource::nodeIndex).toList());
        assertEquals(List.of(0.375, 0.125, 0.375, 0.125), sources.stream()
                .map(ProbeDefinition.InterpolationSource::weight).toList());
    }

    @Test
    void exactOuterBoundaryUsesTheLastCompleteCellWithoutExtrapolation() {
        List<ProbeDefinition.InterpolationSource> sources =
                ProbeInterpolation.createBilinearStencil(
                        new Vector3d(3.0, 3.0, 0.0), "XY",
                        new Vector3i(0, 0, 0), new Vector3i(3, 3, 0));

        assertEquals(4, sources.size());
        assertEquals(new Vector3i(3, 3, 0), sources.get(3).nodeIndex());
        assertEquals(1.0, sources.get(3).weight(), 0.0);
        assertEquals(1.0, sources.stream().mapToDouble(
                ProbeDefinition.InterpolationSource::weight).sum(), 0.0);
    }

    @Test
    void trilinearInterpolationCopiesComponentsAndDoesNotMutateSources() {
        Vector3d target = new Vector3d(0.25, 0.5, 0.75);
        List<ProbeDefinition.InterpolationSource> sources =
                ProbeInterpolation.createTrilinearStencil(target,
                        new Vector3i(0, 0, 0), new Vector3i(1, 1, 1));
        List<Node> nodes = new ArrayList<>();
        List<Vector3d> originalPositions = new ArrayList<>();
        List<Vector3d> originalVelocities = new ArrayList<>();
        List<Vector3d> originalAccelerations = new ArrayList<>();
        List<Vector3d> originalGradients = new ArrayList<>();
        for (ProbeDefinition.InterpolationSource source : sources) {
            Vector3i index = source.nodeIndex();
            Node node = new Node(null, null, new Vector3d(index), new Vector3d(), 1.0);
            node.setIndex(index);
            node.setVelocity(new Vector3d(index).mul(2.0));
            node.setAcceleration(new Vector3d(index).mul(3.0));
            node.getGradientImbalance().set(new Vector3d(index).mul(4.0));
            nodes.add(node);
            originalPositions.add(new Vector3d(node.getPosition()));
            originalVelocities.add(new Vector3d(node.getVelocity()));
            originalAccelerations.add(new Vector3d(node.getAcceleration()));
            originalGradients.add(new Vector3d(node.getGradientImbalance()));
        }

        PassiveObserver.Observation observation = ProbeInterpolation.interpolate(nodes, sources);

        assertVectorEquals(target, observation.position());
        assertVectorEquals(new Vector3d(target).mul(2.0), observation.velocity());
        assertVectorEquals(new Vector3d(target).mul(3.0), observation.acceleration());
        assertVectorEquals(new Vector3d(target).mul(4.0), observation.gradientImbalance());
        for (int index = 0; index < nodes.size(); index++) {
            assertEquals(originalPositions.get(index), nodes.get(index).getPosition());
            assertEquals(originalVelocities.get(index), nodes.get(index).getVelocity());
            assertEquals(originalAccelerations.get(index), nodes.get(index).getAcceleration());
            assertEquals(originalGradients.get(index), nodes.get(index).getGradientImbalance());
        }
        assertEquals(1.0, sources.stream().mapToDouble(
                ProbeDefinition.InterpolationSource::weight).sum(), 1.0e-12);
    }

    private static void assertVectorEquals(Vector3d expected, Vector3d actual) {
        assertTrue(expected.distance(actual) < 1.0e-12,
                   () -> "Expected " + expected + " but was " + actual);
    }
}
