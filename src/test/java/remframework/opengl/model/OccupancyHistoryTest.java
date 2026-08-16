package remframework.opengl.model;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.EntityType;
import remframework.opengl.model.entity.Node;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OccupancyHistoryTest {
    @Test
    void interpolatesEverySampleWithTheSameWeights() {
        List<List<Vector3d>> sources = List.of(
                List.of(new Vector3d(1, 2, 3), new Vector3d(4, 5, 6)),
                List.of(new Vector3d(10, 20, 30), new Vector3d(40, 50, 60)));

        List<Vector3d> result = OccupancyHistory.interpolate(sources, List.of(0.25, 0.75));

        assertEquals(new Vector3d(7.75, 15.5, 23.25), result.get(0));
        assertEquals(new Vector3d(31.0, 38.75, 46.5), result.get(1));
    }

    @Test
    void rawAndInterpolatedProbesSelectDifferentHistorySeries() {
        Node rawNode = node(new Vector3i(0, 0, 0));
        Probe raw = new Probe(new ProbeDefinition("raw", rawNode.getIndex(),
                new Vector4f(1, 1, 1, 1), null), rawNode, entity());
        raw.getOccupancies().getFirst().saveGradientToHistory(new Vector3d(1, 0, 0));

        ProbeDefinition.Target target = new ProbeDefinition.Target(0, 0, "XY", 1,
                new Vector3d(0.5, 0.5, 0), new Vector3d(1, 0, 0),
                0, 0, null, 0, -1);
        List<ProbeDefinition.InterpolationSource> definitions =
                ProbeInterpolation.createBilinearStencil(target.requestedCoordinate(), "XY",
                        new Vector3i(0, 0, 0), new Vector3i(1, 1, 0));
        ProbeDefinition interpolatedDefinition = ProbeDefinition.interpolated("interpolated",
                ProbeDetectorType.INTERPOLATED_2D, new Vector4f(1, 1, 1, 1),
                new ProbeDefinition.Placement(List.of(target), 0, "XY"), definitions);
        List<Node> nodes = definitions.stream().map(s -> node(s.nodeIndex())).toList();
        InterpolatedProbe interpolated = new InterpolatedProbe(interpolatedDefinition, nodes, entity());
        for (int source = 0; source < interpolated.getOccupancies().size(); source++)
            interpolated.getOccupancies().get(source).saveGradientToHistory(new Vector3d(source + 1, 0, 0));

        assertEquals(1, OccupancyHistory.forProbe(raw).size());
        assertEquals(5, OccupancyHistory.forProbe(interpolated).size());
        assertEquals("Interpolated estimate", OccupancyHistory.forProbe(interpolated).getLast().label());
        assertEquals(2.5, OccupancyHistory.forProbe(interpolated).getLast().samples().getFirst().x, 1e-12);
    }

    @Test
    void phaseMarkersAreOnlyForExactly360Samples() {
        assertTrue(OccupancyHistory.showPhaseReferenceMarkers(360));
        assertEquals(false, OccupancyHistory.showPhaseReferenceMarkers(359));
        assertEquals(false, OccupancyHistory.showPhaseReferenceMarkers(361));
    }

    private static Node node(Vector3i index) {
        Node node = new Node(null, null, new Vector3d(index), new Vector3d(), 1.0);
        node.setIndex(index);
        return node;
    }

    private static Entity entity() {
        return new Entity(null, null, new Vector3d(), new Vector3d(), 1.0, EntityType.ENTITY);
    }
}
