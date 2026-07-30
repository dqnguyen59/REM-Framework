package remframework.geometry.discovery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class DiscoveryVisualizationPacingTest {
    @TempDir Path temp;
    private GeometryDiscoveryWorkspace first;
    private GeometryDiscoveryWorkspace second;

    @AfterEach void close() {
        if (first != null) first.close();
        if (second != null) second.close();
    }

    @Test void sliderNumericStateAndValidationRemainSynchronized() {
        for (int index = 0; index <= DiscoveryVisualizationPacing.SPEEDS.length; index++) {
            DiscoveryVisualizationPacing.Selection selection =
                    DiscoveryVisualizationPacing.selection(index);
            assertEquals(index, DiscoveryVisualizationPacing.sliderIndex(
                    selection.updatesPerSecond(), selection.maximum()));
        }
        GeometryDiscoveryWorkspaceState state = new GeometryDiscoveryWorkspaceState();
        state.setSnapshotUpdatesPerSecond(8);
        assertEquals(8, state.snapshotUpdatesPerSecond());
        assertThrows(IllegalArgumentException.class,
                () -> state.setSnapshotUpdatesPerSecond(7));
        assertEquals(8, state.snapshotUpdatesPerSecond());
    }

    @Test void triggerPolicyPublishesOnlyItsNamedEvents() {
        DiscoveryVisualizationPublicationPolicy policy =
                new DiscoveryVisualizationPublicationPolicy();
        assertTrue(policy.shouldPublish(
                DiscoveryVisualizationUpdateTrigger.EVERY_ITERATION, false, false, false));
        assertFalse(policy.shouldPublish(
                DiscoveryVisualizationUpdateTrigger.ACCEPTED_MUTATION, false, true, true));
        assertTrue(policy.shouldPublish(
                DiscoveryVisualizationUpdateTrigger.ACCEPTED_MUTATION, true, false, false));
        assertFalse(policy.shouldPublish(
                DiscoveryVisualizationUpdateTrigger.NEW_BEST_CANDIDATE, true, false, true));
        assertTrue(policy.shouldPublish(
                DiscoveryVisualizationUpdateTrigger.NEW_BEST_CANDIDATE, false, true, false));
        assertFalse(policy.shouldPublish(
                DiscoveryVisualizationUpdateTrigger.TIMED_SNAPSHOT, true, true, false));
        assertTrue(policy.shouldPublish(
                DiscoveryVisualizationUpdateTrigger.TIMED_SNAPSHOT, false, false, true));
        assertFalse(policy.shouldPauseOnNewBest(true, false));
        assertFalse(policy.shouldPauseOnNewBest(false, true));
        assertTrue(policy.shouldPauseOnNewBest(true, true));
    }

    @Test void maximumAddsNoDelayAndSpeedDoesNotChangeDeterministicResult() {
        GeometryDiscoveryWorkspaceState slowState = new GeometryDiscoveryWorkspaceState();
        slowState.setVisualizationUpdateTrigger(
                DiscoveryVisualizationUpdateTrigger.EVERY_ITERATION);
        slowState.setSnapshotUpdatesPerSecond(1);
        first = new GeometryDiscoveryWorkspace(slowState);
        GeometryDiscoveryWorkspaceState fastState = new GeometryDiscoveryWorkspaceState();
        fastState.setVisualizationUpdateTrigger(
                DiscoveryVisualizationUpdateTrigger.EVERY_ITERATION);
        fastState.setMaximumVisualizationSpeed(true);
        second = new GeometryDiscoveryWorkspace(fastState);
        TopologySearchSettings settings = settings(first, 2);
        first.startSearch(settings);
        second.startSearch(settings);
        await(() -> finished(first) && finished(second));
        assertEquals(first.snapshot().search().bestCandidate().topologySignature(),
                second.snapshot().search().bestCandidate().topologySignature());
        assertEquals(first.snapshot().search().bestCandidate().score().total(),
                second.snapshot().search().bestCandidate().score().total());
        assertTrue(first.intentionalPacingWaitCount() > 0);
        assertEquals(0, second.intentionalPacingWaitCount());
    }

    @Test void finalStateIsPublishedRegardlessOfTrigger() {
        GeometryDiscoveryWorkspaceState state = new GeometryDiscoveryWorkspaceState();
        state.setVisualizationUpdateTrigger(
                DiscoveryVisualizationUpdateTrigger.NEW_BEST_CANDIDATE);
        state.setMaximumVisualizationSpeed(true);
        first = new GeometryDiscoveryWorkspace(state);
        first.startSearch(settings(first, 3));
        await(() -> finished(first));
        assertTrue(first.searchVisualPublicationCount() >= 1);
        assertEquals(3, first.snapshot().search().metrics().completedIterations());
        assertEquals(DiscoveryExperimentLifecycle.COMPLETED,
                state.experimentLifecycle());
    }

    @Test void pacingPreferencesPersistAsUiState() {
        Path path = temp.resolve("workspace.properties");
        GeometryDiscoveryWorkspaceState state = new GeometryDiscoveryWorkspaceState();
        state.setVisualizationUpdateTrigger(
                DiscoveryVisualizationUpdateTrigger.TIMED_SNAPSHOT);
        state.setSnapshotUpdatesPerSecond(50);
        state.setMaximumVisualizationSpeed(true);
        state.setPauseOnNewBest(true);
        new GeometryDiscoveryWorkspacePreferences(path).save(
                state, new GeometryDiscoveryViewState());
        GeometryDiscoveryWorkspaceState restored = new GeometryDiscoveryWorkspaceState();
        new GeometryDiscoveryWorkspacePreferences(path).load(
                restored, new GeometryDiscoveryViewState());
        assertEquals(DiscoveryVisualizationUpdateTrigger.TIMED_SNAPSHOT,
                restored.visualizationUpdateTrigger());
        assertEquals(50, restored.snapshotUpdatesPerSecond());
        assertTrue(restored.maximumVisualizationSpeed());
        assertTrue(restored.pauseOnNewBest());
    }

    private TopologySearchSettings settings(GeometryDiscoveryWorkspace workspace, int iterations) {
        TopologySearchSettings base = TopologySearchSettings.defaults(42,
                workspace.snapshot().geometry().settings().withMaximumIterations(5));
        return new TopologySearchSettings(base.searchMode(), base.randomSeed(), iterations,
                base.mutationsAttemptedPerIteration(), base.minimumDegree(),
                base.maximumDegree(), base.requiredEdgeCount(), base.archiveSize(),
                base.initialTemperature(), base.coolingRate(), base.minimumTemperature(),
                base.scoreImprovementTolerance(), base.geometrySettings(), base.scoreWeights(),
                base.scoringProfile(), base.diagnosticSettings(), base.checkpointInterval());
    }

    private boolean finished(GeometryDiscoveryWorkspace workspace) {
        return workspace.snapshot().search() != null
                && workspace.snapshot().search().metrics().completedIterations()
                >= workspace.snapshot().search().settings().maximumSearchIterations()
                && !workspace.snapshot().searchRunning();
    }

    private static void await(java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) fail("Timed out waiting for paced search.");
            Thread.onSpinWait();
        }
    }
}
