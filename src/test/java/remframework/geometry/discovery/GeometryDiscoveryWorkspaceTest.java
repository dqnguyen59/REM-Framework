package remframework.geometry.discovery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import remframework.gui.model.NuklearModel;
import remframework.opengl.model.Lattice;
import remframework.opengl.model.RemModel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeometryDiscoveryWorkspaceTest {
    @TempDir Path temp;
    private GeometryDiscoveryWorkspace workspace;

    @AfterEach void closeWorker() {
        if (workspace != null) workspace.close();
    }

    @Test void workspaceLifecyclePreservesLatticeAndRestoresSimulation() {
        GeometryDiscoveryWorkspaceController controller =
                GeometryDiscoveryWorkspaceManager.getInstance();
        RemModel rem = new RemModel();
        rem.startSimulation();
        NuklearModel ui = new NuklearModel();
        Lattice lattice = Lattice.getInstance();
        controller.attach(rem, ui);
        controller.openWorkspace();
        assertSame(lattice, Lattice.getInstance());
        assertFalse(rem.isSimulating());
        assertTrue(controller.state().active());
        controller.deactivateWorkspace();
        assertTrue(rem.isSimulating());
        assertFalse(controller.state().active());
    }

    @Test void builtInLoadingAndRelaxationDelegateToSessionOffRenderThread() {
        workspace = new GeometryDiscoveryWorkspace(new GeometryDiscoveryWorkspaceState());
        workspace.loadBuiltIn("square");
        assertEquals("square-cycle", workspace.snapshot().geometry().model().name());
        int before = workspace.snapshot().geometry().metrics().iterationCount();
        workspace.singleRelaxationStep();
        await(() -> workspace.snapshot().geometry().metrics().iterationCount() > before);
        assertEquals("geometry-discovery-worker", workspace.lastWorkerThreadName());
    }

    @Test void topologySearchPauseStopAndSnapshotsAreResponsive() {
        workspace = new GeometryDiscoveryWorkspace(new GeometryDiscoveryWorkspaceState());
        GeometryDiscoverySettings geometry = workspace.snapshot().geometry().settings()
                .withMaximumIterations(100);
        TopologySearchSettings base = TopologySearchSettings.defaults(5, geometry);
        TopologySearchSettings settings = new TopologySearchSettings(
                TopologySearchMode.SIMULATED_ANNEALING, 5, 10_000, 1,
                1, 6, -1, 10, 1, .997, 1e-6, 1e-9, geometry,
                base.scoreWeights(), base.scoringProfile(), base.diagnosticSettings(), 100);
        workspace.startSearch(settings);
        await(() -> workspace.snapshot().search() != null
                && workspace.snapshot().search().metrics().completedIterations() > 0);
        workspace.pause();
        await(() -> !workspace.snapshot().searchRunning());
        GeometryDiscoveryWorkspaceSnapshot snapshot = workspace.snapshot();
        assertTrue(snapshot.geometry().metrics().allFinite());
        assertEquals(snapshot.search().topCandidates().size(), snapshot.archive().size());
        workspace.stop();
        assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> await(workspace::workerStopped));
    }

    @Test void invalidCandidateCreatesNonFatalErrorAndVersionedFilesLoad() throws Exception {
        workspace = new GeometryDiscoveryWorkspace(new GeometryDiscoveryWorkspaceState());
        Path invalid = temp.resolve("invalid.toml");
        Files.writeString(invalid, "not = [toml");
        workspace.loadCandidate(invalid);
        assertFalse(workspace.snapshot().error().isBlank());

        GeometryDiscoveryResult result = new GeometryDiscoveryEngine().relax(
                GeometryDiscoveryExamples.tetrahedron(1),
                GeometryDiscoverySettings.defaults(1));
        Path v1 = new GeometryDiscoveryExporter().export(result, temp.resolve("v1.toml"));
        workspace.loadCandidate(v1);
        assertTrue(workspace.snapshot().error().isBlank());

        TopologySearchResult search = new TopologySearchEngine().search(
                GeometryDiscoveryExamples.smallCubic(1),
                TopologySearchSettings.defaults(1,
                        GeometryDiscoverySettings.defaults(1).withMaximumIterations(20)));
        Path v2 = new TopologySearchExporter().export(search, temp.resolve("search"));
        workspace.loadCandidate(v2);
        assertTrue(workspace.snapshot().error().isBlank());
    }

    @Test void profilesOverridesComparisonAndExportsUseExistingServices() throws Exception {
        GeometryDiscoveryWorkspaceController controller =
                new GeometryDiscoveryWorkspaceController();
        controller.selectProfile(TopologyScoringProfile.WAVE_PROPAGATION);
        assertEquals(TopologyScoreWeights.forProfile(TopologyScoringProfile.WAVE_PROPAGATION),
                controller.state().scoreWeights());
        controller.overrideComponentWeight("oppositeDirection", 17);
        assertEquals(17, controller.state().scoreWeights().oppositeDirection());
        assertTrue(controller.state().weightsOverridden());
        controller.resetProfile();
        assertEquals(TopologyScoreWeights.forProfile(TopologyScoringProfile.WAVE_PROPAGATION),
                controller.state().scoreWeights());
        controller.close();

        workspace = new GeometryDiscoveryWorkspace(new GeometryDiscoveryWorkspaceState());
        Path exported = workspace.exportCurrent(temp.resolve("candidate.toml"));
        assertTrue(Files.exists(exported));
        Path template = temp.resolve("cubic-lattice.toml");
        Files.writeString(template, "sentinel");
        assertThrows(IllegalArgumentException.class,
                () -> workspace.exportCurrent(template));
        assertEquals("sentinel", Files.readString(template));
    }

    @Test void preferencesPersistOnlyStoppedUiState() {
        Path path = temp.resolve("workspace.properties");
        GeometryDiscoveryWorkspaceState state = new GeometryDiscoveryWorkspaceState();
        GeometryDiscoveryViewState view = new GeometryDiscoveryViewState();
        state.setScoringProfile(TopologyScoringProfile.CENTROSYMMETRIC);
        state.setOutputDirectory(Path.of("lattice/experiments"));
        state.setCurrentSource(Path.of("candidate.toml"));
        view.setOrthographic(true);
        GeometryDiscoveryWorkspacePreferences preferences =
                new GeometryDiscoveryWorkspacePreferences(path);
        preferences.save(state, view);
        GeometryDiscoveryWorkspaceState restored = new GeometryDiscoveryWorkspaceState();
        GeometryDiscoveryViewState restoredView = new GeometryDiscoveryViewState();
        preferences.load(restored, restoredView);
        assertEquals(TopologyScoringProfile.CENTROSYMMETRIC, restored.scoringProfile());
        assertEquals(Path.of("candidate.toml"), restored.currentSource());
        assertTrue(restoredView.orthographic());
        assertFalse(restored.active());
    }

    @Test void workspaceLabelsAreAsciiOnly() throws Exception {
        List<Path> frames = List.of(
                Path.of("src/main/java/remframework/gui/view/frames/GeometryDiscoveryControlFrame.java"),
                Path.of("src/main/java/remframework/gui/view/frames/GeometryDiscoveryMetricsFrame.java"),
                Path.of("src/main/java/remframework/gui/view/frames/GeometryDiscoveryNodeFrame.java"),
                Path.of("src/main/java/remframework/gui/view/frames/GeometryDiscoverySearchFrame.java"));
        for (Path frame : frames)
            assertTrue(Files.readString(frame).chars().allMatch(value -> value < 128));
    }

    private static void await(java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) fail("Timed out waiting for workspace state.");
            Thread.onSpinWait();
        }
    }
}
