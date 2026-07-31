package remframework.gui.view.frames;

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import org.lwjgl.system.MemoryStack;
import remframework.geometry.discovery.*;
import remframework.helpers.Constants;
import remframework.gui.model.AbstractDialogModel.ConfirmState;
import remframework.gui.model.DialogFileModel;
import remframework.gui.model.NuklearModel;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

public final class GeometryDiscoveryControlFrame extends AbstractFrame {
    private static final String[] EXAMPLES = {"Line", "Square", "Tetrahedron", "Small Cubic",
            "Line Reference", "Equilateral Reference", "Tetrahedral Reference",
            "Octahedral Reference"};
    private static final String[] PROFILES = {"General Geometry", "Wave Propagation",
            "Centrosymmetric", "Tetrahedral", "Cubic Runtime"};
    private static final String[] SEARCH_METHODS = {"Hill Climbing", "Simulated Annealing"};
    private final GeometryDiscoveryWorkspaceController controller =
            GeometryDiscoveryWorkspaceManager.getInstance();
    private DialogFileModel candidateDialog;
    private final Deque<Path> recentCandidates = new ArrayDeque<>();
    private String selectedExample = "Small Cubic";
    private String selectedProfile = "General Geometry";
    private String selectedSearchMethod = "Hill Climbing";
    private int searchIterations = 1000;
    private long searchSeed = 42;
    private int minimumDegree = 1;
    private int maximumDegree = 6;
    private int mutations = 1;
    private int archiveSize = 20;
    private double temperature = 1;
    private double coolingRate = .997;
    private int checkpointInterval = 100;
    private int dynamicNodeCount = 3;
    private int dynamicExactNeighbors = 2;
    private long dynamicSeed = 42;
    private List<DynamicElasticResultRow> dynamicResultRows = List.of();
    private int resultDegreeFilter;
    private int resultNodeFilter;
    private String resultStatusFilter = "All";
    private String resultRunFilter = "";
    private final StringBuffer resultRunFilterBuffer = new StringBuffer();
    private String dynamicSpeedValidation = "";
    private String dynamicMaximumValidation = "";
    private boolean phase2Mode = true;
    private GeometryDiscoveryPhase phase2Stage = GeometryDiscoveryPhase.CONFIGURATION_GENERATION;
    private int phase2NodeCount = 4;
    private int phase2ExactDegree = 2;
    private GeometryEmbeddingDimension phase2Dimension = GeometryEmbeddingDimension.THREE_D;
    private GraphSymmetryFilter phase2SymmetryFilter = GraphSymmetryFilter.BOTH;
    private int phase2MaximumConfigurations = 1000;
    private int phase2TimeLimitSeconds = 30;
    private GeometryConfigurationId phase2SelectedConfiguration;
    private GeometryTestResult phase2LatestResult;
    private GeometryTestStoredResult phase2StoredResult;
    private double phase2TargetDistance = 1;
    private long phase2Seed = 42;
    private int phase2InitialRealizations = 1;
    private int phase2SearchLimit = 100_000;
    private boolean showVertices = true, showEdges = true, showLabels = true,
            showCandidateFaces = true, showBoundaryFaces = true, showRejectedFaces = false,
            showNormals = false, showSelfIntersections = true, showViewportDiagnostics = false,
            showWorldAxes = true, showBoundingBox = true;

    public GeometryDiscoveryControlFrame(NuklearModel model) {
        super(model);
        windowOptions = Nuklear.NK_WINDOW_TITLE | Nuklear.NK_WINDOW_BORDER
                | Nuklear.NK_WINDOW_NO_SCROLLBAR;
    }

    @Override public String getTitle() { return "Geometry Discovery Workflow"; }
    @Override public void render(long window, NkContext context) {
        super.render(window, context);
        GeometryDiscoveryWorkspaceLayout.Bounds bounds = workspaceLayout().workflow();
        createFixedLayout(context, bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    private GeometryDiscoveryWorkspaceLayout workspaceLayout() {
        return GeometryDiscoveryWorkspaceLayout.calculate(getWindowWidth(), getWindowHeight(),
                nuklearModel.getMenuBarHeight() + 4, Constants.STATUS_BAR_HEIGHT);
    }

    @Override protected void layout(NkContext c, int x, int y, int width, int height) {
        processCandidateDialog();
        if (phase2Mode) {
            phasedStageTabs(c);
            if (phase2Stage == GeometryDiscoveryPhase.CONFIGURATION_GENERATION)
                phase2ConfigurationGeneration(c);
            else phase2GeometryTest(c);
            return;
        }
        GeometryDiscoveryWorkflowResolution workflow = controller.workflow();
        if (controller.state().guidedMode())
            controller.state().setSelectedStage(workflow.stage());
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 2);
        if (Nuklear.nk_button_label(c, "Guided Mode: "
                + (controller.state().guidedMode() ? "On" : "Off")))
            controller.state().setGuidedMode(!controller.state().guidedMode());
        if (Nuklear.nk_button_label(c, "Return to REM"))
            controller.deactivateWorkspace();
        for (GeometryDiscoveryWorkflowStage stage : GeometryDiscoveryWorkflowStage.values()) {
            Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 2);
            String prefix = workflow.stage() == stage ? "> " : "";
            if (Nuklear.nk_button_label(c,
                    prefix + stage.number() + ". " + stage.label()))
                controller.state().setSelectedStage(stage);
            if (Nuklear.nk_button_label(c, "Help"))
                nuklearModel.showWarningDialog(stage.label(),
                        GeometryDiscoveryWorkspaceHelp.stageHelp(stage));
        }
        Nuklear.nk_layout_row_dynamic(c, 8, 1);
        Nuklear.nk_spacer(c);
        switch (controller.state().selectedStage()) {
            case CANDIDATE -> candidateSection(c);
            case GEOMETRY_RELAXATION -> relaxationSection(c, workflow);
            case EVALUATION -> evaluationSection(c);
            case TOPOLOGY_SEARCH -> searchSection(c, workflow);
            case RESULTS -> resultsSection(c, workflow);
        }
    }

    private void phasedStageTabs(NkContext c) {
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 3);
        if (Nuklear.nk_button_label(c, phase2Stage == GeometryDiscoveryPhase.CONFIGURATION_GENERATION
                ? "> Stage 1: Configuration Generation and Classification"
                : "Stage 1: Configuration Generation and Classification"))
            phase2Stage = GeometryDiscoveryPhase.CONFIGURATION_GENERATION;
        if (Nuklear.nk_button_label(c, phase2Stage == GeometryDiscoveryPhase.GEOMETRY_TEST
                ? "> Stage 2: Geometry Test" : "Stage 2: Geometry Test"))
            phase2Stage = GeometryDiscoveryPhase.GEOMETRY_TEST;
        if (Nuklear.nk_button_label(c, "Legacy Discovery Tools")) phase2Mode = false;
    }

    private void phase2ConfigurationGeneration(NkContext c) {
        heading(c, "Stage 1 - Configuration Generation and Classification");
        label(c, "Graphs are topology-only. Coordinates, faces, area, and volume are not generated here.");
        phase2NodeCount = dynamicInteger(c, "Nodes (N)", phase2NodeCount, 1, 1_000_000);
        phase2ExactDegree = dynamicInteger(c, "Exact neighbors per node (K)", phase2ExactDegree, 0,
                Math.max(0, phase2NodeCount - 1));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String dimension = nk_label_combo_options(c, stack, "Intended dimension",
                    new String[]{"2D", "3D"}, phase2Dimension == GeometryEmbeddingDimension.TWO_D ? "2D" : "3D", .4, .6);
            phase2Dimension = "2D".equals(dimension) ? GeometryEmbeddingDimension.TWO_D : GeometryEmbeddingDimension.THREE_D;
            String filter = nk_label_combo_options(c, stack, "Graph symmetry filter",
                    new String[]{"Symmetric only", "Asymmetric only", "Both"}, phase2SymmetryFilterLabel(), .4, .6);
            phase2SymmetryFilter = switch (filter) {
                case "Symmetric only" -> GraphSymmetryFilter.SYMMETRIC_ONLY;
                case "Asymmetric only" -> GraphSymmetryFilter.ASYMMETRIC_ONLY;
                default -> GraphSymmetryFilter.BOTH;
            };
        }
        phase2MaximumConfigurations = dynamicInteger(c, "Maximum configurations", phase2MaximumConfigurations, 1, 100_000);
        phase2TimeLimitSeconds = dynamicInteger(c, "Enumeration time limit (seconds)", phase2TimeLimitSeconds, 1, 86_400);
        primaryButton(c, "Generate Configurations", true, "", () -> {
            try {
                GeometryConfigurationRequest request = new GeometryConfigurationRequest(phase2NodeCount,
                        phase2ExactDegree, phase2Dimension, phase2SymmetryFilter,
                        phase2MaximumConfigurations, java.time.Duration.ofSeconds(phase2TimeLimitSeconds),
                        null, Path.of("lattice/geometry-discovery/configuration-sets"));
                controller.phasedWorkflow().generateConfigurations(request);
                phase2SelectedConfiguration = null;
                phase2LatestResult = null;
            } catch (Exception exception) { nuklearModel.showErrorDialog("Configuration generation", exception.getMessage()); }
        });
        primaryButton(c, "Reopen Persisted Configuration Set", true, "", () -> {
            try {
                var root = Path.of("lattice/geometry-discovery/configuration-sets");
                try (var sets = java.nio.file.Files.list(root)) {
                    var latest = sets.filter(java.nio.file.Files::isDirectory).sorted().reduce((first, second) -> second).orElseThrow();
                    controller.phasedWorkflow().reopen(latest);
                }
            } catch (Exception exception) { nuklearModel.showErrorDialog("Configuration set", exception.getMessage()); }
        });
        GeometryConfigurationGenerationResult generation = controller.phasedWorkflow().generation().orElse(null);
        if (generation == null) { label(c, "No configuration set generated."); return; }
        GeometryConfigurationMetrics metrics = generation.metrics();
        heading(c, "Generation status");
        label(c, "Status: " + generation.status().code() + " - " + generation.status().summary());
        label(c, "Completion: " + (metrics.enumerationComplete() ? "Complete" : "Incomplete"));
        label(c, "Examined: " + metrics.configurationsExamined() + "    Retained: " + metrics.uniqueConfigurationsRetained()
                + "    Isomorphic duplicates removed: " + metrics.isomorphicDuplicatesRemoved());
        label(c, "Symmetric: " + metrics.symmetricCount() + "    Asymmetric: " + metrics.asymmetricCount()
                + "    Elapsed: " + metrics.elapsedMilliseconds() + " ms");
        if (!generation.status().mayContinue()) label(c, "Diagnostic: " + generation.status().explanation());
        heading(c, "Retained configurations");
        for (GeometryConfigurationTableRow row : controller.phasedWorkflow().tableRows()) {
            boolean selected = row.configurationId().equals(phase2SelectedConfiguration);
            Nuklear.nk_layout_row_dynamic(c, rowHeightEdit + 4, 1);
            String text = (selected ? "> " : "") + row.configurationId() + "  N=" + row.nodeCount() + " K=" + row.exactDegree()
                    + " edges=" + row.edgeCount() + " aut=" + row.automorphismCount() + " " + row.symmetry()
                    + " vertex-transitive=" + row.vertexTransitive() + " geometry=" + row.geometryTestStatus();
            if (Nuklear.nk_button_label(c, text)) {
                phase2SelectedConfiguration = row.configurationId();
                controller.phasedWorkflow().selectForGeometryTest(row.configurationId());
                phase2LatestResult = null;
            }
        }
        if (phase2SelectedConfiguration != null) {
            label(c, GeometryDiscoveryPhasedWorkflow.TOPOLOGY_PREVIEW_LABEL);
            GeometryConfiguration selected = controller.phasedWorkflow().selectedConfigurationRecord().orElse(null);
            if (selected != null) label(c, "Topology preview: " + selected.edges());
            primaryButton(c, "Frame Geometry", true, "", controller::framePhase2Geometry);
            primaryButton(c, "Select for Geometry Test", true, "", () -> {
                controller.phasedWorkflow().selectForGeometryTest(phase2SelectedConfiguration);
                phase2Stage = GeometryDiscoveryPhase.GEOMETRY_TEST;
            });
        }
    }

    private void phase2GeometryTest(NkContext c) {
        heading(c, "Stage 2 - Geometry Test");
        GeometryConfiguration selected = controller.phasedWorkflow().selectedConfigurationRecord().orElse(null);
        if (selected == null) {
            label(c, "Select exactly one configuration in Stage 1 before starting Geometry Test.");
            primaryButton(c, "Return to Configuration Generation", true, "", () -> phase2Stage = GeometryDiscoveryPhase.CONFIGURATION_GENERATION);
            return;
        }
        label(c, "Immutable selected configuration: " + selected.id());
        label(c, "Canonical identity: " + selected.canonicalGraphLabel());
        label(c, "N=" + selected.nodeCount() + " K=" + selected.exactDegree() + " graph symmetry=" + selected.graphSymmetry());
        phase2TargetDistance = dynamicProperty(c, "Target connected-edge length", phase2TargetDistance, 1e-9, 1e9, .1);
        phase2Seed = dynamicInteger(c, "Random seed", (int) phase2Seed, Integer.MIN_VALUE, Integer.MAX_VALUE);
        phase2InitialRealizations = dynamicInteger(c, "Initial realizations / seeds", phase2InitialRealizations, 1, 100);
        phase2SearchLimit = dynamicInteger(c, "Geometry search limit", phase2SearchLimit, 1, Integer.MAX_VALUE);
        controller.phasedWorkflow().setVisualizationIterationsPerFrame(
                dynamicInteger(c, "Visualization iterations per frame",
                        controller.phasedWorkflow().visualizationIterationsPerFrame(), 1, 10000));
        label(c, "Initialization and relaxation use existing scientific defaults; canonical angle constraints remain disabled.");
        GeometryTestResult result = phase2LatestResult != null ? phase2LatestResult
                : controller.phasedWorkflow().latestGeometryTest(selected.id());
        GeometryTestSessionState sessionState = controller.phasedWorkflow().geometryTestSessionState();
        primaryButton(c, "Start Geometry Test", sessionState == GeometryTestSessionState.READY
                || sessionState == GeometryTestSessionState.PAUSED,
                "The selected session is already active or finished.", () -> {
            try {
                if (sessionState == GeometryTestSessionState.PAUSED) {
                    controller.phasedWorkflow().resumeSelectedGeometryTest();
                } else {
                    phase2LatestResult = null;
                    controller.phasedWorkflow().startSelectedGeometryTest(
                            new GeometryTestRequest(selected.id(), phase2Dimension, phase2TargetDistance,
                                    phase2Seed, phase2InitialRealizations, null,
                                    GeometrySymmetryPolicy.MEASURE_ONLY, phase2SearchLimit));
                }
            } catch (Exception exception) { nuklearModel.showErrorDialog("Geometry Test", exception.getMessage()); }
        });
        primaryButton(c, "Pause", sessionState == GeometryTestSessionState.RUNNING,
                "The selected session is not running.", controller.phasedWorkflow()::pauseSelectedGeometryTest);
        primaryButton(c, "Resume", sessionState == GeometryTestSessionState.PAUSED,
                "The selected session is not paused.", controller.phasedWorkflow()::resumeSelectedGeometryTest);
        primaryButton(c, "Step", sessionState == GeometryTestSessionState.READY
                || sessionState == GeometryTestSessionState.PAUSED,
                "Pause or initialize the selected session before stepping.", controller.phasedWorkflow()::stepSelectedGeometryTest);
        primaryButton(c, "Stop", sessionState == GeometryTestSessionState.RUNNING
                || sessionState == GeometryTestSessionState.PAUSED,
                "The selected session is not active.", controller.phasedWorkflow()::stopSelectedGeometryTest);
        primaryButton(c, "Reset", true, "", () -> { controller.phasedWorkflow().resetSelectedGeometryTest(); phase2LatestResult = null; });
        primaryButton(c, "Save Geometry-Test Result", result != null, "Run Geometry Test before saving a result.", () -> {
            try { controller.phasedWorkflow().saveLatestGeometryTest(Path.of("lattice/geometry-discovery/configuration-sets"), selected.id()); }
            catch (Exception exception) { nuklearModel.showErrorDialog("Geometry-test result", exception.getMessage()); }
        });
        primaryButton(c, "Reopen Stored Geometry-Test Result", true, "", () -> {
            try {
                Path root = Path.of("lattice/geometry-discovery/configuration-sets");
                try (var sets = java.nio.file.Files.walk(root)) {
                    var file = sets.filter(path -> path.getFileName().toString().endsWith(".toml")
                                    && path.toString().contains("geometry-tests"))
                            .sorted().reduce((first, second) -> second).orElseThrow();
                    phase2StoredResult = new GeometryTestResultStore().load(file);
                    controller.phasedWorkflow().reopenStoredResult(phase2StoredResult);
                    phase2SelectedConfiguration = phase2StoredResult.configurationId();
                    phase2Stage = GeometryDiscoveryPhase.GEOMETRY_TEST;
                }
            } catch (Exception exception) { nuklearModel.showErrorDialog("Geometry-test result", exception.getMessage()); }
        });
        visualizationToggles(c);
        if (result == null) {
            if (phase2StoredResult != null)
                label(c, "Reopened stored result: " + phase2StoredResult.status() + " - " + phase2StoredResult.explanation());
            GeometryTestIncrementalSession incremental = controller.phasedWorkflow().incrementalSession();
            if (incremental == null) label(c, "Geometry test has not started.");
            else {
                label(c, "Session: " + incremental.state() + "    Iteration: " + incremental.iteration());
                label(c, String.format("Edge-length accuracy: %.6f    Maximum speed: %.6g",
                        incremental.metrics().meanDistanceAccuracy(), incremental.metrics().maximumNodeSpeed()));
                label(c, "Convergence: elasticEquilibrium=" + incremental.metrics().elasticEquilibrium()
                        + " spatiallyValid=" + incremental.metrics().spatiallyValid()
                        + " realizationStatus=" + incremental.state());
                if (!incremental.stopReason().isBlank()) label(c, "Diagnostic: " + incremental.stopReason());
            }
            return;
        }
        phase2ResultSections(c, result);
        if (phase2StoredResult != null)
            label(c, "Reopened stored result: " + phase2StoredResult.status() + " - " + phase2StoredResult.explanation());
    }

    private void phase2ResultSections(NkContext c, GeometryTestResult result) {
        heading(c, "Realization"); label(c, result.realization().status() + " - converged=" + result.realization().converged()
                + ", effective dimension=" + result.realization().effectiveDimension());
        heading(c, "Face derivation"); label(c, result.faceDerivation() == null ? "Not run" : result.faceDerivation().status().code() + " - unique=" + result.faceDerivation().unique());
        heading(c, "Boundary validation"); label(c, result.boundary() == null ? "Not run" : result.boundary().status().code() + " - closed=" + result.boundary().closed() + " - " + result.boundary().status().explanation());
        heading(c, "Measurement");
        primaryButton(c, "Calculate Area", result.boundary() != null && result.boundary().closed(), "Area requires a validated unique closed boundary.", () -> {});
        primaryButton(c, "Calculate Volume", result.boundary() != null && result.boundary().closed() && phase2Dimension == GeometryEmbeddingDimension.THREE_D, "Volume requires a validated unique closed 3D boundary.", () -> {});
        label(c, "Area: " + (result.area().available() ? result.area().absoluteArea() : "unavailable") + "    Volume: " + (result.volume().available() ? result.volume().absoluteVolume() : "unavailable"));
        heading(c, "Optimization");
        boolean optimizationAllowed = result.boundary() != null && result.boundary().closed() && result.area().available();
        primaryButton(c, "Run Optimization", optimizationAllowed, "Optimization requires a valid closed boundary and baseline measurement.", () -> {
            try { phase2LatestResult = controller.phasedWorkflow().optimizeSelectedGeometryTest(phase2TargetDistance, phase2SearchLimit); }
            catch (Exception exception) { nuklearModel.showErrorDialog("Optimization", exception.getMessage()); }
        });
        label(c, result.optimization().attempted() ? "Baseline measure: " + result.optimization().baselineMeasure()
                + "    Best result found within optimization limits: " + result.optimization().optimizedMeasure()
                + "    Improvement: " + result.optimization().relativeImprovement()
                + "    Iterations: " + result.optimization().iterationCount()
                : "Optimization has not run. It remains separate from realization and measurement.");
        label(c, "Optimization result: " + result.optimization().summary());
        label(c, "Final status: " + result.status().code() + " - " + result.status().explanation());
        if (!result.status().mayContinue()) label(c, "Failure diagnostic: " + result.status().summary());
    }

    private void visualizationToggles(NkContext c) {
        heading(c, "Visualization");
        showVertices = nk_check_label(c, "Vertices", showVertices, .65, .35);
        showEdges = nk_check_label(c, "Edges", showEdges, .65, .35);
        showLabels = nk_check_label(c, "Labels", showLabels, .65, .35);
        controller.viewState().setShowConnections(showEdges);
        controller.viewState().setShowNodeLabels(showLabels);
        controller.viewState().setShowVertices(showVertices);
        showCandidateFaces = nk_check_label(c, "Candidate faces", showCandidateFaces, .65, .35);
        showBoundaryFaces = nk_check_label(c, "Accepted boundary faces", showBoundaryFaces, .65, .35);
        showRejectedFaces = nk_check_label(c, "Rejected faces", showRejectedFaces, .65, .35);
        showNormals = nk_check_label(c, "Normals", showNormals, .65, .35);
        showSelfIntersections = nk_check_label(c, "Detected self-intersections", showSelfIntersections, .65, .35);
        showViewportDiagnostics = nk_check_label(c, "Viewport diagnostics", showViewportDiagnostics, .65, .35);
        showWorldAxes = nk_check_label(c, "World axes", showWorldAxes, .65, .35);
        showBoundingBox = nk_check_label(c, "Geometry bounding box", showBoundingBox, .65, .35);
        controller.viewState().setShowAxes(showWorldAxes);
        controller.viewState().setShowBoundingBox(showBoundingBox);
        if (showViewportDiagnostics && controller.renderDiagnostics() != null) {
            GeometryDiscoveryRenderDiagnostics d = controller.renderDiagnostics();
            label(c, "Render model " + d.configurationId() + ": vertices=" + d.renderedVertexCount()
                    + " edges=" + d.renderedEdgeCount() + " scale=" + d.displayScale());
            label(c, "Centroid=" + d.geometryCentroid() + " radius=" + d.geometryBoundingRadius());
            label(c, "Camera=" + d.cameraPosition() + " target=" + d.cameraTarget()
                    + " distance=" + d.cameraDistance());
            label(c, "Clip near=" + d.nearPlane() + " far=" + d.farPlane()
                    + " intersectsFrustum=" + d.geometryIntersectsFrustum());
        }
    }

    private String phase2SymmetryFilterLabel() {
        return switch (phase2SymmetryFilter) { case SYMMETRIC_ONLY -> "Symmetric only"; case ASYMMETRIC_ONLY -> "Asymmetric only"; case BOTH -> "Both"; };
    }

    private void candidateSection(NkContext c) {
        GeometryDiscoveryWorkspaceSnapshot snapshot = controller.workspace().snapshot();
        heading(c, "1. Candidate");
        primaryButton(c, "New Discovery", true, "", () ->
                nuklearModel.showFrame(NuklearModel.Frame.geometryDiscoveryNew));
        heading(c, "Fixed-Degree Dynamic Elastic");
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 1);
        dynamicExactNeighbors = Nuklear.nk_propertyi(c, "# Exact neighbors (2 or 3)",
                2, dynamicExactNeighbors, 3, 1, 1);
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 1);
        dynamicNodeCount = Nuklear.nk_propertyi(c, "# Node count",
                1, dynamicNodeCount, 32, 1, 1);
        primaryButton(c, "Create Dynamic Elastic Candidate", true, "", () -> {
            FixedDegreeValidation validation = controller.workspace().createDynamicElastic(
                    dynamicNodeCount, dynamicExactNeighbors,
                    DynamicElasticSettings.defaults(10, dynamicSeed));
            if (!validation.valid()) {
                String nearest = "\nNearest lower valid value: "
                        + (validation.nearestLowerValid() == null ? "none"
                        : validation.nearestLowerValid())
                        + "\nNearest higher valid value: "
                        + (validation.nearestHigherValid() == null ? "none"
                        : validation.nearestHigherValid());
                nuklearModel.showWarningDialog("Invalid fixed-degree graph",
                        String.join("\n", validation.messages()) + nearest);
            } else controller.state().setSelectedStage(
                    GeometryDiscoveryWorkflowStage.GEOMETRY_RELAXATION);
        });
        try (MemoryStack stack = MemoryStack.stackPush()) {
            selectedExample = nk_label_combo_options(c, stack, "Example", EXAMPLES,
                    selectedExample, .35, .65);
        }
        primaryButton(c, "Load Example", true, "", () ->
                controller.workspace().loadBuiltIn(exampleId(selectedExample)));
        primaryButton(c, "Open Candidate File", true, "", () ->
                candidateDialog = nuklearModel.showOpenFileDialog(
                        "Open Discovery Candidate", "lattice/candidates", ".toml"));
        primaryButton(c, "Reload Candidate", controller.state().currentSource() != null,
                "The current candidate has no source file.", () ->
                        controller.workspace().loadCandidate(controller.state().currentSource()));
        primaryButton(c, "Recent Candidates", !recentCandidates.isEmpty(),
                "No candidate file has been opened during this session.", () ->
                        controller.workspace().loadCandidate(recentCandidates.getFirst()));
        primaryButton(c, "Save Experiment Configuration",
                controller.state().experimentConfiguration() != null,
                "Create a New Discovery experiment before saving its configuration.", () -> {
                    try {
                        controller.workspace().saveExperimentConfiguration(Path.of(
                                "lattice/experiments/discovery-experiment.properties"));
                    } catch (Exception exception) {
                        nuklearModel.showErrorDialog("Geometry Discovery", exception.getMessage());
                    }
                });
        primaryButton(c, "Load Experiment Configuration", true, "", () -> {
            try {
                controller.workspace().loadExperimentConfiguration(Path.of(
                        "lattice/experiments/discovery-experiment.properties"));
            } catch (Exception exception) {
                nuklearModel.showErrorDialog("Geometry Discovery", exception.getMessage());
            }
        });
        label(c, "Model: " + snapshot.geometry().model().name());
        label(c, "Source: " + (controller.state().currentSource() == null
                ? "Built-in example" : controller.state().currentSource()));
        label(c, "Nodes: " + snapshot.geometry().model().nodes().size()
                + "    Connections: " + snapshot.geometry().model().connections().size());
        label(c, "Metadata: " + metadataVersion());
    }

    private void relaxationSection(NkContext c, GeometryDiscoveryWorkflowResolution workflow) {
        if (controller.workspace().dynamicElasticActive()) {
            dynamicElasticRelaxation(c);
            return;
        }
        GeometryDiscoveryWorkspaceSnapshot snapshot = controller.workspace().snapshot();
        GeometryDiscoveryResult geometry = snapshot.geometry();
        heading(c, "2. Geometry Relaxation");
        if (controller.workflowResolver().geometrySatisfied(geometry))
            label(c, "Geometry is already converged. Relaxation is not required.");
        label(c, "Status: " + snapshot.status());
        label(c, "Iteration: " + geometry.metrics().iterationCount()
                + "    Converged: "
                + controller.workflowResolver().geometrySatisfied(geometry));
        label(c, String.format("Edge error: %.6g",
                geometry.metrics().totalEdgeLengthError()));
        primaryButton(c, "Start Relaxation", workflow.action(
                GeometryDiscoveryWorkflowResolver.START_RELAXATION),
                controller.workspace()::startRelaxation);
        primaryButton(c, "Pause", workflow.action(GeometryDiscoveryWorkflowResolver.PAUSE),
                controller.workspace()::pause);
        primaryButton(c, "Single Step", workflow.action(
                GeometryDiscoveryWorkflowResolver.SINGLE_STEP),
                controller.workspace()::singleRelaxationStep);
        primaryButton(c, "Reset", true, "", controller.workspace()::resetExperiment);
        primaryButton(c, "Run to Convergence", workflow.action(
                GeometryDiscoveryWorkflowResolver.RUN_TO_CONVERGENCE),
                controller.workspace()::startRelaxation);
        expander(c, "Advanced Settings", controller.state().relaxationAdvancedExpanded(),
                value -> controller.state().setRelaxationAdvancedExpanded(value));
        if (controller.state().relaxationAdvancedExpanded())
            relaxationAdvanced(c, geometry.settings());
    }

    private void dynamicElasticRelaxation(NkContext c) {
        DynamicElasticMetrics metrics = controller.workspace().dynamicElasticMetrics();
        DynamicElasticSettings settings = controller.workspace().dynamicElasticSettings();
        heading(c, "2. Dynamic Elastic Relaxation");
        label(c, "Edges satisfied: " + metrics.connectionsSatisfied()
                + " / " + metrics.connectionCount());
        label(c, "Angles satisfied: " + metrics.localAnglesSatisfied()
                + " / " + metrics.localAngleCount());
        label(c, String.format("Mean edge accuracy: %.3f%%",
                metrics.meanDistanceAccuracy() * 100));
        label(c, String.format("Maximum angle error: %.5g degrees",
                metrics.maximumAngleErrorDegrees()));
        label(c, String.format("Maximum node speed: %.7g", metrics.maximumNodeSpeed()));
        label(c, "Stable iterations: " + metrics.stableIterations()
                + " / " + metrics.requiredStableIterations());
        label(c, "Current iteration: " + metrics.iteration());
        label(c, "State: " + metrics.state());
        heading(c, "Spatial Constraints");
        label(c, String.format("Minimum non-neighbor distance: %.7g",
                metrics.minimumObservedNonNeighborDistance()));
        label(c, "Edge clearance violations: " + metrics.edgeClearanceViolations());
        label(c, String.format("Mean local 3D spread: %.7g", metrics.meanLocalSpread()));
        label(c, String.format("Minimum local 3D spread: %.7g",
                metrics.minimumLocalSpread()));
        label(c, "Elastic equilibrium: " + (metrics.elasticEquilibrium() ? "Yes" : "No"));
        label(c, "Spatially valid: " + (metrics.spatiallyValid() ? "Yes" : "No"));
        DynamicElasticSpatialSettings spatial = settings.spatial();
        boolean overlap = nk_check_label(c, "Prevent node overlap - "
                        + enabledLabel(spatial.nonNeighborExclusionEnabled()),
                spatial.nonNeighborExclusionEnabled(), .65, .35);
        boolean crossings = nk_check_label(c, "Prevent edge crossings - "
                        + enabledLabel(spatial.edgeCrossingPreventionEnabled()),
                spatial.edgeCrossingPreventionEnabled(), .65, .35);
        boolean spreadEnabled = nk_check_label(c, "Encourage local 3D spread - "
                        + enabledLabel(spatial.localSpreadEnabled()),
                spatial.localSpreadEnabled(), .65, .35);
        double spatialStrength = dynamicProperty(c, "Spatial constraint strength",
                spatial.strength(), 0, 100, .001);
        if (overlap != spatial.nonNeighborExclusionEnabled()
                || crossings != spatial.edgeCrossingPreventionEnabled()
                || spreadEnabled != spatial.localSpreadEnabled()
                || Math.abs(spatialStrength - spatial.strength()) > 1e-12) {
            spatial = new DynamicElasticSpatialSettings(overlap,
                    spatial.minimumNonNeighborDistance(), crossings,
                    spatial.edgeClearanceDistance(), spreadEnabled, spatialStrength);
            settings = settings.withSpatial(spatial);
            controller.workspace().updateDynamicElasticSettings(settings);
        }
        DynamicElasticResult activeResult = controller.workspace().dynamicElasticResult();
        boolean hasCanonicalAngles = activeResult != null
                && activeResult.graph().canonicalTopology() != null
                && activeResult.graph().canonicalTopology()
                != CanonicalDynamicElasticTopology.CYCLE;
        if (hasCanonicalAngles) {
            heading(c, "Canonical geometry constraints");
            DynamicElasticCanonicalGeometrySettings canonical =
                    settings.canonicalGeometry();
            boolean anglesEnabled = nk_check_label(c, "Enforce canonical angles - "
                            + enabledLabel(canonical.angleConstraintsEnabled()),
                    canonical.angleConstraintsEnabled(), .65, .35);
            double targetAngle = dynamicProperty(c, "Target angle (degrees)",
                    canonical.targetAngleDegrees(), 1e-6, 179.999999, .1);
            double angleTolerance = dynamicProperty(c, "Angle tolerance (degrees)",
                    canonical.angleToleranceDegrees(), 0, 180, .1);
            double angleStrength = dynamicProperty(c, "Angle strength",
                    canonical.angleStrength(), 0, 100, .001);
            DynamicElasticCanonicalGeometrySettings changedCanonical =
                    new DynamicElasticCanonicalGeometrySettings(anglesEnabled,
                            targetAngle, angleTolerance, angleStrength,
                            canonical.faceDiagonalsEnabled());
            if (!changedCanonical.equals(canonical)) {
                settings = settings.withCanonicalGeometry(changedCanonical);
                controller.workspace().updateDynamicElasticSettings(settings);
            }
        } else {
            label(c, "No canonical angle model is defined for this graph.");
        }
        label(c, "Operation: " + controller.workspace()
                .dynamicElasticOperation().label());
        boolean running = controller.workspace().dynamicElasticOperation()
                == DynamicElasticOperation.RUNNING_CONTINUOUSLY
                || controller.workspace().dynamicElasticOperation()
                == DynamicElasticOperation.RUNNING_UNTIL_EQUILIBRIUM;
        boolean terminal = metrics.state() == DynamicElasticState.EQUILIBRIUM_REACHED
                || metrics.state() == DynamicElasticState.NUMERICAL_FAILURE;
        boolean maximumCanContinue = metrics.state()
                != DynamicElasticState.MAX_ITERATIONS_REACHED
                || controller.state().dynamicElasticMaximumIterations()
                > metrics.iteration();
        boolean mayRun = !running && !terminal && maximumCanContinue;
        String runReason = terminal ? "The dynamic elastic run has reached a terminal state."
                : !maximumCanContinue
                ? "Increase Maximum iterations to continue from this state."
                : "Dynamic elastic relaxation is already running.";
        primaryButton(c, "Start", mayRun, runReason,
                controller.workspace()::startRelaxation);
        primaryButton(c, "Pause", running,
                "Dynamic elastic relaxation is not running.", controller.workspace()::pause);
        primaryButton(c, "Single Step", mayRun,
                runReason,
                controller.workspace()::singleRelaxationStep);
        primaryButton(c, "Reset", !running,
                "Pause the dynamic elastic run before resetting.",
                controller.workspace()::resetExperiment);
        primaryButton(c, "Run Until Equilibrium", mayRun, runReason,
                controller.workspace()::runDynamicElasticUntilEquilibrium);
        dynamicPlaybackControls(c, metrics, running);
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 1);
        double accuracyPercent = Nuklear.nk_propertyd(c, "Required accuracy (%)",
                1, settings.requiredAccuracy() * 100, 100, .1, .05f);
        accuracyPercent = Nuklear.nk_slide_float(c, 1,
                (float) accuracyPercent, 100, .1f);
        double required = accuracyPercent / 100;
        double target = dynamicProperty(c, "Target distance",
                settings.targetDistance(), 1e-9, 1e9, .1);
        if (Math.abs(required - settings.requiredAccuracy()) > 1e-9
                || Math.abs(target - settings.targetDistance()) > 1e-12)
            controller.workspace().updateDynamicElasticSettings(
                    settings.withRequiredAccuracy(required).withTargetDistance(target));
        expander(c, "Advanced Settings", controller.state().relaxationAdvancedExpanded(),
                value -> controller.state().setRelaxationAdvancedExpanded(value));
        if (controller.state().relaxationAdvancedExpanded()) {
            double nonNeighborThreshold = dynamicProperty(c,
                    "Minimum non-neighbor distance",
                    settings.spatial().minimumNonNeighborDistance(), 0, 1e9, .1);
            double edgeClearance = dynamicProperty(c, "Edge clearance distance",
                    settings.spatial().edgeClearanceDistance(), 0, 1e9, .1);
            double spring = dynamicProperty(c, "Spring strength",
                    settings.springStrength(), 0, 100, .01);
            double update = dynamicProperty(c, "Update strength",
                    settings.updateStrength(), 0, 100, .01);
            double retention = dynamicProperty(c, "Velocity retention",
                    settings.velocityRetention(), 0, 1, .01);
            double linear = dynamicProperty(c, "Linear velocity damping",
                    settings.linearVelocityDamping(), 0, 1, .01);
            double angular = dynamicProperty(c, "Angular damping",
                    settings.angularVelocityDamping(), 0, 1, .01);
            double maximumVelocity = dynamicProperty(c, "Maximum velocity",
                    settings.maximumVelocity(), 1e-12, 1e9, .1);
            double maximumForce = dynamicProperty(c, "Maximum force",
                    settings.maximumForce(), 1e-12, 1e9, .1);
            double speedThreshold = dynamicProperty(c, "Speed threshold",
                    settings.maximumEquilibriumSpeed(), 0, 1e9, .0001);
            int stable = dynamicInteger(c, "Required stable iterations",
                    settings.requiredStableIterations(), 1, 1_000_000);
            DynamicElasticSettings changed = new DynamicElasticSettings(
                    settings.targetDistance(), settings.requiredAccuracy(), spring,
                    update, retention, linear, angular, maximumVelocity,
                    maximumForce, speedThreshold, stable,
                    settings.maximumIterations(),
                    settings.workerUpdatesPerPublication(), settings.randomSeed(),
                    new DynamicElasticSpatialSettings(
                            settings.spatial().nonNeighborExclusionEnabled(),
                            nonNeighborThreshold,
                            settings.spatial().edgeCrossingPreventionEnabled(),
                            edgeClearance, settings.spatial().localSpreadEnabled(),
                    settings.spatial().strength()));
            changed = changed.withCanonicalGeometry(settings.canonicalGeometry());
            if (!changed.equals(settings))
                controller.workspace().updateDynamicElasticSettings(changed);
        }
    }

    private String enabledLabel(boolean enabled) {
        return DynamicElasticStatusReporter.constraintState(enabled);
    }

    private void dynamicPlaybackControls(NkContext c, DynamicElasticMetrics metrics,
                                         boolean running) {
        heading(c, "Playback");
        int speedIndex = DynamicElasticPlaybackPacing.speedSliderIndex(
                controller.state().dynamicElasticUpdatesPerSecond(),
                controller.state().dynamicElasticMaximumSpeed());
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 1);
        int selectedSpeedIndex = Nuklear.nk_slide_int(c, 0, speedIndex,
                DynamicElasticPlaybackPacing.SPEEDS.length, 1);
        boolean maximum = selectedSpeedIndex
                == DynamicElasticPlaybackPacing.SPEEDS.length;
        controller.state().setDynamicElasticMaximumSpeed(maximum);
        if (!maximum) controller.state().setDynamicElasticUpdatesPerSecond(
                DynamicElasticPlaybackPacing.speedAtSliderIndex(selectedSpeedIndex));
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 2);
        Nuklear.nk_label(c, "Visualization speed", Nuklear.NK_TEXT_LEFT);
        if (maximum) {
            Nuklear.nk_label(c, "Maximum", Nuklear.NK_TEXT_LEFT);
            dynamicSpeedValidation = "";
        } else {
            int entered = Nuklear.nk_propertyi(c, "# updates per second", 1,
                    controller.state().dynamicElasticUpdatesPerSecond(), 1000, 1, 1);
            if (DynamicElasticPlaybackPacing.validSpeed(entered)) {
                controller.state().setDynamicElasticUpdatesPerSecond(entered);
                dynamicSpeedValidation = "";
            } else if (entered != controller.state().dynamicElasticUpdatesPerSecond()) {
                dynamicSpeedValidation = "Choose 1, 2, 5, 10, 20, 30, 50, 100, "
                        + "200, 500, 1000, or Maximum.";
            }
        }
        if (!dynamicSpeedValidation.isBlank()) label(c, dynamicSpeedValidation);

        int publicationIndex = DynamicElasticPlaybackPacing.publicationSliderIndex(
                controller.state().dynamicElasticPublicationInterval());
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 1);
        int selectedPublication = Nuklear.nk_slide_int(c, 0, publicationIndex,
                DynamicElasticPlaybackPacing.PUBLICATION_INTERVALS.length - 1, 1);
        controller.state().setDynamicElasticPublicationInterval(
                DynamicElasticPlaybackPacing.PUBLICATION_INTERVALS[selectedPublication]);
        label(c, "Update viewport every "
                + controller.state().dynamicElasticPublicationInterval()
                + (controller.state().dynamicElasticPublicationInterval() == 1
                ? " iteration" : " iterations"));

        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 1);
        if (running) Nuklear.nk_widget_disable_begin(c);
        int enteredMaximum = Nuklear.nk_propertyi(c, "Maximum iterations", 1,
                controller.state().dynamicElasticMaximumIterations(),
                Integer.MAX_VALUE, 1, 100);
        if (running) Nuklear.nk_widget_disable_end(c);
        if (!running && enteredMaximum
                != controller.state().dynamicElasticMaximumIterations()) {
            if (enteredMaximum < metrics.iteration()) {
                dynamicMaximumValidation = "Current iteration is already "
                        + metrics.iteration() + ". Reset the experiment or choose a "
                        + "maximum greater than " + metrics.iteration() + ".";
            } else if (controller.workspace().updateDynamicElasticMaximumIterations(
                    enteredMaximum)) {
                dynamicMaximumValidation = "";
            }
        }
        if (!dynamicMaximumValidation.isBlank()) label(c, dynamicMaximumValidation);
    }

    private double dynamicProperty(NkContext c, String label, double value,
                                   double minimum, double maximum, double step) {
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 1);
        return Nuklear.nk_propertyd(c, label, minimum, value, maximum, step,
                (float) Math.max(1e-9, step * .1));
    }
    private int dynamicInteger(NkContext c, String label, int value,
                               int minimum, int maximum) {
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 1);
        return Nuklear.nk_propertyi(c, label, minimum, value, maximum, 1, 1);
    }

    private void relaxationAdvanced(NkContext c, GeometryDiscoverySettings settings) {
        settingRow(c, "Target distance", settings.targetDistance(),
                settings.withTargetDistance(settings.targetDistance() * .8),
                settings.withTargetDistance(settings.targetDistance() * 1.25));
        settingRow(c, "Learning rate", settings.learningRate(),
                settings.withLearningRate(settings.learningRate() * .8),
                settings.withLearningRate(settings.learningRate() * 1.25));
        settingRow(c, "Repulsion", settings.repulsionStrength(),
                settings.withRepulsionStrength(settings.repulsionStrength() * .8),
                settings.withRepulsionStrength(settings.repulsionStrength() * 1.25));
        settingRow(c, "Maximum correction", settings.maximumCorrection(),
                settings.withMaximumCorrection(settings.maximumCorrection() * .8),
                settings.withMaximumCorrection(settings.maximumCorrection() * 1.25));
        settingRow(c, "Tolerance", settings.convergenceTolerance(),
                settings.withConvergenceTolerance(settings.convergenceTolerance() * .5),
                settings.withConvergenceTolerance(settings.convergenceTolerance() * 2));
        label(c, "Maximum iterations: " + settings.maximumIterations());
        label(c, "Iterations per worker update: "
                + controller.workspace().relaxationIterationsPerUpdate());
        label(c, "Random seed: " + settings.randomSeed());
    }

    private void evaluationSection(NkContext c) {
        heading(c, "3. Evaluation");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            selectedProfile = nk_label_combo_options(c, stack, "Scoring profile", PROFILES,
                    profileLabel(controller.state().scoringProfile()), .35, .65);
        }
        TopologyScoringProfile profile = profile(selectedProfile);
        if (profile != controller.state().scoringProfile()) controller.selectProfile(profile);
        TopologyCandidate evaluated = controller.evaluatedCurrentCandidate();
        label(c, String.format("Total score: %.7g", evaluated.score().total()));
        label(c, "Most important contributions:");
        evaluated.score().details().values().stream()
                .filter(component -> component.weightedContribution() != 0)
                .sorted(Comparator.comparingDouble(
                        TopologyScoreComponent::weightedContribution).reversed())
                .limit(3).forEach(component -> label(c, String.format(
                        "%s: %.6g", component.name(), component.weightedContribution())));
        label(c, "Converged: " + controller.workflowResolver().geometrySatisfied(
                evaluated.geometryResult()));
        label(c, "Runtime compatible: "
                + evaluated.geometryResult().runtimeCompatibility().compatible());
        label(c, evaluated.geometryResult().runtimeCompatibility().reason());
        primaryButton(c, "View Detailed Diagnostics", true, "", () -> {
            controller.state().setSelectedDetailTab(GeometryDiscoveryDetailTab.OVERVIEW);
            controller.state().setDetailedDiagnosticsExpanded(true);
        });
        primaryButton(c, "Edit Score Weights", true, "", () -> {
            controller.state().setSelectedDetailTab(GeometryDiscoveryDetailTab.SCORE);
            controller.state().setScoreWeightsExpanded(true);
        });
        primaryButton(c, "Reset Profile", true, "", controller::resetProfile);
    }

    private void searchSection(NkContext c, GeometryDiscoveryWorkflowResolution workflow) {
        heading(c, "4. Topology Search");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            selectedSearchMethod = nk_label_combo_options(c, stack, "Search method",
                    SEARCH_METHODS, selectedSearchMethod, .35, .65);
        }
        label(c, "Search iterations: " + searchIterations);
        label(c, "Random seed: " + searchSeed);
        label(c, "Degree range: " + minimumDegree + " to " + maximumDegree);
        primaryButton(c, "Start Search", workflow.action(
                GeometryDiscoveryWorkflowResolver.START_SEARCH), () ->
                controller.workspace().startSearch(searchSettings()));
        if (controller.workspace().snapshot().searchRunning()) {
            primaryButton(c, "Pause", workflow.action(GeometryDiscoveryWorkflowResolver.PAUSE),
                    controller.workspace()::pause);
            primaryButton(c, "Stop Search", workflow.action(
                    GeometryDiscoveryWorkflowResolver.STOP_SEARCH),
                    controller.workspace()::stop);
        } else {
            primaryButton(c, "Single Search Iteration", workflow.action(
                    GeometryDiscoveryWorkflowResolver.SINGLE_SEARCH_STEP), () ->
                    controller.workspace().singleSearchStep(searchSettings()));
        }
        expander(c, "Advanced Settings", controller.state().searchAdvancedExpanded(),
                value -> controller.state().setSearchAdvancedExpanded(value));
        if (controller.state().searchAdvancedExpanded()) {
            label(c, "Mutations per iteration: " + mutations);
            label(c, "Archive size: " + archiveSize);
            label(c, String.format("Initial temperature: %.5g", temperature));
            label(c, String.format("Cooling rate: %.5g", coolingRate));
            label(c, "Checkpoint interval: " + checkpointInterval);
        }
        heading(c, "Live Visualization");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String selected = nk_label_combo_options(c, stack, "Update View On",
                    new String[]{"Every Iteration", "Accepted Mutation",
                            "New Best Candidate", "Timed Snapshot"},
                    visualizationTriggerLabel(), .42, .58);
            controller.state().setVisualizationUpdateTrigger(
                    visualizationTrigger(selected));
        }
        int sliderIndex = DiscoveryVisualizationPacing.sliderIndex(
                controller.state().snapshotUpdatesPerSecond(),
                controller.state().maximumVisualizationSpeed());
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 1);
        int selectedIndex = Nuklear.nk_slide_int(c, 0, sliderIndex,
                DiscoveryVisualizationPacing.SPEEDS.length, 1);
        DiscoveryVisualizationPacing.Selection selection =
                DiscoveryVisualizationPacing.selection(selectedIndex);
        controller.state().setMaximumVisualizationSpeed(selection.maximum());
        if (!selection.maximum())
            controller.state().setSnapshotUpdatesPerSecond(selection.updatesPerSecond());
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 2);
        Nuklear.nk_label(c, "Visualization Speed", Nuklear.NK_TEXT_LEFT);
        if (controller.state().maximumVisualizationSpeed()) {
            Nuklear.nk_label(c, "Maximum", Nuklear.NK_TEXT_LEFT);
        } else {
            int entered = Nuklear.nk_propertyi(c, "# updates per second", 1,
                    controller.state().snapshotUpdatesPerSecond(), 1000, 1, 1);
            if (DiscoveryVisualizationPacing.validSpeed(entered))
                controller.state().setSnapshotUpdatesPerSecond(entered);
        }
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 2);
        if (Nuklear.nk_button_label(c, "Maximum Speed: "
                + (controller.state().maximumVisualizationSpeed() ? "On" : "Off")))
            controller.state().setMaximumVisualizationSpeed(
                    !controller.state().maximumVisualizationSpeed());
        if (Nuklear.nk_button_label(c, "Pause on New Best: "
                + (controller.state().pauseOnNewBest() ? "On" : "Off")))
            controller.state().setPauseOnNewBest(!controller.state().pauseOnNewBest());
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 1);
        if (Nuklear.nk_button_label(c, "Follow Best Candidate: "
                + (controller.state().followBestCandidate() ? "On" : "Off")))
            controller.state().setFollowBestCandidate(
                    !controller.state().followBestCandidate());
    }

    private String visualizationTriggerLabel() {
        return switch (controller.state().visualizationUpdateTrigger()) {
            case EVERY_ITERATION -> "Every Iteration";
            case ACCEPTED_MUTATION -> "Accepted Mutation";
            case NEW_BEST_CANDIDATE -> "New Best Candidate";
            case TIMED_SNAPSHOT -> "Timed Snapshot";
        };
    }

    private DiscoveryVisualizationUpdateTrigger visualizationTrigger(String label) {
        return switch (label) {
            case "Every Iteration" -> DiscoveryVisualizationUpdateTrigger.EVERY_ITERATION;
            case "New Best Candidate" ->
                    DiscoveryVisualizationUpdateTrigger.NEW_BEST_CANDIDATE;
            case "Timed Snapshot" -> DiscoveryVisualizationUpdateTrigger.TIMED_SNAPSHOT;
            default -> DiscoveryVisualizationUpdateTrigger.ACCEPTED_MUTATION;
        };
    }

    private void resultsSection(NkContext c, GeometryDiscoveryWorkflowResolution workflow) {
        heading(c, "5. Results");
        GeometryDiscoveryWorkspaceSnapshot snapshot = controller.workspace().snapshot();
        boolean available = controller.workflowResolver().hasSearchResults(snapshot);
        if (!available) {
            label(c, "Results become available after the first completed search iteration.");
        } else {
            TopologyCandidate best = snapshot.search().bestCandidate();
            TopologyMetrics metrics = best.topologyMetrics();
            label(c, String.format("Best score: %.7g", best.score().total()));
            label(c, "Nodes: " + metrics.nodeCount() + "    Edges: " + metrics.edgeCount());
            label(c, "Degree: " + metrics.minimumDegree() + " to " + metrics.maximumDegree()
                    + "    Diameter: " + metrics.graphDiameter());
            label(c, "Converged: " + best.geometryResult().converged());
            label(c, "Runtime compatible: "
                    + best.geometryResult().runtimeCompatibility().compatible());
        }
        primaryButton(c, "Preview Best", workflow.action(
                GeometryDiscoveryWorkflowResolver.PREVIEW_BEST), () ->
                controller.workspace().preview(snapshot.search().bestCandidate()));
        primaryButton(c, "Load Best", workflow.action(
                GeometryDiscoveryWorkflowResolver.LOAD_BEST),
                controller.workspace()::loadBestCandidate);
        primaryButton(c, "Compare", workflow.action(
                GeometryDiscoveryWorkflowResolver.COMPARE), () ->
                controller.workspace().compare(snapshot.search().currentCandidate(),
                        snapshot.search().bestCandidate()));
        primaryButton(c, "Export Best", workflow.action(
                GeometryDiscoveryWorkflowResolver.LOAD_BEST), () -> {
            try {
                controller.workspace().exportBest(Path.of("lattice/topology-search/workspace"));
            } catch (Exception exception) {
                nuklearModel.showErrorDialog("Geometry Discovery", exception.getMessage());
            }
        });
        primaryButton(c, "Browse Archive", workflow.action(
                GeometryDiscoveryWorkflowResolver.PREVIEW_BEST), () -> {
            controller.state().setArchiveExpanded(true);
            controller.state().setSelectedDetailTab(GeometryDiscoveryDetailTab.HISTORY);
        });
        heading(c, "Dynamic Elastic Discovery Results");
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 2);
        resultDegreeFilter = Nuklear.nk_propertyi(c, "# Exact neighbors (0 = all)",
                0, resultDegreeFilter, 3, 1, 1);
        resultNodeFilter = Nuklear.nk_propertyi(c, "# Nodes (0 = all)",
                0, resultNodeFilter, 32, 1, 1);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            resultStatusFilter = nk_label_combo_options(c, stack, "Status",
                    new String[]{"All", "STABLE_CANDIDATE_FOUND",
                            "NO_EQUILIBRIUM_FOUND", "NO_VALID_TOPOLOGY_GENERATED",
                            "IMPOSSIBLE_GRAPH_PARAMETERS", "NUMERICAL_FAILURE"},
                    resultStatusFilter, .35, .65);
            resultRunFilter = nk_label_edit(c, stack, "Date or run ID",
                    resultRunFilterBuffer, resultRunFilter, .35, .65);
        }
        primaryButton(c, "Reload Discovery Results", true, "", () -> {
            try {
                dynamicResultRows = DynamicElasticResultStore.defaults().loadRows();
            } catch (Exception exception) {
                nuklearModel.showErrorDialog("Discovery Results", exception.getMessage());
            }
        });
        if (dynamicResultRows.isEmpty()) {
            try {
                dynamicResultRows = DynamicElasticResultStore.defaults().loadRows();
            } catch (Exception ignored) {
                // The workspace remains usable when the optional result index is malformed.
            }
        }
        dynamicResultRows.stream().filter(row ->
                        (resultDegreeFilter == 0
                                || row.neighborsPerNode() == resultDegreeFilter)
                        && (resultNodeFilter == 0 || row.nodeCount() == resultNodeFilter)
                        && ("All".equals(resultStatusFilter)
                                || row.status().name().equals(resultStatusFilter))
                        && (resultRunFilter.isBlank()
                                || row.runId().contains(resultRunFilter)
                                || row.timestamp().toString().contains(resultRunFilter)))
                .limit(20).forEach(row -> dynamicResultRow(c, row));
    }

    private void dynamicResultRow(NkContext c, DynamicElasticResultRow row) {
        label(c, "K=" + row.neighborsPerNode() + " N=" + row.nodeCount()
                + " " + row.status());
        label(c, String.format("Accuracy %.3f%%; satisfied %d/%d; speed %.5g",
                row.meanDistanceAccuracy() * 100, row.connectionsSatisfied(),
                row.connectionCount(), row.maximumNodeSpeed()));
        label(c, "Iterations " + row.iterations() + "; elapsed "
                + row.elapsedMilliseconds() + " ms; run " + row.runId());
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 3);
        if (Nuklear.nk_button_label(c, "Open Result"))
            nuklearModel.showWarningDialog("Dynamic Elastic Result",
                    "Run: " + row.runId() + "\nStatus: " + row.status()
                            + "\nFile: " + row.resultFile());
        if (Nuklear.nk_button_label(c, "Show Final Geometry"))
            controller.workspace().loadDynamicElasticResult(row.resultFile());
        if (Nuklear.nk_button_label(c, "Open Result File")) {
            try {
                if (!java.awt.Desktop.isDesktopSupported())
                    throw new IllegalStateException("Desktop file opening is unavailable.");
                java.awt.Desktop.getDesktop().open(row.resultFile().toFile());
            } catch (Exception exception) {
                nuklearModel.showErrorDialog("Open Result File",
                        exception.getMessage() + "\n" + row.resultFile());
            }
        }
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 2);
        if (Nuklear.nk_button_label(c, "Export Candidate")) {
            controller.workspace().loadDynamicElasticResult(row.resultFile());
            try {
                controller.workspace().exportCurrent(Path.of(
                        "lattice/candidates/" + row.runId() + "-candidate.toml"));
            } catch (Exception exception) {
                nuklearModel.showErrorDialog("Geometry Discovery", exception.getMessage());
            }
        }
        if (Nuklear.nk_button_label(c, "Compare With Current"))
            controller.workspace().compareDynamicResult(row.resultFile());
    }

    private TopologySearchSettings searchSettings() {
        return controller.searchSettings(searchIterations,
                selectedSearchMethod.equals("Simulated Annealing")
                        ? TopologySearchMode.SIMULATED_ANNEALING
                        : TopologySearchMode.HILL_CLIMBING,
                searchSeed, mutations, minimumDegree, maximumDegree, archiveSize,
                temperature, coolingRate, checkpointInterval);
    }

    private void primaryButton(NkContext c, String label, boolean enabled,
                               String disabledReason, Runnable action) {
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit + 2, 1);
        if (!enabled) Nuklear.nk_widget_disable_begin(c);
        boolean clicked = Nuklear.nk_button_label(c, label);
        if (!enabled) Nuklear.nk_widget_disable_end(c);
        String tooltip = GeometryDiscoveryWorkspaceHelp.PRIMARY_TOOLTIPS.get(label);
        if (!enabled) tooltip(c, disabledReason);
        else if (tooltip != null) tooltip(c, tooltip);
        if (clicked && enabled) action.run();
    }
    private void primaryButton(NkContext c, String label,
                               GeometryDiscoveryActionAvailability availability, Runnable action) {
        primaryButton(c, label, availability.enabled(), availability.disabledReason(), action);
    }
    private void expander(NkContext c, String label, boolean expanded,
                          java.util.function.Consumer<Boolean> setter) {
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 1);
        if (Nuklear.nk_button_label(c, (expanded ? "Hide " : "Show ") + label))
            setter.accept(!expanded);
    }
    private void settingRow(NkContext c, String label, double value,
                            GeometryDiscoverySettings lower, GeometryDiscoverySettings upper) {
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 3);
        if (Nuklear.nk_button_label(c, label + " -"))
            controller.workspace().updateGeometrySettings(lower);
        Nuklear.nk_label(c, String.format("%.5g", value), Nuklear.NK_TEXT_CENTERED);
        if (Nuklear.nk_button_label(c, label + " +"))
            controller.workspace().updateGeometrySettings(upper);
    }
    private void heading(NkContext c, String text) {
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit + 4, 1);
        Nuklear.nk_style_set_font(c, nuklearModel.getDefaultFontBold());
        Nuklear.nk_label(c, text, Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_style_set_font(c, nuklearModel.getDefaultFont());
    }
    private void label(NkContext c, String text) {
        Nuklear.nk_layout_row_dynamic(c, rowHeight, 1);
        Nuklear.nk_label_wrap(c, text);
    }
    private void processCandidateDialog() {
        if (candidateDialog == null) return;
        if (candidateDialog.getConfirmState() == ConfirmState.open) {
            Path path = candidateDialog.getSelectedFilePath().isBlank()
                    ? Path.of(candidateDialog.getDirectory(), candidateDialog.getSelectedFileName())
                    : Path.of(candidateDialog.getSelectedFilePath());
            controller.workspace().loadCandidate(path);
            recentCandidates.remove(path);
            recentCandidates.addFirst(path);
            while (recentCandidates.size() > 5) recentCandidates.removeLast();
            candidateDialog = null;
        } else if (candidateDialog.getConfirmState() == ConfirmState.cancel)
            candidateDialog = null;
    }
    private String metadataVersion() {
        Path source = controller.state().currentSource();
        if (source == null) return "Built-in";
        try {
            TopologySearchMetadata metadata = new TopologySearchMetadataLoader().load(source);
            return metadata == null ? "Candidate 1.0" : "Topology " + metadata.metadataVersion();
        } catch (Exception exception) {
            return "Unavailable";
        }
    }
    private String exampleId(String label) {
        return switch (label) {
            case "Line" -> "line";
            case "Square" -> "square";
            case "Tetrahedron" -> "tetrahedron";
            case "Small Cubic" -> "small-cubic";
            case "Line Reference" -> "line-center";
            case "Equilateral Reference" -> "equilateral";
            case "Tetrahedral Reference" -> "tetrahedral-neighborhood";
            case "Octahedral Reference" -> "octahedral-neighborhood";
            default -> "small-cubic";
        };
    }
    private String profileLabel(TopologyScoringProfile profile) {
        return switch (profile) {
            case GENERAL_GEOMETRY -> "General Geometry";
            case WAVE_PROPAGATION -> "Wave Propagation";
            case CENTROSYMMETRIC -> "Centrosymmetric";
            case TETRAHEDRAL -> "Tetrahedral";
            case CUBIC_RUNTIME -> "Cubic Runtime";
        };
    }
    private TopologyScoringProfile profile(String label) {
        return switch (label) {
            case "Wave Propagation" -> TopologyScoringProfile.WAVE_PROPAGATION;
            case "Centrosymmetric" -> TopologyScoringProfile.CENTROSYMMETRIC;
            case "Tetrahedral" -> TopologyScoringProfile.TETRAHEDRAL;
            case "Cubic Runtime" -> TopologyScoringProfile.CUBIC_RUNTIME;
            default -> TopologyScoringProfile.GENERAL_GEOMETRY;
        };
    }
}
