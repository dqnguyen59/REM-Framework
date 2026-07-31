package remframework.geometry.discovery;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GeometryDiscoveryOpenGlRenderer implements AutoCloseable {
    private final int program;
    private final int vao;
    private final int vbo;
    private final int matrixLocation;

    public GeometryDiscoveryOpenGlRenderer() {
        program = createProgram();
        matrixLocation = GL20.glGetUniformLocation(program, "viewProjection");
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 24, 0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 24, 12);
        GL30.glBindVertexArray(0);
    }

    public void render(GeometryDiscoveryVisualizer visualizer, int width, int height) {
        render(visualizer.session().model(), visualizer.session().settings(),
                visualizer.viewState(), width, height);
    }

    public void render(GeometryDiscoveryModel model, GeometryDiscoverySettings settings,
                       GeometryDiscoveryViewState view, int width, int height) {
        GeometryViewFit fit = view.calculateFit(model);
        Matrix4f matrix = viewProjection(view, fit, width, height);
        GL20.glUseProgram(program);
        GL20.glUniformMatrix4fv(matrixLocation, false, matrix.get(new float[16]));
        GL30.glBindVertexArray(vao);

        List<Float> lines = new ArrayList<>();
        if (view.showAxes()) addAxes(lines, fit);
        addGrid(lines, fit);
        if (view.showBoundingBox()) addBoundingBox(lines, model);
        draw(lines, GL11.GL_LINES, 1.5f);
        if (view.showConnections()) {
            List<Float> tooShort = new ArrayList<>();
            List<Float> satisfied = new ArrayList<>();
            List<Float> tooLong = new ArrayList<>();
            addConnections(tooShort, satisfied, tooLong, model, settings,
                    view.edgeErrorColoring());
            // Width as well as color distinguishes the three states.
            draw(tooShort, GL11.GL_LINES, 1.5f);
            draw(tooLong, GL11.GL_LINES, 2.75f);
            draw(satisfied, GL11.GL_LINES, 4f);
        }

        List<Float> points = new ArrayList<>();
        for (DiscoveryNode node : model.nodes()) {
            if (!view.showVertices()) continue;
            boolean selected = java.util.Objects.equals(node.id(), view.selectedNodeId());
            float r = selected ? 1f : node.fixedPosition() && view.highlightFixedNodes() ? 1f : 0.2f;
            float g = selected ? 0.85f : node.fixedPosition() && view.highlightFixedNodes() ? 0.25f : 0.65f;
            float b = selected ? 0.1f : node.fixedPosition() && view.highlightFixedNodes() ? 0.15f : 1f;
            vertex(points, node.position(), r, g, b);
        }
        GL11.glPointSize(11f);
        draw(points, GL11.GL_POINTS, 1f);
        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);
    }

    public Matrix4f viewProjection(GeometryDiscoveryViewState view, GeometryViewFit fit,
                                   int width, int height) {
        double yaw = Math.toRadians(view.yaw());
        double pitch = Math.toRadians(view.pitch());
        Vector3f center = new Vector3f((float) view.center().x, (float) view.center().y,
                (float) view.center().z);
        Vector3f eye = new Vector3f(
                center.x + (float) (view.distance() * Math.cos(pitch) * Math.sin(yaw)),
                center.y + (float) (view.distance() * Math.sin(pitch)),
                center.z + (float) (view.distance() * Math.cos(pitch) * Math.cos(yaw)));
        float aspect = Math.max(1, width) / (float) Math.max(1, height);
        float dynamicNear = (float) Math.max(0.001, view.distance() - fit.radius() * 1.5);
        float dynamicFar = (float) Math.max(dynamicNear + 1,
                view.distance() + fit.radius() * 3.0);
        Matrix4f projection = new Matrix4f();
        if (view.orthographic()) {
            float size = (float) Math.max(1, fit.radius() * 1.5);
            projection.setOrtho(-size * aspect, size * aspect, -size, size,
                    dynamicNear, dynamicFar);
        } else {
            projection.setPerspective((float) Math.toRadians(45), aspect,
                    dynamicNear, dynamicFar);
        }
        return projection.mul(new Matrix4f().lookAt(eye, center, new Vector3f(0, 1, 0)));
    }

    private void addConnections(List<Float> tooShort, List<Float> satisfied,
                                List<Float> tooLong, GeometryDiscoveryModel model,
                                GeometryDiscoverySettings settings, boolean errorColoring) {
        Map<Integer, DiscoveryNode> byId = new HashMap<>();
        for (DiscoveryNode node : model.nodes()) byId.put(node.id(), node);
        for (DiscoveryConnection connection : model.connections()) {
            DiscoveryNode a = byId.get(connection.firstNodeId());
            DiscoveryNode b = byId.get(connection.secondNodeId());
            if (a == null || b == null) continue;
            double distance = a.position().distance(b.position());
            double signedError = (distance - settings.targetDistance())
                    / settings.targetDistance();
            double tolerance = Math.max(1e-9, settings.convergenceTolerance());
            List<Float> vertices = Math.abs(signedError) <= tolerance ? satisfied
                    : signedError < 0 ? tooShort : tooLong;
            float r = !errorColoring ? .65f : signedError > tolerance ? .95f : .2f;
            float g = !errorColoring ? .7f
                    : Math.abs(signedError) <= tolerance ? .9f : .45f;
            float blue = !errorColoring ? .25f : signedError < -tolerance ? 1f : .2f;
            vertex(vertices, a.position(), r, g, blue);
            vertex(vertices, b.position(), r, g, blue);
        }
    }

    private void addAxes(List<Float> v, GeometryViewFit fit) {
        double length = Math.max(1, fit.radius() * 1.5);
        Vector3d o = new Vector3d();
        line(v, o, new Vector3d(length, 0, 0), 1, 0.2f, 0.2f);
        line(v, o, new Vector3d(0, length, 0), 0.2f, 1, 0.2f);
        line(v, o, new Vector3d(0, 0, length), 0.2f, 0.5f, 1);
    }

    private void addGrid(List<Float> v, GeometryViewFit fit) {
        double size = Math.max(1, Math.ceil(fit.radius()));
        double step = Math.max(1, Math.pow(10, Math.floor(Math.log10(size))) / 2);
        for (double p = -size; p <= size + 1e-9; p += step) {
            line(v, new Vector3d(-size, 0, p), new Vector3d(size, 0, p),
                    0.18f, 0.18f, 0.2f);
            line(v, new Vector3d(p, 0, -size), new Vector3d(p, 0, size),
                    0.18f, 0.18f, 0.2f);
        }
    }

    private void addBoundingBox(List<Float> v, GeometryDiscoveryModel model) {
        if (model.nodes().isEmpty()) return;
        Vector3d min = new Vector3d(Double.POSITIVE_INFINITY);
        Vector3d max = new Vector3d(Double.NEGATIVE_INFINITY);
        for (DiscoveryNode node : model.nodes()) {
            min.min(node.position());
            max.max(node.position());
        }
        Vector3d[] c = {
                new Vector3d(min.x, min.y, min.z), new Vector3d(max.x, min.y, min.z),
                new Vector3d(min.x, max.y, min.z), new Vector3d(max.x, max.y, min.z),
                new Vector3d(min.x, min.y, max.z), new Vector3d(max.x, min.y, max.z),
                new Vector3d(min.x, max.y, max.z), new Vector3d(max.x, max.y, max.z)};
        int[][] edges = {{0,1},{0,2},{1,3},{2,3},{4,5},{4,6},{5,7},{6,7},
                {0,4},{1,5},{2,6},{3,7}};
        for (int[] edge : edges) line(v, c[edge[0]], c[edge[1]], 0.7f, 0.4f, 0.15f);
    }

    private void line(List<Float> v, Vector3d a, Vector3d b, float r, float g, float blue) {
        vertex(v, a, r, g, blue);
        vertex(v, b, r, g, blue);
    }

    private void vertex(List<Float> v, Vector3d p, float r, float g, float b) {
        v.add((float) p.x); v.add((float) p.y); v.add((float) p.z);
        v.add(r); v.add(g); v.add(b);
    }

    private void draw(List<Float> vertices, int mode, float lineWidth) {
        if (vertices.isEmpty()) return;
        float[] data = new float[vertices.size()];
        for (int i = 0; i < data.length; i++) data[i] = vertices.get(i);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_DYNAMIC_DRAW);
        GL11.glLineWidth(lineWidth);
        GL11.glDrawArrays(mode, 0, data.length / 6);
    }

    private int createProgram() {
        String vertex = """
                #version 330 core
                layout(location=0) in vec3 position;
                layout(location=1) in vec3 color;
                uniform mat4 viewProjection;
                out vec3 vertexColor;
                void main() {
                    gl_Position = viewProjection * vec4(position, 1.0);
                    vertexColor = color;
                }
                """;
        String fragment = """
                #version 330 core
                in vec3 vertexColor;
                out vec4 outputColor;
                void main() { outputColor = vec4(vertexColor, 1.0); }
                """;
        int vertexShader = shader(GL20.GL_VERTEX_SHADER, vertex);
        int fragmentShader = shader(GL20.GL_FRAGMENT_SHADER, fragment);
        int result = GL20.glCreateProgram();
        GL20.glAttachShader(result, vertexShader);
        GL20.glAttachShader(result, fragmentShader);
        GL20.glLinkProgram(result);
        if (GL20.glGetProgrami(result, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
            throw new IllegalStateException(GL20.glGetProgramInfoLog(result));
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
        return result;
    }

    private int shader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
            throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));
        return shader;
    }

    @Override public void close() {
        GL20.glDeleteProgram(program);
        GL15.glDeleteBuffers(vbo);
        GL30.glDeleteVertexArrays(vao);
    }
}
