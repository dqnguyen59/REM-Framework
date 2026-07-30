package remframework.geometry.discovery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DiscoveryExperimentTest {
    @TempDir Path temp;
    private GeometryDiscoveryWorkspace workspace;

    @AfterEach void close() {
        if (workspace != null) workspace.close();
    }

    @Test void sameConfigurationAndSeedReproduceGraphAndCoordinates() {
        DiscoveryExperimentConfiguration configuration =
                DiscoveryExperimentConfiguration.defaults();
        DiscoveryExperimentGenerator generator = new DiscoveryExperimentGenerator();
        GeometryDiscoveryModel first = generator.generate(configuration);
        GeometryDiscoveryModel second = generator.generate(configuration);
        assertEquals(TopologySignature.of(first), TopologySignature.of(second));
        for (int i = 0; i < first.sortedNodes().size(); i++)
            assertEquals(first.sortedNodes().get(i).position(),
                    second.sortedNodes().get(i).position());
    }

    @Test void randomConnectedGraphIsValidConnectedAndDegreeBounded() {
        DiscoveryExperimentConfiguration configuration =
                DiscoveryExperimentConfiguration.defaults();
        GeometryDiscoveryModel model = new DiscoveryExperimentGenerator().generate(configuration);
        TopologyMetrics metrics = new TopologyMetricsCalculator().calculate(model);
        assertEquals(1, metrics.connectedComponents());
        assertTrue(metrics.minimumDegree() >= configuration.minimumDegree());
        assertTrue(metrics.maximumDegree() <= configuration.maximumDegree());
        Set<Long> edges = new HashSet<>();
        model.connections().forEach(edge -> {
            assertNotEquals(edge.firstNodeId(), edge.secondNodeId());
            assertTrue(edges.add(edge.undirectedKey()));
        });
    }

    @Test void impossibleDegreeConstraintsAreReportedTogether() {
        DiscoveryExperimentConfiguration base = DiscoveryExperimentConfiguration.defaults();
        DiscoveryExperimentConfiguration invalid = copy(base, 2, 12,
                DiscoveryInitialTopology.RANDOM_CONNECTED_GRAPH,
                DiscoveryGeometryInitialization.RANDOM_SPHERE, 12, 4, 5);
        DiscoveryExperimentValidation validation =
                new DiscoveryExperimentValidator().validate(invalid);
        assertFalse(validation.valid());
        assertTrue(validation.errors().stream().anyMatch(e -> e.contains("Maximum neighbors")));
        assertTrue(validation.errors().stream().anyMatch(e -> e.contains("Target neighbors")));
    }

    @Test void twoDimensionalInitializationsRemainPlanarAndSphereIsFinite() {
        DiscoveryExperimentConfiguration base = DiscoveryExperimentConfiguration.defaults();
        GeometryDiscoveryModel planar = new DiscoveryExperimentGenerator().generate(
                copy(base, 2, 12, DiscoveryInitialTopology.RANDOM_CONNECTED_GRAPH,
                        DiscoveryGeometryInitialization.RANDOM_SPHERE, 3, 6, 4));
        assertTrue(planar.nodes().stream().allMatch(node -> node.position().z == 0));
        assertTrue(planar.nodes().stream().allMatch(node -> node.position().isFinite()));
        assertTrue(planar.nodes().stream().skip(1)
                .anyMatch(node -> node.position().lengthSquared() > 0));
    }

    @Test void createModesRespectWorkerSequencingAndLifecycle() {
        workspace = new GeometryDiscoveryWorkspace(new GeometryDiscoveryWorkspaceState());
        DiscoveryExperimentConfiguration base = shortConfiguration();
        assertTrue(workspace.createExperiment(base).valid());
        assertTrue(workspace.workerStopped());
        assertNull(workspace.snapshot().search());
        assertEquals(DiscoveryExperimentLifecycle.CREATED,
                workspace.state().experimentLifecycle());

        assertTrue(workspace.createAndRelax(base).valid());
        await(() -> !workspace.lastWorkerThreadName().isBlank());
        assertNull(workspace.snapshot().search());

        assertTrue(workspace.startDiscovery(base).valid());
        await(() -> workspace.snapshot().search() != null);
        assertTrue(workspace.eventLog().stream()
                .anyMatch(event -> event.type().equals("relaxation-started")));
        int relaxationEvent = eventIndex("relaxation-completed", "relaxation-failed");
        int searchEvent = eventIndex("search-started");
        assertTrue(relaxationEvent >= 0 && searchEvent > relaxationEvent);
    }

    @Test void invalidConfigurationDoesNotReplaceCandidate() {
        workspace = new GeometryDiscoveryWorkspace(new GeometryDiscoveryWorkspaceState());
        String before = workspace.snapshot().geometry().model().name();
        DiscoveryExperimentConfiguration base = DiscoveryExperimentConfiguration.defaults();
        DiscoveryExperimentConfiguration invalid = new DiscoveryExperimentConfiguration(
                "", base.description(), base.embeddingDimension(), 100, base.randomSeed(),
                base.scoringProfile(), base.scoreWeights(), false, base.initialTopology(),
                base.neighborCountPolicy(),
                base.minimumDegree(), base.maximumDegree(), base.targetInitialDegree(),
                base.edgeProbability(), null, base.geometryInitialization(),
                base.initialExtent(), base.perturbationMagnitude(), base.geometrySettings(),
                base.workerIterationsPerUpdate(), base.topologySearchSettings());
        assertFalse(workspace.createExperiment(invalid).valid());
        assertEquals(before, workspace.snapshot().geometry().model().name());
    }

    @Test void viewportSelectionFollowBestAndSnapshotRateAreStateControlled() {
        GeometryDiscoveryWorkspaceState state = new GeometryDiscoveryWorkspaceState();
        assertEquals(DiscoveryViewportSelection.CURRENT_CANDIDATE, state.viewportSelection());
        state.setViewportSelection(DiscoveryViewportSelection.BEST_CANDIDATE);
        assertEquals(DiscoveryViewportSelection.BEST_CANDIDATE, state.viewportSelection());
        state.setFollowBestCandidate(true);
        assertTrue(state.followBestCandidate());
        state.setSnapshotUpdatesPerSecond(8);
        assertEquals(8, state.snapshotUpdatesPerSecond());
        state.setSnapshotUpdatesPerSecond(1000);
        assertEquals(60, state.snapshotUpdatesPerSecond());
    }

    @Test void savedConfigurationReproducesWithoutResumingWorkerAndExportEmbedsIt()
            throws Exception {
        DiscoveryExperimentConfiguration configuration =
                DiscoveryExperimentConfiguration.defaults();
        DiscoveryExperimentConfigurationStore store =
                new DiscoveryExperimentConfigurationStore();
        Path saved = store.save(configuration, temp.resolve("experiment.properties"));
        DiscoveryExperimentConfiguration loaded = store.load(saved);
        GeometryDiscoveryModel expected = new DiscoveryExperimentGenerator()
                .generate(configuration);
        GeometryDiscoveryModel actual = new DiscoveryExperimentGenerator().generate(loaded);
        assertEquals(TopologySignature.of(expected), TopologySignature.of(actual));
        assertEquals(expected.sortedNodes().get(3).position(),
                actual.sortedNodes().get(3).position());

        workspace = new GeometryDiscoveryWorkspace(new GeometryDiscoveryWorkspaceState());
        assertTrue(workspace.loadExperimentConfiguration(saved).valid());
        assertTrue(workspace.workerStopped());
        assertFalse(workspace.snapshot().relaxationRunning());
        assertFalse(workspace.snapshot().searchRunning());
        Path candidate = workspace.exportCurrent(temp.resolve("candidate.toml"));
        String text = Files.readString(candidate);
        assertTrue(text.contains("[experiment]"));
        assertTrue(text.contains("randomSeed = 42"));
        assertTrue(text.contains("[experiment.scoreWeights]"));
    }

    @Test void significantEventLogIsBoundedAndContainsCreation() {
        workspace = new GeometryDiscoveryWorkspace(new GeometryDiscoveryWorkspaceState());
        for (int i = 0; i < 240; i++)
            workspace.createExperiment(DiscoveryExperimentConfiguration.defaults());
        assertTrue(workspace.eventLog().size() <= 200);
        assertTrue(workspace.eventLog().stream()
                .anyMatch(event -> event.type().equals("created")));
    }

    @Test void neighborFieldsMapDirectlyToDegreeFieldsAndBoundedPolicyValidates() {
        DiscoveryExperimentConfiguration configuration = withNeighborPolicy(
                DiscoveryExperimentConfiguration.defaults(), 12,
                DiscoveryNeighborCountPolicy.BOUNDED, 4, 6, 8);
        assertEquals(4, configuration.minimumDegree());
        assertEquals(6, configuration.targetInitialDegree());
        assertEquals(8, configuration.maximumDegree());
        assertTrue(new DiscoveryExperimentValidator().validate(configuration).valid());
    }

    @Test void uniformPolicyRequiresEqualGraphicallyPossibleDegree() {
        DiscoveryExperimentConfiguration base = DiscoveryExperimentConfiguration.defaults();
        DiscoveryExperimentConfiguration unequal = withNeighborPolicy(base, 12,
                DiscoveryNeighborCountPolicy.UNIFORM, 4, 6, 8);
        DiscoveryExperimentValidation unequalValidation =
                new DiscoveryExperimentValidator().validate(unequal);
        assertFalse(unequalValidation.valid());
        assertTrue(unequalValidation.errors().stream()
                .anyMatch(error -> error.contains("Uniform policy requires")));

        DiscoveryExperimentConfiguration selfDegree = withNeighborPolicy(base, 5,
                DiscoveryNeighborCountPolicy.UNIFORM, 5, 5, 5);
        DiscoveryExperimentValidation selfValidation =
                new DiscoveryExperimentValidator().validate(selfDegree);
        assertFalse(selfValidation.valid());
        assertTrue(selfValidation.errors().stream()
                .anyMatch(error -> error.contains("cannot connect to itself")));

        DiscoveryExperimentConfiguration possible = withNeighborPolicy(base, 12,
                DiscoveryNeighborCountPolicy.UNIFORM, 4, 4, 4);
        assertTrue(new DiscoveryExperimentValidator().validate(possible).valid());
        GeometryDiscoveryModel model = new DiscoveryExperimentGenerator().generate(possible);
        TopologyMetrics metrics = new TopologyMetricsCalculator().calculate(model);
        assertEquals(4, metrics.minimumDegree());
        assertEquals(4, metrics.maximumDegree());
    }

    private DiscoveryExperimentConfiguration withNeighborPolicy(
            DiscoveryExperimentConfiguration base, int nodeCount,
            DiscoveryNeighborCountPolicy policy, int minimum, double target, int maximum) {
        TopologySearchSettings old = base.topologySearchSettings();
        TopologySearchSettings search = new TopologySearchSettings(
                old.searchMode(), old.randomSeed(), old.maximumSearchIterations(),
                old.mutationsAttemptedPerIteration(), minimum, maximum,
                old.requiredEdgeCount(), old.archiveSize(), old.initialTemperature(),
                old.coolingRate(), old.minimumTemperature(),
                old.scoreImprovementTolerance(), old.geometrySettings(),
                old.scoreWeights(), old.scoringProfile(), old.diagnosticSettings(),
                old.checkpointInterval());
        return new DiscoveryExperimentConfiguration(base.name(), base.description(),
                base.embeddingDimension(), nodeCount, base.randomSeed(),
                base.scoringProfile(), base.scoreWeights(), base.customWeights(),
                DiscoveryInitialTopology.RANDOM_CONNECTED_GRAPH, policy,
                minimum, maximum, target, base.edgeProbability(), null,
                base.geometryInitialization(), base.initialExtent(),
                base.perturbationMagnitude(), base.geometrySettings(),
                base.workerIterationsPerUpdate(), search);
    }

    private int eventIndex(String... types) {
        for (int i = 0; i < workspace.eventLog().size(); i++)
            for (String type : types)
                if (workspace.eventLog().get(i).type().equals(type)) return i;
        return -1;
    }

    private DiscoveryExperimentConfiguration shortConfiguration() {
        DiscoveryExperimentConfiguration base = DiscoveryExperimentConfiguration.defaults();
        GeometryDiscoverySettings geometry = base.geometrySettings().withMaximumIterations(2);
        TopologySearchSettings old = base.topologySearchSettings();
        TopologySearchSettings search = new TopologySearchSettings(
                old.searchMode(), old.randomSeed(), 2, old.mutationsAttemptedPerIteration(),
                old.minimumDegree(), old.maximumDegree(), old.requiredEdgeCount(),
                old.archiveSize(), old.initialTemperature(), old.coolingRate(),
                old.minimumTemperature(), old.scoreImprovementTolerance(), geometry,
                old.scoreWeights(), old.scoringProfile(), old.diagnosticSettings(),
                old.checkpointInterval());
        return new DiscoveryExperimentConfiguration(base.name(), base.description(),
                base.embeddingDimension(), base.nodeCount(), base.randomSeed(),
                base.scoringProfile(), base.scoreWeights(), false, base.initialTopology(),
                base.neighborCountPolicy(),
                base.minimumDegree(), base.maximumDegree(), base.targetInitialDegree(),
                base.edgeProbability(), null, base.geometryInitialization(), base.initialExtent(),
                base.perturbationMagnitude(), geometry, 1, search);
    }

    private DiscoveryExperimentConfiguration copy(
            DiscoveryExperimentConfiguration base, int dimension, int count,
            DiscoveryInitialTopology topology,
            DiscoveryGeometryInitialization initialization,
            int minimumDegree, int maximumDegree, double targetDegree) {
        TopologySearchSettings old = base.topologySearchSettings();
        TopologySearchSettings search = new TopologySearchSettings(old.searchMode(),
                old.randomSeed(), old.maximumSearchIterations(),
                old.mutationsAttemptedPerIteration(), minimumDegree, maximumDegree,
                old.requiredEdgeCount(), old.archiveSize(), old.initialTemperature(),
                old.coolingRate(), old.minimumTemperature(), old.scoreImprovementTolerance(),
                old.geometrySettings(), old.scoreWeights(), old.scoringProfile(),
                old.diagnosticSettings(), old.checkpointInterval());
        return new DiscoveryExperimentConfiguration(base.name(), base.description(),
                dimension, count, base.randomSeed(), base.scoringProfile(), base.scoreWeights(),
                false, topology, base.neighborCountPolicy(),
                minimumDegree, maximumDegree, targetDegree,
                base.edgeProbability(), null, initialization, base.initialExtent(),
                base.perturbationMagnitude(), base.geometrySettings(),
                base.workerIterationsPerUpdate(), search);
    }

    private static void await(java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) fail("Timed out waiting for discovery state.");
            Thread.onSpinWait();
        }
    }
}
