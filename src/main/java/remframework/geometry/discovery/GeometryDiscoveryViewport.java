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
        GL11.glViewport(0, 0, width, height);
        GL11.glClearColor(.055f, .065f, .08f, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GeometryDiscoveryResult geometry = snapshot.preview() == null
                ? snapshot.geometry() : snapshot.preview().geometryResult();
        renderer.render(geometry.model(), geometry.settings(),
                controller.viewState(), width, height);
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
        GeometryDiscoveryModel model = controller.workspace().snapshot().geometry().model();
        GeometryViewFit fit = controller.viewState().calculateFit(model);
        Matrix4f matrix = renderer.viewProjection(controller.viewState(), fit, width, height);
        Integer closest = null;
        double closestDistance = 14;
        for (DiscoveryNode node : model.nodes()) {
            Vector4f projected = new Vector4f((float) node.position().x,
                    (float) node.position().y, (float) node.position().z, 1).mul(matrix);
            if (projected.w <= 0) continue;
            double screenX = (projected.x / projected.w * .5 + .5) * width;
            double screenY = (1 - (projected.y / projected.w * .5 + .5)) * height;
            double distance = Math.hypot(mouseX - screenX, mouseY - screenY);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = node.id();
            }
        }
        controller.viewState().setSelectedNodeId(closest);
    }

    @Override public void close() { renderer.close(); }
}
