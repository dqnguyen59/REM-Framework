package remframework.opengl.model;

import org.ini4j.Wini;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import remframework.opengl.controller.TransportSystem;
import remframework.opengl.helpers.Neighbor;
import remframework.opengl.model.entity.Identity;
import remframework.opengl.model.entity.Node;
import remframework.opengl.model.entity.Occupancy;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProbeOccupancyFoundationTest {

    @Test
    void probeDetectorTypesOwnOneFourOrEightMeasurementOccupancies() {
        Node rawNode = node(0, 0, 0);
        Probe raw = new Probe(rawDefinition(new Vector3i(0, 0, 0)), rawNode, null);
        assertEquals(1, raw.getOccupancies().size());

        List<Node> four = nodes(4);
        InterpolatedProbe twoD = new InterpolatedProbe(
                interpolatedDefinition(ProbeDetectorType.INTERPOLATED_2D, 4), four, null);
        assertEquals(4, twoD.getOccupancies().size());

        List<Node> eight = nodes(8);
        InterpolatedProbe threeD = new InterpolatedProbe(
                interpolatedDefinition(ProbeDetectorType.INTERPOLATED_3D, 8), eight, null);
        assertEquals(8, threeD.getOccupancies().size());
        assertTrue(threeD.getOccupancies().stream()
                .allMatch(occupancy -> occupancy.getIdentity() == null));
    }

    @Test
    void probeUsesScaledHistoryAndCycleExtremaWithoutTransportState() {
        Node source = asymmetricNode();
        Probe probe = new Probe(rawDefinition(new Vector3i(0, 0, 0)), source, null);
        Occupancy occupancy = probe.getOccupancies().getFirst();
        TransportSystem transport = TransportSystem.getInstance();
        double previousGain = transport.getGradientGain();
        try {
            transport.setGradientGain(2.0);
            source.setVelocity(new Vector3d(0, 0, -1));
            transport.update(occupancy);
            assertEquals(2.0, occupancy.getLastMeasuredGradient().x, 1.0e-12);
            assertEquals(1, occupancy.getCycleSampleCountForAudit());

            Vector3d position = new Vector3d(source.getPosition());
            Vector3d velocity = new Vector3d(source.getVelocity());
            Vector3d acceleration = new Vector3d(source.getAcceleration());
            Vector3d force = new Vector3d(source.getForce());
            source.setVelocity(new Vector3d(0, 0, 0));
            transport.update(occupancy);

            assertTrue(occupancy.wasCycleCompletedThisUpdate());
            assertEquals(2, occupancy.getSamplesPerCycleForAudit());
            assertEquals(2.0, occupancy.getLastMaxPositiveGradient().x, 1.0e-12);
            assertEquals(0.0, occupancy.getLastMaxNegativeGradient().x, 1.0e-12);
            assertEquals(1.0, occupancy.getGradientImbalance().x, 1.0e-12);
            assertEquals(position, source.getPosition());
            assertEquals(new Vector3d(0, 0, 0), source.getVelocity());
            assertEquals(acceleration, source.getAcceleration());
            assertEquals(force, source.getForce());
            assertEquals(new Vector3d(), occupancy.getPressure());
            assertEquals(new Vector3d(), occupancy.getTransportMomentum());
            assertEquals(occupancy.getGradientImbalance(), probe.getStableGradientImbalance());
        } finally {
            transport.setGradientGain(previousGain);
        }
    }

    @Test
    void rawAndInterpolatedStableGradientsUseOccupancyResults() {
        List<Node> sources = nodes(4);
        InterpolatedProbe probe = new InterpolatedProbe(
                interpolatedDefinition(ProbeDetectorType.INTERPOLATED_2D, 4), sources, null);
        Vector3d[] stable = {
                new Vector3d(1, 2, 3), new Vector3d(4, 5, 6),
                new Vector3d(7, 8, 9), new Vector3d(10, 11, 12)
        };
        for (int index = 0; index < stable.length; index++) {
            probe.getOccupancies().get(index).getGradientImbalance().set(stable[index]);
        }
        assertEquals(new Vector3d(5.5, 6.5, 7.5),
                probe.getStableGradientImbalance());
        assertEquals(probe.getStableGradientImbalance(),
                probe.getObservation().gradientImbalance());

        List<Node> threeDSources = nodes(8);
        InterpolatedProbe threeD = new InterpolatedProbe(
                interpolatedDefinition(ProbeDetectorType.INTERPOLATED_3D, 8),
                threeDSources, null);
        for (int index = 0; index < 8; index++) {
            threeD.getOccupancies().get(index).getGradientImbalance()
                    .set(index, index + 1.0, 2.0 * index);
        }
        assertEquals(new Vector3d(3.5, 4.5, 7.0),
                threeD.getStableGradientImbalance());
    }

    @Test
    void probeResetClearsMeasurementStateWithoutIdentityEntity() {
        Node source = asymmetricNode();
        Probe probe = new Probe(rawDefinition(new Vector3i(0, 0, 0)), source, null);
        Occupancy occupancy = probe.getOccupancies().getFirst();
        occupancy.getGradientImbalance().set(4, 5, 6);
        occupancy.saveGradientToHistory(new Vector3d(4, 5, 6));
        occupancy.reset();
        assertNull(occupancy.getIdentity());
        assertEquals(new Vector3d(), occupancy.getGradientImbalance());
        assertEquals(0, occupancy.getCycleSampleCountForAudit());
        assertEquals(0, occupancy.getLastMeasuredGradient().length());
    }

    @Test
    void identityRetainsCapabilitiesAndNormalOccupancyBackReference() {
        Identity identity = new Identity("identity");
        Occupancy occupancy = new Occupancy(identity, null);
        assertTrue(identity.canExcite());
        assertTrue(identity.canTransfer());
        assertSame(occupancy, identity.getOccupancy());
        assertSame(identity, occupancy.getOccupant());
    }

    @Test
    void identityOccupancySerializationRetainsLegacyIdentityBinding() {
        Identity identity = new Identity("identity");
        Occupancy original = new Occupancy(identity, node(0, 0, 0));
        Wini ini = new Wini();
        original.saveToFile(ini, "Oscillator", 0);

        Occupancy loaded = new Occupancy();
        loaded.loadFromFile(ini, "Oscillator", 0);

        assertEquals("identity", loaded.getIdentity().getName());
        assertTrue(loaded.getOccupant() instanceof Identity);
        assertEquals(original.getNodeIndex(), loaded.getNodeIndex());
    }

    private static ProbeDefinition rawDefinition(Vector3i index) {
        return new ProbeDefinition("probe-test", index, new Vector4f(1.0f));
    }

    private static ProbeDefinition interpolatedDefinition(ProbeDetectorType type, int count) {
        List<ProbeDefinition.InterpolationSource> sources = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            sources.add(new ProbeDefinition.InterpolationSource(
                    new Vector3i(index, 0, 0), 1.0 / count));
        }
        ProbeDefinition.Placement placement = new ProbeDefinition.Placement(
                List.of(new ProbeDefinition.Target(0, 0, "XY", 1,
                        new Vector3d(), new Vector3d(1, 0, 0),
                        1.0, 0.0, new Vector3i(1, 0, 0), 0.0, 1)),
                0, "XYZ");
        return ProbeDefinition.interpolated("probe-test", type,
                new Vector4f(1.0f), placement, sources);
    }

    private static List<Node> nodes(int count) {
        List<Node> nodes = new ArrayList<>();
        for (int index = 0; index < count; index++) nodes.add(node(index, 0, 0));
        return nodes;
    }

    private static Node asymmetricNode() {
        Node center = node(0, 0, 0);
        center.setNeighbor(Neighbor.RIGHT, node(2, 0, 0));
        center.setNeighbor(Neighbor.LEFT, node(1, 0, 0));
        return center;
    }

    private static Node node(int x, int y, int z) {
        Node node = new Node(null, null, new Vector3d(x, y, z), new Vector3d(), 1.0);
        node.setIndex(x, y, z);
        return node;
    }
}
