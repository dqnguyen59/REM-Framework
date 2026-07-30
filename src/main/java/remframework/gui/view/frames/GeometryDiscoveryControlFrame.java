package remframework.gui.view.frames;

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import org.lwjgl.system.MemoryStack;
import remframework.geometry.discovery.*;
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

    public GeometryDiscoveryControlFrame(NuklearModel model) {
        super(model);
        windowOptions = Nuklear.NK_WINDOW_TITLE | Nuklear.NK_WINDOW_BORDER
                | Nuklear.NK_WINDOW_NO_SCROLLBAR;
    }

    @Override public String getTitle() { return "Geometry Discovery Workflow"; }
    @Override public void render(long window, NkContext context) {
        super.render(window, context);
        createLayout(context, 5, nuklearModel.getMenuBarHeight() + 5,
                335, getWindowHeight() - nuklearModel.getMenuBarHeight() - 10);
    }

    @Override protected void layout(NkContext c, int x, int y, int width, int height) {
        processCandidateDialog();
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

    private void candidateSection(NkContext c) {
        GeometryDiscoveryWorkspaceSnapshot snapshot = controller.workspace().snapshot();
        heading(c, "1. Candidate");
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
        label(c, "Model: " + snapshot.geometry().model().name());
        label(c, "Source: " + (controller.state().currentSource() == null
                ? "Built-in example" : controller.state().currentSource()));
        label(c, "Nodes: " + snapshot.geometry().model().nodes().size()
                + "    Connections: " + snapshot.geometry().model().connections().size());
        label(c, "Metadata: " + metadataVersion());
    }

    private void relaxationSection(NkContext c, GeometryDiscoveryWorkflowResolution workflow) {
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
