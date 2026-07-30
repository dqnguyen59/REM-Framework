package remframework.gui.view.frames;

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import remframework.geometry.discovery.*;
import remframework.gui.model.NuklearModel;
import remframework.helpers.Constants;

import java.util.List;

/**
 * Context-sensitive bottom details area. Metrics are separated into tabs instead
 * of being presented as one dense paragraph.
 */
public final class GeometryDiscoveryMetricsFrame extends AbstractFrame {
    private final GeometryDiscoveryWorkspaceController controller =
            GeometryDiscoveryWorkspaceManager.getInstance();

    public GeometryDiscoveryMetricsFrame(NuklearModel model) {
        super(model);
        windowOptions = Nuklear.NK_WINDOW_TITLE | Nuklear.NK_WINDOW_BORDER;
    }

    @Override public String getTitle() { return "Discovery Details"; }

    @Override public void render(long window, NkContext context) {
        super.render(window, context);
        GeometryDiscoveryWorkspaceLayout.Bounds bounds =
                GeometryDiscoveryWorkspaceLayout.calculate(getWindowWidth(), getWindowHeight(),
                        nuklearModel.getMenuBarHeight() + 4,
                        Constants.STATUS_BAR_HEIGHT).details();
        createFixedLayout(context, bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    @Override protected void layout(NkContext c, int x, int y, int width, int height) {
        GeometryDiscoveryWorkspaceSnapshot snapshot = controller.workspace().snapshot();
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, GeometryDiscoveryDetailTab.values().length);
        for (GeometryDiscoveryDetailTab tab : GeometryDiscoveryDetailTab.values())
            if (Nuklear.nk_button_label(c, tab.label()))
                controller.state().setSelectedDetailTab(tab);
        GeometryDiscoveryResult result = snapshot.geometry();
        switch (controller.state().selectedDetailTab()) {
            case OVERVIEW -> overview(c, snapshot);
            case GEOMETRY -> geometry(c, result.metrics());
            case TOPOLOGY -> topology(c, new TopologyMetricsCalculator().calculate(result.model()));
            case DIRECTIONAL -> directional(c,
                    new DirectionalBalanceCalculator().calculate(result.model()));
            case SCORE -> score(c, controller.evaluatedCurrentCandidate().score());
            case SELECTED_NODE -> selectedNode(c, snapshot);
            case HISTORY -> history(c, snapshot);
        }
    }

    private void overview(NkContext c, GeometryDiscoveryWorkspaceSnapshot snapshot) {
        GeometryDiscoveryWorkflowResolution workflow = controller.workflow();
        TopologyCandidate evaluated = controller.evaluatedCurrentCandidate();
        label(c, "Candidate: " + snapshot.geometry().model().name());
        label(c, "Experiment state: " + display(workflow.state().name()));
        label(c, "Geometry converged: "
                + controller.workflowResolver().geometrySatisfied(snapshot.geometry()));
        label(c, String.format("Total score: %.7g", evaluated.score().total()));
        label(c, "Runtime compatible: "
                + snapshot.geometry().runtimeCompatibility().compatible());
        label(c, "Search status: " + (snapshot.searchRunning() ? "Running"
                : snapshot.search() == null ? "Not started" : "Stopped"));
    }

    private void geometry(NkContext c, GeometryDiscoveryMetrics g) {
        label(c, "Converged: " + g.converged() + "    Iteration: " + g.iterationCount());
        label(c, String.format("Edge error: %.7g    Edge deviation: %.7g",
                g.totalEdgeLengthError(), g.standardDeviationConnectedNeighborDistance()));
        label(c, String.format("Minimum separation: %.7g    Collisions: %d",
                g.minimumDistinctNodeDistance(), g.collisionViolationCount()));
        label(c, String.format("Extents: %.5g x %.5g x %.5g",
                g.extentX(), g.extentY(), g.extentZ()));
        label(c, "Effective dimensionality: " + g.effectiveDimensionality());
    }

    private void topology(NkContext c, TopologyMetrics t) {
        label(c, "Nodes: " + t.nodeCount() + "    Edges: " + t.edgeCount());
        label(c, String.format("Degree: %d to %d    Mean: %.5g",
                t.minimumDegree(), t.maximumDegree(), t.meanDegree()));
        label(c, String.format("Diameter: %d    Mean shortest path: %.5g",
                t.graphDiameter(), t.meanShortestPathLength()));
        label(c, String.format("Clustering: %.5g    Triangles: %d",
                t.clusteringCoefficient(), t.triangleCount()));
        label(c, "Bridges: " + t.bridgeEdgeCount()
                + "    Articulation points: " + t.articulationPointCount());
    }

    private void directional(NkContext c, DirectionalBalanceMetrics d) {
        label(c, String.format("Directional bias: %.6g    Tensor isotropy: %.6g",
                d.meanNormalizedDirectionalBias(), d.meanTensorIsotropyError()));
        label(c, String.format("Angular reference error: %.6g    Applicable nodes: %d",
                d.meanKnownReferenceAngularError(), d.angularReferenceApplicableNodeCount()));
        label(c, String.format("Spherical coverage mean: %.6g    Maximum gap: %.6g",
                d.meanNearestDirectionAngle(), d.maximumUncoveredAngle()));
        label(c, String.format("Opposite-direction mismatch: %.6g",
                d.meanOppositeDirectionMismatch()));
        label(c, "Dimension-deficient nodes: " + d.dimensionDeficientNodeCount());
    }

    private void score(NkContext c, TopologyScore score) {
        label(c, "Profile: " + display(controller.state().scoringProfile().name()));
        label(c, String.format("Total score: %.7g", score.total()));
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 2);
        if (Nuklear.nk_button_label(c, "Show only non-zero: "
                + controller.state().showOnlyNonZeroScoreComponents()))
            controller.state().setShowOnlyNonZeroScoreComponents(
                    !controller.state().showOnlyNonZeroScoreComponents());
        if (Nuklear.nk_button_label(c, (controller.state().scoreWeightsExpanded()
                ? "Hide" : "Edit") + " Score Weights"))
            controller.state().setScoreWeightsExpanded(!controller.state().scoreWeightsExpanded());
        for (TopologyScoreComponent component : score.details().values()) {
            if (controller.state().showOnlyNonZeroScoreComponents()
                    && component.weightedContribution() == 0) continue;
            double percentage = score.total() == 0 ? 0
                    : component.weightedContribution() * 100 / score.total();
            label(c, String.format("%s: raw %.4g norm %.4g weight %.4g add %.4g (%.1f%%) app=%s",
                    component.name(), component.rawValue(), component.normalizedValue(),
                    component.weight(), component.weightedContribution(), percentage,
                    component.applicable()));
            if (controller.state().scoreWeightsExpanded()) {
                Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 2);
                if (Nuklear.nk_button_label(c, component.name() + " Weight -"))
                    controller.overrideComponentWeight(component.name(),
                            Math.max(0, component.weight() * .8));
                if (Nuklear.nk_button_label(c, component.name() + " Weight +"))
                    controller.overrideComponentWeight(component.name(),
                            component.weight() == 0 ? 1 : component.weight() * 1.25);
            }
        }
    }

    private void selectedNode(NkContext c, GeometryDiscoveryWorkspaceSnapshot snapshot) {
        Integer id = controller.viewState().selectedNodeId();
        if (id == null) {
            label(c, "Select a node in the viewport to inspect it.");
            return;
        }
        GeometryDiscoveryModel model = snapshot.geometry().model();
        DiscoveryNode node = model.nodes().stream().filter(candidate -> candidate.id() == id)
                .findFirst().orElse(null);
        if (node == null) {
            label(c, "The selected node is not present in this candidate.");
            return;
        }
        List<Integer> neighbors = List.copyOf(
                TopologyConstraintValidator.adjacency(model).get(id));
        label(c, "Node ID: " + id + "    Degree: " + neighbors.size());
        label(c, "Position: " + node.position());
        label(c, "Fixed: " + node.fixedPosition() + "    Neighbors: " + neighbors);
        LocalDirectionalDiagnostics diagnostics =
                new DirectionalBalanceCalculator().calculate(model).localDiagnostics().get(id);
        label(c, String.format("Directional bias: %.6g    Tensor error: %.6g",
                diagnostics.normalizedDirectionalBias(), diagnostics.tensorIsotropyError()));
        label(c, "Local dimensionality: " + diagnostics.localDimensionality()
                + "    Eigenvalues: " + diagnostics.eigenvalues());
        label(c, "Pairwise dot products: " + diagnostics.pairwiseDotProducts());
    }

    private void history(NkContext c, GeometryDiscoveryWorkspaceSnapshot snapshot) {
        if (snapshot.search() == null || snapshot.search().history().isEmpty()) {
            label(c, "Search history and candidate archive will appear after search begins.");
            return;
        }
        label(c, "Score History");
        double minimum = snapshot.search().history().stream()
                .mapToDouble(TopologySearchHistoryEntry::bestScore).min().orElse(0);
        double maximum = snapshot.search().history().stream()
                .mapToDouble(TopologySearchHistoryEntry::currentScore).max().orElse(minimum + 1);
        if (maximum <= minimum) maximum = minimum + 1;
        Nuklear.nk_layout_row_dynamic(c, 70, 1);
        int first = Math.max(0, snapshot.search().history().size() - 500);
        if (Nuklear.nk_chart_begin(c, Nuklear.NK_CHART_LINES,
                snapshot.search().history().size() - first, (float) minimum, (float) maximum)) {
            for (int i = first; i < snapshot.search().history().size(); i++)
                Nuklear.nk_chart_push(c,
                        (float) snapshot.search().history().get(i).bestScore());
            Nuklear.nk_chart_end(c);
        }
        label(c, "Archived candidates: " + snapshot.search().topCandidates().size());
        label(c, "Important Events (latest first)");
        List<DiscoveryExperimentEvent> events = controller.workspace().eventLog();
        for (int i = events.size() - 1; i >= Math.max(0, events.size() - 8); i--) {
            DiscoveryExperimentEvent event = events.get(i);
            label(c, event.type() + " at iteration " + event.iteration()
                    + ": " + event.details());
        }
    }

    private void label(NkContext c, String text) {
        Nuklear.nk_layout_row_dynamic(c, rowHeight + 3, 1);
        Nuklear.nk_label_wrap(c, text);
    }

    private String display(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
