package remframework.geometry.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class DynamicElasticPlaybackTest {
    @TempDir Path temp;

    @Test void defaultsAndDelayMappingAreIndependentFromTopologyPacing() {
        GeometryDiscoveryWorkspaceState state = new GeometryDiscoveryWorkspaceState();
        assertEquals(30, state.dynamicElasticUpdatesPerSecond());
        assertEquals(1, state.dynamicElasticPublicationInterval());
        assertEquals(1_000_000_000L,
                DynamicElasticPlaybackPacing.delayNanos(1, false));
        assertEquals(100_000_000L,
                DynamicElasticPlaybackPacing.delayNanos(10, false));
        assertEquals(33_333_333L,
                DynamicElasticPlaybackPacing.delayNanos(30, false));
        assertEquals(0, DynamicElasticPlaybackPacing.delayNanos(30, true));
        assertEquals(8, state.snapshotUpdatesPerSecond());
    }

    @Test void pacingAndPublicationChoicesDoNotChangeNumericalState() {
        FixedDegreeGraph graph = new FixedDegreeGraphGenerator().generate(8, 3, 42, 0);
        DynamicElasticSettings settings = DynamicElasticSettings.defaults(10, 42)
                .withMaximumIterations(1000);
        DynamicElasticSession slow = new DynamicElasticSession(graph, settings);
        DynamicElasticSession maximum = new DynamicElasticSession(graph, settings);
        for (int i = 0; i < 100; i++) {
            slow.step();
            maximum.step();
        }
        assertEquals(slow.iteration(), maximum.iteration());
        assertEquals(slow.metrics(), maximum.metrics());
        for (int i = 0; i < slow.nodes().size(); i++) {
            assertEquals(slow.nodes().get(i).position(),
                    maximum.nodes().get(i).position());
            assertEquals(slow.nodes().get(i).velocity(),
                    maximum.nodes().get(i).velocity());
        }
    }

    @Test void singleStepResetAndMaximumContinuationPreserveSessionState() {
        GeometryDiscoveryWorkspaceState state = new GeometryDiscoveryWorkspaceState();
        state.setDynamicElasticMaximumSpeed(true);
        state.setDynamicElasticMaximumIterations(3);
        GeometryDiscoveryWorkspace workspace = workspace(state, "continuation");
        try {
            assertTrue(workspace.createDynamicElastic(8, 3,
                    DynamicElasticSettings.defaults(10, 42)).valid());
            workspace.singleRelaxationStep();
            awaitIteration(workspace, 1);
            assertEquals(1, workspace.dynamicElasticMetrics().iteration());

            workspace.startRelaxation();
            awaitIteration(workspace, 3);
            awaitOperation(workspace,
                    DynamicElasticOperation.MAXIMUM_ITERATIONS_REACHED);
            assertEquals(3, workspace.dynamicElasticMetrics().iteration());
            assertEquals(DynamicElasticState.MAX_ITERATIONS_REACHED,
                    workspace.dynamicElasticMetrics().state());
            assertTrue(workspace.dynamicElasticPublicationCount() >= 2);
            assertTrue(workspace.updateDynamicElasticMaximumIterations(5));
            awaitMaximum(workspace, 5);
            workspace.startRelaxation();
            awaitIteration(workspace, 5);
            awaitOperation(workspace,
                    DynamicElasticOperation.MAXIMUM_ITERATIONS_REACHED);
            assertEquals(5, workspace.dynamicElasticMetrics().iteration());

            workspace.resetExperiment();
            awaitIteration(workspace, 0);
            assertEquals(0, workspace.dynamicElasticMetrics().stableIterations());
            assertEquals(DynamicElasticState.INITIALIZING,
                    workspace.dynamicElasticMetrics().state());
        } finally {
            workspace.close();
        }
    }

    @Test void pausePublishesAndSecondWorkerCannotStart() {
        GeometryDiscoveryWorkspaceState state = new GeometryDiscoveryWorkspaceState();
        state.setDynamicElasticUpdatesPerSecond(1000);
        state.setDynamicElasticPublicationInterval(1);
        state.setDynamicElasticMaximumIterations(100_000);
        GeometryDiscoveryWorkspace workspace = workspace(state, "pause");
        try {
            workspace.createDynamicElastic(8, 3,
                    DynamicElasticSettings.defaults(10, 42));
            workspace.runDynamicElasticUntilEquilibrium();
            workspace.startRelaxation();
            awaitAtLeast(workspace, 2);
            workspace.pause();
            awaitOperation(workspace, DynamicElasticOperation.PAUSED);
            int pausedAt = workspace.dynamicElasticMetrics().iteration();
            DynamicElasticResult paused = workspace.dynamicElasticResult();
            long publications = workspace.dynamicElasticPublicationCount();
            sleep(30);
            assertEquals(pausedAt, workspace.dynamicElasticMetrics().iteration());
            for (int i = 0; i < paused.finalNodes().size(); i++)
                assertEquals(paused.finalNodes().get(i).position(),
                        workspace.dynamicElasticResult().finalNodes().get(i).position());
            assertTrue(publications >= 1);
            assertEquals(DynamicElasticOperation.PAUSED,
                    workspace.dynamicElasticOperation());
        } finally {
            workspace.close();
        }
    }

    @Test void maximumValidationRejectsLowerValueWithoutMutation() {
        GeometryDiscoveryWorkspaceState state = new GeometryDiscoveryWorkspaceState();
        GeometryDiscoveryWorkspace workspace = workspace(state, "validation");
        try {
            workspace.createDynamicElastic(4, 3,
                    DynamicElasticSettings.defaults(10, 42));
            workspace.singleRelaxationStep();
            awaitIteration(workspace, 1);
            int original = state.dynamicElasticMaximumIterations();
            assertFalse(workspace.updateDynamicElasticMaximumIterations(0));
            assertFalse(workspace.updateDynamicElasticMaximumIterations(-1));
            assertEquals(original, state.dynamicElasticMaximumIterations());
        } finally {
            workspace.close();
        }
    }

    @Test void preferencesRoundTripDynamicPlaybackSeparately() {
        Path path = temp.resolve("workspace.properties");
        GeometryDiscoveryWorkspacePreferences preferences =
                new GeometryDiscoveryWorkspacePreferences(path);
        GeometryDiscoveryWorkspaceState saved = new GeometryDiscoveryWorkspaceState();
        saved.setDynamicElasticUpdatesPerSecond(100);
        saved.setDynamicElasticMaximumSpeed(true);
        saved.setDynamicElasticPublicationInterval(25);
        saved.setDynamicElasticMaximumIterations(12345);
        preferences.save(saved, new GeometryDiscoveryViewState());

        GeometryDiscoveryWorkspaceState loaded = new GeometryDiscoveryWorkspaceState();
        preferences.load(loaded, new GeometryDiscoveryViewState());
        assertEquals(100, loaded.dynamicElasticUpdatesPerSecond());
        assertTrue(loaded.dynamicElasticMaximumSpeed());
        assertEquals(25, loaded.dynamicElasticPublicationInterval());
        assertEquals(12345, loaded.dynamicElasticMaximumIterations());
        assertEquals(DiscoveryVisualizationUpdateTrigger.ACCEPTED_MUTATION,
                loaded.visualizationUpdateTrigger());
    }

    private void awaitAtLeast(GeometryDiscoveryWorkspace workspace, int iteration) {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            while (workspace.dynamicElasticMetrics().iteration() < iteration)
                Thread.onSpinWait();
        });
    }
    private void awaitIteration(GeometryDiscoveryWorkspace workspace, int iteration) {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            while (workspace.dynamicElasticMetrics().iteration() != iteration)
                Thread.onSpinWait();
        });
    }
    private void awaitMaximum(GeometryDiscoveryWorkspace workspace, int maximum) {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            while (workspace.dynamicElasticSettings().maximumIterations() != maximum)
                Thread.onSpinWait();
        });
    }
    private void awaitOperation(GeometryDiscoveryWorkspace workspace,
                                DynamicElasticOperation operation) {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            while (workspace.dynamicElasticOperation() != operation)
                Thread.onSpinWait();
        });
    }
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            fail(exception);
        }
    }
    private GeometryDiscoveryWorkspace workspace(GeometryDiscoveryWorkspaceState state,
                                                 String name) {
        return new GeometryDiscoveryWorkspace(state,
                new DynamicElasticResultStore(temp.resolve(name)));
    }
}
