package remframework.geometry.discovery;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

public final class GeometryDiscoveryViewport implements AutoCloseable {
    private final GeometryDiscoveryWorkspaceController controller;
    private final GeometryDiscoveryOpenGlRenderer renderer;
    private double lastX;
    private double lastY;
    private boolean orbiting;
    private boolean panning;

    public GeometryDiscoveryViewport(GeometryDiscoveryWorkspaceController controller) {
        this.controller = controller;
        renderer = new GeometryDiscoveryOpenGlRenderer();
    }

    public void render(int width, int height) {
        GeometryDiscoveryWorkspaceSnapshot snapshot = controller.workspace().snapshot();
        GeometryDiscoveryWorkspaceLayout.Bounds bounds =
                controller.layout(width, height).viewport();
        int openGlY = height - bounds.y() - bounds.height();
        GL11.glViewport(bounds.x(), openGlY, bounds.width(), bounds.height());
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(bounds.x(), openGlY, bounds.width(), bounds.height());
        GL11.glClearColor(.055f, .065f, .08f, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GeometryDiscoveryResult geometry = displayedGeometry(snapshot);
        renderer.render(geometry.model(), geometry.settings(),
                controller.viewState(), bounds.width(), bounds.height());
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public void cursor(double x, double y) {
        if (orbiting) controller.viewState().orbit((x - lastX) * .3, (y - lastY) * .3);
        if (panning) controller.viewState().pan(-(x - lastX) * .01, (y - lastY) * .01, 0);
        lastX = x;
        lastY = y;
    }

    public void button(int button, int action, int width, int height) {
        if (button == 0) {
            orbiting = action == 1;
            if (action == 0) select(lastX, lastY, width, height);
        }
        if (button == 1) panning = action == 1;
    }

    public void scroll(double amount) { controller.viewState().zoom(amount * .12); }

    public void select(double mouseX, double mouseY, int width, int height) {
        GeometryDiscoveryWorkspaceLayout.Bounds bounds =
                controller.layout(width, height).viewport();
        if (mouseX < bounds.x() || mouseX >= bounds.right()
                || mouseY < bounds.y() || mouseY >= bounds.bottom()) {
            controller.viewState().setSelectedNodeId(null);
            return;
        }
        double localX = mouseX - bounds.x();
        double localY = mouseY - bounds.y();
        GeometryDiscoveryModel model = displayedGeometry(
                controller.workspace().snapshot()).model();
        GeometryViewFit fit = controller.viewState().calculateFit(model);
        Matrix4f matrix = renderer.viewProjection(controller.viewState(), fit,
                bounds.width(), bounds.height());
        Integer closest = null;
        double closestDistance = 14;
        for (DiscoveryNode node : model.nodes()) {
            Vector4f projected = new Vector4f((float) node.position().x,
                    (float) node.position().y, (float) node.position().z, 1).mul(matrix);
            if (projected.w <= 0) continue;
            double screenX = (projected.x / projected.w * .5 + .5) * bounds.width();
            double screenY = (1 - (projected.y / projected.w * .5 + .5)) * bounds.height();
            double distance = Math.hypot(localX - screenX, localY - screenY);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = node.id();
            }
        }
        controller.viewState().setSelectedNodeId(closest);
    }

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
}
