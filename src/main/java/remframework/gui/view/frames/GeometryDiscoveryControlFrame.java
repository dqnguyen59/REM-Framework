package remframework.gui.view.frames;

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
import remframework.geometry.discovery.experiment.*;
import remframework.geometry.discovery.realization.sequence.*;

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import remframework.helpers.Constants;
import remframework.helpers.PerformanceMonitor;
import remframework.gui.model.AbstractDialogModel.ConfirmState;
import remframework.gui.model.DialogFileModel;
import remframework.gui.model.NuklearModel;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

public final class GeometryDiscoveryControlFrame extends AbstractFrame {
    private static final int WORKFLOW_WINDOW_OPTIONS = Nuklear.NK_WINDOW_TITLE | Nuklear.NK_WINDOW_BORDER;

    public static List<String> primaryAuthoritativeControls(PrimaryParameterMode mode) {
        return switch (mode) {
            case NODE_COUNT -> List.of("Node count N", "Exact degree K");
            case FACE_COUNT -> List.of("Total face count F", "Exact degree K");
            case FACE_COMPOSITION -> List.of("Exact face counts F_m", "Exact degree K");
        };
    }

    public static List<String> primaryDerivedReadOnlyFields(PrimaryParameterMode mode) {
        return switch (mode) {
            case NODE_COUNT -> List.of("Expected edge count E", "Expected face count F");
            case FACE_COUNT -> List.of("Derived node count N", "Derived edge count E");
            case FACE_COMPOSITION -> List.of("Derived total faces F", "Derived edge count E", "Derived node count N");
        };
    }

    public static boolean generationEnabled(ParameterDerivationReport report) {return report != null && report.arithmeticFeasible();}

    public static List<String> profileConditionControls(ExperimentProfile profile) {
        return profile == ExperimentProfile.CLOSED_2D_AREA ? List.of("Global planarity mode", "Polygon angle condition") : List.of(
                "Face planarity condition", "Face-angle condition", "Dihedral-angle condition", "Vertex-radius condition",
                "Face-centroid-radius condition", "Edge-midpoint-radius condition", "Face-plane-radius condition",
                "Normal-center alignment condition", "Center definition", "Face-planarity tolerance", "Absolute regularity tolerance",
                "Relative regularity tolerance");
    }

    private static final String[]                         EXAMPLES                    = {
            "Line", "Square", "Tetrahedron", "Small Cubic",
            "Line Reference", "Equilateral Reference", "Tetrahedral Reference",
            "Octahedral Reference"
    };
    private static final String[]                         PROFILES                    = {
            "General Geometry", "Wave Propagation",
            "Centrosymmetric", "Tetrahedral", "Cubic Runtime"
    };
    private static final String[]                         SEARCH_METHODS              = {"Hill Climbing", "Simulated Annealing"};
    private final        WorkspaceController              controller                  =
            GeometryDiscoveryWorkspaceManager.getInstance();
    private              DialogFileModel                  candidateDialog;
    private final        Deque<Path>                      recentCandidates            = new ArrayDeque<>();
    private              GenerationResult                 terminalPresentationResult;
    private              List<String>                     terminalPresentationLines   = List.of();
    private              String                           selectedExample             = "Small Cubic";
    private              String                           selectedProfile             = "General Geometry";
    private              String                           selectedSearchMethod        = "Hill Climbing";
    private              int                              searchIterations            = 1000;
    private              long                             searchSeed                  = 42;
    private              int                              minimumDegree               = 1;
    private              int                              maximumDegree               = 6;
    private              int                              mutations                   = 1;
    private              int                              archiveSize                 = 20;
    private              double                           temperature                 = 1;
    private              double                           coolingRate                 = .997;
    private              int                              checkpointInterval          = 100;
    private              int                              dynamicNodeCount            = 3;
    private              int                              dynamicExactNeighbors       = 2;
    private              long                             dynamicSeed                 = 42;
    private              List<DynamicElasticResultRow>    dynamicResultRows           = List.of();
    private              int                              resultDegreeFilter;
    private              int                              resultNodeFilter;
    private              String                           resultStatusFilter          = "All";
    private              String                           resultRunFilter             = "";
    private final        StringBuffer                     resultRunFilterBuffer       = new StringBuffer();
    private              String                           dynamicSpeedValidation      = "";
    private              String                           dynamicMaximumValidation    = "";
    private              boolean                          phase2Mode                  = true;
    private              Phase                            phase2Stage                 = Phase.CONFIGURATION_GENERATION;
    private              int                              phase2NodeCount             = 4;
    private              PrimaryParameterMode             phase2PrimaryParameterMode  = PrimaryParameterMode.FACE_COUNT;
    private              int                              phase2FaceCount             = 4;
    private final        java.util.TreeMap<Integer, Long> phase2ExactFaceCounts       = new java.util.TreeMap<>(
            java.util.Map.of(3, 2L, 4, 3L));
    private              String                           phase2PrimarySignature      = "";
    private              int                              phase2ExactDegree           = 2;
    private              GeometryEmbeddingDimension       phase2Dimension             = GeometryEmbeddingDimension.THREE_D;
    private              ExperimentProfile                phase2ExperimentProfile     = ExperimentProfile.CLOSED_3D_VOLUME;
    private              FaceCompositionMode              phase2FaceCompositionMode   = FaceCompositionMode.UNIFORM_FACE_SIZE;
    private              int                              phase2CommonFaceEdgeCount   = 3;
    private              GraphSymmetryFilter              phase2SymmetryFilter        = GraphSymmetryFilter.BOTH;
    private              int                              phase2MaximumConfigurations = 1000;
    private              int                              phase2TimeLimitSeconds      = 30;
    private              boolean                          phase2AutomaticWorkers      = true;
    private              int                              phase2GenerationWorkers     = Math.max(1, Runtime.getRuntime()
                                                                                                           .availableProcessors() - 1);
    private final        PointerBuffer                    phase2ProgressBar           = PointerBuffer.allocateDirect(1);
    private              ConfigurationId                  phase2SelectedConfiguration;
    private              TestResult                       phase2LatestResult;
    private              StoredResult                     phase2StoredResult;
    private              double                           phase2TargetDistance        = 1;
    private              long                             phase2Seed                  = 42;
    private              int                              phase2InitialRealizations   = 1;
    private              int                              phase2SearchLimit           = 100_000;
    private              RealizationStrategy              phase2RealizationStrategy   = RealizationStrategy.MANUAL_RELAXATION;
    private              TopologyInitialization           phase2Initialization        = TopologyInitialization.SPECTRAL_EMBEDDING;
    private              SpectralPresentationMode         phase2SpectralPresentation  = SpectralPresentationMode.INSTANT;
    private              double                           phase2AnimationDuration     = 2, phase2GuidanceStrength = .08;
    private int phase2AnimationFps = 60, phase2GuidanceIterations = 240, phase2HandoverIterations = 120;
    private int phase2ContinuationRampIterations = 200, phase2ContinuationStageLimit = 10_000;
    private ConstraintSequenceMode        phase2SequenceMode   = ConstraintSequenceMode.BUILT_IN_SEQUENCE;
    private ConstraintSequenceEditorModel phase2SequenceEditor = new ConstraintSequenceEditorModel(ConstraintSequencePresets.edgeFirst());
    private int                           phase2SelectedSequenceStage;
    private PlanarityRealizationMode      phase2PlanarityMode  = PlanarityRealizationMode.STRICT_2D_COORDINATES;
    private AngleConditionMode            phase2AngleMode      = AngleConditionMode.REQUIRE_EQUAL_ANGLES;
    private double                        phase2PlaneStrength  = .08, phase2AngleStrength = .01;
    private FacePlanarityMode      phase2FacePlanarity = FacePlanarityMode.REQUIRE_PLANAR_FACES;
    private FaceAngleConditionMode phase2FaceAngles    = FaceAngleConditionMode.MEASURE_ONLY;
    private DihedralConditionMode  phase2Dihedrals     = DihedralConditionMode.MEASURE_ONLY;
    private RegularityMode         phase2VertexRadii   = RegularityMode.MEASURE_ONLY, phase2FaceCentroidRadii = RegularityMode.MEASURE_ONLY,
            phase2EdgeMidpointRadii                    = RegularityMode.MEASURE_ONLY, phase2NormalAlignment = RegularityMode.MEASURE_ONLY;
    private FacePlaneRadiusMode      phase2FacePlaneRadii         = FacePlaneRadiusMode.MEASURE_ONLY;
    private GeometryCenterDefinition phase2CenterDefinition       = GeometryCenterDefinition.VERTEX_CENTROID;
    private BenchmarkPreset          phase2BenchmarkPreset        = BenchmarkPreset.NONE;
    private double                   phase2FacePlanarityTolerance = 1e-7, phase2RegularityTolerance = 1e-6,
            phase2RelativeRegularityTolerance                     = 1e-6;
    private double phase2FacePlanarityStrength = 1, phase2FaceAngleStrength = .6, phase2DihedralStrength = .5, phase2FacePlaneRadiusStrength
                                               = .6;
    private boolean showVertices                                       = true, showEdges = true, showLabels = true,
            showCandidateFaces                                         = true, showBoundaryFaces = true, showRejectedFaces = false,
            showNormals                                                = false, showSelfIntersections = true, showViewportDiagnostics
                                                                       = false,
            showWorldAxes                                              = true, showBoundingBox = true, showCenter = true, showCenterLabel
                                                                       = true,
            showCenterFaceLines, showCenterVertexLines, selectVertices = true, selectEdges = true,
            selectFaces                                                = true, selectCenter = true, selectedNormalOnly, showEdgeLengths;

    public GeometryDiscoveryControlFrame(NuklearModel model) {
        super(model);
        windowOptions = WORKFLOW_WINDOW_OPTIONS;
    }

    public static boolean workflowScrollingEnabled() {return (WORKFLOW_WINDOW_OPTIONS & Nuklear.NK_WINDOW_NO_SCROLLBAR) == 0;}

    @Override
    public String getTitle() {return "Geometry Discovery Workflow";}

    @Override
    public void render(long window, NkContext context) {
        super.render(window, context);
        GeometryDiscoveryWorkspaceLayout.Bounds bounds  = workspaceLayout().workflow();
        long                                    started = System.nanoTime();
        createFixedLayout(context, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        DevelopmentTimingDiagnostics.record("ui.geometry-control-frame", System.nanoTime() - started);
    }

    private GeometryDiscoveryWorkspaceLayout workspaceLayout() {
        return GeometryDiscoveryWorkspaceLayout.calculate(getWindowWidth(), getWindowHeight(),
                                                          nuklearModel.getMenuBarHeight() + 4, Constants.STATUS_BAR_HEIGHT);
    }

    @Override
    protected void layout(NkContext c, int x, int y, int width, int height) {
        processCandidateDialog();
        if (phase2Mode) {
            phasedStageTabs(c);
            if (phase2Stage == Phase.CONFIGURATION_GENERATION)
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
        if (Nuklear.nk_button_label(c, phase2Stage == Phase.CONFIGURATION_GENERATION
                                       ? "> Stage 1: Configuration Generation and Classification"
                                       : "Stage 1: Configuration Generation and Classification"))
            phase2Stage = Phase.CONFIGURATION_GENERATION;
        if (Nuklear.nk_button_label(c, phase2Stage == Phase.GEOMETRY_TEST
                                       ? "> Stage 2: Geometry Test" : "Stage 2: Geometry Test"))
            phase2Stage = Phase.GEOMETRY_TEST;
        if (Nuklear.nk_button_label(c, "Legacy Discovery Tools")) phase2Mode = false;
    }

    private void phase2ConfigurationGeneration(NkContext c) {
        heading(c, "Stage 1 - Configuration Generation and Classification");
        label(c, "Graphs are topology-only. Coordinates, faces, area, and volume are not generated here.");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String primary = nk_label_combo_options(c, stack, "Primary parameters",
                                                    new String[]{"Node count", "Face count", "Face composition"},
                                                    switch (phase2PrimaryParameterMode) {
                                                        case NODE_COUNT -> "Node count";
                                                        case FACE_COUNT -> "Face count";
                                                        case FACE_COMPOSITION -> "Face composition";
                                                    }, .4, .6);
            phase2PrimaryParameterMode = switch (primary) {
                case "Face count" -> PrimaryParameterMode.FACE_COUNT;
                case "Face composition" -> PrimaryParameterMode.FACE_COMPOSITION;
                default -> PrimaryParameterMode.NODE_COUNT;
            };
            String experiment = nk_label_combo_options(c, stack, "Experiment profile",
                                                       new String[]{"Closed 2D area", "Closed 3D volume", "Convex polyhedral volume"},
                                                       switch (phase2ExperimentProfile) {
                                                           case CLOSED_2D_AREA -> "Closed 2D area";
                                                           case CLOSED_3D_VOLUME -> "Closed 3D volume";
                                                           case CONVEX_POLYHEDRAL_VOLUME -> "Convex polyhedral volume";
                                                       }, .4, .6);
            ExperimentProfile selectedExperiment = switch (experiment) {
                case "Closed 2D area" -> ExperimentProfile.CLOSED_2D_AREA;
                case "Convex polyhedral volume" -> ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME;
                default -> ExperimentProfile.CLOSED_3D_VOLUME;
            };
            if (selectedExperiment != phase2ExperimentProfile) {
                phase2ExperimentProfile     = selectedExperiment;
                phase2SelectedConfiguration = null;
                phase2LatestResult          = null;
                phase2StoredResult          = null;
                controller.phasedWorkflow().beginGenerationTransaction();
            }
            phase2Dimension = phase2ExperimentProfile.intendedDimension();
            String composition = nk_label_combo_options(c, stack, "Face composition mode",
                                                        new String[]{"DISCOVER", "UNIFORM_FACE_SIZE"}, phase2FaceCompositionMode.name(), .4,
                                                        .6);
            FaceCompositionMode selectedComposition = FaceCompositionMode.valueOf(composition);
            if (selectedComposition != phase2FaceCompositionMode) {
                phase2FaceCompositionMode   = selectedComposition;
                phase2SelectedConfiguration = null;
                phase2LatestResult          = null;
                phase2StoredResult          = null;
                controller.phasedWorkflow().beginGenerationTransaction();
            }
            String dimension = nk_label_combo_options(c, stack, "Intended dimension",
                                                      new String[]{"2D", "3D"},
                                                      phase2Dimension == GeometryEmbeddingDimension.TWO_D ? "2D" : "3D", .4, .6);
            phase2Dimension = phase2ExperimentProfile.intendedDimension();
            String filter = nk_label_combo_options(c, stack, "Graph symmetry filter",
                                                   new String[]{"Symmetric only", "Asymmetric only", "Both"}, phase2SymmetryFilterLabel(),
                                                   .4, .6);
            phase2SymmetryFilter = switch (filter) {
                case "Symmetric only" -> GraphSymmetryFilter.SYMMETRIC_ONLY;
                case "Asymmetric only" -> GraphSymmetryFilter.ASYMMETRIC_ONLY;
                default -> GraphSymmetryFilter.BOTH;
            };
        }
        if (phase2PrimaryParameterMode == PrimaryParameterMode.NODE_COUNT)
            phase2NodeCount = dynamicInteger(c, "Node count N", phase2NodeCount, 1, 1_000_000);
        else if (phase2PrimaryParameterMode == PrimaryParameterMode.FACE_COUNT)
            phase2FaceCount = dynamicInteger(c, "Total face count F", phase2FaceCount, 1, Integer.MAX_VALUE);
        else {
            for (var entry : List.copyOf(phase2ExactFaceCounts.entrySet())) {
                int size  = dynamicInteger(c, "Face size m", entry.getKey(), 3, Integer.MAX_VALUE);
                int count = dynamicInteger(c, "Exact count F_" + entry.getKey(), Math.toIntExact(entry.getValue()), 0, Integer.MAX_VALUE);
                if (size != entry.getKey()) {
                    phase2ExactFaceCounts.remove(entry.getKey());
                    phase2ExactFaceCounts.put(size, (long) count);
                } else phase2ExactFaceCounts.put(size, (long) count);
            }
            Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 2);
            if (Nuklear.nk_button_label(c, "Add face-count row")) {
                int size = phase2ExactFaceCounts.isEmpty() ? 3 : phase2ExactFaceCounts.lastKey() + 1;
                phase2ExactFaceCounts.put(size, 0L);
            }
            if (Nuklear.nk_button_label(c, "Remove last row") && !phase2ExactFaceCounts.isEmpty()) phase2ExactFaceCounts.pollLastEntry();
        }
        phase2ExactDegree = dynamicInteger(c, "Exact neighbors per node (K)", phase2ExactDegree, 0, Integer.MAX_VALUE);
        if (phase2FaceCompositionMode == FaceCompositionMode.UNIFORM_FACE_SIZE)
            phase2CommonFaceEdgeCount = dynamicInteger(c, "Common face size", phase2CommonFaceEdgeCount, 3, Integer.MAX_VALUE);
        ConfigurationRequest      preview    = phase2Request(null);
        ParameterDerivationReport derivation = preview.parameterDerivation();
        label(c, "Surface topology: " + preview.surfaceTopologyAssumption() + " (chi=" + derivation.eulerCharacteristic() + ")");
        label(c, "Derived N=" + derivation.derivedVertexCount() + " E=" + derivation.derivedEdgeCount() + " F=" +
                 derivation.derivedFaceCount());
        if (derivation.arithmeticFeasible()) label(c, "Selected strategy: " + preview.normalizedGraphRequest().selectedStrategy());
        label(c, derivation.explanation());
        String signature = preview.primaryParameterMode() + ":" + preview.parameterDerivation().authoritativeInputs() + ":" +
                           preview.faceComposition() + ":" + preview.experimentProfile() + ":" + preview.symmetryFilter();
        if (!signature.equals(phase2PrimarySignature)) {
            if (!phase2PrimarySignature.isEmpty()) controller.phasedWorkflow().beginGenerationTransaction();
            phase2PrimarySignature      = signature;
            phase2SelectedConfiguration = null;
            phase2LatestResult          = null;
            phase2StoredResult          = null;
        }
        phase2MaximumConfigurations = dynamicInteger(c, "Maximum configurations", phase2MaximumConfigurations, 1, 100_000);
        phase2TimeLimitSeconds      = dynamicInteger(c, "Enumeration time limit (seconds)", phase2TimeLimitSeconds, 1, 86_400);
        String workerMode = nk_label_combo_options(c, MemoryStack.stackGet(), "Generation workers", new String[]{"AUTO", "Explicit"},
                                                   phase2AutomaticWorkers ? "AUTO" : "Explicit", .45, .55);
        phase2AutomaticWorkers = "AUTO".equals(workerMode);
        if (!phase2AutomaticWorkers) phase2GenerationWorkers = dynamicInteger(c, "Worker count", phase2GenerationWorkers, 1,
                                                                              Math.max(1, Runtime.getRuntime().availableProcessors() * 2));
        boolean generating = controller.phasedWorkflow().generationRunning();
        primaryButton(c, "Generate Configurations", generationEnabled(derivation) && !generating, derivation.explanation(), () -> {
            try {
                phase2SelectedConfiguration = null;
                phase2LatestResult          = null;
                phase2StoredResult          = null;
                ConfigurationRequest request = phase2Request(Path.of("lattice/geometry-discovery/configuration-sets"));
                controller.phasedWorkflow().generateConfigurationsAsync(request, phase2AutomaticWorkers ? GenerationWorkerCount.auto()
                                                                                                        : GenerationWorkerCount.explicit(
                                                                                                                phase2GenerationWorkers));
            }
            catch (Exception exception) {
                nuklearModel.showErrorDialog("Configuration generation", exception.getMessage());
            }
        });
        primaryButton(c, "Cancel Generation", generating, "No generation is active.", controller.phasedWorkflow()::cancelGeneration);
        long               progressStarted = System.nanoTime();
        GenerationProgress live            = controller.phasedWorkflow().generationProgress();
        if (live != null) {
            heading(c, "Generation progress");
            phase2ProgressBar.put(0, live.running() ? Math.floorMod(live.elapsed().toMillis() / 100, 100) : live.cancelled() ? 0 : 100);
            Nuklear.nk_layout_row_dynamic(c, 18, 1);
            Nuklear.nk_progress(c, phase2ProgressBar, 100, false);
            label(c, (live.running() ? "GENERATING" : "GENERATION FINISHED") + " - workers " + live.activeWorkers() + "/" +
                     live.workerCount() + " - " + String.format("%.1f states/s", live.statesPerSecond()));
            label(c, "Elapsed=" + live.elapsed() + " examined=" + live.statesExamined() + " pruned=" + live.statesPruned() + " frontier=" +
                     live.frontierSize());
            label(c, "Completed=" + live.completedCandidates() + " duplicates=" + live.duplicateCandidates() + " retained=" +
                     live.retainedConfigurations());
            label(c, "Limits: " + live.limits());
        }
        DevelopmentTimingDiagnostics.record("ui.generation-progress-poll", System.nanoTime() - progressStarted);
        primaryButton(c, "Reopen Persisted Configuration Set", true, "", () -> {
            try {
                var root = Path.of("lattice/geometry-discovery/configuration-sets");
                try (var sets = java.nio.file.Files.list(root)) {
                    var latest = sets.filter(java.nio.file.Files::isDirectory).sorted().reduce((first, second) -> second).orElseThrow();
                    controller.phasedWorkflow().reopen(latest);
                }
            }
            catch (Exception exception) {
                nuklearModel.showErrorDialog("Configuration set", exception.getMessage());
            }
        });
        GenerationResult generation = controller.phasedWorkflow().generation().orElse(null);
        if (generation == null) {
            label(c, "No configuration set generated.");
            return;
        }
        heading(c, "Generation status");
        terminalPresentation(generation).forEach(line -> label(c, line));
        heading(c, "Retained configurations");
        // Keep the candidate list independently scrollable so detail text never pushes rows away.
        Nuklear.nk_layout_row_dynamic(c, 220, 1);
        if (Nuklear.nk_group_begin(c, "Phase2ConfigurationList", 0)) {
            for (ConfigurationTableRow row : controller.phasedWorkflow().tableRows()) {
                boolean selected = row.configurationId().equals(phase2SelectedConfiguration);
                Nuklear.nk_layout_row_dynamic(c, rowHeightEdit + 4, 1);
                String text = (selected ? "> " : "") + row.configurationId() + "  N=" + row.nodeCount() + " K=" + row.exactDegree()
                              + " edges=" + row.edgeCount() + " aut=" + row.automorphismCount() + " " + row.symmetry()
                              + " reference=" + row.referenceTopology()
                              + " face composition=" + row.faceCompositionMatch()
                              + " profile=" + row.experimentProfile() + " topology=" + row.topologyStatus()
                              + " eligibility=" + row.eligibilityStatus() + " geometry=" + row.geometryTestStatus();
                if (Nuklear.nk_button_label(c, text)) {
                    phase2SelectedConfiguration = row.configurationId();
                    controller.phasedWorkflow().selectForGeometryTest(row.configurationId());
                    phase2LatestResult = null;
                }
            }
            Nuklear.nk_group_end(c);
        }
        if (phase2SelectedConfiguration != null) {
            label(c, PhasedWorkflow.TOPOLOGY_PREVIEW_LABEL);
            Configuration selected = controller.phasedWorkflow().selectedConfigurationRecord().orElse(null);
            if (selected != null) {
                heading(c, "Selected configuration");
                label(c, selected.id() + "  N=" + selected.nodeCount() + " K=" + selected.exactDegree()
                         + " edges=" + selected.edges().size() + " symmetry=" + selected.graphSymmetry()
                         + " automorphisms=" + selected.automorphismGroupSize());
                label(c, "Stage 2: " + selectedGeometrySummary());
            }
            primaryButton(c, "Frame Geometry", true, "", controller::framePhase2Geometry);
            primaryButton(c, "Select for Geometry Test", true, "", () -> {
                controller.phasedWorkflow().selectForGeometryTest(phase2SelectedConfiguration);
                phase2Stage = Phase.GEOMETRY_TEST;
            });
        }
    }

    private List<String> terminalPresentation(GenerationResult generation) {
        if (terminalPresentationResult == generation) return terminalPresentationLines;
        Metrics               metrics  = generation.metrics();
        GenerationDiagnostics strategy = generation.generationDiagnostics();
        List<String>          lines    = new java.util.ArrayList<>();
        lines.add("Status: " + generation.status().code() + " - " + generation.status().summary());
        lines.add("Completion: " + (metrics.enumerationComplete() ? "Complete" : "Incomplete"));
        lines.add("Examined: " + metrics.configurationsExamined() + "    Retained: " + metrics.uniqueConfigurationsRetained() +
                  "    Isomorphic duplicates removed: " + metrics.isomorphicDuplicatesRemoved());
        lines.add("Symmetric: " + metrics.symmetricCount() + "    Asymmetric: " + metrics.asymmetricCount() + "    Elapsed: " +
                  metrics.elapsedMilliseconds() + " ms");
        lines.add("Strategy: " + strategy.strategy() + " construction=" + strategy.constructionMethod() + " completeness=" +
                  strategy.completeness());
        lines.add("Partial states examined=" + strategy.partialStatesExamined() + " pruned=" + strategy.partialStatesPruned() +
                  " completed=" + strategy.completedCandidates() + " duplicates=" + strategy.duplicateCandidates());
        if (!strategy.pruningCounts().isEmpty()) lines.add("Pruning counts: " + strategy.pruningCounts());
        if (!generation.status().mayContinue()) lines.add("Diagnostic: " + generation.status().explanation());
        terminalPresentationResult = generation;
        terminalPresentationLines  = List.copyOf(lines);
        return terminalPresentationLines;
    }

    private ConfigurationRequest phase2Request(Path outputDirectory) {
        FaceComposition constraint = phase2FaceCompositionMode == FaceCompositionMode.UNIFORM_FACE_SIZE
                                     ? FaceComposition.uniform(phase2CommonFaceEdgeCount) : FaceComposition.discover();
        ConfigurationRequest request = switch (phase2PrimaryParameterMode) {
            case NODE_COUNT -> new ConfigurationRequest(phase2NodeCount, phase2ExactDegree, phase2Dimension,
                                                        phase2SymmetryFilter, phase2MaximumConfigurations,
                                                        java.time.Duration.ofSeconds(phase2TimeLimitSeconds),
                                                        null, outputDirectory, phase2ExperimentProfile);
            case FACE_COUNT -> new ConfigurationRequest(0, phase2ExactDegree, phase2Dimension, phase2SymmetryFilter,
                                                        phase2MaximumConfigurations, java.time.Duration.ofSeconds(phase2TimeLimitSeconds),
                                                        null,
                                                        outputDirectory, phase2ExperimentProfile, 10_000, null,
                                                        PrimaryParameterMode.FACE_COUNT,
                                                        null, (long) phase2FaceCount, null, SurfaceTopologyAssumption.SPHERE_LIKE, null);
            case FACE_COMPOSITION -> new ConfigurationRequest(0, phase2ExactDegree, phase2Dimension, phase2SymmetryFilter,
                                                              phase2MaximumConfigurations,
                                                              java.time.Duration.ofSeconds(phase2TimeLimitSeconds), null,
                                                              outputDirectory, phase2ExperimentProfile, 10_000, null,
                                                              PrimaryParameterMode.FACE_COMPOSITION,
                                                              null, null, new ExactFaceCounts(phase2ExactFaceCounts),
                                                              SurfaceTopologyAssumption.SPHERE_LIKE, null);
        };
        return request.withFaceComposition(constraint);
    }

    private void phase2GeometryTest(NkContext c) {
        heading(c, "Stage 2 - Geometry Test");
        Configuration selected = controller.phasedWorkflow().selectedConfigurationRecord().orElse(null);
        if (selected == null) {
            label(c, "Select exactly one configuration in Stage 1 before starting Geometry Test.");
            primaryButton(c, "Return to Configuration Generation", true, "", () -> phase2Stage = Phase.CONFIGURATION_GENERATION);
            return;
        }
        label(c, "Immutable selected configuration: " + selected.id());
        label(c, "Canonical identity: " + selected.canonicalGraphLabel());
        label(c, "N=" + selected.nodeCount() + " K=" + selected.exactDegree()
                 + " edges=" + selected.edges().size() + " graph symmetry=" + selected.graphSymmetry()
                 + " automorphisms=" + selected.automorphismGroupSize());
        label(c, "N and K constrain vertex count and degree only; target geometry is not established.");
        phase2TargetDistance      = dynamicProperty(c, "Target connected-edge length", phase2TargetDistance, 1e-9, 1e9, .1);
        phase2Seed                = dynamicInteger(c, "Random seed", (int) phase2Seed, Integer.MIN_VALUE, Integer.MAX_VALUE);
        phase2InitialRealizations = dynamicInteger(c, "Initial realizations / seeds", phase2InitialRealizations, 1, 100);
        phase2SearchLimit         = dynamicInteger(c, "Geometry search limit", phase2SearchLimit, 1, Integer.MAX_VALUE);
        if (phase2ExperimentProfile == ExperimentProfile.CLOSED_2D_AREA) try (MemoryStack stack = MemoryStack.stackPush()) {
            String plane = nk_label_combo_options(c, stack, "Planarity realization mode",
                                                  new String[]{"Strict 2D coordinates", "Plane constraint", "Measure only"},
                                                  switch (phase2PlanarityMode) {
                                                      case STRICT_2D_COORDINATES -> "Strict 2D coordinates";
                                                      case PLANE_CONSTRAINT -> "Plane constraint";
                                                      case MEASURE_ONLY -> "Measure only";
                                                  }, .4, .6);
            phase2PlanarityMode = switch (plane) {
                case "Plane constraint" -> PlanarityRealizationMode.PLANE_CONSTRAINT;
                case "Measure only" -> PlanarityRealizationMode.MEASURE_ONLY;
                default -> PlanarityRealizationMode.STRICT_2D_COORDINATES;
            };
            String angle = nk_label_combo_options(c, stack, "Angle condition",
                                                  new String[]{"None", "Measure only", "Require equal angles"},
                                                  switch (phase2AngleMode) {
                                                      case NONE -> "None";
                                                      case MEASURE_ONLY -> "Measure only";
                                                      case REQUIRE_EQUAL_ANGLES -> "Require equal angles";
                                                  }, .4, .6);
            phase2AngleMode = switch (angle) {
                case "None" -> AngleConditionMode.NONE;
                case "Require equal angles" -> AngleConditionMode.REQUIRE_EQUAL_ANGLES;
                default -> AngleConditionMode.MEASURE_ONLY;
            };
        }
        if (phase2ExperimentProfile != ExperimentProfile.CLOSED_2D_AREA) try (MemoryStack stack = MemoryStack.stackPush()) {
            String strategyLabel = nk_label_combo_options(c, stack, "Realization strategy",
                                                          new String[]{"Simultaneous", "Sequential continuation", "Manual relaxation"},
                                                          phase2RealizationStrategy == RealizationStrategy.SEQUENTIAL_CONTINUATION
                                                          ? "Sequential continuation"
                                                          : phase2RealizationStrategy == RealizationStrategy.MANUAL_RELAXATION
                                                            ? "Manual relaxation" : "Simultaneous", .45, .55);
            phase2RealizationStrategy = switch (strategyLabel) {
                case "Sequential continuation" -> RealizationStrategy.SEQUENTIAL_CONTINUATION;
                case "Manual relaxation" -> RealizationStrategy.MANUAL_RELAXATION;
                default -> RealizationStrategy.SIMULTANEOUS;
            };
            phase2Initialization      = "Topology spectral embedding".equals(
                    nk_label_combo_options(c, stack, "Initialization", new String[]{"Topology spectral embedding", "Random spherical"},
                                           phase2Initialization == TopologyInitialization.SPECTRAL_EMBEDDING ? "Topology spectral embedding"
                                                                                                             : "Random spherical", .45,
                                           .55)) ? TopologyInitialization.SPECTRAL_EMBEDDING : TopologyInitialization.RANDOM_SPHERICAL;
            label(c, phase2Initialization == TopologyInitialization.SPECTRAL_EMBEDDING
                     ? "Uses only the immutable adjacency eigenspace and target edge length; no reference geometry is consulted."
                     : "Uses the existing deterministic seeded random spherical initializer.");
            if (phase2Initialization == TopologyInitialization.SPECTRAL_EMBEDDING) {
                String behavior = nk_label_combo_options(c, stack, "Spectral behavior",
                                                         new String[]{"Instant", "Animated Display", "Guided Relaxation"},
                                                         switch (phase2SpectralPresentation) {
                                                             case INSTANT -> "Instant";
                                                             case ANIMATED_DISPLAY -> "Animated Display";
                                                             case GUIDED_RELAXATION -> "Guided Relaxation";
                                                         }, .45, .55);
                phase2SpectralPresentation = switch (behavior) {
                    case "Animated Display" -> SpectralPresentationMode.ANIMATED_DISPLAY;
                    case "Guided Relaxation" -> SpectralPresentationMode.GUIDED_RELAXATION;
                    default -> SpectralPresentationMode.INSTANT;
                };
            }
            label(c, phase2RealizationStrategy == RealizationStrategy.SEQUENTIAL_CONTINUATION
                     ? "Introduces required constraint classes one stage at a time. Previously satisfied constraints remain active."
                     : "All required realization targets begin at their configured full strengths.");
            if (phase2RealizationStrategy == RealizationStrategy.SEQUENTIAL_CONTINUATION) {
                phase2ContinuationRampIterations = dynamicInteger(c, "Continuation ramp iterations", phase2ContinuationRampIterations, 1,
                                                                  100_000);
                phase2ContinuationStageLimit     = dynamicInteger(c, "Maximum iterations per stage", phase2ContinuationStageLimit, 1,
                                                                  1_000_000);
                label(c, "Ramp gradually increases a new constraint's restoring influence; it does not change acceptance tolerance.");
                label(c,
                      "Schedule: CLOSED_UNIFORM_FACE_SEQUENTIAL - edges, outward orientation, required face constraints, joint " +
                      "qualification.");
            }
            if (phase2RealizationStrategy == RealizationStrategy.SEQUENTIAL_CONTINUATION) sequenceEditor(c, stack);
            if (phase2RealizationStrategy == RealizationStrategy.MANUAL_RELAXATION) manualRelaxationEditor(c, stack);
            String benchmark = nk_label_combo_options(c, stack, "Benchmark preset",
                                                      new String[]{"None", "Cube emergent benchmark", "Cube constrained benchmark"},
                                                      switch (phase2BenchmarkPreset) {
                                                          case NONE -> "None";
                                                          case CUBE_EMERGENT_BENCHMARK -> "Cube emergent benchmark";
                                                          case CUBE_CONSTRAINED_BENCHMARK -> "Cube constrained benchmark";
                                                      }, .45, .55);
            BenchmarkPreset selectedPreset = switch (benchmark) {
                case "Cube emergent benchmark" -> BenchmarkPreset.CUBE_EMERGENT_BENCHMARK;
                case "Cube constrained benchmark" -> BenchmarkPreset.CUBE_CONSTRAINED_BENCHMARK;
                default -> BenchmarkPreset.NONE;
            };
            if (selectedPreset != phase2BenchmarkPreset) {
                phase2BenchmarkPreset = selectedPreset;
                ThreeDExperimentConditions preset = BenchmarkPresets.conditions(selectedPreset);
                phase2FacePlanarity     = preset.facePlanarity();
                phase2FaceAngles        = preset.faceAngles();
                phase2Dihedrals         = preset.dihedrals();
                phase2VertexRadii       = preset.vertexRadii();
                phase2FaceCentroidRadii = preset.faceCentroidRadii();
                phase2EdgeMidpointRadii = preset.edgeMidpointRadii();
                phase2FacePlaneRadii    = preset.facePlaneRadii();
                phase2NormalAlignment   = preset.normalCenterAlignment();
                if (selectedPreset == BenchmarkPreset.CUBE_CONSTRAINED_BENCHMARK) phase2InitialRealizations
                        = BenchmarkPresets.CONSTRAINED_INITIAL_REALIZATIONS;
            }
            phase2FacePlanarity     = "Measure only".equals(
                    nk_label_combo_options(c, stack, "Face planarity condition", new String[]{"Require planar faces", "Measure only"},
                                           phase2FacePlanarity == FacePlanarityMode.REQUIRE_PLANAR_FACES ? "Require planar faces"
                                                                                                         : "Measure only", .45, .55))
                                      ? FacePlanarityMode.MEASURE_ONLY : FacePlanarityMode.REQUIRE_PLANAR_FACES;
            phase2FaceAngles        = switch (nk_label_combo_options(c, stack, "Face-angle condition",
                                                                     new String[]{"None", "Measure only", "Require equal within each face"},
                                                                     modeLabel(phase2FaceAngles), .45, .55)) {
                case "None" -> FaceAngleConditionMode.NONE;
                case "Require equal within each face" -> FaceAngleConditionMode.REQUIRE_EQUAL_ANGLES_WITHIN_EACH_FACE;
                default -> FaceAngleConditionMode.MEASURE_ONLY;
            };
            phase2Dihedrals         = switch (nk_label_combo_options(c, stack, "Dihedral-angle condition",
                                                                     new String[]{"None", "Measure only", "Require equal dihedrals"},
                                                                     modeLabel(phase2Dihedrals), .45, .55)) {
                case "None" -> DihedralConditionMode.NONE;
                case "Require equal dihedrals" -> DihedralConditionMode.REQUIRE_EQUAL_DIHEDRAL_ANGLES;
                default -> DihedralConditionMode.MEASURE_ONLY;
            };
            phase2VertexRadii       = regularityMode(c, stack, "Vertex-radius condition", phase2VertexRadii);
            phase2FaceCentroidRadii = regularityMode(c, stack, "Face-centroid-radius condition", phase2FaceCentroidRadii);
            phase2EdgeMidpointRadii = regularityMode(c, stack, "Edge-midpoint-radius condition", phase2EdgeMidpointRadii);
            phase2FacePlaneRadii    = switch (nk_label_combo_options(c, stack, "Face-plane-radius condition",
                                                                     new String[]{"None", "Measure only", "Require equal face-plane radii"},
                                                                     modeLabel(phase2FacePlaneRadii), .45, .55)) {
                case "None" -> FacePlaneRadiusMode.NONE;
                case "Require equal face-plane radii" -> FacePlaneRadiusMode.REQUIRE_EQUAL_FACE_PLANE_RADII;
                default -> FacePlaneRadiusMode.MEASURE_ONLY;
            };
            phase2NormalAlignment   = regularityMode(c, stack, "Normal-center alignment", phase2NormalAlignment);
            nk_label_combo_options(c, stack, "Center definition", new String[]{"Vertex centroid"}, "Vertex centroid", .45, .55);
            phase2CenterDefinition = GeometryCenterDefinition.VERTEX_CENTROID;
        }
        if (phase2Initialization == TopologyInitialization.SPECTRAL_EMBEDDING &&
            phase2SpectralPresentation == SpectralPresentationMode.ANIMATED_DISPLAY) {
            phase2AnimationDuration = dynamicProperty(c, "Animation duration (seconds)", phase2AnimationDuration, .1, 60, .1);
            phase2AnimationFps      = dynamicInteger(c, "Animation frames per second", phase2AnimationFps, 1, 240);
            label(c,
                  "DISPLAY ANIMATION: scientific solver remains stopped and iteration stays zero until the preview completes or is " +
                  "skipped.");
        }
        if (phase2Initialization == TopologyInitialization.SPECTRAL_EMBEDDING &&
            phase2SpectralPresentation == SpectralPresentationMode.GUIDED_RELAXATION) {
            phase2GuidanceIterations = dynamicInteger(c, "Guidance iterations", phase2GuidanceIterations, 1, 100_000);
            phase2HandoverIterations = dynamicInteger(c, "Handover iterations", phase2HandoverIterations, 1, 100_000);
            phase2GuidanceStrength   = dynamicProperty(c, "Spectral guidance strength", phase2GuidanceStrength, 1e-6, 1, .001);
            label(c, "SCIENTIFIC RELAXATION: temporary topology-spectral guidance decays to exactly zero before final qualification.");
        }
        if (phase2ExperimentProfile != ExperimentProfile.CLOSED_2D_AREA) {
            heading(c, "Constraint acceptance and influence");
            label(c, "Tolerance changes acceptance; restoring strength changes solver influence. Neither guarantees convergence.");
            if (phase2FacePlanarity == FacePlanarityMode.REQUIRE_PLANAR_FACES) {
                phase2FacePlanarityTolerance = dynamicProperty(c, "Face planarity - tolerance (length)", phase2FacePlanarityTolerance,
                                                               1e-12, 1, .000001);
                phase2FacePlanarityStrength  = dynamicProperty(c, "Face planarity - restoring strength", phase2FacePlanarityStrength, 0, 10,
                                                               .05);
            } else label(c, "Face planarity: MEASURE_ONLY - tolerance and strength not used.");
            boolean sharedRequired = phase2FaceAngles == FaceAngleConditionMode.REQUIRE_EQUAL_ANGLES_WITHIN_EACH_FACE
                                     || phase2Dihedrals == DihedralConditionMode.REQUIRE_EQUAL_DIHEDRAL_ANGLES
                                     || phase2VertexRadii == RegularityMode.REQUIRE_WITHIN_TOLERANCE
                                     || phase2FaceCentroidRadii == RegularityMode.REQUIRE_WITHIN_TOLERANCE
                                     || phase2EdgeMidpointRadii == RegularityMode.REQUIRE_WITHIN_TOLERANCE
                                     || phase2FacePlaneRadii == FacePlaneRadiusMode.REQUIRE_EQUAL_FACE_PLANE_RADII
                                     || phase2NormalAlignment == RegularityMode.REQUIRE_WITHIN_TOLERANCE;
            if (sharedRequired) {
                phase2RegularityTolerance = dynamicProperty(c, "Shared regularity tolerance (absolute)", phase2RegularityTolerance, 1e-12,
                                                            1, .000001);
                label(c, "Applies to required face angles, dihedrals, radius equalities, and normal-center alignment.");
            } else label(c, "Shared regularity tolerance: disabled (no dependent condition is required).");
            if (phase2FacePlaneRadii == FacePlaneRadiusMode.REQUIRE_EQUAL_FACE_PLANE_RADII)
                phase2RelativeRegularityTolerance = dynamicProperty(c, "Shared regularity tolerance (relative)",
                                                                    phase2RelativeRegularityTolerance, 1e-12, 1, .000001);
            if (phase2FaceAngles == FaceAngleConditionMode.REQUIRE_EQUAL_ANGLES_WITHIN_EACH_FACE)
                phase2FaceAngleStrength = dynamicProperty(c, "Face angles - restoring strength", phase2FaceAngleStrength, 0, 10, .05);
            if (phase2Dihedrals == DihedralConditionMode.REQUIRE_EQUAL_DIHEDRAL_ANGLES)
                phase2DihedralStrength = dynamicProperty(c, "Dihedrals - restoring strength", phase2DihedralStrength, 0, 10, .05);
            if (phase2FacePlaneRadii == FacePlaneRadiusMode.REQUIRE_EQUAL_FACE_PLANE_RADII)
                phase2FacePlaneRadiusStrength = dynamicProperty(c, "Face-plane radii - restoring strength", phase2FacePlaneRadiusStrength,
                                                                0, 10, .05);
            label(c, "MEASURE_ONLY properties contribute no corrective force and are not required for convergence.");
            label(c, "Raw error / tolerance is shown live in Current Status; zero error is ideal.");
        }
        if (phase2PlanarityMode == PlanarityRealizationMode.PLANE_CONSTRAINT)
            phase2PlaneStrength = dynamicProperty(c, "Plane-restoring strength", phase2PlaneStrength, 0, 100, .01);
        if (phase2AngleMode == AngleConditionMode.REQUIRE_EQUAL_ANGLES)
            phase2AngleStrength = dynamicProperty(c, "Angle-restoring strength", phase2AngleStrength, 0, 100, .01);
        controller.phasedWorkflow().setVisualizationIterationsPerFrame(
                dynamicInteger(c, "Visualization iterations per frame",
                               controller.phasedWorkflow().visualizationIterationsPerFrame(), 1, 10000));
        controller.phasedWorkflow().setScientificUpdatesPerSecond(
                dynamicInteger(c, "Scientific updates per second", controller.phasedWorkflow().scientificUpdatesPerSecond(), 1, 500));
        label(c, "Initialization and relaxation use existing scientific defaults; canonical angle constraints remain disabled.");
        TestResult result = phase2LatestResult != null ? phase2LatestResult
                                                       : controller.phasedWorkflow().latestGeometryTest(selected.id());
        Stage2InitializationState initialization = controller.phasedWorkflow().stage2InitializationState();
        label(c, initialization == Stage2InitializationState.INITIALIZING ? "INITIALIZING STAGE 2 ..."
                                                                          : "Stage 2 initialization: " + initialization +
                                                                            (initialization == Stage2InitializationState.READY ? " (" +
                                                                                                                                 controller.phasedWorkflow()
                                                                                                                                           .stage2InitializationMillis() +
                                                                                                                                 " ms)"
                                                                                                                               : ""));
        SessionState   sessionState   = controller.phasedWorkflow().geometryTestSessionState();
        ExecutionState executionState = controller.phasedWorkflow().geometryExecutionState();
        primaryButton(c, "Start Geometry Test", executionState != ExecutionState.RUNNING,
                      "The selected session is already active.", () -> {
                    try {
                        if (executionState == ExecutionState.PAUSED) {
                            controller.phasedWorkflow().resumeSelectedGeometryTest();
                        } else {
                            phase2LatestResult = null;
                            controller.phasedWorkflow().startSelectedGeometryTest(
                                    new TestRequest(selected.id(), phase2Dimension, phase2TargetDistance,
                                                    phase2Seed, phase2InitialRealizations, null,
                                                    GeometrySymmetryPolicy.MEASURE_ONLY, phase2SearchLimit,
                                                    phase2ExperimentProfile, phase2AngleMode,
                                                    RegularityMode.MEASURE_ONLY, OptimizationObjective.NONE, 0, 0, 0, 0,
                                                    phase2PlanarityMode, phase2PlaneStrength, phase2AngleStrength,
                                                    new ThreeDExperimentConditions(phase2FacePlanarity, phase2FaceAngles, phase2Dihedrals,
                                                                                   phase2VertexRadii, phase2FaceCentroidRadii,
                                                                                   phase2EdgeMidpointRadii,
                                                                                   phase2FacePlaneRadii, phase2NormalAlignment,
                                                                                   phase2CenterDefinition,
                                                                                   phase2FacePlanarityTolerance, phase2RegularityTolerance,
                                                                                   phase2RelativeRegularityTolerance),
                                                    phase2BenchmarkPreset,
                                                    new ThreeDConstraintStrengths(phase2FacePlanarityStrength, phase2FaceAngleStrength,
                                                                                  phase2DihedralStrength, phase2FacePlaneRadiusStrength),
                                                    phase2FaceCompositionMode == FaceCompositionMode.UNIFORM_FACE_SIZE
                                                    ? FaceComposition.uniform(phase2CommonFaceEdgeCount) : FaceComposition.discover())
                                            .withParameterDerivation(controller.phasedWorkflow().generation().orElseThrow().request())
                                            .withContinuation((phase2RealizationStrategy == RealizationStrategy.SEQUENTIAL_CONTINUATION
                                                               ? phase2SequenceMode == ConstraintSequenceMode.CUSTOM_SEQUENCE
                                                                 ? ContinuationSettings.custom(phase2SequenceEditor.sequence())
                                                                 : ContinuationSettings.sequential(
                                                                         new ThreeDExperimentConditions(phase2FacePlanarity,
                                                                                                        phase2FaceAngles, phase2Dihedrals,
                                                                                                        phase2VertexRadii,
                                                                                                        phase2FaceCentroidRadii,
                                                                                                        phase2EdgeMidpointRadii,
                                                                                                        phase2FacePlaneRadii,
                                                                                                        phase2NormalAlignment,
                                                                                                        phase2CenterDefinition,
                                                                                                        phase2FacePlanarityTolerance,
                                                                                                        phase2RegularityTolerance,
                                                                                                        phase2RelativeRegularityTolerance),
                                                                         100, phase2ContinuationStageLimit,
                                                                         phase2ContinuationRampIterations)
                                                               : phase2RealizationStrategy == RealizationStrategy.MANUAL_RELAXATION
                                                                 ? ContinuationSettings.manual()
                                                                 : ContinuationSettings.simultaneous()).withInitialization(
                                                                                                               phase2Initialization)
                                                                                                       .withSpectralInitialization(
                                                                                                               new SpectralInitializationSettings(
                                                                                                                       phase2SpectralPresentation,
                                                                                                                       phase2AnimationDuration,
                                                                                                                       phase2AnimationFps,
                                                                                                                       phase2GuidanceIterations,
                                                                                                                       phase2HandoverIterations,
                                                                                                                       phase2GuidanceStrength))));
                        }
                    }
                    catch (Exception exception) {
                        nuklearModel.showErrorDialog("Geometry Test", exception.getMessage());
                    }
                });
        primaryButton(c, "Pause", executionState == ExecutionState.RUNNING,
                      "The selected session is not running.", controller.phasedWorkflow()::pauseSelectedGeometryTest);
        primaryButton(c, "Resume", executionState == ExecutionState.PAUSED,
                      "The selected session is not paused.", controller.phasedWorkflow()::resumeSelectedGeometryTest);
        primaryButton(c, "Step", sessionState == SessionState.READY
                                 || sessionState == SessionState.PAUSED,
                      "Pause or initialize the selected session before stepping.", controller.phasedWorkflow()::stepSelectedGeometryTest);
        primaryButton(c, "Stop", executionState == ExecutionState.RUNNING
                                 || executionState == ExecutionState.PAUSED,
                      "The selected session is not active.", controller.phasedWorkflow()::stopSelectedGeometryTest);
        primaryButton(c, "Reset Same Seed", true, "", () -> {
            controller.phasedWorkflow().resetSelectedGeometryTest();
            phase2LatestResult = null;
        });
        primaryButton(c, "Reset New Seed", controller.phasedWorkflow().incrementalSession() != null, "Start a geometry test first.", () -> {
            controller.phasedWorkflow().resetSelectedGeometryTestNewSeed();
            phase2Seed++;
            phase2LatestResult = null;
        });
        IncrementalSession initializationSession = controller.phasedWorkflow().incrementalSession();
        boolean            displayAnimation      = initializationSession != null &&
                                                   initializationSession.initializationSnapshot().phase() ==
                                                   InitializationPhase.DISPLAY_ANIMATION;
        primaryButton(c, "Skip Animation", displayAnimation, "Display animation is not active.",
                      controller.phasedWorkflow()::skipInitializationAnimation);
        primaryButton(c, "Restart Animation", initializationSession != null && initializationSession.initializationSnapshot().mode() ==
                                                                               SpectralPresentationMode.ANIMATED_DISPLAY,
                      "Animated Display is not selected.", controller.phasedWorkflow()::restartInitializationAnimation);
        primaryButton(c, "Save Geometry-Test Result", result != null, "Run Geometry Test before saving a result.", () -> {
            try {
                controller.phasedWorkflow().saveLatestGeometryTest(Path.of("lattice/geometry-discovery/configuration-sets"), selected.id());
            }
            catch (Exception exception) {
                nuklearModel.showErrorDialog("Geometry-test result", exception.getMessage());
            }
        });
        primaryButton(c, "Reopen Stored Geometry-Test Result", true, "", () -> {
            try {
                Path root = Path.of("lattice/geometry-discovery/configuration-sets");
                try (var sets = java.nio.file.Files.walk(root)) {
                    var file = sets.filter(path -> path.getFileName().toString().endsWith(".toml")
                                                   && path.toString().contains("geometry-tests"))
                                   .sorted().reduce((first, second) -> second).orElseThrow();
                    phase2StoredResult = new TestResultStore().load(file);
                    controller.phasedWorkflow().reopenStoredResult(phase2StoredResult);
                    phase2SelectedConfiguration = phase2StoredResult.configurationId();
                    phase2Stage                 = Phase.GEOMETRY_TEST;
                }
            }
            catch (Exception exception) {
                nuklearModel.showErrorDialog("Geometry-test result", exception.getMessage());
            }
        });
        visualizationToggles(c);
        if (result == null) {
            if (phase2StoredResult != null)
                label(c, "Reopened stored result: " + phase2StoredResult.status() + " - " + phase2StoredResult.explanation());
            IncrementalSession incremental = controller.phasedWorkflow().incrementalSession();
            heading(c, "Stage 2 - Realization");
            label(c, "Geometry-test state: " + StatusPresentation.realizationState(incremental));
            label(c, "Live numerical status is shown in Current Status.");
            return;
        }
        phase2ResultSections(c, result);
        if (phase2StoredResult != null)
            label(c, "Reopened stored result: " + phase2StoredResult.status() + " - " + phase2StoredResult.explanation());
    }

    private void sequenceEditor(NkContext c, MemoryStack stack) {
        String mode = nk_label_combo_options(c, stack, "Constraint sequence", new String[]{"Built-in sequence", "Custom sequence"},
                                             phase2SequenceMode == ConstraintSequenceMode.CUSTOM_SEQUENCE ? "Custom sequence"
                                                                                                          : "Built-in sequence", .45, .55);
        phase2SequenceMode = "Custom sequence".equals(mode) ? ConstraintSequenceMode.CUSTOM_SEQUENCE
                                                            : ConstraintSequenceMode.BUILT_IN_SEQUENCE;
        if (phase2SequenceMode != ConstraintSequenceMode.CUSTOM_SEQUENCE) return;
        phase2Initialization       = TopologyInitialization.RANDOM_SPHERICAL;
        phase2SpectralPresentation = SpectralPresentationMode.INSTANT;
        phase2BenchmarkPreset      = BenchmarkPreset.NONE;
        heading(c, "Sequence Editor");
        label(c, "RANDOM_SPHERICAL; spectral coordinates OFF; benchmark reference OFF; topology-specific coordinates OFF.");
        String preset = nk_label_combo_options(c, stack, "Starting hypothesis",
                                               new String[]{"EDGE_FIRST", "ORIENTATION_FIRST", "CONVEXITY_FIRST", "FACE_FIRST"},
                                               phase2SequenceEditor.sequence().name(), .45, .55);
        if (!preset.equals(phase2SequenceEditor.sequence().name())) phase2SequenceEditor = new ConstraintSequenceEditorModel(
                switch (preset) {
                    case "ORIENTATION_FIRST" -> ConstraintSequencePresets.orientationFirst();
                    case "CONVEXITY_FIRST" -> ConstraintSequencePresets.convexityFirst();
                    case "FACE_FIRST" -> ConstraintSequencePresets.faceFirst();
                    default -> ConstraintSequencePresets.edgeFirst();
                });
        List<ConstraintSequenceStage> stages = phase2SequenceEditor.sequence().stages();
        if (phase2SelectedSequenceStage >= stages.size()) phase2SelectedSequenceStage = Math.max(0, stages.size() - 1);
        for (int i = 0; i < stages.size(); i++) {
            ConstraintSequenceStage s      = stages.get(i);
            String                  marker = i == phase2SelectedSequenceStage ? "> " : "";
            if (Nuklear.nk_button_label(c, marker + (i + 1) + ". " + s.displayName() + " | " + s.mode() + " | " + s.activationBehavior() +
                                           " | " + s.rampMode() + "/" + s.rampIterations() + " | max " + s.maximumIterations() + " | tol " +
                                           s.transitionTolerance())) phase2SelectedSequenceStage = i;
        }
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 5);
        if (Nuklear.nk_button_label(c, "Add Stage")) phase2SequenceEditor.add(ConstraintSequenceStage.required(ConstraintId.EDGE_LENGTH));
        if (Nuklear.nk_button_label(c, "Remove Stage") && stages.size() > 1) phase2SequenceEditor.remove(phase2SelectedSequenceStage);
        if (Nuklear.nk_button_label(c, "Move Up")) {
            phase2SequenceEditor.moveUp(phase2SelectedSequenceStage);
            phase2SelectedSequenceStage = Math.max(0, phase2SelectedSequenceStage - 1);
        }
        if (Nuklear.nk_button_label(c, "Move Down")) {
            phase2SequenceEditor.moveDown(phase2SelectedSequenceStage);
            phase2SelectedSequenceStage = Math.min(stages.size() - 1, phase2SelectedSequenceStage + 1);
        }
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 5);
        if (Nuklear.nk_button_label(c, "Duplicate Stage")) phase2SequenceEditor.duplicate(phase2SelectedSequenceStage);
        ConstraintSequenceStage selected = phase2SequenceEditor.sequence().stages().get(
                Math.max(0, Math.min(phase2SelectedSequenceStage, phase2SequenceEditor.sequence().stages().size() - 1)));
        if (Nuklear.nk_button_label(c, selected.enabled() ? "Disable" : "Enable")) phase2SequenceEditor.setEnabled(
                phase2SelectedSequenceStage, !selected.enabled());
        if (Nuklear.nk_button_label(c, "Reset to Built-In")) {
            phase2SequenceMode   = ConstraintSequenceMode.BUILT_IN_SEQUENCE;
            phase2SequenceEditor = new ConstraintSequenceEditorModel(ConstraintSequencePresets.edgeFirst());
        }
        if (Nuklear.nk_button_label(c, "Save Sequence")) try {
            new ConstraintSequenceStore().save(sequenceFile(), phase2SequenceEditor.sequence());
        }
        catch (Exception failure) {
            nuklearModel.showErrorDialog("Constraint Sequence", failure.getMessage());
        }
        if (Nuklear.nk_button_label(c, "Reopen Sequence")) try {
            phase2SequenceEditor = new ConstraintSequenceEditorModel(new ConstraintSequenceStore().load(sequenceFile()));
        }
        catch (Exception failure) {
            nuklearModel.showErrorDialog("Constraint Sequence", failure.getMessage());
        }
        for (ConstraintSequenceIssue issue : new ConstraintSequenceValidator().validate(phase2SequenceEditor.sequence()))
            label(c, issue.severity() + ": " + issue.explanation());
        label(c,
              "Selected details: ID=" + selected.stageId() + ", constraint=" + selected.constraintId() + ", enabled=" + selected.enabled() +
              ", mode=" + selected.mode() + ", activation=" + selected.activationBehavior() + ", strength=" +
              selected.finalRestoringStrength() + ", ramp=" + selected.rampMode() + "/" + selected.rampIterations() + ", tolerance=" +
              selected.transitionTolerance() + ", stable=" + selected.stableIterations() + ", maximum=" + selected.maximumIterations() +
              ", stagnation=" + selected.stagnationPolicy() + ", severe regression=" + selected.severeRegressionThreshold() +
              ", rollback=" + selected.rollbackPolicy() + ", notes=" + selected.notes());
        if (selected.activationBehavior() == ConstraintActivationBehavior.STAGE_ONLY) label(c,
                                                                                            "EXPERIMENTAL: STAGE_ONLY does not retain " +
                                                                                            "earlier required constraints.");
    }

    private Path sequenceFile() {
        return Path.of("lattice/geometry-discovery/constraint-sequences", phase2SequenceEditor.sequence().sequenceId() + "-v" +
                                                                          phase2SequenceEditor.sequence().version() + ".properties");
    }

    private void manualRelaxationEditor(NkContext c, MemoryStack stack) {
        phase2Initialization       = TopologyInitialization.RANDOM_SPHERICAL;
        phase2SpectralPresentation = SpectralPresentationMode.INSTANT;
        heading(c, "Manual Relaxation");
        label(c, "Experimental direct force control. Coordinates are preserved when force sets change; no continuation schedule is used.");
        label(c, "Initialization: RANDOM_SPHERICAL; spectral guidance OFF.");
        IncrementalSession session = controller.phasedWorkflow().incrementalSession();
        if (session == null) {
            label(c, "Start Geometry Test to initialize with all corrective forces OFF.");
            return;
        }
        label(c, "Execution: " + manualExecutionLabel(session.manualExecutionState()) + "    Active relaxation forces: " +
                 session.manualActiveForces().size());
        label(c, "Velocity policy: " + manualVelocityLabel(session.manualVelocityPolicy()));
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 3);
        if (Nuklear.nk_button_label(c, "Turn All Off")) session.turnAllManualRelaxationOff();
        if (Nuklear.nk_button_label(c, "Turn All On")) session.turnAllManualRelaxationOn();
        if (Nuklear.nk_button_label(c, "Reset Toggles")) session.turnAllManualRelaxationOff();
        if (Nuklear.nk_button_label(c, "Reset All Factors")) session.resetAllManualStrengthFactors();
        if (Nuklear.nk_button_label(c, "Save Force Set")) try {
            new ManualRelaxationStore().saveForceSetWithFactors(manualForceSetFile(), session.manualActiveForces(),
                                                                session.constraintStrengthFactors());
        }
        catch (Exception failure) {
            nuklearModel.showErrorDialog("Manual force set", failure.getMessage());
        }
        if (Nuklear.nk_button_label(c, "Load Force Set")) try {
            ManualRelaxationStore store = new ManualRelaxationStore();
            session.setManualActiveForceSet(store.loadForceSet(manualForceSetFile()));
            session.setManualStrengthFactors(store.loadStrengthFactors(manualForceSetFile()));
        }
        catch (Exception failure) {
            nuklearModel.showErrorDialog("Manual force set", failure.getMessage());
        }
        String policy = nk_label_combo_options(c, stack, "Velocity policy", new String[]{"Reset on force change", "Preserve velocity"},
                                               session.manualVelocityPolicy() == ManualVelocityPolicy.PRESERVE_VELOCITY_ON_FORCE_CHANGE
                                               ? "Preserve velocity" : "Reset on force change", .45, .55);
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 3);
        session.setManualVelocityPolicy("Preserve velocity".equals(policy) ? ManualVelocityPolicy.PRESERVE_VELOCITY_ON_FORCE_CHANGE
                                                                           : ManualVelocityPolicy.RESET_VELOCITY_ON_FORCE_CHANGE);
        if (Nuklear.nk_button_label(c, "Save Snapshot")) session.saveManualSnapshot("manual-" + session.manualSnapshots().size());
        if (Nuklear.nk_button_label(c, "Restore Snapshot") && !session.manualSnapshots().isEmpty()) session.restoreManualSnapshot(
                session.manualSnapshots().getLast().snapshotId(), false);
        if (Nuklear.nk_button_label(c, "Delete Snapshot") && !session.manualSnapshots().isEmpty()) session.deleteManualSnapshot(
                session.manualSnapshots().getLast().snapshotId());
        if (Nuklear.nk_button_label(c, "Evaluate Final Qualification")) session.evaluateManualFinalQualification();
        heading(c, "Experiment Recording");
        label(c, session.manualRecording() ? "Recording ACTIVE" : "Recording stopped");

        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 4);
        if (Nuklear.nk_button_label(c, "Start Recording")) session.startManualRecording("manual-recording");
        if (Nuklear.nk_button_label(c, "Stop Recording")) session.stopManualRecording("manual-recording");
        if (Nuklear.nk_button_label(c, "Replay Recording") && session.lastManualRecording() != null) session.replayManualRecording(
                session.lastManualRecording());
        if (Nuklear.nk_button_label(c, "Save as Candidate Strategy") &&
            session.lastManualRecording() != null) session.saveAsCandidateStrategy("MANUAL_DISCOVERED_STRATEGY_CANDIDATE");
        heading(c, "Shape Construction");
        manualRows(c, session, List.of(ManualConstraint.EDGE_LENGTH, ManualConstraint.FACE_ORIENTATION, ManualConstraint.CONVEXITY,
                                       ManualConstraint.FACE_PLANARITY, ManualConstraint.FACE_ANGLE, ManualConstraint.FACE_DISTANCE,
                                       ManualConstraint.NORMAL_ALIGNMENT, ManualConstraint.FACE_AREA));
        heading(c, "Validation Assistance");
        manualRows(c, session, List.of(ManualConstraint.SELF_INTERSECTION_AVOIDANCE));
        heading(c, "Regularity");
        manualRows(c, session,
                   List.of(ManualConstraint.DIHEDRAL_ANGLE, ManualConstraint.VERTEX_DISTANCE, ManualConstraint.FACE_CENTER_DISTANCE,
                           ManualConstraint.EDGE_CENTER_DISTANCE));
    }

    private void manualRows(NkContext c, IncrementalSession session, List<ManualConstraint> constraints) {
        for (ManualConstraint constraint : constraints) {
            ManualConstraintState state = session.manualConstraintStates().stream().filter(s -> s.constraint() == constraint).findFirst()
                                                 .orElseThrow();
            Nuklear.nk_layout_row_begin(c, Nuklear.NK_DYNAMIC, rowHeightEdit + 2, 3);
            Nuklear.nk_layout_row_push(c, .58f);
            Nuklear.nk_label(c, constraint.displayName(), Nuklear.NK_TEXT_LEFT);
            Nuklear.nk_layout_row_push(c, .20f);
            if (Nuklear.nk_button_label(c, state.relaxationActive() ? "ON" : "OFF")) session.setManualRelaxation(constraint,
                                                                                                                 !state.relaxationActive());
            Nuklear.nk_layout_row_push(c, .22f);
            if (Nuklear.nk_button_label(c, state.requiredForFinalQualification() ? "REQ YES" : "REQ NO")) session.setManualFinalRequirement(
                    constraint, !state.requiredForFinalQualification());
            Nuklear.nk_layout_row_end(c);
            ConstraintStrengthFactor factor = session.constraintStrengthFactor(constraint);
            if (factor.correctiveForceImplemented()) {
                Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 3);
                Nuklear.nk_label(c, String.format(java.util.Locale.ROOT, "Base %.6g", factor.baseStrength()), Nuklear.NK_TEXT_LEFT);
                Nuklear.nk_label(c, String.format(java.util.Locale.ROOT, "Effective %.6g", factor.effectiveStrength()),
                                 Nuklear.NK_TEXT_LEFT);
                double updated = dynamicProperty(c, "Factor (experimental multiplier)", factor.runtimeFactor(), 0, 100, .1);
                if (Math.abs(updated - factor.runtimeFactor()) > 1e-12) session.setManualStrengthFactor(constraint, updated);
                Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 4);
                for (double preset : new double[]{0, .1, .25, .5, 1, 2, 5, 10, 25, 100})
                    if (Nuklear.nk_button_label(c, String.format(java.util.Locale.ROOT, "%.2gx", preset))) session.setManualStrengthFactor(
                            constraint, preset);
                label(c,
                      "Base strength is the accepted configured restoring strength. Factor is an experimental runtime multiplier. " +
                      "Effective = base * factor.");
            } else label(c, "Strength unavailable - measurement only");
            var m = state.measurement();
            label(c, m == null || m.rawValue() == null ? constraint.displayName() + " | " +
                                                         (m == null ? "measurement unavailable" : m.state().name().replace('_', ' ')) +
                                                         " | " + (m == null ? "" : m.reasonText()) : String.format(java.util.Locale.ROOT,
                                                                                                                   "Error %.7g   " +
                                                                                                                   "Tolerance %.7g   " +
                                                                                                                   "Ratio %.7g   %s",
                                                                                                                   m.rawValue(),
                                                                                                                   state.tolerance(),
                                                                                                                   state.normalizedRatio(),
                                                                                                                   state.status()));
        }
    }

    private static String manualExecutionLabel(ManualExecutionState state) {
        return switch (state) {
            case MANUAL_IDLE -> "Idle";
            case MANUAL_RELAXING -> "Relaxing";
            case MANUAL_PAUSED -> "Paused";
        };
    }

    private static String manualVelocityLabel(ManualVelocityPolicy policy) {
        return policy == ManualVelocityPolicy.PRESERVE_VELOCITY_ON_FORCE_CHANGE ? "Preserve velocity" : "Reset on force change";
    }

    private Path manualForceSetFile() {return Path.of("lattice/geometry-discovery/manual-force-set.properties");}

    private void phase2ResultSections(NkContext c, TestResult result) {
        heading(c, "Eligibility");
        label(c, "Benchmark preset: " + result.benchmarkPreset());
        if (result.benchmarkPreset() != BenchmarkPreset.NONE) label(c, "Required exact CUBE_GRAPH topology: " +
                                                                       (result.benchmarkTopologyRequirementPassed() ? "PASS" : "FAIL"));
        label(c, "Profile: " + result.experimentProfile() + "    Stage 2 eligibility: " + result.eligibility().status());
        label(c, "Reason: " + result.eligibility().conclusion());
        result.eligibility().conditions().forEach(
                condition -> label(c, condition.condition() + ": " + condition.outcome() + " - " + condition.explanation()));
        heading(c, "Stage 2 - Realization");
        label(c, (result.realization().converged() ? "EQUAL_EDGE_REALIZATION_FOUND" : result.realization().status()) + " - converged=" +
                 result.realization().converged()
                 + ", effective dimension=" + result.realization().effectiveDimension());
        label(c, "Target geometry: Not established");
        label(c, "Geometry-test state: " + StatusPresentation.realizationState(controller.phasedWorkflow().incrementalSession()));
        label(c, "Equal-edge realization: " + StatusPresentation.equalEdgeConclusion(
                controller.phasedWorkflow().incrementalSession(), result));
        heading(c, "Face derivation");
        label(c, result.faceDerivation() == null ? "Not run" : result.faceDerivation().status().code() + " - unique=" +
                                                               result.faceDerivation().unique());
        heading(c, "Boundary validation");
        label(c, result.boundary() == null ? "Not run"
                                           : result.boundary().status().code() + " - closed=" + result.boundary().closed() + " - " +
                                             result.boundary().status().explanation());
        heading(c, "Measurement");
        primaryButton(c, "Calculate Area", result.boundary() != null && result.boundary().closed(),
                      "Area requires a validated unique closed boundary.", () -> {});
        primaryButton(c, "Calculate Volume",
                      result.boundary() != null && result.boundary().closed() && phase2Dimension == GeometryEmbeddingDimension.THREE_D,
                      "Volume requires a validated unique closed 3D boundary.", () -> {});
        label(c, "Area: " + (result.area().available() ? result.area().absoluteArea() : "unavailable") + "    Volume: " +
                 (result.volume().available() ? result.volume().absoluteVolume() : "unavailable"));
        if (result.twoDRegularity() != null) {
            heading(c, "2D Regularity");
            var m = result.twoDRegularity();
            label(c, "edge variance=" + m.edgeLengths().variance() + " angle variance=" + m.interiorAngles().variance() +
                     " centroid-radius variance=" + m.centroidRadii().variance());
            label(c, "perimeter=" + m.perimeter() + " circularity=4*pi*A/P^2=" + m.circularity());
        }
        if (result.threeDRegularity() != null) {
            heading(c, "3D Regularity");
            var m = result.threeDRegularity();
            label(c,
                  "edge variance=" + m.edgeLengths().variance() + " interior-dihedral variance=" + m.interiorDihedralAngles().variance());
            label(c, "vertex-radius variance=" + m.vertexRadii().variance() + " face-center-radius variance=" +
                     m.faceCenterRadii().variance() + " edge-midpoint-radius variance=" + m.edgeMidpointRadii().variance());
        }
        heading(c, "Optimization");
        boolean optimizationAllowed = result.boundary() != null && result.boundary().closed() &&
                                      (result.area().available() || result.volume().available());
        primaryButton(c, "Run Optimization", optimizationAllowed, "Optimization requires a valid closed boundary and baseline measurement.",
                      () -> {
                          try {
                              phase2LatestResult = controller.phasedWorkflow().optimizeSelectedGeometryTest(phase2TargetDistance,
                                                                                                            phase2SearchLimit);
                          }
                          catch (Exception exception) {
                              nuklearModel.showErrorDialog("Optimization", exception.getMessage());
                          }
                      });
        label(c, result.optimization().attempted() ? "Baseline measure: " + result.optimization().baselineMeasure()
                                                     + "    Best result found within optimization limits: " +
                                                     result.optimization().optimizedMeasure()
                                                     + "    Improvement: " + result.optimization().relativeImprovement()
                                                     + "    Iterations: " + result.optimization().iterationCount()
                                                   : "Optimization has not run. It remains separate from realization and measurement.");
        label(c, "Optimization result: " + result.optimization().summary());
        label(c, "Final status: " + result.status().code() + " - " + result.status().explanation());
        if (!result.status().mayContinue()) label(c, "Failure diagnostic: " + result.status().summary());
        heading(c, "Candidate Eligibility");
        label(c, "Equal-edge realization found: " + yesNo(result.realization() != null && result.realization().converged()));
        label(c, "Faces derived: " + yesNo(result.faceDerivation() != null && !result.faceDerivation().faces().isEmpty()));
        label(c, "Unique face set: " + yesNo(result.faceDerivation() != null && result.faceDerivation().unique()));
        label(c, "Closed boundary: " + yesNo(result.boundary() != null && result.boundary().closed()));
        label(c, "Manifold boundary: " + yesNo(result.boundary() != null && result.boundary().manifold()));
        label(c, "Area available: " + yesNo(result.area().available()));
        label(c, "Volume available: " + yesNo(result.volume().available()));
        label(c, "Valid geometry candidate: " + yesNo(result.status().code() == GeometryConfigurationStatusCode.VALID_CANDIDATE));
        label(c, "Non-manifold boundary means the proposed surface fails manifold edge-incidence rules.");
        label(c, "Valid candidate requires all selected realization, boundary, and measurement requirements.");
    }

    private void phase2ConfigurationSummary(NkContext c, Configuration selected) {
        heading(c, "Stage 1 - Configuration");
        TopologyInspection topology = TopologyInspection.from(selected);
        label(c, "Configuration ID: " + selected.id());
        label(c, "Status: " + StatusPresentation.configurationSummary(selected.status()));
        label(c, "Vertices N=" + topology.vertexCount() + "    Regular degree K=" + topology.expectedDegree()
                 + "    Edges E=" + topology.edgeCount() + " (E = N*K/2 = "
                 + (topology.vertexCount() * topology.expectedDegree() / 2) + ")");
        label(c, "Connected: " + yesNo(selected.connected()) + "    Simple graph: " + yesNo(topology.simpleGraph()) + "    Regular: "
                 + yesNo(topology.degreeMismatches().isEmpty()));
        label(c, "Canonical encoding: " + selected.canonicalGraphLabel());
        label(c, "Graph symmetry: " + selected.graphSymmetry()
                 + " (automorphisms=" + selected.automorphismGroupSize() + ")");
        label(c, "Vertex-transitive: " + yesNo(selected.graphSymmetry() == GraphSymmetryClassification.HIGHLY_SYMMETRIC)
                 + "    Enumeration: " + (selected.enumerationComplete() ? "Complete" : "Incomplete"));
        label(c, "Graph symmetry describes automorphisms, not geometric symmetry.");
        label(c, "Vertex-transitive means graph automorphisms can move every vertex to every other vertex.");
    }

    private void phase2TopologyInspection(NkContext c, Configuration selected) {
        heading(c, "Topology inspection");
        TopologyInspection topology = TopologyInspection.from(selected);
        label(c, "Vertices: " + topology.vertexCount() + "    Connections / edges: " + topology.edgeCount()
                 + "    Expected regular degree: " + topology.expectedDegree());
        label(c, "Adjacency symmetric: " + yesNo(topology.adjacencySymmetric())
                 + "    Undirected edge list unique: Yes");
        for (int i = 0; i < topology.adjacencyLines().size(); i++) {
            String suffix = topology.degreeMismatches().contains(i) ? "  <-- degree mismatch" : "";
            label(c, topology.adjacencyLines().get(i) + suffix);
        }
        label(c, "Edge list:");
        for (String edge : topology.edgeLines()) label(c, edge);
    }

    private static String yesNo(boolean value) {return value ? "Yes" : "No";}

    private String selectedGeometrySummary() {
        IncrementalSession session = controller.phasedWorkflow().incrementalSession();
        TestResult result = phase2LatestResult != null ? phase2LatestResult
                                                       : phase2SelectedConfiguration == null ? null
                                                                                             : controller.phasedWorkflow()
                                                                                                         .latestGeometryTest(
                                                                                                                 phase2SelectedConfiguration);
        return StatusPresentation.equalEdgeConclusion(session, result);
    }

    private void visualizationToggles(NkContext c) {
        heading(c, "Visualization");
        showVertices = nk_check_label(c, "Vertices", showVertices, .65, .35);
        showEdges    = nk_check_label(c, "Edges", showEdges, .65, .35);
        showLabels   = nk_check_label(c, "Labels", showLabels, .65, .35);
        controller.viewState().setShowConnections(showEdges);
        controller.viewState().setShowNodeLabels(showLabels);
        controller.viewState().setShowVertices(showVertices);
        showCandidateFaces = nk_check_label(c, "Candidate faces", showCandidateFaces, .65, .35);
        showBoundaryFaces  = nk_check_label(c, "Accepted boundary faces", showBoundaryFaces, .65, .35);
        showRejectedFaces  = nk_check_label(c, "Rejected faces", showRejectedFaces, .65, .35);
        showNormals        = nk_check_label(c, "Show All Face Normals", showNormals, .65, .35);
        selectedNormalOnly = nk_check_label(c, "Show Selected Face Normal Only", selectedNormalOnly, .65, .35);
        controller.viewState().setShowDerivedFaces(showCandidateFaces);
        controller.viewState().setShowAcceptedFaces(showBoundaryFaces);
        controller.viewState().setShowRejectedFaces(showRejectedFaces);
        controller.viewState().setShowFaceNormals(showNormals);
        controller.viewState().setShowSelectedFaceNormalOnly(selectedNormalOnly);
        showCenter            = nk_check_label(c, "Show Geometry Center", showCenter, .65, .35);
        showCenterLabel       = nk_check_label(c, "Show Center Label", showCenterLabel, .65, .35);
        showCenterFaceLines   = nk_check_label(c, "Show Center-to-Face Lines", showCenterFaceLines, .65, .35);
        showCenterVertexLines = nk_check_label(c, "Show Center-to-Vertex Lines", showCenterVertexLines, .65, .35);
        controller.viewState().setShowGeometryCenter(showCenter);
        controller.viewState().setShowCenterLabel(showCenterLabel);
        controller.viewState().setShowCenterToFaceLines(showCenterFaceLines);
        controller.viewState().setShowCenterToVertexLines(showCenterVertexLines);
        heading(c, "Viewport Selection");
        selectVertices = nk_check_label(c, "Select vertices", selectVertices, .65, .35);
        selectEdges    = nk_check_label(c, "Select edges", selectEdges, .65, .35);
        selectFaces    = nk_check_label(c, "Select faces", selectFaces, .65, .35);
        selectCenter   = nk_check_label(c, "Select center", selectCenter, .65, .35);
        controller.viewState().setSelectionFilters(new SelectionFilters(selectVertices, selectEdges, selectFaces, selectCenter));
        showEdgeLengths = nk_check_label(c, "Show Edge Lengths", showEdgeLengths, .65, .35);
        controller.viewState().setShowEdgeLengths(showEdgeLengths);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String trail = nk_label_combo_options(c, stack, "Initialization trails",
                                                  new String[]{"Off", "Short", "Full initialization path"},
                                                  switch (controller.viewState().initializationTrailMode()) {
                                                      case OFF -> "Off";
                                                      case SHORT -> "Short";
                                                      case FULL -> "Full initialization path";
                                                  }, .45, .55);
            controller.viewState().setInitializationTrailMode(switch (trail) {
                case "Short" -> InitializationTrailMode.SHORT;
                case "Full initialization path" -> InitializationTrailMode.FULL;
                default -> InitializationTrailMode.OFF;
            });
        }
        showSelfIntersections   = nk_check_label(c, "Detected self-intersections", showSelfIntersections, .65, .35);
        showViewportDiagnostics = nk_check_label(c, "Viewport diagnostics", showViewportDiagnostics, .65, .35);
        showWorldAxes           = nk_check_label(c, "World axes", showWorldAxes, .65, .35);
        showBoundingBox         = nk_check_label(c, "Geometry bounding box", showBoundingBox, .65, .35);
        controller.viewState().setShowAxes(showWorldAxes);
        controller.viewState().setShowBoundingBox(showBoundingBox);
        if (showViewportDiagnostics && controller.renderDiagnostics() != null) {
            RenderDiagnostics d = controller.renderDiagnostics();
            label(c, "Render model " + d.configurationId() + ": vertices=" + d.renderedVertexCount()
                     + " edges=" + d.renderedEdgeCount() + " scale=" + d.displayScale());
            label(c, "Centroid=" + d.geometryCentroid() + " radius=" + d.geometryBoundingRadius());
            label(c, "Camera=" + d.cameraPosition() + " target=" + d.cameraTarget()
                     + " distance=" + d.cameraDistance());
            label(c, "Clip near=" + d.nearPlane() + " far=" + d.farPlane()
                     + " intersectsFrustum=" + d.geometryIntersectsFrustum());
            var timing = DevelopmentTimingDiagnostics.snapshot();
            label(c, String.format("UI frame %.2f ms; OpenGL %.2f ms; workflow panel %.2f ms; topology preview %.2f ms",
                                   PerformanceMonitor.getMeasurement(PerformanceMonitor.Measurement.gui2D),
                                   averageTiming(timing, "viewport.opengl-render"),
                                   averageTiming(timing, "ui.geometry-control-frame"),
                                   averageTiming(timing, "viewport.topology-preview-construction")));
            GenerationProgress progress = controller.phasedWorkflow().generationProgress();
            label(c, "Stage 1 workers=" + (progress == null ? 0 : progress.activeWorkers())
                     + "; Stage 2 worker=" + controller.phasedWorkflow().geometryExecutionState());
        }
    }

    private static double averageTiming(java.util.Map<String, DevelopmentTimingDiagnostics.Timing> timing, String key) {
        var value = timing.get(key);
        return value == null ? 0 : value.averageMillis();
    }

    private String phase2SymmetryFilterLabel() {
        return switch (phase2SymmetryFilter) {
            case SYMMETRIC_ONLY -> "Symmetric only";
            case ASYMMETRIC_ONLY -> "Asymmetric only";
            case BOTH -> "Both";
        };
    }

    private RegularityMode regularityMode(NkContext c, MemoryStack stack, String label, RegularityMode current) {
        String value = nk_label_combo_options(c, stack, label, new String[]{"None", "Measure only", "Require within tolerance"},
                                              modeLabel(current), .45, .55);
        return switch (value) {
            case "None" -> RegularityMode.NONE;
            case "Require within tolerance" -> RegularityMode.REQUIRE_WITHIN_TOLERANCE;
            default -> RegularityMode.MEASURE_ONLY;
        };
    }

    private String modeLabel(Object mode) {
        String name = ((Enum<?>) mode).name();
        if (name.equals("NONE")) return "None";
        if (name.equals("MEASURE_ONLY")) return "Measure only";
        if (name.contains("FACE_PLANE")) return "Require equal face-plane radii";
        if (name.contains("WITHIN_EACH_FACE")) return "Require equal within each face";
        if (name.contains("DIHEDRAL")) return "Require equal dihedrals";
        return "Require within tolerance";
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
                    }
                    catch (Exception exception) {
                        nuklearModel.showErrorDialog("Geometry Discovery", exception.getMessage());
                    }
                });
        primaryButton(c, "Load Experiment Configuration", true, "", () -> {
            try {
                controller.workspace().loadExperimentConfiguration(Path.of(
                        "lattice/experiments/discovery-experiment.properties"));
            }
            catch (Exception exception) {
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
        GeometryDiscoveryResult            geometry = snapshot.geometry();
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
        DynamicElasticMetrics  metrics  = controller.workspace().dynamicElasticMetrics();
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
            spatial  = new DynamicElasticSpatialSettings(overlap,
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

    private void dynamicPlaybackControls(
            NkContext c, DynamicElasticMetrics metrics,
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

    private double dynamicProperty(
            NkContext c, String label, double value,
            double minimum, double maximum, double step) {
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 1);
        return Nuklear.nk_propertyd(c, label, minimum, value, maximum, step,
                                    (float) Math.max(1e-9, step * .1));
    }

    private int dynamicInteger(
            NkContext c, String label, int value,
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
                                                     new String[]{
                                                             "Every Iteration", "Accepted Mutation",
                                                             "New Best Candidate", "Timed Snapshot"
                                                     },
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
            case "New Best Candidate" -> DiscoveryVisualizationUpdateTrigger.NEW_BEST_CANDIDATE;
            case "Timed Snapshot" -> DiscoveryVisualizationUpdateTrigger.TIMED_SNAPSHOT;
            default -> DiscoveryVisualizationUpdateTrigger.ACCEPTED_MUTATION;
        };
    }

    private void resultsSection(NkContext c, GeometryDiscoveryWorkflowResolution workflow) {
        heading(c, "5. Results");
        GeometryDiscoveryWorkspaceSnapshot snapshot  = controller.workspace().snapshot();
        boolean                            available = controller.workflowResolver().hasSearchResults(snapshot);
        if (!available) {
            label(c, "Results become available after the first completed search iteration.");
        } else {
            TopologyCandidate best    = snapshot.search().bestCandidate();
            TopologyMetrics   metrics = best.topologyMetrics();
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
            }
            catch (Exception exception) {
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
        resultNodeFilter   = Nuklear.nk_propertyi(c, "# Nodes (0 = all)",
                                                  0, resultNodeFilter, 32, 1, 1);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            resultStatusFilter = nk_label_combo_options(c, stack, "Status",
                                                        new String[]{
                                                                "All", "STABLE_CANDIDATE_FOUND",
                                                                "NO_EQUILIBRIUM_FOUND", "NO_VALID_TOPOLOGY_GENERATED",
                                                                "IMPOSSIBLE_GRAPH_PARAMETERS", "NUMERICAL_FAILURE"
                                                        },
                                                        resultStatusFilter, .35, .65);
            resultRunFilter    = nk_label_edit(c, stack, "Date or run ID",
                                               resultRunFilterBuffer, resultRunFilter, .35, .65);
        }
        primaryButton(c, "Reload Discovery Results", true, "", () -> {
            try {
                dynamicResultRows = DynamicElasticResultStore.defaults().loadRows();
            }
            catch (Exception exception) {
                nuklearModel.showErrorDialog("Discovery Results", exception.getMessage());
            }
        });
        if (dynamicResultRows.isEmpty()) {
            try {
                dynamicResultRows = DynamicElasticResultStore.defaults().loadRows();
            }
            catch (Exception ignored) {
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
            }
            catch (Exception exception) {
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
            }
            catch (Exception exception) {
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

    private void primaryButton(
            NkContext c, String label, boolean enabled,
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

    private void primaryButton(
            NkContext c, String label,
            GeometryDiscoveryActionAvailability availability, Runnable action) {
        primaryButton(c, label, availability.enabled(), availability.disabledReason(), action);
    }

    private void expander(
            NkContext c, String label, boolean expanded,
            java.util.function.Consumer<Boolean> setter) {
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 1);
        if (Nuklear.nk_button_label(c, (expanded ? "Hide " : "Show ") + label))
            setter.accept(!expanded);
    }

    private void settingRow(
            NkContext c, String label, double value,
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
        }
        catch (Exception exception) {
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
