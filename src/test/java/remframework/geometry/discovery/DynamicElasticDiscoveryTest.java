package remframework.geometry.discovery;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class DynamicElasticDiscoveryTest {
    @TempDir Path temp;

    @Test void degreeTwoValidationAndCycleAreExact() {
        assertFalse(new FixedDegreeValidator().validate(2, 2).valid());
        FixedDegreeGraph graph = new FixedDegreeGraphGenerator().generate(7, 2, 42, 0);
        assertEquals(7, graph.connections().size());
        Map<Integer, java.util.Set<Integer>> adjacency = adjacency(graph);
        assertEquals(7, connectedCount(adjacency));
        assertTrue(adjacency.values().stream().allMatch(neighbors -> neighbors.size() == 2));
    }

    @Test void degreeThreeValidationGenerationAndDeterminism() {
        assertFalse(new FixedDegreeValidator().validate(3, 3).valid());
        assertFalse(new FixedDegreeValidator().validate(7, 3).valid());
        FixedDegreeGraph first = new FixedDegreeGraphGenerator().generate(8, 3, 42, 0);
        FixedDegreeGraph second = new FixedDegreeGraphGenerator().generate(8, 3, 42, 0);
        assertEquals(first.topologySignature(), second.topologySignature());
        Map<Integer, java.util.Set<Integer>> adjacency = adjacency(first);
        assertTrue(adjacency.values().stream().allMatch(neighbors -> neighbors.size() == 3));
        assertEquals(8, connectedCount(adjacency));
        DynamicElasticSession a = new DynamicElasticSession(first,
                DynamicElasticSettings.defaults(10, 42));
        DynamicElasticSession b = new DynamicElasticSession(second,
                DynamicElasticSettings.defaults(10, 42));
        for (int i = 0; i < 8; i++)
            assertEquals(a.nodes().get(i).position(), b.nodes().get(i).position());
    }

    @Test void topologyNeverChangesAndSpringForcesAreSymmetric() {
        FixedDegreeGraph graph = new FixedDegreeGraphGenerator().generate(4, 2, 1, 0);
        DynamicElasticSession session = new DynamicElasticSession(graph,
                DynamicElasticSettings.defaults(10, 1));
        String signature = graph.topologySignature();
        Map<Integer, Vector3d> forces = session.calculateForces();
        Vector3d sum = new Vector3d();
        forces.values().forEach(sum::add);
        assertEquals(0, sum.length(), 1e-10);
        for (int i = 0; i < 20; i++) session.step();
        assertEquals(signature, session.graph().topologySignature());
        assertEquals(graph.connections(), session.graph().connections());
    }

    @Test void longEdgesPullAndShortEdgesPush() {
        FixedDegreeGraph graph = new FixedDegreeGraphGenerator().generate(3, 2, 1, 0);
        DynamicElasticSession longEdge = session(graph, List.of(
                node(0, 0, 0, 0, true), node(1, 10, 0, 0, true),
                node(2, 30, 0, 0, false)), .98, 10);
        assertTrue(longEdge.calculateForces().get(2).x < 0);
        DynamicElasticSession shortEdge = session(graph, List.of(
                node(0, 0, 0, 0, true), node(1, 10, 0, 0, true),
                node(2, 9, 0, 0, false)), .98, 10);
        // The very short 1-2 edge dominates and pushes node 2 away from node 1.
        assertTrue(shortEdge.calculateForces().get(2).x < 0);
    }

    @Test void anchorsNeverMoveAndDampingReducesMotion() {
        FixedDegreeGraph graph = new FixedDegreeGraphGenerator().generate(4, 2, 2, 0);
        DynamicElasticSettings base = DynamicElasticSettings.defaults(10, 2);
        DynamicElasticSession session = new DynamicElasticSession(graph, base);
        session.setNodeState(2, new Vector3d(5, 8, 0), new Vector3d(0, 1, 0));
        session.step();
        assertEquals(new Vector3d(), session.nodes().get(0).position());
        assertEquals(new Vector3d(10, 0, 0), session.nodes().get(1).position());
        assertEquals(0, session.nodes().get(0).velocity().length());
        assertEquals(0, session.nodes().get(1).velocity().length());

        List<DynamicElasticNode> initial = List.of(node(0, 0, 0, 0, true),
                node(1, 10, 0, 0, true), moving(2, 5, 8, 0, 0, 2, 0),
                node(3, -3, 4, 0, false));
        DynamicElasticSession low = new DynamicElasticSession(graph,
                withDamping(base, 0, 0), initial);
        DynamicElasticSession high = new DynamicElasticSession(graph,
                withDamping(base, .8, .8), initial);
        low.step(); high.step();
        assertTrue(high.metrics().maximumNodeSpeed() < low.metrics().maximumNodeSpeed());
    }

    @Test void accuracySatisfiedCountAndStableCriterionAreExact() {
        FixedDegreeGraph triangle = new FixedDegreeGraphGenerator().generate(3, 2, 3, 0);
        double h = Math.sqrt(75);
        List<DynamicElasticNode> exact = List.of(node(0, 0, 0, 0, true),
                node(1, 10, 0, 0, true), node(2, 5, h, 0, false));
        DynamicElasticSettings settings = stableSettings(10, .98, 2, 20);
        DynamicElasticSession session = new DynamicElasticSession(triangle, settings, exact);
        DynamicElasticMetrics initial = session.metrics();
        assertEquals(3, initial.connectionsSatisfied());
        assertEquals(1, initial.meanDistanceAccuracy(), 1e-12);
        session.step();
        assertNotEquals(DynamicElasticState.EQUILIBRIUM_REACHED, session.state());
        session.step();
        assertEquals(DynamicElasticState.EQUILIBRIUM_REACHED, session.state());

        DynamicElasticSession fast = new DynamicElasticSession(triangle, settings, exact);
        fast.setNodeState(2, new Vector3d(5, h, 0), new Vector3d(1, 0, 0));
        fast.step();
        assertNotEquals(DynamicElasticState.EQUILIBRIUM_REACHED, fast.state());
        assertEquals(0, fast.metrics().stableIterations());
    }

    @Test void stableCounterResetsAfterTransient() {
        FixedDegreeGraph triangle = new FixedDegreeGraphGenerator().generate(3, 2, 4, 0);
        double h = Math.sqrt(75);
        DynamicElasticSession session = new DynamicElasticSession(triangle,
                stableSettings(10, .98, 3, 30), List.of(node(0, 0, 0, 0, true),
                node(1, 10, 0, 0, true), node(2, 5, h, 0, false)));
        session.step();
        assertEquals(1, session.metrics().stableIterations());
        session.setNodeState(2, new Vector3d(5, 20, 0), new Vector3d());
        session.step();
        assertEquals(0, session.metrics().stableIterations());
    }

    @Test void nearestValidCountsAndBoundedSearchSkipOddDegreeThreeCounts() {
        FixedDegreeValidation validation = new FixedDegreeValidator().validate(7, 3);
        assertFalse(validation.valid());
        assertEquals(6, validation.nearestLowerValid());
        assertEquals(8, validation.nearestHigherValid());
        DynamicElasticSearchSettings search = new DynamicElasticSearchSettings(3, 5, 10,
                1, 1, DynamicElasticSearchPolicy.FIND_ALL_STABLE_CANDIDATES,
                stableSettings(10, .98, 1, 1));
        assertEquals(List.of(6, 8, 10),
                new DynamicElasticBoundedSearch().validNodeCounts(search));
    }

    @Test void resultStoreAppendsAndLoadsDefensively() throws Exception {
        FixedDegreeGraph graph = new FixedDegreeGraphGenerator().generate(3, 2, 5, 0);
        DynamicElasticResult result = new DynamicElasticSession(graph,
                stableSettings(10, .98, 1, 1), List.of(node(0, 0, 0, 0, true),
                node(1, 10, 0, 0, true), node(2, 5, Math.sqrt(75), 0, false)))
                .runUntilFinished();
        DynamicElasticResultStore store = new DynamicElasticResultStore(temp);
        DynamicElasticResultRow first = store.append(result);
        DynamicElasticResultRow second = store.append(result);
        assertNotEquals(first.runId(), second.runId());
        Path csv = temp.resolve("stable-results.csv");
        Files.writeString(csv, "malformed,row\n", java.nio.file.StandardOpenOption.APPEND);
        assertEquals(2, store.loadRows().size());
        String detail = Files.readString(first.resultFile());
        assertTrue(detail.contains("[[initialNodes]]"));
        assertTrue(detail.contains("[[finalNodes]]"));
        assertTrue(detail.contains("[[connections]]"));
        assertTrue(detail.contains("topologySource"));
        assertTrue(detail.contains("minimumNonNeighborDistance"));
        assertTrue(detail.contains("elasticEquilibrium"));
        assertTrue(detail.contains("spatiallyValid"));
        assertTrue(detail.contains("canonicalAngleConstraintsEnabled"));
        assertTrue(detail.contains("maximumAngleErrorDegrees"));
        assertTrue(detail.contains("canonicalGeometryValid"));
        assertTrue(detail.contains("canonicalGraphSignature"));
        LoadedDynamicElasticResult loaded =
                new DynamicElasticResultLoader().load(first.resultFile());
        assertEquals(3, loaded.model().nodes().size());
        assertEquals(3, loaded.model().connections().size());
        assertEquals(10, loaded.settings().targetDistance());
    }

    @Test void invalidSettingsAndInitialStatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                DynamicElasticSettings.defaults(10, 1).withRequiredAccuracy(1.01));
        FixedDegreeGraph graph = new FixedDegreeGraphGenerator().generate(3, 2, 1, 0);
        assertThrows(IllegalArgumentException.class, () ->
                new DynamicElasticSession(graph, DynamicElasticSettings.defaults(10, 1),
                        List.of(node(0, 0, 0, 0, true),
                                node(1, 10, 0, 0, true))));
    }

    @Test void wizardDraftRoundTripsAndResetClearsIt() {
        Path path = temp.resolve("draft.properties");
        GeometryDiscoveryWizardDraftStore store =
                new GeometryDiscoveryWizardDraftStore(path);
        Properties values = new Properties();
        values.setProperty("nodes", "8");
        values.setProperty("exactNeighbors", "3");
        store.save(values);
        assertEquals("8", store.load().getProperty("nodes"));
        assertEquals("3", store.load().getProperty("exactNeighbors"));
        store.reset();
        assertTrue(store.load().isEmpty());
    }

    @Test void workspaceCreatesDynamicModeWithoutStartingWorker() {
        GeometryDiscoveryWorkspace workspace =
                new GeometryDiscoveryWorkspace(new GeometryDiscoveryWorkspaceState(),
                        new DynamicElasticResultStore(temp.resolve("workspace-results")));
        try {
            FixedDegreeValidation validation = workspace.createDynamicElastic(
                    4, 3, DynamicElasticSettings.defaults(10, 42));
            assertTrue(validation.valid());
            assertTrue(workspace.dynamicElasticActive());
            assertFalse(workspace.snapshot().relaxationRunning());
            assertEquals(6, workspace.dynamicElasticMetrics().connectionCount());
            workspace.singleRelaxationStep();
            for (int i = 0; i < 100 && workspace.dynamicElasticMetrics().iteration() == 0; i++)
                Thread.onSpinWait();
            assertEquals(6, workspace.dynamicElasticMetrics().connectionCount());
        } finally {
            workspace.close();
        }
    }

    private DynamicElasticSession session(FixedDegreeGraph graph,
                                          List<DynamicElasticNode> nodes,
                                          double accuracy, double target) {
        return new DynamicElasticSession(graph,
                stableSettings(target, accuracy, 100, 1000), nodes);
    }
    private DynamicElasticSettings stableSettings(double target, double accuracy,
                                                  int stable, int maximum) {
        DynamicElasticSettings d = DynamicElasticSettings.defaults(target, 42);
        return new DynamicElasticSettings(target, accuracy, d.springStrength(),
                d.updateStrength(), d.velocityRetention(), d.linearVelocityDamping(),
                d.angularVelocityDamping(), d.maximumVelocity(), d.maximumForce(),
                d.maximumEquilibriumSpeed(), stable, maximum, 1, d.randomSeed());
    }
    private DynamicElasticSettings withDamping(DynamicElasticSettings d,
                                               double linear, double angular) {
        return new DynamicElasticSettings(d.targetDistance(), d.requiredAccuracy(),
                d.springStrength(), d.updateStrength(), d.velocityRetention(), linear,
                angular, d.maximumVelocity(), d.maximumForce(),
                d.maximumEquilibriumSpeed(), d.requiredStableIterations(),
                d.maximumIterations(), d.workerUpdatesPerPublication(), d.randomSeed());
    }
    private DynamicElasticNode node(int id, double x, double y, double z, boolean fixed) {
        return new DynamicElasticNode(id, new Vector3d(x, y, z), new Vector3d(), fixed);
    }
    private DynamicElasticNode moving(int id, double x, double y, double z,
                                      double vx, double vy, double vz) {
        return new DynamicElasticNode(id, new Vector3d(x, y, z),
                new Vector3d(vx, vy, vz), false);
    }
    private Map<Integer, java.util.Set<Integer>> adjacency(FixedDegreeGraph graph) {
        GeometryDiscoveryModel model = new GeometryDiscoveryModel("graph", "",
                java.util.stream.IntStream.range(0, graph.nodeCount())
                        .mapToObj(id -> new DiscoveryNode(id, new Vector3d(), id == 0)).toList(),
                graph.connections());
        return TopologyConstraintValidator.adjacency(model);
    }
    private int connectedCount(Map<Integer, java.util.Set<Integer>> adjacency) {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        queue.add(0); seen.add(0);
        while (!queue.isEmpty())
            for (int next : adjacency.get(queue.removeFirst()))
                if (seen.add(next)) queue.addLast(next);
        return seen.size();
    }
}
