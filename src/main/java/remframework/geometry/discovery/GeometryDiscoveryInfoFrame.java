package remframework.geometry.discovery;

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import org.lwjgl.system.MemoryStack;
import remframework.gui.model.NuklearModel;
import remframework.gui.view.frames.AbstractFrame;

import java.nio.file.Path;
import java.util.stream.Collectors;

final class GeometryDiscoveryInfoFrame extends AbstractFrame {
    private final GeometryDiscoveryVisualizer visualizer;
    private final Path outputDirectory;

    GeometryDiscoveryInfoFrame(NuklearModel model, GeometryDiscoveryVisualizer visualizer,
                               Path outputDirectory) {
        super(model);
        this.visualizer = visualizer;
        this.outputDirectory = outputDirectory;
        windowOptions = Nuklear.NK_WINDOW_BORDER | Nuklear.NK_WINDOW_TITLE
                | Nuklear.NK_WINDOW_MOVABLE | Nuklear.NK_WINDOW_SCALABLE;
    }

    @Override public String getTitle() { return "Geometry Discovery"; }

    @Override
    public void render(long windowHandle, NkContext context) {
        super.render(windowHandle, context);
        createLayout(context, 10, 10, 455, Math.max(500, getWindowHeight() - 20));
    }

    @Override
    protected void layout(NkContext context, int x, int y, int width, int height) {
        GeometryDiscoveryResult result = visualizer.result();
        GeometryDiscoveryMetrics metrics = result.metrics();
        label(context, "Model: " + result.model().name());
        label(context, "Source: " + visualizer.sourceDescription());
        label(context, "Nodes: " + result.model().nodes().size()
                + "  Connections: " + result.model().connections().size());
        label(context, "Status: " + visualizer.session().state().status()
                + "  Iteration: " + metrics.iterationCount());
        label(context, String.format("Target %.6g  Edge mean/min/max %.6g / %.6g / %.6g",
                result.settings().targetDistance(), metrics.meanConnectedNeighborDistance(),
                metrics.minimumConnectedNeighborDistance(),
                metrics.maximumConnectedNeighborDistance()));
        label(context, String.format("Edge std %.6g  Min separation %.6g",
                metrics.standardDeviationConnectedNeighborDistance(),
                metrics.minimumDistinctNodeDistance()));
        label(context, String.format("Extents %.6g x %.6g x %.6g", metrics.extentX(),
                metrics.extentY(), metrics.extentZ()));
        label(context, "Dimension: " + metrics.effectiveDimensionality());
        label(context, "Runtime compatible: " + result.runtimeCompatibility().compatible());
        label(context, result.runtimeCompatibility().reason());
        TopologySearchMetadata topology = visualizer.topologySearchMetadata();
        if (topology != null) {
            label(context, String.format("Topology score %.7g  Mode %s  Seed %d",
                    topology.totalScore(), topology.searchMode(), topology.searchSeed()));
            label(context, String.format("Degree min/mean/max %d / %.4g / %d  Diameter %d",
                    topology.minimumDegree(), topology.meanDegree(), topology.maximumDegree(),
                    topology.graphDiameter()));
            label(context, "Bridges: " + topology.bridgeCount() + "  Articulation points: "
                    + topology.articulationPointCount());
            label(context, String.format("Directional imbalance %.6g  Tensor anisotropy %.6g",
                    topology.meanDirectionalImbalance(), topology.tensorAnisotropy()));
            label(context, String.format("Score parts edge %.5g direction %.5g tensor %.5g degree %.5g",
                    topology.edgeLengthComponent(), topology.directionalComponent(),
                    topology.tensorComponent(), topology.degreeVarianceComponent()));
            label(context, "Parent mutation: " + topology.parentMutation());
        }
        if (visualizer.viewState().showNodeLabels())
            label(context, "Node IDs: " + result.model().sortedNodes().stream()
                    .map(node -> Integer.toString(node.id())).collect(Collectors.joining(", ")));

        Nuklear.nk_layout_row_dynamic(context, rowHeightEdit, 3);
        if (Nuklear.nk_button_label(context, "Start")) visualizer.start();
        if (Nuklear.nk_button_label(context, "Pause")) visualizer.pause();
        if (Nuklear.nk_button_label(context, "Single Step")) visualizer.singleStep();
        Nuklear.nk_layout_row_dynamic(context, rowHeightEdit, 3);
        if (Nuklear.nk_button_label(context, "Reset")) visualizer.reset();
        if (Nuklear.nk_button_label(context, "Run to Convergence"))
            visualizer.runToConvergence();
        if (Nuklear.nk_button_label(context, "Fit View")) visualizer.fitToView();
        Nuklear.nk_layout_row_dynamic(context, rowHeightEdit, 3);
        if (Nuklear.nk_button_label(context, "Iterations -"))
            visualizer.setIterationsPerFrame(visualizer.iterationsPerFrame() - 1);
        labelInline(context, "Per frame: " + visualizer.iterationsPerFrame());
        if (Nuklear.nk_button_label(context, "Iterations +"))
            visualizer.setIterationsPerFrame(visualizer.iterationsPerFrame() + 1);

        GeometryDiscoverySettings settings = visualizer.session().settings();
        Nuklear.nk_layout_row_dynamic(context, rowHeightEdit, 3);
        if (Nuklear.nk_button_label(context, "Learning -"))
            visualizer.updateSettings(settings.withLearningRate(settings.learningRate() * 0.8));
        labelInline(context, "Learning: " + settings.learningRate());
        if (Nuklear.nk_button_label(context, "Learning +"))
            visualizer.updateSettings(settings.withLearningRate(settings.learningRate() * 1.25));
        settings = visualizer.session().settings();
        Nuklear.nk_layout_row_dynamic(context, rowHeightEdit, 3);
        if (Nuklear.nk_button_label(context, "Repulsion -"))
            visualizer.updateSettings(settings.withRepulsionStrength(
                    settings.repulsionStrength() * 0.8));
        labelInline(context, "Repulsion: " + settings.repulsionStrength());
        if (Nuklear.nk_button_label(context, "Repulsion +"))
            visualizer.updateSettings(settings.withRepulsionStrength(
                    Math.max(1e-12, settings.repulsionStrength() * 1.25)));
        settings = visualizer.session().settings();
        Nuklear.nk_layout_row_dynamic(context, rowHeightEdit, 3);
        if (Nuklear.nk_button_label(context, "Correction -"))
            visualizer.updateSettings(settings.withMaximumCorrection(
                    settings.maximumCorrection() * 0.8));
        labelInline(context, "Correction: " + settings.maximumCorrection());
        if (Nuklear.nk_button_label(context, "Correction +"))
            visualizer.updateSettings(settings.withMaximumCorrection(
                    settings.maximumCorrection() * 1.25));
        GeometryDiscoveryViewState view = visualizer.viewState();
        Nuklear.nk_layout_row_dynamic(context, rowHeightEdit, 3);
        if (Nuklear.nk_button_label(context, "Labels " + on(view.showNodeLabels())))
            view.setShowNodeLabels(!view.showNodeLabels());
        if (Nuklear.nk_button_label(context, "Connections " + on(view.showConnections())))
            view.setShowConnections(!view.showConnections());
        if (Nuklear.nk_button_label(context, "Axes " + on(view.showAxes())))
            view.setShowAxes(!view.showAxes());
        Nuklear.nk_layout_row_dynamic(context, rowHeightEdit, 3);
        if (Nuklear.nk_button_label(context, "Bounds " + on(view.showBoundingBox())))
            view.setShowBoundingBox(!view.showBoundingBox());
        if (Nuklear.nk_button_label(context, "Fixed " + on(view.highlightFixedNodes())))
            view.setHighlightFixedNodes(!view.highlightFixedNodes());
        if (Nuklear.nk_button_label(context, "Edge Error " + on(view.edgeErrorColoring())))
            view.setEdgeErrorColoring(!view.edgeErrorColoring());
        Nuklear.nk_layout_row_dynamic(context, rowHeightEdit, 1);
        if (Nuklear.nk_button_label(context,
                "Neighbor Distance Labels " + on(view.showNeighborDistanceLabels())))
            view.setShowNeighborDistanceLabels(!view.showNeighborDistanceLabels());

        Nuklear.nk_layout_row_dynamic(context, rowHeightEdit, 2);
        if (Nuklear.nk_button_label(context, "Export Candidate")) {
            try {
                visualizer.exportCandidate(outputDirectory.resolve(
                        result.model().name() + "-visualizer.toml"));
            } catch (Exception exception) {
                System.err.println("Visualizer export failed: " + exception.getMessage());
            }
        }
        if (Nuklear.nk_button_label(context, "Save Metrics Report")) {
            try {
                visualizer.saveMetricsReport(outputDirectory.resolve(
                        result.model().name() + "-metrics.txt"));
            } catch (Exception exception) {
                System.err.println("Metrics export failed: " + exception.getMessage());
            }
        }
        label(context, "Keys: arrows orbit, WASD pan, +/- zoom, F fit, R reset");
        label(context, "Space start/pause, N step, C converge, Tab select, P projection");

        Integer selectedId = visualizer.viewState().selectedNodeId();
        if (selectedId != null) {
            DiscoveryNode node = result.model().nodes().stream()
                    .filter(candidate -> candidate.id() == selectedId).findFirst().orElse(null);
            SelectedNodeMetrics local = visualizer.selectedNodeMetrics();
            if (node != null && local != null) {
                label(context, "Selected node: " + node.id()
                        + (node.fixedPosition() ? " [ANCHOR]" : ""));
                label(context, "Position: " + node.position());
                label(context, "Neighbors: " + node.neighborIds().stream().sorted()
                        .map(String::valueOf).collect(Collectors.joining(", ")));
                if (visualizer.viewState().showNeighborDistanceLabels())
                    label(context, "Neighbor distances: " + local.neighborDistances());
                label(context, String.format("Local mean %.6g  Max error %.6g",
                        local.meanLocalEdgeDistance(), local.maximumLocalEdgeError()));
            }
        }
        Nuklear.nk_layout_row_end(context);
    }

    private void label(NkContext context, String text) {
        Nuklear.nk_layout_row_dynamic(context, rowHeight, 1);
        Nuklear.nk_label_wrap(context, text);
    }

    private void labelInline(NkContext context, String text) {
        Nuklear.nk_label(context, text, Nuklear.NK_TEXT_CENTERED);
    }

    private String on(boolean value) { return value ? "[ON]" : "[OFF]"; }
}
