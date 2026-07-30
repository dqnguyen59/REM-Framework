package remframework.gui.view.frames;

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import remframework.geometry.discovery.*;
import remframework.gui.model.NuklearModel;

import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;

/**
 * Ordered setup wizard for constructing a fresh discovery experiment.
 */
public final class GeometryDiscoveryNewFrame extends AbstractFrame {
    private final GeometryDiscoveryWorkspaceController controller =
            GeometryDiscoveryWorkspaceManager.getInstance();
    private int step = 1;
    private String name = "Wave lattice experiment 001";
    private String description = "";
    private final StringBuffer nameBuffer = new StringBuffer();
    private final StringBuffer descriptionBuffer = new StringBuffer();
    private int dimension = 3;
    private int nodes = 12;
    private long seed = 42;
    private String profile = "Wave Propagation";
    private String topology = "Random Connected Graph";
    private String neighborPolicy = "Bounded";
    private int minimumDegree = 3;
    private int maximumDegree = 6;
    private double targetDegree = 4;
    private double edgeProbability = .3;
    private Path sourceCandidate;
    private String initialization = "Random Sphere";
    private double extent = 15;
    private double targetDistance = 10;
    private double minimumSeparation = 5;
    private double perturbation;
    private int relaxationIterations = 20_000;
    private double learningRate = .08;
    private double repulsion = .25;
    private double maximumCorrection = 2;
    private double tolerance = 1e-7;
    private int workerIterations = 25;
    private String searchMode = "Simulated Annealing";
    private int searchIterations = 5000;
    private int mutations = 1;
    private int archiveSize = 20;
    private double temperature = 1;
    private double cooling = .997;
    private double minimumTemperature = 1e-6;
    private double improvementTolerance = 1e-9;
    private int checkpointInterval = 100;
    private boolean relaxationAdvanced;
    private boolean searchAdvanced;
    private boolean scoringWeights;
    private TopologyScoreWeights weights =
            TopologyScoreWeights.forProfile(TopologyScoringProfile.WAVE_PROPAGATION);
    private String validationMessage = "";
    private int renderedStep = 1;

    public GeometryDiscoveryNewFrame(NuklearModel model) {
        super(model);
        isDialog = true;
        windowOptions = Nuklear.NK_WINDOW_TITLE | Nuklear.NK_WINDOW_BORDER
                | Nuklear.NK_WINDOW_MOVABLE | Nuklear.NK_WINDOW_CLOSABLE;
    }

    @Override public String getTitle() { return "New Discovery"; }

    @Override public void render(long window, NkContext context) {
        super.render(window, context);
        createLayoutCentered(context, Math.min(720, Math.max(360, getWindowWidth() - 40)),
                Math.min(720, Math.max(300, getWindowHeight() - 60)));
    }

    @Override protected void layout(NkContext c, int x, int y, int width, int height) {
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 7);
        for (int i = 1; i <= 7; i++)
            if (Nuklear.nk_button_label(c, Integer.toString(i))) changeStep(i);
        heading(c, step + ". " + title(step));
        int contentHeight = Math.max(130, height - 145);
        Nuklear.nk_layout_row_dynamic(c, contentHeight, 1);
        if (Nuklear.nk_group_begin(c, "New Discovery Wizard Content",
                Nuklear.NK_WINDOW_BORDER)) {
            if (renderedStep != step) {
                Nuklear.nk_group_set_scroll(c, "New Discovery Wizard Content", 0, 0);
                renderedStep = step;
            }
            switch (step) {
                case 1 -> experiment(c);
                case 2 -> topology(c);
                case 3 -> geometry(c);
                case 4 -> relaxation(c);
                case 5 -> search(c);
                case 6 -> scoring(c);
                case 7 -> review(c);
                default -> { }
            }
            if (!validationMessage.isBlank()) {
                heading(c, "Validation");
                label(c, validationMessage);
            }
            Nuklear.nk_group_end(c);
        }
        Nuklear.nk_layout_row_dynamic(c, 6, 1);
        Nuklear.nk_spacer(c);
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 3);
        if (Nuklear.nk_button_label(c, "Back") && step > 1) changeStep(step - 1);
        if (Nuklear.nk_button_label(c, "Next") && step < 7) {
            DiscoveryExperimentValidation validation =
                    new DiscoveryExperimentValidator().validate(configuration());
            validationMessage = String.join("\n", validation.errors());
            if (validation.valid() || step < 6) changeStep(step + 1);
        }
        if (Nuklear.nk_button_label(c, "Cancel")) close();
    }

    private void experiment(NkContext c) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            name = nk_label_edit(c, stack, "Experiment name", nameBuffer, name, .35, .65);
            description = multilineDescription(c, stack, description);
            nodes = formInt(c, "Node count", nodes, 4, 32);
            String dimensions = nk_label_combo_options(c, stack, "Embedding dimension",
                    new String[]{"2D", "3D"}, dimension + "D", .35, .65);
            dimension = dimensions.equals("2D") ? 2 : 3;
            profile = nk_label_combo_options(c, stack, "Scoring profile",
                    profiles(), profile, .35, .65);
            seed = formInt(c, "Random seed", (int) seed,
                    Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
    }

    private String multilineDescription(NkContext c, MemoryStack stack, String value) {
        Nuklear.nk_layout_row_begin(c, Nuklear.NK_DYNAMIC,
                GeometryDiscoveryWizardFormLayout.DESCRIPTION_ROW_HEIGHT, 2);
        Nuklear.nk_layout_row_push(c, (float) GeometryDiscoveryWizardFormLayout.LABEL_FRACTION);
        Nuklear.nk_label(c, "Description", Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_layout_row_push(c,
                (float) (1 - GeometryDiscoveryWizardFormLayout.LABEL_FRACTION));
        ByteBuffer buffer = stack.calloc(1024);
        int length = MemoryUtil.memASCII(value, false, buffer);
        IntBuffer lengthBuffer = stack.ints(length);
        Nuklear.nk_edit_string(c, Nuklear.NK_EDIT_BOX | Nuklear.NK_EDIT_MULTILINE
                        | Nuklear.NK_EDIT_CLIPBOARD | Nuklear.NK_EDIT_ALLOW_TAB,
                buffer, lengthBuffer, 1023, Nuklear::nnk_filter_ascii);
        String result = MemoryUtil.memASCII(buffer, lengthBuffer.get(0));
        Nuklear.nk_layout_row_end(c);
        return result;
    }

    private int formInt(NkContext c, String label, int value, int minimum, int maximum) {
        Nuklear.nk_layout_row_begin(c, Nuklear.NK_DYNAMIC,
                GeometryDiscoveryWizardFormLayout.STANDARD_ROW_HEIGHT, 2);
        Nuklear.nk_layout_row_push(c, (float) GeometryDiscoveryWizardFormLayout.LABEL_FRACTION);
        Nuklear.nk_label(c, label, Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_layout_row_push(c,
                (float) (1 - GeometryDiscoveryWizardFormLayout.LABEL_FRACTION));
        int result = Nuklear.nk_propertyi(c, "#", minimum, value, maximum, 1, 1);
        Nuklear.nk_layout_row_end(c);
        return result;
    }

    private double formDouble(NkContext c, String label, double value,
                              double minimum, double maximum, double step) {
        Nuklear.nk_layout_row_begin(c, Nuklear.NK_DYNAMIC,
                GeometryDiscoveryWizardFormLayout.STANDARD_ROW_HEIGHT, 2);
        Nuklear.nk_layout_row_push(c, (float) GeometryDiscoveryWizardFormLayout.LABEL_FRACTION);
        Nuklear.nk_label(c, label, Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_layout_row_push(c,
                (float) (1 - GeometryDiscoveryWizardFormLayout.LABEL_FRACTION));
        double result = Nuklear.nk_propertyd(c, "#", minimum, value, maximum,
                step, (float) Math.max(1e-9, step / 10));
        Nuklear.nk_layout_row_end(c);
        return result;
    }

    private String formCombo(NkContext c, MemoryStack stack, String label,
                             String[] options, String value) {
        return nk_label_combo_options(c, stack, label, options, value,
                GeometryDiscoveryWizardFormLayout.LABEL_FRACTION,
                1 - GeometryDiscoveryWizardFormLayout.LABEL_FRACTION);
    }

    private void helpBlock(NkContext c, String text) {
        Nuklear.nk_layout_row_dynamic(c, 72, 1);
        Nuklear.nk_label_wrap(c, text);
    }

    private void topology(NkContext c) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            topology = formCombo(c, stack, "Initial topology",
                    new String[]{"Line", "Ring", "Cubic Grid", "Random Tree",
                            "Random Connected Graph", "Complete Graph",
                            "Load Candidate", "Load TOML"}, topology);
            neighborPolicy = formCombo(c, stack, "Neighbor-count policy",
                    new String[]{"Uniform", "Bounded", "Boundary-aware",
                            "Unrestricted within bounds"}, neighborPolicy);
        }
        minimumDegree = formInt(c, "Minimum neighbors per node", minimumDegree, 0, 31);
        targetDegree = formDouble(c, "Target initial neighbors per node",
                targetDegree, 0, 31, .25);
        maximumDegree = formInt(c, "Maximum neighbors per node", maximumDegree, 0, 31);
        if (topology.equals("Random Connected Graph"))
            edgeProbability = formDouble(c, "Edge probability",
                    edgeProbability, 0, 1, .05);
        helpBlock(c, "Help:\nThe number of neighbors is |N| in the Fundamental Node Equation. "
                + "The current cubic REM lattice uses |N| = 6, but Geometry Discovery "
                + "may explore other fixed or bounded neighbor counts.");
        if (neighborPolicy.equals("Boundary-aware"))
            helpBlock(c, "Boundary-aware is provisional and currently uses bounded behavior "
                    + "because reliable boundary classification is not yet available.");
        if (topology.startsWith("Load")) {
            if (sourceCandidate == null) sourceCandidate = controller.state().currentSource();
            helpBlock(c, sourceCandidate == null ? "No source candidate selected."
                    : "Source: " + sourceCandidate);
            helpBlock(c, "Use Open Candidate File in stage 1, then choose Existing Coordinates.");
        }
    }

    private void geometry(NkContext c) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            initialization = formCombo(c, stack, "Initial geometry",
                    new String[]{"Random Sphere", "Random Cube", "Cubic Grid",
                            "Planar Circle", "Existing Coordinates"},
                    initialization);
        }
        extent = formDouble(c, "Initial radius or extent", extent, .001, 1_000_000, 1);
        targetDistance = formDouble(c, "Target edge distance",
                targetDistance, .001, 1_000_000, 1);
        minimumSeparation = formDouble(c, "Minimum separation",
                minimumSeparation, .001, 1_000_000, .5);
        if (!initialization.equals("Existing Coordinates"))
            perturbation = formDouble(c, "Perturbation magnitude",
                    perturbation, 0, 1_000_000, .1);
    }

    private void relaxation(NkContext c) {
        targetDistance = Nuklear.nk_propertyd(c, "Target edge distance", .001,
                targetDistance, 1_000_000, 1, .1f);
        minimumSeparation = Nuklear.nk_propertyd(c, "Minimum separation", .001,
                minimumSeparation, 1_000_000, .5, .1f);
        relaxationIterations = Nuklear.nk_propertyi(c, "Maximum iterations", 1,
                relaxationIterations, 10_000_000, 100, 10);
        if (Nuklear.nk_button_label(c, (relaxationAdvanced ? "Hide" : "Show")
                + " Advanced Settings")) relaxationAdvanced = !relaxationAdvanced;
        if (relaxationAdvanced) {
            learningRate = property(c, "Learning rate", learningRate);
            repulsion = property(c, "Repulsion strength", repulsion);
            maximumCorrection = property(c, "Maximum correction", maximumCorrection);
            tolerance = property(c, "Convergence tolerance", tolerance);
            workerIterations = Nuklear.nk_propertyi(c, "Worker iterations per update",
                    1, workerIterations, 10_000, 1, 1);
        }
    }

    private void search(NkContext c) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            searchMode = formCombo(c, stack, "Search method",
                    new String[]{"Hill Climbing", "Simulated Annealing"},
                    searchMode);
        }
        searchIterations = formInt(c, "Search iterations",
                searchIterations, 1, 10_000_000);
        mutations = formInt(c, "Mutations per iteration", mutations, 1, 100);
        minimumDegree = formInt(c, "Minimum neighbors", minimumDegree, 0, 31);
        maximumDegree = formInt(c, "Maximum neighbors", maximumDegree, 0, 31);
        archiveSize = formInt(c, "Archive size", archiveSize, 1, 1000);
        if (Nuklear.nk_button_label(c, (searchAdvanced ? "Hide" : "Show")
                + " Advanced Settings")) searchAdvanced = !searchAdvanced;
        if (searchAdvanced) {
            temperature = formDouble(c, "Initial temperature",
                    temperature, 1e-12, 1e9, .1);
            cooling = formDouble(c, "Cooling rate", cooling, 1e-12, 1, .001);
            minimumTemperature = formDouble(c, "Minimum temperature",
                    minimumTemperature, 1e-12, 1e9, 1e-6);
            improvementTolerance = formDouble(c, "Improvement tolerance",
                    improvementTolerance, 1e-12, 1e9, 1e-9);
            checkpointInterval = formInt(c, "Checkpoint interval",
                    checkpointInterval, 1, 1_000_000);
        }
    }

    private void scoring(NkContext c) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String selected = nk_label_combo_options(c, stack, "Scoring profile",
                    profiles(), profile, .35, .65);
            if (!selected.equals(profile)) {
                profile = selected;
                if (!profile.equals("Custom"))
                    weights = TopologyScoreWeights.forProfile(profileValue());
            }
        }
        label(c, profilePurpose());
        label(c, "Principal active components:");
        principalWeights().forEach(thisWeight -> label(c, thisWeight));
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 2);
        if (Nuklear.nk_button_label(c, "Edit Individual Weights"))
            scoringWeights = !scoringWeights;
        if (Nuklear.nk_button_label(c, "Reset Profile Defaults") && !profile.equals("Custom"))
            weights = TopologyScoreWeights.forProfile(profileValue());
        if (scoringWeights) {
            double edge = property(c, "Edge equality weight", weights.edgeLengthError());
            double coverage = property(c, "Spherical coverage weight",
                    weights.sphericalCoverage());
            double opposite = property(c, "Opposite-direction weight",
                    weights.oppositeDirection());
            double bridges = property(c, "Bridge penalty weight", weights.bridgePenalty());
            double articulation = property(c, "Articulation penalty weight",
                    weights.articulationPenalty());
            TopologyScoreWeights changed = weights
                    .override("edge-length-error", edge)
                    .override("spherical-coverage", coverage)
                    .override("opposite-direction", opposite)
                    .override("bridges", bridges)
                    .override("articulation", articulation);
            if (!changed.equals(weights)) {
                weights = changed;
                profile = "Custom";
            }
        }
    }

    private void review(NkContext c) {
        DiscoveryExperimentConfiguration configuration = configuration();
        label(c, "Name: " + configuration.name());
        label(c, "Nodes: " + nodes + "    Dimension: " + dimension + "D");
        label(c, "Initial topology: " + topology);
        label(c, "Degree constraints: " + minimumDegree + " to " + maximumDegree);
        label(c, "Coordinate initialization: " + initialization);
        label(c, "Target edge distance: " + targetDistance);
        label(c, "Scoring profile: " + profile);
        label(c, "Search mode: " + searchMode
                + "    Iterations: " + searchIterations);
        label(c, "Seed: " + seed);
        DiscoveryExperimentValidation validation =
                new DiscoveryExperimentValidator().validate(configuration);
        if (!validation.valid()) label(c, String.join("\n", validation.errors()));
        helpBlock(c, "Create Initial Candidate:\nBuilds and displays the initial graph and "
                + "coordinates without running any process.");
        helpBlock(c, "Create and Relax Geometry:\nBuilds the candidate and relaxes node "
                + "positions while keeping topology fixed.");
        helpBlock(c, "Create and Start Topology Search:\nBuilds and relaxes the candidate, "
                + "then begins topology mutation and evaluation.");
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit + 8, 3);
        createButton(c, "Create Initial Candidate", validation, () ->
                controller.workspace().createExperiment(configuration));
        createButton(c, "Create and Relax Geometry", validation, () ->
                controller.workspace().createAndRelax(configuration));
        createButton(c, "Create and Start Topology Search", validation, () ->
                controller.workspace().startDiscovery(configuration));
    }

    private void createButton(NkContext c, String text,
                              DiscoveryExperimentValidation validation,
                              java.util.function.Supplier<DiscoveryExperimentValidation> action) {
        if (!validation.valid()) Nuklear.nk_widget_disable_begin(c);
        boolean clicked = Nuklear.nk_button_label(c, text);
        tooltip(c, switch (text) {
            case "Create Initial Candidate" -> "Build and display the graph and coordinates without running a process.";
            case "Create and Relax Geometry" -> "Build the candidate and relax positions with fixed topology.";
            default -> "Build and relax the candidate, then begin topology mutation and evaluation.";
        });
        if (!validation.valid()) Nuklear.nk_widget_disable_end(c);
        if (clicked && validation.valid()) {
            DiscoveryExperimentValidation result = action.get();
            validationMessage = String.join("\n", result.errors());
            if (result.valid()) close();
        }
    }

    private DiscoveryExperimentConfiguration configuration() {
        GeometryDiscoverySettings geometry = new GeometryDiscoverySettings(
                targetDistance, learningRate, relaxationIterations, tolerance,
                minimumSeparation, repulsion, seed, maximumCorrection,
                targetDistance * 1e-6, 1000);
        TopologyScoringProfile selectedProfile = profileValue();
        TopologySearchSettings search = new TopologySearchSettings(
                searchMode.equals("Simulated Annealing")
                        ? TopologySearchMode.SIMULATED_ANNEALING
                        : TopologySearchMode.HILL_CLIMBING,
                seed, searchIterations, mutations, minimumDegree, maximumDegree,
                -1, archiveSize, temperature, cooling, minimumTemperature,
                improvementTolerance, geometry, weights, selectedProfile,
                TopologyDiagnosticSettings.defaults(), checkpointInterval);
        return new DiscoveryExperimentConfiguration(name, description, dimension,
                nodes, seed, selectedProfile, weights, profile.equals("Custom"),
                topologyValue(), neighborPolicyValue(),
                minimumDegree, maximumDegree, targetDegree,
                edgeProbability, sourceCandidate, initializationValue(), extent,
                perturbation, geometry, workerIterations, search);
    }

    private TopologyScoringProfile profileValue() {
        return switch (profile) {
            case "Wave Propagation" -> TopologyScoringProfile.WAVE_PROPAGATION;
            case "Centrosymmetric" -> TopologyScoringProfile.CENTROSYMMETRIC;
            case "Tetrahedral" -> TopologyScoringProfile.TETRAHEDRAL;
            case "Cubic Runtime" -> TopologyScoringProfile.CUBIC_RUNTIME;
            default -> TopologyScoringProfile.GENERAL_GEOMETRY;
        };
    }
    private DiscoveryInitialTopology topologyValue() {
        return DiscoveryInitialTopology.valueOf(topology.toUpperCase()
                .replace(' ', '_'));
    }
    private DiscoveryGeometryInitialization initializationValue() {
        return DiscoveryGeometryInitialization.valueOf(initialization.toUpperCase()
                .replace(' ', '_'));
    }
    private DiscoveryNeighborCountPolicy neighborPolicyValue() {
        return switch (neighborPolicy) {
            case "Uniform" -> DiscoveryNeighborCountPolicy.UNIFORM;
            case "Boundary-aware" -> DiscoveryNeighborCountPolicy.BOUNDARY_AWARE;
            case "Unrestricted within bounds" ->
                    DiscoveryNeighborCountPolicy.UNRESTRICTED_WITHIN_BOUNDS;
            default -> DiscoveryNeighborCountPolicy.BOUNDED;
        };
    }
    private String[] profiles() {
        return new String[]{"General Geometry", "Wave Propagation", "Centrosymmetric",
                "Tetrahedral", "Cubic Runtime", "Custom"};
    }
    private String profilePurpose() {
        return switch (profile) {
            case "Wave Propagation" -> "Prioritizes equal edge lengths, broad angular coverage, "
                    + "opposite-direction coverage, dimensional consistency, and robust connectivity.";
            case "Centrosymmetric" -> "Prioritizes paired opposite directions and low directional bias.";
            case "Tetrahedral" -> "Prioritizes tetrahedral angular symmetry and equal edges.";
            case "Cubic Runtime" -> "Prioritizes compatibility with the current cubic REM runtime.";
            case "Custom" -> "Uses manually overridden component weights.";
            default -> "Balances equal edges, angular coverage, collision avoidance, and robustness.";
        };
    }
    private List<String> principalWeights() {
        return List.of(String.format("Edge equality: %.3g", weights.edgeLengthError()),
                String.format("Spherical coverage: %.3g", weights.sphericalCoverage()),
                String.format("Opposite directions: %.3g", weights.oppositeDirection()),
                String.format("Graph robustness: %.3g",
                        weights.bridgePenalty() + weights.articulationPenalty()));
    }
    private double property(NkContext c, String name, double value) {
        return Nuklear.nk_propertyd(c, name, 1e-12, value, 1e9,
                Math.max(1e-12, value * .1),
                (float) Math.max(1e-12, value * .01));
    }
    private String title(int number) {
        return switch (number) {
            case 1 -> "Experiment";
            case 2 -> "Initial Topology";
            case 3 -> "Initial Geometry";
            case 4 -> "Geometry Relaxation";
            case 5 -> "Topology Search";
            case 6 -> "Scoring";
            default -> "Review";
        };
    }
    private void changeStep(int value) {
        step = Math.max(1, Math.min(7, value));
    }
    private void heading(NkContext c, String text) {
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit + 5, 1);
        Nuklear.nk_style_set_font(c, nuklearModel.getDefaultFontBold());
        Nuklear.nk_label_wrap(c, text);
        Nuklear.nk_style_set_font(c, nuklearModel.getDefaultFont());
    }
    private void label(NkContext c, String text) {
        Nuklear.nk_layout_row_dynamic(c, rowHeight + 3, 1);
        Nuklear.nk_label_wrap(c, text);
    }
}
