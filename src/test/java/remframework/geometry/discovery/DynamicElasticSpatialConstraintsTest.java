package remframework.geometry.discovery;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DynamicElasticSpatialConstraintsTest {
    @Test void canonicalCubeHasRequiredTopology() {
        FixedDegreeGraph cube = CanonicalDynamicElasticTopology.CUBE.create(8);
        assertEquals(8, cube.nodeCount());
        assertEquals(12, cube.connections().size());
        Map<Integer, Set<Integer>> adjacency = adjacency(cube);
        assertTrue(adjacency.values().stream().allMatch(n -> n.size() == 3));
        assertEquals(8, connectedCount(adjacency));
        assertEquals(0, triangleCount(adjacency));
        assertEquals(DynamicElasticTopologySource.CANONICAL_TOPOLOGY,
                cube.topologySource());
        assertEquals(CanonicalDynamicElasticTopology.CUBE, cube.canonicalTopology());
    }

    @Test void generatedEightNodeGraphIsNotAutomaticallyLabelledCube() {
        FixedDegreeGraph generated = new FixedDegreeGraphGenerator().generate(8, 3, 42, 0);
        assertEquals(DynamicElasticTopologySource.GENERATED_FIXED_DEGREE_TOPOLOGY,
                generated.topologySource());
        assertNull(generated.canonicalTopology());
    }

    @Test void canonicalCubeTopologyRemainsFixedDuringRelaxation() {
        FixedDegreeGraph cube = CanonicalDynamicElasticTopology.CUBE.create(8);
        DynamicElasticSession session = new DynamicElasticSession(cube,
                DynamicElasticSettings.defaults(10, 42));
        List<DiscoveryConnection> edges = cube.connections();
        String signature = cube.topologySignature();
        for (int i = 0; i < 100; i++) session.step();
        assertEquals(edges, session.graph().connections());
        assertEquals(signature, session.graph().topologySignature());
    }

    @Test void nonNeighborExclusionHasThresholdAndEqualOppositeForce() {
        FixedDegreeGraph cycle = CanonicalDynamicElasticTopology.CYCLE.create(4);
        List<DynamicElasticNode> far = nodes(new Vector3d(0, 0, 0),
                new Vector3d(10, 0, 0), new Vector3d(20, 0, 0),
                new Vector3d(0, 10, 0));
        DynamicElasticSettings disabled = disabled(10);
        DynamicElasticSettings enabled = disabled.withSpatial(
                new DynamicElasticSpatialSettings(true, 7, false, 1, false, .2));
        DynamicElasticSession farOff = new DynamicElasticSession(cycle, disabled, far);
        DynamicElasticSession farOn = new DynamicElasticSession(cycle, enabled, far);
        assertEquals(farOff.calculateForces().get(0), farOn.calculateForces().get(0));
        assertEquals(farOff.calculateForces().get(2), farOn.calculateForces().get(2));

        List<DynamicElasticNode> close = nodes(new Vector3d(0, 0, 0),
                new Vector3d(10, 0, 0), new Vector3d(1, 0, 0),
                new Vector3d(0, 10, 0));
        DynamicElasticSession closeOff = new DynamicElasticSession(cycle, disabled, close);
        DynamicElasticSession closeOn = new DynamicElasticSession(cycle, enabled, close);
        Vector3d first = new Vector3d(closeOn.calculateForces().get(0))
                .sub(closeOff.calculateForces().get(0));
        Vector3d second = new Vector3d(closeOn.calculateForces().get(2))
                .sub(closeOff.calculateForces().get(2));
        assertTrue(first.x < 0);
        assertEquals(0, first.add(second).length(), 1e-12);
    }

    @Test void crossingChecksSkipSharedEndpointsAndSeparateIndependentEdges() {
        DiscoveryConnection a = new DiscoveryConnection(0, 1);
        assertTrue(DynamicElasticSpatialConstraints.shareEndpoint(a,
                new DiscoveryConnection(1, 2)));
        assertFalse(DynamicElasticSpatialConstraints.shareEndpoint(a,
                new DiscoveryConnection(2, 3)));

        FixedDegreeGraph cycle = CanonicalDynamicElasticTopology.CYCLE.create(4);
        List<DynamicElasticNode> crossing = nodes(new Vector3d(-1, 0, 0),
                new Vector3d(1, 0, 0), new Vector3d(0, -1, 0),
                new Vector3d(0, 1, 0));
        DynamicElasticSettings off = disabled(2);
        DynamicElasticSettings on = off.withSpatial(new DynamicElasticSpatialSettings(
                false, 1, true, .5, false, .2));
        Map<Integer, Vector3d> base = new DynamicElasticSession(cycle, off, crossing)
                .calculateForces();
        Map<Integer, Vector3d> corrected = new DynamicElasticSession(cycle, on, crossing)
                .calculateForces();
        double difference = 0;
        Vector3d sum = new Vector3d();
        for (int id = 0; id < 4; id++) {
            Vector3d delta = new Vector3d(corrected.get(id)).sub(base.get(id));
            difference += delta.length();
            sum.add(delta);
        }
        assertTrue(difference > 0);
        assertEquals(0, sum.length(), 1e-12);
    }

    @Test void localSpreadMatchesCoplanarAndOrthogonalDefinitions() {
        assertEquals(0, DynamicElasticSpatialConstraints.localSpread(
                new Vector3d(1, 0, 0), new Vector3d(0, 1, 0),
                new Vector3d(1, 1, 0)), 1e-12);
        assertEquals(1, DynamicElasticSpatialConstraints.localSpread(
                new Vector3d(1, 0, 0), new Vector3d(0, 1, 0),
                new Vector3d(0, 0, 1)), 1e-12);
    }

    @Test void constraintsAreDeterministicAndDisabledPathMatchesSpringOnlyBehavior() {
        FixedDegreeGraph cube = CanonicalDynamicElasticTopology.CUBE.create(8);
        DynamicElasticSettings enabled = DynamicElasticSettings.defaults(10, 42)
                .withSpatial(DynamicElasticSettings.defaults(10, 42).spatial()
                        .withLocalSpreadEnabled(true));
        DynamicElasticSession a = new DynamicElasticSession(cube, enabled);
        DynamicElasticSession b = new DynamicElasticSession(cube, enabled);
        for (int i = 0; i < 25; i++) { a.step(); b.step(); }
        assertEquals(a.nodes().stream().map(DynamicElasticNode::position).toList(),
                b.nodes().stream().map(DynamicElasticNode::position).toList());

        FixedDegreeGraph triangle = CanonicalDynamicElasticTopology.TETRAHEDRON.create(4);
        DynamicElasticSession springOnly = new DynamicElasticSession(triangle, disabled(10));
        Map<Integer, Vector3d> forces = springOnly.calculateForces();
        Map<Integer, Vector3d> expected = springForces(triangle, springOnly.nodes(), 10,
                springOnly.settings().springStrength());
        expected.forEach((id, force) -> assertEquals(0,
                new Vector3d(force).sub(forces.get(id)).length(), 1e-12));
    }

    @Test void spatialValidityIsIndependentFromElasticEquilibrium() {
        FixedDegreeGraph cycle = CanonicalDynamicElasticTopology.CYCLE.create(4);
        DynamicElasticSession session = new DynamicElasticSession(cycle, disabled(10)
                .withSpatial(new DynamicElasticSpatialSettings(false, 7,
                        false, 1, false, 0)), nodes(new Vector3d(0, 0, 0),
                new Vector3d(10, 0, 0), new Vector3d(.1, 0, 0),
                new Vector3d(0, 10, 0)));
        assertFalse(session.metrics().elasticEquilibrium());
        assertFalse(session.metrics().spatiallyValid());
    }

    private DynamicElasticSettings disabled(double target) {
        return DynamicElasticSettings.defaults(target, 42)
                .withSpatial(DynamicElasticSpatialSettings.disabled(target));
    }
    private List<DynamicElasticNode> nodes(Vector3d... positions) {
        return java.util.stream.IntStream.range(0, positions.length)
                .mapToObj(id -> new DynamicElasticNode(id, positions[id],
                        new Vector3d(), id < 2)).toList();
    }
    private Map<Integer, Vector3d> springForces(FixedDegreeGraph graph,
            List<DynamicElasticNode> nodes, double target, double strength) {
        Map<Integer, Vector3d> result = new java.util.HashMap<>();
        nodes.forEach(n -> result.put(n.id(), new Vector3d()));
        for (DiscoveryConnection edge : graph.connections()) {
            Vector3d d = new Vector3d(nodes.get(edge.secondNodeId()).position())
                    .sub(nodes.get(edge.firstNodeId()).position());
            double length = d.length();
            Vector3d force = d.div(length).mul(strength * (length - target));
            result.get(edge.firstNodeId()).add(force);
            result.get(edge.secondNodeId()).sub(force);
        }
        return result;
    }
    private Map<Integer, Set<Integer>> adjacency(FixedDegreeGraph graph) {
        Map<Integer, Set<Integer>> result = new java.util.HashMap<>();
        for (int i = 0; i < graph.nodeCount(); i++) result.put(i, new java.util.HashSet<>());
        for (DiscoveryConnection edge : graph.connections()) {
            result.get(edge.firstNodeId()).add(edge.secondNodeId());
            result.get(edge.secondNodeId()).add(edge.firstNodeId());
        }
        return result;
    }
    private int connectedCount(Map<Integer, Set<Integer>> adjacency) {
        Set<Integer> seen = new java.util.HashSet<>(Set.of(0));
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>(List.of(0));
        while (!queue.isEmpty()) for (int next : adjacency.get(queue.removeFirst()))
            if (seen.add(next)) queue.add(next);
        return seen.size();
    }
    private int triangleCount(Map<Integer, Set<Integer>> adjacency) {
        int count = 0;
        for (int a : adjacency.keySet()) for (int b : adjacency.get(a))
            for (int c : adjacency.get(b))
                if (a < b && b < c && adjacency.get(c).contains(a)) count++;
        return count;
    }
}
