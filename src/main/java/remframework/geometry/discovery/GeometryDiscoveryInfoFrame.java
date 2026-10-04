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
    private int additionalNodes=200;

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
        if (visualizer.closureTrial() != null) { layoutClosure(context); return; }
        if (visualizer.tetrahedralBuilder() != null) { layoutIncremental(context); return; }
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
        for (var researchStatus : visualizer.researchStatuses()) {
            label(context, "Research: " + researchStatus.code());
            Nuklear.nk_layout_row_dynamic(context, 90, 1);
            Nuklear.nk_label_wrap(context, researchStatus.explanation());
        }
        label(context, "Runtime compatible: " + result.runtimeCompatibility().compatible());
        label(context, result.runtimeCompatibility().reason());
        TopologySearchMetadata topology = visualizer.topologySearchMetadata();
        if (topology != null) {
            label(context, String.format("Topology score %.7g  Profile %s",
                    topology.totalScore(), topology.scoringProfile()));
            label(context, "Diagnostics metadata: "
                    + (topology.redesignedDiagnosticsAvailable() ? "version "
                    + topology.metadataVersion() : "legacy; new metrics unavailable"));
            label(context, String.format("Degree min/mean/max %d / %.4g / %d  Diameter %d",
                    topology.minimumDegree(), topology.meanDegree(), topology.maximumDegree(),
                    topology.graphDiameter()));
            label(context, "Bridges: " + topology.bridgeCount() + "  Articulation points: "
                    + topology.articulationPointCount());
            label(context, String.format("Directional bias %.6g  Tensor isotropy %.6g",
                    topology.meanDirectionalBias(), topology.tensorIsotropyError()));
            if (topology.redesignedDiagnosticsAvailable()) {
                label(context, "Angular reference: " + (topology.angularReferenceAvailable()
                        ? String.format("error %.6g", topology.angularReferenceError())
                        : "not applicable"));
                label(context, String.format("Coverage max gap %.6g  Opposite mismatch %.6g",
                        topology.maximumUncoveredAngle(),
                        topology.oppositeDirectionMismatch()));
                label(context, "Global dimension: " + topology.globalDimensionality()
                        + "  local mismatches: " + topology.dimensionalityMismatchCount());
            }
            label(context, String.format("Score parts edge %.5g bias %.5g tensor %.5g degree %.5g",
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
        ViewState view = visualizer.viewState();
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
                LocalDirectionalDiagnostics nodeDiagnostics =
                        visualizer.selectedNodeDirectionalDiagnostics();
                if (nodeDiagnostics != null) {
                    label(context, "Local degree: " + nodeDiagnostics.degree()
                            + "  dimension: " + nodeDiagnostics.localDimensionality());
                    label(context, "Eigenvalues: " + nodeDiagnostics.eigenvalues());
                    label(context, String.format("Bias %.6g  Tensor error %.6g",
                            nodeDiagnostics.normalizedDirectionalBias(),
                            nodeDiagnostics.tensorIsotropyError()));
                    label(context, "Pairwise dots: " + nodeDiagnostics.pairwiseDotProducts());
                    label(context, "Angular reference: "
                            + (nodeDiagnostics.angularReferenceApplicable()
                            ? nodeDiagnostics.angularReferenceType() + " error "
                            + nodeDiagnostics.knownReferenceAngularError() : "not applicable"));
                    label(context, String.format("Opposite mismatch %.6g  Coverage max gap %s",
                            nodeDiagnostics.meanOppositeDirectionMismatch(),
                            nodeDiagnostics.sphericalCoverageApplicable()
                                    ? String.format("%.6g", nodeDiagnostics.maximumUncoveredAngle())
                                    : "unavailable"));
                }
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
    private void layoutIncremental(NkContext context) {
        var builder = visualizer.tetrahedralBuilder();
        label(context, "Tetrahedral network - shared-node construction");
        Nuklear.nk_layout_row_dynamic(context, rowHeightEdit, 1);
        double length = Nuklear.nk_propertyd(context, builder.sharingMode() ? "Base length target" : "Base length", 1e-6,
                builder.baseLength(), 1e6, 0.5, 0.1f);
        if (Double.compare(length, builder.baseLength()) != 0)
            visualizer.setTetrahedralBaseLength(length);
        Nuklear.nk_layout_row_dynamic(context, rowHeightEdit, 2);
        if (Nuklear.nk_button_label(context, "+")) visualizer.addTetrahedralNode();
        if (Nuklear.nk_button_label(context, "-")) visualizer.removeTetrahedralNode();
        Nuklear.nk_layout_row_dynamic(context, rowHeightEdit, 2);
        additionalNodes=Nuklear.nk_propertyi(context, "Desired total nodes", 4, additionalNodes, 1000, 1, 1);
        if (Nuklear.nk_button_label(context, "Add")) visualizer.growTetrahedralToTotal(additionalNodes);
        if(builder.continuousLayout()) {
            label(context, "Add waits for settling; deformed lengths allowed");
            label(context, "Movement: "+builder.liveMovement()+"  Updates: "+builder.liveUpdates());
            label(context, "Batch nodes remaining: "+builder.remainingBatchNodes());
            Nuklear.nk_layout_row_dynamic(context, rowHeightEdit, 2);
            if(Nuklear.nk_button_label(context,builder.layoutPaused()?"Resume layout":"Pause layout")) builder.toggleLayoutPause();
            if(Nuklear.nk_button_label(context,"Cancel batch")) builder.cancelBatch();
        } else label(context, "Add stops on connection error > 0.0000001");
        label(context, "Nodes: " + builder.snapshot().nodeCount() + "  Connections: "
                + builder.snapshot().edges().size() + "  Tetrahedra: " + builder.snapshot().tetrahedra().size());
        label(context, "Completed layer: " + builder.completedGeneration());
        label(context, "Layer " + builder.currentLayer() + ": " + builder.additionsInLayer()
                + " / " + builder.layerCapacity() + " added");
        label(context, builder.sharingMode() ? "+ attaches next face; - undoes the last attachment" : "+ adds next face; - undoes the last added node");
        Nuklear.nk_layout_row_dynamic(context, rowHeightEdit, 2);
        if (Nuklear.nk_button_label(context, "Reset to ABCD")) visualizer.reset();
        if (Nuklear.nk_button_label(context, "Fit View")) visualizer.fitToView();
        if (!builder.sharingMode()) {
            Nuklear.nk_layout_row_dynamic(context, rowHeightEdit, 1);
            if (Nuklear.nk_button_label(context, "Test 8/11 closure (7 nodes)")) {
                visualizer.showTetrahedralClosure(builder.baseLength()); return;
            }
        }
        var view = visualizer.viewState();
        Nuklear.nk_layout_row_dynamic(context, rowHeightEdit, 3);
        if (Nuklear.nk_button_label(context, "Labels " + on(view.showNodeLabels())))
            view.setShowNodeLabels(!view.showNodeLabels());
        if (Nuklear.nk_button_label(context, "Lines " + on(view.showConnections())))
            view.setShowConnections(!view.showConnections());
        if (Nuklear.nk_button_label(context, "Axes " + on(view.showAxes())))
            view.setShowAxes(!view.showAxes());
        var metrics = visualizer.result().metrics();
        label(context, String.format("Connected length min/max: %.6g / %.6g",
                metrics.minimumConnectedNeighborDistance(), metrics.maximumConnectedNeighborDistance()));
        label(context, String.format("Closest distinct nodes: %.6g", metrics.minimumDistinctNodeDistance()));
        label(context, "Base length applies to connections, not all node pairs");
        if (builder.sharingMode() && builder.deformation() != null) {
            label(context, String.format("Maximum target error: %.4f%%", 100*builder.deformation().maximumRelativeEdgeError()));
            label(context, "Reuse rule: close five-tetrahedron edge rings");
            label(context, "Settled: " + builder.deformation().stationary());
            if (builder.deformation().assessment() != null)
                label(context, "Overlapping tetrahedron pairs: " + builder.deformation().assessment().overlappingTetrahedronPairs());
        }
        for (var status : visualizer.researchStatuses()) {
            label(context, status.code().toString());
            Nuklear.nk_layout_row_dynamic(context, builder.sharingMode() ? 160 : 65, 1);
            Nuklear.nk_label_wrap(context, status.explanation());
        }
        label(context, "Drag to rotate; right drag to pan; wheel to zoom");
        label(context, builder.sharingMode() ? "All positions relaxed toward target; not REM mechanics" : "Equal connected lengths; no mechanical relaxation");
        Integer selected = view.selectedNodeId();
        if (selected != null && selected < builder.snapshot().nodeCount()) {
            var node = visualizer.session().model().sortedNodes().get(selected);
            label(context, "Selected node: " + selected + "  Degree: " + node.neighborIds().size());
            label(context, "Neighbors: " + node.neighborIds().stream().sorted().map(String::valueOf).collect(Collectors.joining(", ")));
        }
    }
    private void layoutClosure(NkContext context) {
        label(context, "Controlled closure: five tetrahedra around edge 0-1");
        label(context, "Original nodes 8 and 11 are shared node 6");
        Nuklear.nk_layout_row_dynamic(context, rowHeightEdit, 1);
        double currentTarget = visualizer.session().settings().targetDistance();
        double target = Nuklear.nk_propertyd(context, "Base length target", 1e-6,
                currentTarget, 1e6, 0.5, 0.1f);
        if (Double.compare(target, currentTarget) != 0) visualizer.showTetrahedralClosure(target);
        Nuklear.nk_layout_row_dynamic(context, rowHeightEdit, 2);
        if (Nuklear.nk_button_label(context, "Return to builder")) {
            visualizer.returnToTetrahedralBuilder(); return;
        }
        if (Nuklear.nk_button_label(context, "Fit View")) visualizer.fitToView();
        var trial = visualizer.closureTrial();
        label(context, "7 nodes, 16 connections, 5 tetrahedra");
        label(context, String.format("Connected lengths %.6g .. %.6g",
                trial.result().metrics().minimumConnectedNeighborDistance(),
                trial.result().metrics().maximumConnectedNeighborDistance()));
        label(context, String.format("Maximum target deviation %.4f%%", 100 * trial.maximumRelativeEdgeError()));
        label(context, "Overlap pairs: " + trial.assessment().overlappingTetrahedronPairs()
                + "  Degenerate tetrahedra: " + trial.assessment().degenerateTetrahedra());
        label(context, "Settled layout: " + trial.stationary() + "  Trial steps: " + trial.iterations());
        var view = visualizer.viewState();
        Nuklear.nk_layout_row_dynamic(context, rowHeightEdit, 3);
        if (Nuklear.nk_button_label(context, "Labels " + on(view.showNodeLabels())))
            view.setShowNodeLabels(!view.showNodeLabels());
        if (Nuklear.nk_button_label(context, "Lines " + on(view.showConnections())))
            view.setShowConnections(!view.showConnections());
        if (Nuklear.nk_button_label(context, "Axes " + on(view.showAxes())))
            view.setShowAxes(!view.showAxes());
        for (var status : visualizer.researchStatuses()) {
            label(context, status.code().toString());
            Nuklear.nk_layout_row_dynamic(context, 90, 1);
            Nuklear.nk_label_wrap(context, status.explanation());
        }
        label(context, "Local closure experiment; not a crystal qualification");
        label(context, "Drag to rotate; wheel to zoom");
    }
}
