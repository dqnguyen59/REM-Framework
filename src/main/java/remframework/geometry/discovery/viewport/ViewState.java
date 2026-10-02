package remframework.geometry.discovery.viewport;
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

import org.joml.Vector3d;

import java.util.List;

public final class ViewState {
    private final Vector3d center = new Vector3d();
    private double yaw = 35;
    private double pitch = 25;
    private double distance = 10;
    private boolean orthographic;
    private boolean showNodeLabels = true;
    private boolean showVertices = true;
    private boolean showConnections = true;
    private boolean showAxes = true;
    private boolean showBoundingBox = true;
    private boolean highlightFixedNodes = true;
    private boolean edgeErrorColoring = true;
    private boolean showNeighborDistanceLabels;
    private boolean showDerivedFaces=true,showAcceptedFaces=true,showRejectedFaces,showFaceNormals;
    private boolean showGeometryCenter=true,showCenterLabel=true,showCenterToFaceLines;
    private boolean showCenterToVertexLines,showSelectedFaceNormalOnly,showNormalAlignmentState;
    private boolean showEdgeLengths,showSelectedEdgeLengthOnly=true,showEdgeError,showEdgeTarget;
    private double normalDisplayLength=.35;
    private SelectionFilters selectionFilters=SelectionFilters.all();
    private SelectedElement selectedElement;
    private Integer selectedNodeId;
    private long fitInvocationCount;
    private int workflowPanelWidth=GeometryDiscoveryWorkspaceLayout.PREFERRED_LEFT_WIDTH;
    private InitializationTrailMode initializationTrailMode=InitializationTrailMode.OFF;

    public ViewFit fitToView(GeometryDiscoveryModel model) {
        fitInvocationCount++;
        ViewFit fit = calculateFit(model);
        center.set(fit.center());
        distance = fit.cameraDistance();
        return fit;
    }

    public ViewFit calculateFit(GeometryDiscoveryModel model) {
        List<DiscoveryNode> nodes = model.nodes();
        if (nodes.isEmpty()) {
            return new ViewFit(new Vector3d(), 1, 10, 0.01, 100);
        }
        Vector3d min = new Vector3d(Double.POSITIVE_INFINITY);
        Vector3d max = new Vector3d(Double.NEGATIVE_INFINITY);
        for (DiscoveryNode node : nodes) {
            min.min(node.position());
            max.max(node.position());
        }
        Vector3d calculatedCenter = new Vector3d(min).add(max).mul(0.5);
        double radius = 0;
        for (DiscoveryNode node : nodes)
            radius = Math.max(radius, node.position().distance(calculatedCenter));
        radius = Math.max(radius, 0.5);
        double calculatedDistance = Math.max(3.0, radius * 3.0);
        return new ViewFit(calculatedCenter, radius, calculatedDistance,
                Math.max(0.001, calculatedDistance - radius * 2.5),
                calculatedDistance + radius * 4.0);
    }

    public void orbit(double deltaYaw, double deltaPitch) {
        yaw += deltaYaw;
        pitch = Math.max(-89, Math.min(89, pitch + deltaPitch));
    }

    public void pan(double x, double y, double z) { center.add(x, y, z); }
    public void zoom(double amount) { distance = Math.max(0.01, distance * Math.exp(-amount)); }
    public void resetCamera() { yaw = 35; pitch = 25; center.zero(); distance = 10; }

    public Vector3d center() { return center; }
    public double yaw() { return yaw; }
    public double pitch() { return pitch; }
    public double distance() { return distance; }
    public long fitInvocationCount(){return fitInvocationCount;}
    public boolean orthographic() { return orthographic; }
    public void setOrthographic(boolean value) { orthographic = value; }
    public Integer selectedNodeId() { return selectedNodeId; }
    public void setSelectedNodeId(Integer value) { selectedNodeId = value; selectedElement=value==null?null:SelectedElement.vertex(value); }
    public SelectedElement selectedElement(){return selectedElement;}
    public void setSelectedElement(SelectedElement value){selectedElement=value;selectedNodeId=value!=null&&value.type()==SelectedElement.Type.VERTEX?Integer.valueOf(value.id()):null;}
    public SelectionFilters selectionFilters(){return selectionFilters;}
    public void setSelectionFilters(SelectionFilters value){selectionFilters=value;}
    public boolean showNodeLabels() { return showNodeLabels; }
    public void setShowNodeLabels(boolean value) { showNodeLabels = value; }
    public boolean showVertices() { return showVertices; }
    public void setShowVertices(boolean value) { showVertices = value; }
    public boolean showConnections() { return showConnections; }
    public void setShowConnections(boolean value) { showConnections = value; }
    public boolean showAxes() { return showAxes; }
    public void setShowAxes(boolean value) { showAxes = value; }
    public boolean showBoundingBox() { return showBoundingBox; }
    public void setShowBoundingBox(boolean value) { showBoundingBox = value; }
    public boolean highlightFixedNodes() { return highlightFixedNodes; }
    public void setHighlightFixedNodes(boolean value) { highlightFixedNodes = value; }
    public boolean edgeErrorColoring() { return edgeErrorColoring; }
    public void setEdgeErrorColoring(boolean value) { edgeErrorColoring = value; }
    public boolean showNeighborDistanceLabels() { return showNeighborDistanceLabels; }
    public void setShowNeighborDistanceLabels(boolean value) {
        showNeighborDistanceLabels = value;
    }
    public boolean showDerivedFaces(){return showDerivedFaces;} public void setShowDerivedFaces(boolean v){showDerivedFaces=v;}
    public boolean showAcceptedFaces(){return showAcceptedFaces;} public void setShowAcceptedFaces(boolean v){showAcceptedFaces=v;}
    public boolean showRejectedFaces(){return showRejectedFaces;} public void setShowRejectedFaces(boolean v){showRejectedFaces=v;}
    public boolean showFaceNormals(){return showFaceNormals;} public void setShowFaceNormals(boolean v){showFaceNormals=v;}
    public boolean showGeometryCenter(){return showGeometryCenter;} public void setShowGeometryCenter(boolean v){showGeometryCenter=v;}
    public boolean showCenterLabel(){return showCenterLabel;} public void setShowCenterLabel(boolean v){showCenterLabel=v;}
    public boolean showCenterToFaceLines(){return showCenterToFaceLines;} public void setShowCenterToFaceLines(boolean v){showCenterToFaceLines=v;}
    public boolean showCenterToVertexLines(){return showCenterToVertexLines;} public void setShowCenterToVertexLines(boolean v){showCenterToVertexLines=v;}
    public boolean showSelectedFaceNormalOnly(){return showSelectedFaceNormalOnly;} public void setShowSelectedFaceNormalOnly(boolean v){showSelectedFaceNormalOnly=v;}
    public boolean showNormalAlignmentState(){return showNormalAlignmentState;} public void setShowNormalAlignmentState(boolean v){showNormalAlignmentState=v;}
    public double normalDisplayLength(){return normalDisplayLength;} public void setNormalDisplayLength(double v){normalDisplayLength=Math.max(.01,v);}
    public boolean showEdgeLengths(){return showEdgeLengths;} public void setShowEdgeLengths(boolean v){showEdgeLengths=v;}
    public InitializationTrailMode initializationTrailMode(){return initializationTrailMode;} public void setInitializationTrailMode(InitializationTrailMode value){initializationTrailMode=value==null?InitializationTrailMode.OFF:value;}
    public boolean showSelectedEdgeLengthOnly(){return showSelectedEdgeLengthOnly;} public void setShowSelectedEdgeLengthOnly(boolean v){showSelectedEdgeLengthOnly=v;}
    public boolean showEdgeError(){return showEdgeError;} public void setShowEdgeError(boolean v){showEdgeError=v;}
    public boolean showEdgeTarget(){return showEdgeTarget;} public void setShowEdgeTarget(boolean v){showEdgeTarget=v;}
    public int workflowPanelWidth(){return workflowPanelWidth;}
    public void setWorkflowPanelWidth(int value){workflowPanelWidth=Math.max(GeometryDiscoveryWorkspaceLayout.MINIMUM_WORKFLOW_WIDTH,value);}
}
