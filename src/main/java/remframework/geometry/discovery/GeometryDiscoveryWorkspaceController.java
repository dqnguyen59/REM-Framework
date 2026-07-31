package remframework.geometry.discovery;

import remframework.gui.model.NuklearModel;
import remframework.gui.model.NuklearModel.Frame;
import remframework.opengl.model.RemModel;
import remframework.helpers.Constants;

import java.nio.file.Path;

public final class GeometryDiscoveryWorkspaceController implements AutoCloseable {
    private final GeometryDiscoveryWorkspaceState state = new GeometryDiscoveryWorkspaceState();
    private final GeometryDiscoveryViewState viewState = new GeometryDiscoveryViewState();
    private final GeometryDiscoveryWorkspace workspace = new GeometryDiscoveryWorkspace(state);
    private final GeometryDiscoveryWorkspacePreferences preferences =
            new GeometryDiscoveryWorkspacePreferences(
                    Path.of("geometry-discovery-workspace.properties"));
    private RemModel remModel;
    private NuklearModel nuklearModel;
    private final GeometryDiscoveryWorkflowResolver workflowResolver =
            new GeometryDiscoveryWorkflowResolver();
    private final GeometryDiscoveryPhasedWorkflow phasedWorkflow =
            new GeometryDiscoveryPhasedWorkflow();
    private volatile GeometryDiscoveryRenderDiagnostics renderDiagnostics;

    public GeometryDiscoveryWorkspaceController() {
        preferences.load(state, viewState);
    }

    public void attach(RemModel remModel, NuklearModel nuklearModel) {
        this.remModel = remModel;
        this.nuklearModel = nuklearModel;
    }

    public void openWorkspace() {
        requireAttached();
        if (!state.open()) {
            state.setOpen(true);
            state.setRemWasRunning(remModel.isSimulating());
            if (state.currentSource() != null
                    && java.nio.file.Files.exists(state.currentSource()))
                workspace.loadCandidate(state.currentSource());
        }
        activateWorkspace();
        showFrames();
    }

    public void activateWorkspace() {
        requireAttached();
        if (!state.open()) state.setOpen(true);
        if (!state.active()) {
            state.setRemWasRunning(remModel.isSimulating());
            remModel.stopSimulation();
            state.setActive(true);
        }
    }

    public void deactivateWorkspace() {
        if (!state.active()) return;
        state.setActive(false);
        workspace.pause();
        hideFrames();
        if (state.remWasRunning()) remModel.startSimulation();
    }

    public void closeWorkspace() {
        deactivateWorkspace();
        state.setOpen(false);
        hideFrames();
        preferences.save(state, viewState);
    }

    private void showFrames() {
        nuklearModel.showFrame(Frame.geometryDiscoveryControls);
        nuklearModel.showFrame(Frame.geometryDiscoveryMetrics);
        nuklearModel.showFrame(Frame.geometryDiscoverySearch);
        nuklearModel.showFrame(Frame.geometryDiscoveryStatus);
        if (!state.introductionDismissed())
            nuklearModel.showFrame(Frame.geometryDiscoveryIntroduction);
    }

    private void hideFrames() {
        closeIfVisible(Frame.geometryDiscoveryControls);
        closeIfVisible(Frame.geometryDiscoveryMetrics);
        closeIfVisible(Frame.geometryDiscoveryNode);
        closeIfVisible(Frame.geometryDiscoverySearch);
        closeIfVisible(Frame.geometryDiscoveryStatus);
        closeIfVisible(Frame.geometryDiscoveryIntroduction);
        closeIfVisible(Frame.geometryDiscoveryNew);
    }

    private void closeIfVisible(Frame frame) {
        if (nuklearModel.isFrameVisible(frame)) nuklearModel.closeFrame(frame);
    }

    public TopologySearchSettings searchSettings(int iterations, TopologySearchMode mode) {
        return searchSettings(iterations, mode,
                workspace.snapshot().geometry().settings().randomSeed(),
                1, 1, 6, 20, 1, .997, 100);
    }

    public TopologySearchSettings searchSettings(
            int iterations, TopologySearchMode mode, long seed, int mutations,
            int minimumDegree, int maximumDegree, int archiveSize,
            double temperature, double coolingRate, int checkpointInterval) {
        GeometryDiscoverySettings geometry = workspace.snapshot().geometry().settings();
        TopologySearchSettings defaults = TopologySearchSettings.defaults(seed, geometry);
        return new TopologySearchSettings(mode, seed, iterations, mutations,
                minimumDegree, maximumDegree, -1, archiveSize, temperature, coolingRate,
                defaults.minimumTemperature(), defaults.scoreImprovementTolerance(), geometry,
                state.scoreWeights(), state.scoringProfile(), defaults.diagnosticSettings(),
                checkpointInterval);
    }

    public void selectProfile(TopologyScoringProfile profile) {
        state.setScoringProfile(profile);
        state.setScoreWeights(TopologyScoreWeights.forProfile(profile));
        state.setWeightsOverridden(false);
    }

    public void overrideWeight(String name, double value) {
        if (!Double.isFinite(value) || value < 0)
            throw new IllegalArgumentException("Weight must be finite and non-negative.");
        state.setScoreWeights(state.scoreWeights().override(name, value));
        state.setWeightsOverridden(true);
    }

    public void overrideComponentWeight(String componentName, double value) {
        String option = switch (componentName) {
            case "edgeLengthError" -> "edge-length-error";
            case "edgeLengthVariance" -> "edge-length-variance";
            case "separationCollision" -> "separation";
            case "nonConvergence" -> "non-convergence";
            case "directionalBias" -> "directional-bias";
            case "tensorIsotropy" -> "tensor-isotropy";
            case "knownReferenceAngular" -> "angular-reference";
            case "sphericalCoverage" -> "spherical-coverage";
            case "oppositeDirection" -> "opposite-direction";
            case "localDimensionality" -> "local-dimensionality";
            case "degreeVariance" -> "degree-variance";
            case "bridge" -> "bridges";
            case "articulationPoint" -> "articulation";
            case "graphDiameter" -> "diameter";
            case "runtimeCompatibility" -> "runtime-compatibility";
            case "profileDegree" -> "profile-degree";
            default -> throw new IllegalArgumentException("Unknown score component " + componentName);
        };
        overrideWeight(option, value);
    }

    public void resetProfile() { selectProfile(state.scoringProfile()); }

    public GeometryDiscoveryWorkflowResolution workflow() {
        return workflowResolver.resolve(workspace.snapshot());
    }

    public TopologyCandidate evaluatedCurrentCandidate() {
        GeometryDiscoveryResult geometry = workspace.snapshot().geometry();
        TopologyMetrics topology = new TopologyMetricsCalculator().calculate(geometry.model());
        DirectionalBalanceMetrics directional = new DirectionalBalanceCalculator()
                .calculate(geometry.model());
        TopologySearchSettings settings = searchSettings(0, TopologySearchMode.HILL_CLIMBING);
        TopologyScore score = TopologyScore.calculate(
                geometry, topology, directional, settings);
        return new TopologyCandidate(0, null, geometry, topology, directional, score,
                null, TopologySignature.of(geometry.model()), "workspace evaluation");
    }

    private void requireAttached() {
        if (remModel == null || nuklearModel == null)
            throw new IllegalStateException("Geometry Discovery workspace is not attached.");
    }

    public GeometryDiscoveryWorkspace workspace() { return workspace; }
    public GeometryDiscoveryWorkspaceState state() { return state; }
    public GeometryDiscoveryViewState viewState() { return viewState; }
    public GeometryDiscoveryWorkflowResolver workflowResolver() { return workflowResolver; }
    public GeometryDiscoveryPhasedWorkflow phasedWorkflow() { return phasedWorkflow; }
    public void framePhase2Geometry() {
        GeometryDiscoveryRenderModel model = phasedWorkflow.renderModel();
        if (model != null) viewState.fitToView(model.model());
    }
    public GeometryDiscoveryRenderDiagnostics renderDiagnostics() { return renderDiagnostics; }
    public void setRenderDiagnostics(GeometryDiscoveryRenderDiagnostics value) { renderDiagnostics = value; }

    public GeometryDiscoveryWorkspaceLayout layout(int width, int height) {
        int top = nuklearModel == null ? 30 : nuklearModel.getMenuBarHeight() + 4;
        return GeometryDiscoveryWorkspaceLayout.calculate(
                width, height, top, Constants.STATUS_BAR_HEIGHT);
    }

    @Override public void close() {
        deactivateWorkspace();
        preferences.save(state, viewState);
        workspace.close();
        phasedWorkflow.close();
    }
}
