package remframework.geometry.discovery;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class GeometryDiscoveryVisualizer {
    private final GeometryDiscoveryModel sourceModel;
    private final String sourceDescription;
    private final GeometryDiscoveryViewState viewState = new GeometryDiscoveryViewState();
    private TopologySearchMetadata topologySearchMetadata;
    private GeometryDiscoverySession session;
    private int iterationsPerFrame = 1;

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
        if (!session.isRunning()) return;
        for (int i = 0; i < iterationsPerFrame && !session.isFinished(); i++) session.step();
    }

    public void start() { session.start(); }
    public void pause() { session.pause(); }
    public void singleStep() { session.step(); }
    public void reset() {
        session.reset();
        viewState.fitToView(session.model());
    }
    public void runToConvergence() { session.runUntilConverged(); }
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

    public Path exportCandidate(Path path) throws IOException {
        return new GeometryDiscoveryExporter().export(session.result(), path);
    }

    public Path saveMetricsReport(Path path) throws IOException {
        return new GeometryDiscoveryMetricsReportWriter().write(session.result(), path);
    }

    public void updateSettings(GeometryDiscoverySettings settings) {
        session.updateSettings(settings);
    }

    public GeometryDiscoverySession session() { return session; }
    public GeometryDiscoveryViewState viewState() { return viewState; }
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
