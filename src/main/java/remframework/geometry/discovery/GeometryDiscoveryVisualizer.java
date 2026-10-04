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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class GeometryDiscoveryVisualizer {
    private final GeometryDiscoveryModel sourceModel;
    private final String sourceDescription;
    private final ViewState viewState = new ViewState();
    private TopologySearchMetadata topologySearchMetadata;
    private GeometryDiscoverySession session;
    private int iterationsPerFrame = 1;
    private List<GeometryConfigurationStatus> researchStatuses = List.of();
    private remframework.geometry.discovery.research.tetrahedral.FaceSharingTetrahedralBuilder tetrahedralBuilder;
    private remframework.geometry.discovery.research.tetrahedral.FaceSharingTetrahedralBuilder savedTetrahedralBuilder;
    private remframework.geometry.discovery.research.tetrahedral.FaceSharingTetrahedralClosure.Trial closureTrial;

    public GeometryDiscoveryVisualizer(GeometryDiscoveryModel model,
                                       GeometryDiscoverySettings settings,
                                       String sourceDescription) {
        sourceModel = model.copy();
        this.sourceDescription = sourceDescription;
        session = new GeometryDiscoverySession(sourceModel, settings);
        viewState.fitToView(session.model());
        if (!session.model().nodes().isEmpty())
            viewState.setSelectedNodeId(session.model().sortedNodes().getFirst().id());
    }

    public void updateFrame() {
        if (tetrahedralBuilder != null && tetrahedralBuilder.continuousLayout()) {
            tetrahedralBuilder.updateLayout(20);
            session=new GeometryDiscoverySession(tetrahedralBuilder.model(),GeometryDiscoverySettings.defaults(tetrahedralBuilder.baseLength()));
            researchStatuses=tetrahedralBuilder.statuses();return;
        }
        if (tetrahedralBuilder != null || closureTrial != null || !session.isRunning()) return;
        for (int i = 0; i < iterationsPerFrame && !session.isFinished(); i++) session.step();
    }

    public void start() { if (tetrahedralBuilder == null && closureTrial == null) session.start(); }
    public void pause() { session.pause(); }
    public void singleStep() { if (tetrahedralBuilder == null && closureTrial == null) session.step(); }
    public void reset() {
        if (closureTrial != null) { showTetrahedralClosure(session.settings().targetDistance()); return; }
        if (tetrahedralBuilder != null) { tetrahedralBuilder.reset(); refreshBuilder(); return; }
        session.reset();
        viewState.fitToView(session.model());
    }
    public void runToConvergence() { if (tetrahedralBuilder == null && closureTrial == null) session.runUntilConverged(); }
    public void fitToView() { viewState.fitToView(session.model()); }

    public void selectNextNode(int direction) {
        List<DiscoveryNode> nodes = session.model().sortedNodes();
        if (nodes.isEmpty()) {
            viewState.setSelectedNodeId(null);
            return;
        }
        int current = 0;
        for (int i = 0; i < nodes.size(); i++)
            if (java.util.Objects.equals(nodes.get(i).id(), viewState.selectedNodeId())) current = i;
        viewState.setSelectedNodeId(nodes.get(Math.floorMod(current + direction, nodes.size())).id());
    }

    public SelectedNodeMetrics selectedNodeMetrics() {
        if (viewState.selectedNodeId() == null) return null;
        return new SelectedNodeMetricsCalculator().calculate(session.model(),
                viewState.selectedNodeId(), session.settings().targetDistance());
    }

    public LocalDirectionalDiagnostics selectedNodeDirectionalDiagnostics() {
        if (viewState.selectedNodeId() == null) return null;
        return new DirectionalBalanceCalculator().calculate(session.model())
                .localDiagnostics().get(viewState.selectedNodeId());
    }

    public Path exportCandidate(Path path) throws IOException {
        return new GeometryDiscoveryExporter().export(session.result(), path);
    }

    public Path saveMetricsReport(Path path) throws IOException {
        return new GeometryDiscoveryMetricsReportWriter().write(session.result(), path);
    }

    public void updateSettings(GeometryDiscoverySettings settings) {
        if (tetrahedralBuilder != null || closureTrial != null) return;
        session.updateSettings(settings);
    }

    public remframework.geometry.discovery.research.tetrahedral.FaceSharingTetrahedralBuilder tetrahedralBuilder() {
        return tetrahedralBuilder;
    }
    public void enableTetrahedralBuilder(double baseLength, int maximumNodes) {
        enableTetrahedralBuilder(baseLength, maximumNodes, false);
    }
    public void enableTetrahedralBuilder(double baseLength, int maximumNodes, boolean sharingMode) {
        closureTrial = null; savedTetrahedralBuilder = null;
        tetrahedralBuilder = new remframework.geometry.discovery.research.tetrahedral.FaceSharingTetrahedralBuilder(baseLength, maximumNodes, sharingMode);
        viewState.setShowConnections(true); viewState.setShowVertices(true);
        viewState.setEdgeErrorColoring(false);
        refreshBuilder();
    }
    public void enableContinuousTetrahedralLayout() {
        if(tetrahedralBuilder!=null && tetrahedralBuilder.sharingMode()) { tetrahedralBuilder.enableContinuousLayout();refreshBuilder(); }
    }
    public void addTetrahedralNode() {
        if (tetrahedralBuilder != null) { tetrahedralBuilder.cancelBatch(); tetrahedralBuilder.addNode(); refreshBuilder(); }
    }
    public void addTetrahedralNodes(int count) {
        if (tetrahedralBuilder != null) { if(tetrahedralBuilder.continuousLayout()) tetrahedralBuilder.queueNodes(count); else tetrahedralBuilder.addNodes(count); refreshBuilder(); }
    }
    public void growTetrahedralToTotal(int total) {
        if(tetrahedralBuilder!=null) { tetrahedralBuilder.queueToTotal(total);refreshBuilder(); }
    }
    public void removeTetrahedralNode() {
        if (tetrahedralBuilder != null) { tetrahedralBuilder.removeNode(); refreshBuilder(); }
    }
    public void setTetrahedralBaseLength(double length) {
        if (tetrahedralBuilder != null) { tetrahedralBuilder.setBaseLength(length); refreshBuilder(); }
    }
    private void refreshBuilder() {
        session = new GeometryDiscoverySession(tetrahedralBuilder.model(),
                GeometryDiscoverySettings.defaults(tetrahedralBuilder.baseLength()));
        researchStatuses = tetrahedralBuilder.statuses();
        Integer selected = viewState.selectedNodeId();
        if (selected == null || selected >= session.model().nodes().size())
            viewState.setSelectedNodeId(session.model().nodes().size() - 1);
        viewState.fitToView(session.model());
    }
    public remframework.geometry.discovery.research.tetrahedral.FaceSharingTetrahedralClosure.Trial closureTrial() {
        return closureTrial;
    }
    public void showTetrahedralClosure(double targetLength) {
        var trial = remframework.geometry.discovery.research.tetrahedral.FaceSharingTetrahedralClosure.run(targetLength, 2000);
        if (trial.result() == null || trial.assessment() == null) {
            researchStatuses = trial.statuses(); return;
        }
        if (tetrahedralBuilder != null) savedTetrahedralBuilder = tetrahedralBuilder;
        tetrahedralBuilder = null;
        closureTrial = trial;
        session = new GeometryDiscoverySession(trial.result().model(), trial.result().settings());
        researchStatuses = trial.statuses();
        viewState.setShowConnections(true); viewState.setShowVertices(true); viewState.setEdgeErrorColoring(false);
        viewState.setSelectedNodeId(6);
        viewState.fitToView(session.model());
    }
    public void returnToTetrahedralBuilder() {
        double target = session.settings().targetDistance();
        closureTrial = null;
        if (savedTetrahedralBuilder != null) {
            tetrahedralBuilder = savedTetrahedralBuilder; savedTetrahedralBuilder = null; refreshBuilder();
        } else enableTetrahedralBuilder(target, 1000);
    }
    public List<GeometryConfigurationStatus> researchStatuses() { return researchStatuses; }
    public void setResearchStatuses(List<GeometryConfigurationStatus> statuses) {
        researchStatuses = List.copyOf(statuses);
    }
    public GeometryDiscoverySession session() { return session; }
    public ViewState viewState() { return viewState; }
    public GeometryDiscoveryResult result() { return session.result(); }
    public String sourceDescription() { return sourceDescription; }
    public TopologySearchMetadata topologySearchMetadata() { return topologySearchMetadata; }
    public void setTopologySearchMetadata(TopologySearchMetadata value) {
        topologySearchMetadata = value;
    }
    public int iterationsPerFrame() { return iterationsPerFrame; }
    public void setIterationsPerFrame(int value) {
        iterationsPerFrame = Math.max(1, Math.min(10_000, value));
    }
}
