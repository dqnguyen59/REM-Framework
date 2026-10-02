package remframework.geometry.discovery;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeometryDiscoveryWorkflowTest {
    private GeometryDiscoveryWorkspace workspace;
    private final GeometryDiscoveryWorkflowResolver resolver =
            new GeometryDiscoveryWorkflowResolver();

    @AfterEach void closeWorkspace() {
        if (workspace != null) workspace.close();
    }

    @Test void guidedModeAndAdvancedSectionsHaveSafeDefaults() {
        GeometryDiscoveryWorkspaceState state = new GeometryDiscoveryWorkspaceState();
        assertTrue(state.guidedMode());
        assertFalse(state.relaxationAdvancedExpanded());
        assertFalse(state.searchAdvancedExpanded());
        assertFalse(state.detailedDiagnosticsExpanded());
        assertFalse(state.scoreWeightsExpanded());
        assertEquals(GeometryDiscoveryDetailTab.OVERVIEW, state.selectedDetailTab());
    }

    @Test void candidateLoadingAdvancesWorkflowAndConvergedGeometrySkipsRelaxation() {
        workspace = new GeometryDiscoveryWorkspace(new GeometryDiscoveryWorkspaceState());
        workspace.loadBuiltIn("small-cubic");
        GeometryDiscoveryWorkflowResolution workflow = resolver.resolve(workspace.snapshot());
        assertTrue(resolver.geometrySatisfied(workspace.snapshot().geometry()));
        assertEquals(GeometryDiscoveryWorkflowStage.EVALUATION, workflow.stage());
        assertEquals(GeometryDiscoveryWorkflowState.READY_FOR_SEARCH, workflow.state());
        assertFalse(workflow.action(GeometryDiscoveryWorkflowResolver.START_RELAXATION).enabled());
        assertTrue(workflow.recommendation().action().contains("evaluation profile"));

        workspace.loadBuiltIn("tetrahedron");
        assertNotEquals(GeometryDiscoveryWorkflowState.NO_CANDIDATE,
                resolver.resolve(workspace.snapshot()).state());
    }

    @Test void invalidCandidateBlocksRelaxationAndSearch() throws Exception {
        workspace = new GeometryDiscoveryWorkspace(new GeometryDiscoveryWorkspaceState());
        Path invalid = Files.createTempFile("invalid-discovery", ".toml");
        Files.writeString(invalid, "invalid = [");
        workspace.loadCandidate(invalid);
        GeometryDiscoveryWorkflowResolution workflow = resolver.resolve(workspace.snapshot());
        assertEquals(GeometryDiscoveryWorkflowState.ERROR, workflow.state());
        assertFalse(workflow.action(GeometryDiscoveryWorkflowResolver.START_RELAXATION).enabled());
        assertFalse(workflow.action(GeometryDiscoveryWorkflowResolver.START_SEARCH).enabled());
    }

    @Test void controlsFollowWorkerAndResultAvailability() {
        workspace = new GeometryDiscoveryWorkspace(new GeometryDiscoveryWorkspaceState());
        GeometryDiscoveryWorkflowResolution initial = resolver.resolve(workspace.snapshot());
        assertFalse(initial.action(GeometryDiscoveryWorkflowResolver.LOAD_BEST).enabled());
        assertFalse(initial.action(GeometryDiscoveryWorkflowResolver.PAUSE).enabled());

        TopologySearchSettings base = TopologySearchSettings.defaults(7,
                workspace.snapshot().geometry().settings());
        TopologySearchSettings settings = new TopologySearchSettings(
                base.searchMode(), base.randomSeed(), 1,
                base.mutationsAttemptedPerIteration(), base.minimumDegree(),
                base.maximumDegree(), base.requiredEdgeCount(), base.archiveSize(),
                base.initialTemperature(), base.coolingRate(), base.minimumTemperature(),
                base.scoreImprovementTolerance(), base.geometrySettings(), base.scoreWeights(),
                base.scoringProfile(), base.diagnosticSettings(), base.checkpointInterval());
        workspace.singleSearchStep(settings);
        await(() -> workspace.snapshot().search() != null
                && !workspace.snapshot().search().history().isEmpty());
        GeometryDiscoveryWorkflowResolution finished = resolver.resolve(workspace.snapshot());
        assertEquals(GeometryDiscoveryWorkflowState.BEST_CANDIDATE_AVAILABLE, finished.state());
        assertTrue(finished.action(GeometryDiscoveryWorkflowResolver.LOAD_BEST).enabled());
        assertTrue(finished.action(GeometryDiscoveryWorkflowResolver.COMPARE).enabled());
    }

    @Test void profileSelectionUpdatesEvaluationWeights() {
        WorkspaceController controller =
                new WorkspaceController();
        try {
            controller.selectProfile(TopologyScoringProfile.WAVE_PROPAGATION);
            assertEquals(TopologyScoringProfile.WAVE_PROPAGATION,
                    controller.state().scoringProfile());
            assertEquals(TopologyScoreWeights.forProfile(
                    TopologyScoringProfile.WAVE_PROPAGATION),
                    controller.state().scoreWeights());
        } finally {
            controller.close();
        }
    }

    @Test void primaryControlLabelsAreFullAsciiAndHaveTooltips() throws Exception {
        List<String> expected = List.of(
                "Load Example", "Open Candidate File", "Reload Candidate",
                "Recent Candidates", "Start Relaxation", "Pause", "Single Step",
                "Reset", "Run to Convergence", "View Detailed Diagnostics",
                "Edit Score Weights", "Reset Profile", "Start Search", "Stop Search",
                "Single Search Iteration", "Preview Best", "Load Best", "Compare",
                "Export Best", "Browse Archive");
        assertTrue(GeometryDiscoveryWorkspaceHelp.PRIMARY_TOOLTIPS.keySet()
                .containsAll(expected));
        assertTrue(GeometryDiscoveryWorkspaceHelp.PRIMARY_TOOLTIPS.values().stream()
                .allMatch(value -> !value.isBlank()));
        String controls = Files.readString(Path.of(
                "src/main/java/remframework/gui/view/frames/GeometryDiscoveryControlFrame.java"));
        for (String abbreviation : List.of(
                "Open Candi", "Reload Can", "Run to Conv", "Start Se", "Reset Se"))
            assertFalse(controls.contains("\"" + abbreviation + "\""));
        assertTrue((controls + GeometryDiscoveryWorkspaceHelp.PRIMARY_TOOLTIPS)
                .chars().allMatch(value -> value < 128));
    }

    private static void await(java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(4).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) fail("Timed out waiting for workflow state.");
            Thread.onSpinWait();
        }
    }
}
