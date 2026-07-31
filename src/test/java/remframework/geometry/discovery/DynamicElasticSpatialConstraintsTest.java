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

    @Test void exactCubeHasTwentyFourSatisfiedCanonicalAngles() {
        FixedDegreeGraph cube = CanonicalDynamicElasticTopology.CUBE.create(8);
        DynamicElasticSession session = new DynamicElasticSession(cube,
                canonicalSettings(10, 2, 10), exactCube(10));
        DynamicElasticMetrics metrics = session.metrics();
        assertEquals(24, metrics.localAngleCount());
        assertEquals(24, metrics.localAnglesSatisfied());
        assertEquals(0, metrics.maximumAngleErrorDegrees(), 1e-10);
        session.step();
        session.step();
        assertTrue(session.metrics().elasticEquilibrium());
        assertTrue(session.metrics().canonicalGeometryValid());
    }

    @Test void shearedCubeFailsCanonicalAngleValidity() {
        List<DynamicElasticNode> sheared = exactCube(10);
        for (int id = 4; id < 8; id++)
            sheared.get(id).position().add(3, 0, 0);
        DynamicElasticSession session = new DynamicElasticSession(
                CanonicalDynamicElasticTopology.CUBE.create(8),
                canonicalSettings(10, 1, 1), sheared);
        assertTrue(session.metrics().localAnglesSatisfied() < 24);
        assertFalse(session.metrics().canonicalGeometryValid());
    }

    @Test void angleEnergyAndCorrectionFollowOrthogonalityEnergy() {
        assertEquals(0, DynamicElasticAngleConstraints.energy(
                new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), 90, 1), 1e-12);
        assertTrue(DynamicElasticAngleConstraints.energy(
                new Vector3d(1, 0, 0), new Vector3d(1, 1, 0), 90, 1) > 0);

        List<DynamicElasticNode> sheared = exactCube(10);
        for (int id = 4; id < 8; id++) sheared.get(id).position().add(4, 0, 0);
        DynamicElasticSettings base = canonicalSettings(10, 100, 1);
        DynamicElasticSettings angleOnly = new DynamicElasticSettings(10, .98, 0, .1,
                0, 0, 0, 10, 10, 0, 100, 1, 1, 42,
                DynamicElasticSpatialSettings.disabled(10),
                new DynamicElasticCanonicalGeometrySettings(true, 90, 2, .5, false));
        DynamicElasticSession session = new DynamicElasticSession(
                CanonicalDynamicElasticTopology.CUBE.create(8), angleOnly, sheared);
        double before = session.metrics().meanAngleErrorDegrees();
        session.step();
        assertTrue(session.metrics().meanAngleErrorDegrees() < before);
    }

    @Test void canonicalAnglesPreserveTopologyDegreeAnchorsAndDeterminism() {
        FixedDegreeGraph cube = CanonicalDynamicElasticTopology.CUBE.create(8);
        DynamicElasticSettings settings = canonicalSettings(10, 100, 100);
        DynamicElasticSession first = new DynamicElasticSession(cube, settings);
        DynamicElasticSession second = new DynamicElasticSession(cube, settings);
        List<DiscoveryConnection> edges = cube.connections();
        for (int i = 0; i < 20; i++) { first.step(); second.step(); }
        assertEquals(edges, first.graph().connections());
        assertTrue(adjacency(first.graph()).values().stream().allMatch(n -> n.size() == 3));
        assertEquals(new Vector3d(), first.nodes().get(0).position());
        assertEquals(new Vector3d(10, 0, 0), first.nodes().get(1).position());
        assertEquals(first.nodes().stream().map(DynamicElasticNode::position).toList(),
                second.nodes().stream().map(DynamicElasticNode::position).toList());
    }

    @Test void generatedGraphsHaveNoImplicitCubeAngles() {
        FixedDegreeGraph generated = new FixedDegreeGraphGenerator().generate(8, 3, 42, 0);
        DynamicElasticSession session = new DynamicElasticSession(generated,
                DynamicElasticSettings.defaults(10, 42));
        assertFalse(session.settings().canonicalGeometry().angleConstraintsEnabled());
        assertEquals(0, session.metrics().localAngleCount());
    }

    @Test void canonicalSignaturesSkipRelabelledDuplicatesAndRetainDistinctGraphs() {
        FixedDegreeGraph cube = CanonicalDynamicElasticTopology.CUBE.create(8);
        List<DiscoveryConnection> relabelled = cube.connections().stream()
                .map(e -> new DiscoveryConnection((e.firstNodeId() + 3) % 8,
                        (e.secondNodeId() + 3) % 8)).toList();
        String cubeSignature = CanonicalGraphSignature.of(8, cube.connections());
        assertEquals(cubeSignature, CanonicalGraphSignature.of(8, relabelled));
        FixedDegreeGraph wagner = new FixedDegreeGraphGenerator().generate(8, 3, 1, 0);
        boolean foundDistinct = false;
        for (int attempt = 0; attempt < 30 && !foundDistinct; attempt++) {
            FixedDegreeGraph candidate = new FixedDegreeGraphGenerator()
                    .generate(8, 3, 7, attempt);
            foundDistinct = !candidate.canonicalGraphSignature().equals(cubeSignature);
        }
        assertTrue(foundDistinct);
    }

    @Test void statusCannotReportConvergenceForNineOfTwelveEdgesAndDisabledIsExplicit() {
        DynamicElasticMetrics base = new DynamicElasticSession(
                CanonicalDynamicElasticTopology.CUBE.create(8),
                canonicalSettings(10, 2, 10), exactCube(10)).metrics();
        DynamicElasticMetrics incomplete = new DynamicElasticMetrics(base.iteration(),
                9, 12, base.meanDistanceAccuracy(), 75, 0, 100, 100,
                DynamicElasticState.EQUILIBRIUM_REACHED, base.edges(),
                base.minimumObservedNonNeighborDistance(), base.edgeClearanceViolations(),
                base.meanLocalSpread(), base.minimumLocalSpread(), true, true,
                24, 24, 0, 0, 90, 2, true);
        assertEquals("Relaxation Required", DynamicElasticStatusReporter.label(incomplete));
        assertEquals("Disabled", DynamicElasticStatusReporter.constraintState(false));
    }

    @Test void boundedEnumerationRetainsOnlyUniqueCanonicalSignatures() {
        DynamicElasticSettings oneStep = DynamicElasticSettings.defaults(10, 42)
                .withMaximumIterations(1);
        DynamicElasticSearchSettings search = new DynamicElasticSearchSettings(
                3, 8, 8, 20, 1,
                DynamicElasticSearchPolicy.FIND_ALL_STABLE_CANDIDATES,
                oneStep, 10);
        List<DynamicElasticSearchRecord> records =
                new DynamicElasticBoundedSearch().run(search).stream()
                        .filter(record -> record.result() != null).toList();
        Set<String> signatures = records.stream()
                .map(record -> record.result().graph().canonicalGraphSignature())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(signatures.size(), records.size());
        assertTrue(records.stream().anyMatch(record ->
                record.result().graph().generationAttempts()
                        > record.result().graph().topologyIndex() + 1));
    }

    private DynamicElasticSettings disabled(double target) {
        return DynamicElasticSettings.defaults(target, 42)
                .withSpatial(DynamicElasticSpatialSettings.disabled(target));
    }
    private DynamicElasticSettings canonicalSettings(double target, int stable, int maximum) {
        DynamicElasticSettings defaults = DynamicElasticSettings.defaults(target, 42);
        return new DynamicElasticSettings(target, .98, defaults.springStrength(),
                defaults.updateStrength(), defaults.velocityRetention(),
                defaults.linearVelocityDamping(), defaults.angularVelocityDamping(),
                defaults.maximumVelocity(), defaults.maximumForce(),
                defaults.maximumEquilibriumSpeed(), stable, maximum, 1, 42,
                DynamicElasticSpatialSettings.disabled(target),
                DynamicElasticCanonicalGeometrySettings.forTopology(
                        CanonicalDynamicElasticTopology.CUBE));
    }
    private List<DynamicElasticNode> exactCube(double side) {
        return nodes(new Vector3d(0, 0, 0), new Vector3d(side, 0, 0),
                new Vector3d(side, side, 0), new Vector3d(0, side, 0),
                new Vector3d(0, 0, side), new Vector3d(side, 0, side),
                new Vector3d(side, side, side), new Vector3d(0, side, side));
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
