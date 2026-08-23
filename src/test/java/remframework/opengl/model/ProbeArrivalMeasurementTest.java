package remframework.opengl.model;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import remframework.opengl.model.entity.Node;
import remframework.opengl.model.entity.Occupancy;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbeArrivalMeasurementTest {

    @Test
    void recordsFirstPositionAndGradientChangesAndCombinesTheirArrival() {
        Probe probe = probe("probe-0", 0, 0, "X axis");
        Occupancy occupancy = probe.getOccupancies().getFirst();
        occupancy.saveGradientToHistory(new Vector3d(3.0, 0.0, 0.0));
        ProbeArrivalMeasurement measurement = new ProbeArrivalMeasurement();

        assertTrue(measurement.start(List.of(probe), 0, 0.1, 0.5));

        probe.getNode().setPosition(new Vector3d(0.2, 0.0, 0.0));
        probe.getNode().updatePosition();
        occupancy.saveGradientToHistory(new Vector3d(3.4, 0.0, 0.0));
        measurement.sample();
        ProbeArrivalMeasurement.Result first = measurement.snapshot().results().getFirst();
        assertEquals(1, first.positionArrivalUpdate());
        assertEquals(-1, first.gradientArrivalUpdate());
        assertEquals(-1, first.combinedArrivalUpdate());

        probe.getNode().setPosition(new Vector3d(0.3, 0.0, 0.0));
        probe.getNode().updatePosition();
        occupancy.saveGradientToHistory(new Vector3d(3.6, 0.0, 0.0));
        measurement.sample();
        ProbeArrivalMeasurement.Snapshot complete = measurement.snapshot();
        ProbeArrivalMeasurement.Result result = complete.results().getFirst();
        assertEquals(1, result.positionArrivalUpdate());
        assertEquals(2, result.gradientArrivalUpdate());
        assertEquals(2, result.combinedArrivalUpdate());
        assertTrue(complete.complete());
        assertFalse(complete.running());
    }

    @Test
    void selectedShellContainsOneResultPerPhysicalProbeInRayOrder() {
        Probe secondRay = probe("probe-b", 1, 1, "XY positive");
        Probe firstRay = probe("probe-a", 1, 0, "X axis");
        Probe otherShell = probe("probe-c", 2, 0, "X axis");
        ProbeArrivalMeasurement measurement = new ProbeArrivalMeasurement();

        assertEquals(List.of(1, 2), ProbeArrivalMeasurement.availableShells(
                List.of(secondRay, otherShell, firstRay)));
        assertTrue(measurement.start(
                List.of(secondRay, otherShell, firstRay), 1, 1.0e-12, 1.0e-12));

        ProbeArrivalMeasurement.Snapshot snapshot = measurement.snapshot();
        assertEquals(2, snapshot.results().size());
        assertEquals("probe-a", snapshot.results().get(0).probeId());
        assertEquals("X axis (ray 1)", snapshot.results().get(0).representedRays());
        assertEquals("probe-b", snapshot.results().get(1).probeId());
    }

    @Test
    void interpolatedProbeUsesWeightedPositionAndLatestGradientSamples() {
        List<Node> nodes = IntStream.range(0, 4)
                .mapToObj(index -> node(index, 0, 0))
                .toList();
        List<ProbeDefinition.InterpolationSource> sources = IntStream.range(0, 4)
                .mapToObj(index -> new ProbeDefinition.InterpolationSource(
                        nodes.get(index).getIndex(), 0.25))
                .toList();
        ProbeDefinition.Target target = new ProbeDefinition.Target(
                0, 0, "XY positive", 1, new Vector3d(), new Vector3d(1, 1, 0),
                Double.NaN, 0.0, null, 0.0, -1);
        InterpolatedProbe probe = new InterpolatedProbe(
                ProbeDefinition.interpolated("probe-interpolated",
                        ProbeDetectorType.INTERPOLATED_2D, new Vector4f(1.0f),
                        new ProbeDefinition.Placement(List.of(target), 0, "XY"), sources),
                nodes, null);
        for (Occupancy occupancy : probe.getOccupancies()) {
            occupancy.saveGradientToHistory(new Vector3d(2.0, 0.0, 0.0));
        }
        ProbeArrivalMeasurement measurement = new ProbeArrivalMeasurement();
        assertTrue(measurement.start(List.of(probe), 0, 0.1, 0.9));

        nodes.getFirst().setPosition(new Vector3d(0.8, 0.0, 0.0));
        nodes.getFirst().updatePosition();
        for (int index = 0; index < probe.getOccupancies().size(); index++) {
            probe.getOccupancies().get(index).saveGradientToHistory(
                    new Vector3d(index == 0 ? 6.0 : 2.0, 0.0, 0.0));
        }
        measurement.sample();

        ProbeArrivalMeasurement.Result result = measurement.snapshot().results().getFirst();
        assertEquals(1, result.positionArrivalUpdate());
        assertEquals(1, result.gradientArrivalUpdate());
        assertEquals(0.2, result.maximumPositionChange(), 1.0e-12);
        assertEquals(1.0, result.maximumGradientChange(), 1.0e-12);
    }

    @Test
    void rejectsNonPositiveOrNonFiniteChangeThresholds() {
        Probe probe = probe("probe-0", 0, 0, "X axis");
        ProbeArrivalMeasurement measurement = new ProbeArrivalMeasurement();

        assertThrows(IllegalArgumentException.class,
                () -> measurement.start(List.of(probe), 0, 0.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> measurement.start(List.of(probe), 0, 1.0, Double.NaN));
    }

    private static Probe probe(String id, int shell, int ray, String rayPlane) {
        Node node = node(ray, shell, 0);
        ProbeDefinition.Target target = new ProbeDefinition.Target(
                shell, ray, rayPlane, shell + 1,
                new Vector3d(ray, shell, 0), new Vector3d(1, 0, 0),
                shell + 1, 0.0, new Vector3i(1, 0, 0), 0.0, shell + 1);
        ProbeDefinition definition = new ProbeDefinition(id, node.getIndex(), new Vector4f(1.0f),
                new ProbeDefinition.Placement(List.of(target), 0, "XYZ"));
        return new Probe(definition, node, null);
    }

    private static Node node(int x, int y, int z) {
        Node node = new Node(null, null, new Vector3d(x, y, z), new Vector3d(), 1.0);
        node.setIndex(x, y, z);
        return node;
    }
}
