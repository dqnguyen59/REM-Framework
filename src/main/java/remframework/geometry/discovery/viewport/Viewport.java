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

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

public final class Viewport implements AutoCloseable {
    private final WorkspaceController controller;
    private final OpenGlRenderer renderer;
    private double lastX;
    private double lastY;
    private boolean orbiting;
    private boolean panning;
    private boolean resizingDetails;
    private int windowWidth=1,windowHeight=1;
    private String lastConfigurationId = "";
    private long lastRenderRevision = Long.MIN_VALUE;
    private boolean lastTopologyPreview;
    private RenderDiagnostics diagnostics = new RenderDiagnostics(
            "", 0, 0, new org.joml.Vector3d(), 0, 1,
            new org.joml.Vector3d(), new org.joml.Vector3d(), .01, 100, 0, false);

    public Viewport(WorkspaceController controller) {
        this.controller = controller;
        renderer = new OpenGlRenderer();
    }

    public void render(int width, int height) {
        windowWidth=width;windowHeight=height;
        GeometryDiscoveryWorkspaceSnapshot snapshot = controller.workspace().snapshot();
        GeometryDiscoveryWorkspaceLayout.Bounds bounds =
                controller.layout(width, height).viewport();
        int openGlY = height - bounds.y() - bounds.height();
        GL11.glViewport(bounds.x(), openGlY, bounds.width(), bounds.height());
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(bounds.x(), openGlY, bounds.width(), bounds.height());
        GL11.glClearColor(.055f, .065f, .08f, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        long retrievalStarted=System.nanoTime();
        RenderModel phased = controller.phasedWorkflow().renderModel();
        DevelopmentTimingDiagnostics.record("viewport.render-model-retrieval",System.nanoTime()-retrievalStarted);
        GeometryDiscoveryModel model;
        GeometryDiscoverySettings settings;
        String configurationId;
        if (phased != null) {
            model = phased.model();
            settings = GeometryDiscoverySettings.defaults(1);
            configurationId = phased.configurationId();
            IncrementalSession incremental = controller.phasedWorkflow().incrementalSession();
            boolean frameRequested = !configurationId.equals(lastConfigurationId)
                    || phased.topologyPreview() != lastTopologyPreview;
            if (frameRequested) {
                long fitStarted=System.nanoTime();
                controller.viewState().fitToView(model);
                DevelopmentTimingDiagnostics.record("viewport.camera-fit",System.nanoTime()-fitStarted);
            }
            lastConfigurationId = configurationId;
            lastTopologyPreview = phased.topologyPreview();
            lastRenderRevision = phased.revision();
        } else {
            GeometryDiscoveryResult geometry = displayedGeometry(snapshot);
            model = geometry.model();
            settings = geometry.settings();
            configurationId = "legacy:" + model.name();
        }
        if(phased!=null)renderer.render(phased,settings,controller.viewState(),bounds.width(),bounds.height());
        else renderer.render(model, settings,controller.viewState(), bounds.width(), bounds.height());
        diagnostics = diagnostics(configurationId, model, phased, bounds.width(), bounds.height());
        controller.setRenderDiagnostics(diagnostics);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public void cursor(double x, double y) {
        if(resizingDetails){controller.setDiscoveryDetailsHeight((int)Math.max(GeometryDiscoveryWorkspaceLayout.MINIMUM_BOTTOM_HEIGHT,controller.layout(windowWidth,windowHeight).details().bottom()-y));}
        if (orbiting) controller.viewState().orbit((x - lastX) * .3, (y - lastY) * .3);
        if (panning) controller.viewState().pan(-(x - lastX) * .01, (y - lastY) * .01, 0);
        lastX = x;
        lastY = y;
    }

    public void button(int button, int action, int width, int height) {
        GeometryDiscoveryWorkspaceLayout.Bounds detail=controller.layout(width,height).details();
        if(button==0&&action==1&&Math.abs(lastY-detail.y())<=6){resizingDetails=true;return;}
        if(button==0&&action==0&&resizingDetails){resizingDetails=false;return;}
        if (button == 0) {
            orbiting = action == 1;
            if (action == 0) select(lastX, lastY, width, height);
        }
        if (button == 1) panning = action == 1;
    }

    public void scroll(double amount) { controller.viewState().zoom(amount * .12); }

    public void frameGeometry() {
        RenderModel phased = controller.phasedWorkflow().renderModel();
        if (phased != null) controller.viewState().fitToView(phased.model());
        else controller.viewState().fitToView(displayedGeometry(controller.workspace().snapshot()).model());
    }

    public RenderDiagnostics diagnostics() { return diagnostics; }

    public void select(double mouseX, double mouseY, int width, int height) {
        GeometryDiscoveryWorkspaceLayout.Bounds bounds =
                controller.layout(width, height).viewport();
        if (mouseX < bounds.x() || mouseX >= bounds.right()
                || mouseY < bounds.y() || mouseY >= bounds.bottom()) {
            controller.viewState().setSelectedElement(null);
            return;
        }
        double localX = mouseX - bounds.x();
        double localY = mouseY - bounds.y();
        RenderModel renderModel=controller.phasedWorkflow().renderModel();
        GeometryDiscoveryModel model=renderModel==null?displayedGeometry(controller.workspace().snapshot()).model():renderModel.model();
        ViewFit fit = controller.viewState().calculateFit(model);
        Matrix4f matrix = renderer.viewProjection(controller.viewState(), fit,
                bounds.width(), bounds.height());
        SelectedElement closest = null;
        double closestDistance = 14;
        SelectionFilters filters=controller.viewState().selectionFilters();
        if(filters.vertices())for (DiscoveryNode node : model.nodes()) {
            Vector4f projected = new Vector4f((float) node.position().x,
                    (float) node.position().y, (float) node.position().z, 1).mul(matrix);
            if (projected.w <= 0) continue;
            double screenX = (projected.x / projected.w * .5 + .5) * bounds.width();
            double screenY = (1 - (projected.y / projected.w * .5 + .5)) * bounds.height();
            double distance = Math.hypot(localX - screenX, localY - screenY);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = SelectedElement.vertex(node.id());
            }
        }
        if(closest==null&&filters.center()&&renderModel!=null&&controller.viewState().showGeometryCenter()){
            double[] q=screen(new org.joml.Vector3d(),matrix,bounds.width(),bounds.height());
            if(q!=null&&Math.hypot(localX-q[0],localY-q[1])<16)closest=SelectedElement.center();
        }
        if(closest==null&&filters.edges())for(DiscoveryConnection edge:model.connections()){
            double[] a=screen(model.nodes().get(edge.firstNodeId()).position(),matrix,bounds.width(),bounds.height());
            double[] b=screen(model.nodes().get(edge.secondNodeId()).position(),matrix,bounds.width(),bounds.height());
            if(a!=null&&b!=null&&segmentDistance(localX,localY,a[0],a[1],b[0],b[1])<8){closest=SelectedElement.edge(edge.firstNodeId(),edge.secondNodeId());break;}
        }
        if(closest==null&&filters.faces()&&renderModel!=null)for(DerivedFace face:renderModel.derivedFaces()){
            java.util.List<double[]> polygon=new java.util.ArrayList<>();for(int id:face.nodeIds()){double[] q=screen(model.nodes().get(id).position(),matrix,bounds.width(),bounds.height());if(q!=null)polygon.add(q);}if(polygon.size()==face.nodeIds().size()&&inside(localX,localY,polygon)){closest=SelectedElement.face(face.id());break;}
        }
        controller.viewState().setSelectedElement(closest);
    }

    private static double[] screen(org.joml.Vector3d p,Matrix4f matrix,int width,int height){Vector4f q=new Vector4f((float)p.x,(float)p.y,(float)p.z,1).mul(matrix);return q.w<=0?null:new double[]{(q.x/q.w*.5+.5)*width,(1-(q.y/q.w*.5+.5))*height};}
    private static double segmentDistance(double px,double py,double ax,double ay,double bx,double by){double dx=bx-ax,dy=by-ay;double d=dx*dx+dy*dy;if(d==0)return Math.hypot(px-ax,py-ay);double t=Math.max(0,Math.min(1,((px-ax)*dx+(py-ay)*dy)/d));return Math.hypot(px-(ax+t*dx),py-(ay+t*dy));}
    private static boolean inside(double x,double y,java.util.List<double[]> p){boolean result=false;for(int i=0,j=p.size()-1;i<p.size();j=i++){double[] a=p.get(i),b=p.get(j);if((a[1]>y)!=(b[1]>y)&&x<(b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0])result=!result;}return result;}

    @Override public void close() { renderer.close(); }

    private GeometryDiscoveryResult displayedGeometry(
            GeometryDiscoveryWorkspaceSnapshot snapshot) {
        return switch (controller.state().viewportSelection()) {
            case PREVIEW_CANDIDATE -> snapshot.preview() == null ? snapshot.geometry()
                    : snapshot.preview().geometryResult();
            case BEST_CANDIDATE -> snapshot.search() == null ? snapshot.geometry()
                    : snapshot.search().bestCandidate().geometryResult();
            case CURRENT_CANDIDATE -> snapshot.search() == null ? snapshot.geometry()
                    : snapshot.search().currentCandidate().geometryResult();
        };
    }

    private RenderDiagnostics diagnostics(String configurationId,
            GeometryDiscoveryModel model, RenderModel phased,
            int width, int height) {
        ViewFit fit = controller.viewState().calculateFit(model);
        double yaw = Math.toRadians(controller.viewState().yaw());
        double pitch = Math.toRadians(controller.viewState().pitch());
        org.joml.Vector3d target = new org.joml.Vector3d(controller.viewState().center());
        org.joml.Vector3d camera = new org.joml.Vector3d(
                target.x + controller.viewState().distance() * Math.cos(pitch) * Math.sin(yaw),
                target.y + controller.viewState().distance() * Math.sin(pitch),
                target.z + controller.viewState().distance() * Math.cos(pitch) * Math.cos(yaw));
        double cameraDistance = camera.distance(target);
        double near = Math.max(0.001, controller.viewState().distance() - fit.radius() * 1.5);
        double far = Math.max(near + 1, controller.viewState().distance() + fit.radius() * 3.0);
        boolean intersects = cameraDistance + fit.radius() >= near
                && cameraDistance - fit.radius() <= far;
        return new RenderDiagnostics(configurationId, model.nodes().size(),
                model.connections().size(), phased == null ? fit.center() : phased.transform().centroid(),
                fit.radius(), phased == null ? 1 : phased.transform().displayScale(), camera,
                target, near, far, cameraDistance, intersects);
    }
}
