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
    private remframework.geometry.discovery.research.diamond.DiamondMechanics diamondMotion;
    private GeometryDiscoveryModel diamondRest;
    private remframework.geometry.discovery.research.diamond.DiamondCanonicalAdapter diamondCanonical;
    public boolean diamondCanonicalActive() { return diamondCanonical!=null; }
    private int diamondSteps;
    private boolean diamondPaused;
    public boolean diamondMotionActive() { return diamondMotion!=null || diamondCanonical!=null; }
    public int diamondMotionSteps() { return diamondSteps; }
    public void toggleDiamondMotion() { diamondPaused=!diamondPaused; }
    public void startDiamondMotion() {
        diamondCanonical=null;
        diamondRest=remframework.geometry.discovery.research.diamond.DiamondReference.create(5,session.settings().targetDistance());
        diamondMotion=new remframework.geometry.discovery.research.diamond.DiamondMechanics(diamondRest,.5,.1);
        var center=new org.joml.Vector3d();diamondRest.nodes().forEach(n->center.add(n.position()));center.div(diamondRest.nodes().size());
        int source=diamondRest.nodes().stream().filter(n->n.neighborIds().size()==4)
            .min(java.util.Comparator.comparingDouble(n->n.position().distanceSquared(center))).orElseThrow().id();
        diamondMotion.displace(source,new org.joml.Vector3d(.001,0,0));diamondSteps=0;diamondPaused=false;
        viewState.setSelectedNodeId(source);
        viewState.fitToView(diamondRest);
    }

    public void startDiamondCanonicalMotion() {
        startDiamondMotion();diamondMotion=null;
        diamondCanonical=new remframework.geometry.discovery.research.diamond.DiamondCanonicalAdapter(diamondRest,.5,1,
            remframework.geometry.discovery.research.diamond.DiamondCanonicalAdapter.Normalization.RESEARCH_FOUR);
        diamondCanonical.displace(viewState.selectedNodeId(),new org.joml.Vector3d(.001,0,0));
    }

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

    public boolean diamondReference() { return session.model().name().equals("diamond-reference"); }
    public void showDiamondReference(double length) {
        diamondMotion=null;diamondCanonical=null;
        session=new GeometryDiscoverySession(GeometryDiscoveryExamples.create("diamond-reference",length),GeometryDiscoverySettings.defaults(length));
        topologySearchMetadata=null;viewState.setSelectedNodeId(0);viewState.setShowConnections(true);
        viewState.setShowVertices(true);viewState.setHighlightFixedNodes(false);viewState.fitToView(session.model());
    }
    public void updateFrame() {
        if(diamondMotionActive()) {
            if(!diamondPaused && diamondSteps<200) { if(diamondCanonical!=null) diamondCanonical.step();else diamondMotion.step();diamondSteps++; }
            var nodes=new java.util.ArrayList<DiscoveryNode>();
            for(int id=0;id<diamondRest.nodes().size();id++) {
                var rest=diamondRest.nodes().get(id).position();
                var displayed=(diamondCanonical!=null?diamondCanonical.position(id):diamondMotion.position(id)).sub(rest).mul(1000).add(rest);
                nodes.add(new DiscoveryNode(id,displayed,true));
            }
            session=new GeometryDiscoverySession(new GeometryDiscoveryModel("diamond-reference","Disturbance preview; displacement magnified 1000x",nodes,diamondRest.connections()),session.settings());
            return;
        }
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
        session.updateSettings(settings);
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
