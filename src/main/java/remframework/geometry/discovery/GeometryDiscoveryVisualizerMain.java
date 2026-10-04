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

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import remframework.gui.controller.NuklearController;
import remframework.gui.model.NuklearModel;
import remframework.gui.view.NuklearView;
import remframework.settings.AppSettings;

import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GeometryDiscoveryVisualizerMain {
    private GeometryDiscoveryVisualizerMain() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parse(args);
        LoadedDiscoveryCandidate loaded;
        boolean incrementalBuilder = false;
        boolean controlledClosure = false;
        java.util.List<GeometryConfigurationStatus> researchStatuses = java.util.List.of();
        if (options.containsKey("file")) {
            loaded = new GeometryDiscoveryCandidateLoader().load(Path.of(options.get("file")));
        } else {
            String example = options.getOrDefault("model", "tetrahedron");
            double target = Double.parseDouble(options.getOrDefault("target", "10"));
            GeometryDiscoveryModel model;
            GeometryDiscoverySettings settings = GeometryDiscoverySettings.defaults(target);
            if ("tetrahedral-closure".equalsIgnoreCase(example)) {
                var candidate = remframework.geometry.discovery.research.tetrahedral.FaceSharingTetrahedralClosure.candidate(target);
                if (candidate.model() == null) {
                    System.out.println(candidate.status().code() + ": " + candidate.status().explanation());
                    return;
                }
                model = candidate.model();
                controlledClosure = true;
            } else if ("tetrahedral-builder".equalsIgnoreCase(example)) {
                int maximumNodes = Integer.parseInt(options.getOrDefault("max-nodes", "1000"));
                var builder = new remframework.geometry.discovery.research.tetrahedral.FaceSharingTetrahedralBuilder(target, maximumNodes,
                        Boolean.parseBoolean(options.getOrDefault("reuse-node-8", "true")));
                model = builder.model();
                researchStatuses = builder.statuses();
                incrementalBuilder = true;
            } else if ("tetrahedral-network".equalsIgnoreCase(example)) {
                int generation = Integer.parseInt(options.getOrDefault("generation", "3"));
                int maximumNodes = Integer.parseInt(options.getOrDefault("max-nodes", "1000"));
                var preview = remframework.geometry.discovery.research.tetrahedral.FaceSharingTetrahedralPreview
                        .create(generation, maximumNodes, target);
                researchStatuses = preview.statuses();
                for (var status : preview.statuses())
                    System.out.println(status.code() + ": " + status.explanation());
                if (preview.model() == null) return;
                model = preview.model();
                String layout = options.getOrDefault("layout", "reflected");
                if ("soft-separation".equals(layout)) {
                    int iterations = Integer.parseInt(options.getOrDefault("layout-iterations", "2000"));
                    settings = settings.withMaximumIterations(iterations);
                    var trial = remframework.geometry.discovery.research.tetrahedral.FaceSharingTetrahedralRelaxation.run(preview, settings);
                    model = trial.result().model();
                    researchStatuses = trial.statuses();
                    for (var status : researchStatuses)
                        System.out.println(status.code() + ": " + status.explanation());
                } else if (!"reflected".equals(layout)) {
                    System.out.println("INVALID_PARAMETERS: --layout must be reflected or soft-separation.");
                    return;
                }
            } else {
                model = GeometryDiscoveryExamples.create(example, target);
            }
            GeometryDiscoveryResult initial = new GeometryDiscoverySession(model, settings).result();
            loaded = new LoadedDiscoveryCandidate(model, settings, initial, null);
        }
        GeometryDiscoveryVisualizer visualizer = new GeometryDiscoveryVisualizer(
                loaded.model(), loaded.settings(),
                loaded.sourcePath() == null ? "built-in" : loaded.sourcePath().toString());
        visualizer.setResearchStatuses(researchStatuses);
        if (controlledClosure) {
            visualizer.showTetrahedralClosure(loaded.settings().targetDistance());
            visualizer.researchStatuses().forEach(s -> System.out.println(s.code() + ": " + s.explanation()));
        }
        if (incrementalBuilder) {
            visualizer.enableTetrahedralBuilder(loaded.settings().targetDistance(),
                    Integer.parseInt(options.getOrDefault("max-nodes", "1000")),
                    Boolean.parseBoolean(options.getOrDefault("reuse-node-8", "true")));
            visualizer.enableContinuousTetrahedralLayout();
        }
        if (incrementalBuilder) {
            int attachments = Integer.parseInt(options.getOrDefault("attachments", "0"));
            if (attachments < 0 || attachments > 1000) {
                System.out.println("INVALID_PARAMETERS: --attachments must be between 0 and 1000.");
                return;
            }
            for (int i = 0; i < attachments; i++) {
                if (!visualizer.tetrahedralBuilder().canAdd()) break;
                int previousTetrahedra = visualizer.tetrahedralBuilder().snapshot().tetrahedra().size();
                visualizer.addTetrahedralNode();
                if (visualizer.tetrahedralBuilder().snapshot().tetrahedra().size() == previousTetrahedra) break;
            }
            var builder = visualizer.tetrahedralBuilder();
            System.out.println("Sharing mode=" + builder.sharingMode()
                    + "; nodes=" + builder.snapshot().nodeCount() + "; connections=" + builder.snapshot().edges().size());
            if (builder.snapshot().nodeCount() > 8)
                System.out.println("Node 8 neighbors: " + visualizer.session().model().nodes().get(8).neighborIds());
        }
        if ("face-sharing-tetrahedral-preview".equals(loaded.model().name())) {
            visualizer.viewState().setShowConnections(true);
            visualizer.viewState().setShowVertices(true);
            visualizer.viewState().setEdgeErrorColoring(false);
            System.out.println(loaded.model().description());
        }
        if (loaded.sourcePath() != null)
            visualizer.setTopologySearchMetadata(
                    new TopologySearchMetadataLoader().load(loaded.sourcePath()));
        if (Boolean.parseBoolean(options.getOrDefault("relax", "false")))
            visualizer.runToConvergence();
        int frameLimit = Integer.parseInt(options.getOrDefault("frames", "0"));
        Path outputDirectory = Path.of(options.getOrDefault("output-dir", "lattice/candidates"));
        runWindow(visualizer, outputDirectory, frameLimit);
    }

    private static void runWindow(GeometryDiscoveryVisualizer visualizer,
                                  Path outputDirectory, int frameLimit) {
        AppSettings.getInstance().setCurrentFilename(Path.of(
                System.getProperty("java.io.tmpdir"),
                "rem-geometry-discovery-visualizer.ini").toString());
        GLFWErrorCallback errorCallback = GLFWErrorCallback.createPrint(System.err);
        errorCallback.set();
        if (!GLFW.glfwInit()) throw new IllegalStateException("Unable to initialize GLFW.");
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        long window = GLFW.glfwCreateWindow(visualizer.tetrahedralBuilder() == null && visualizer.closureTrial() == null ? 860 : 1280,
                visualizer.tetrahedralBuilder() == null && visualizer.closureTrial() == null ? 640 : 800,
                visualizer.tetrahedralBuilder() != null && visualizer.tetrahedralBuilder().sharingMode()
                        ? "REM Framework - Shared-node Builder (desired total)"
                        : "REM Framework Geometry Discovery Visualizer", MemoryUtil.NULL, MemoryUtil.NULL);
        if (window == MemoryUtil.NULL) throw new IllegalStateException("Unable to create window.");
        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1);
        GL.createCapabilities();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        NuklearModel nuklearModel = new NuklearModel();
        NuklearView nuklearView = new NuklearView(nuklearModel);
        nuklearView.init(window);
        NuklearController nuklearController =
                new NuklearController(nuklearView.getContext(), nuklearModel);
        nuklearModel.addToVisibleFrames(new GeometryDiscoveryInfoFrame(
                nuklearModel, visualizer, outputDirectory));
        installCallbacks(window, visualizer, nuklearController);

        try (OpenGlRenderer renderer =
                     new OpenGlRenderer()) {
            int frames = 0;
            while (!GLFW.glfwWindowShouldClose(window)
                    && (frameLimit <= 0 || frames < frameLimit)) {
                nuklearView.beginInput();
                GLFW.glfwPollEvents();
                nuklearView.processInput();
                nuklearView.endInput();
                visualizer.updateFrame();
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer width = stack.mallocInt(1);
                    IntBuffer height = stack.mallocInt(1);
                    GLFW.glfwGetFramebufferSize(window, width, height);
                    GL11.glViewport(0, 0, width.get(0), height.get(0));
                    GL11.glClearColor(0.055f, 0.065f, 0.08f, 1);
                    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
                    renderer.render(visualizer, width.get(0), height.get(0));
                }
                nuklearView.render();
                GLFW.glfwSwapBuffers(window);
                frames++;
            }
        } finally {
            nuklearView.cleanUp();
            Callbacks.glfwFreeCallbacks(window);
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
            errorCallback.free();
        }
    }

    private static void installCallbacks(long window, GeometryDiscoveryVisualizer visualizer,
                                         NuklearController nuklear) {
        final boolean[] leftDown = {false};
        final boolean[] rightDown = {false};
        final double[] last = new double[2];
        GLFW.glfwSetCursorPosCallback(window, (handle, x, y) -> {
            boolean consumed = nuklear.mouseCursorPosCallback(handle, x, y);
            if (!consumed && leftDown[0])
                visualizer.viewState().orbit((x - last[0]) * 0.3, (y - last[1]) * 0.3);
            if (!consumed && rightDown[0])
                visualizer.viewState().pan(-(x - last[0]) * 0.01,
                        (y - last[1]) * 0.01, 0);
            last[0] = x;
            last[1] = y;
        });
        GLFW.glfwSetMouseButtonCallback(window, (handle, button, action, mods) -> {
            nuklear.mouseButtonCallback(handle, button, action, mods);
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) leftDown[0] = action != GLFW.GLFW_RELEASE;
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) rightDown[0] = action != GLFW.GLFW_RELEASE;
        });
        GLFW.glfwSetScrollCallback(window, (handle, x, y) -> {
            boolean consumed = nuklear.mouseScrollCallback(handle, x, y);
            if (!consumed) visualizer.viewState().zoom(y * 0.12);
        });
        GLFW.glfwSetCharCallback(window, nuklear::charCallback);
        GLFW.glfwSetKeyCallback(window, (handle, key, scan, action, mods) -> {
            nuklear.keyCallback(handle, key, scan, action, mods);
            if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) return;
            switch (key) {
                case GLFW.GLFW_KEY_ESCAPE -> GLFW.glfwSetWindowShouldClose(handle, true);
                case GLFW.GLFW_KEY_SPACE -> {
                    if (visualizer.session().isRunning()) visualizer.pause();
                    else visualizer.start();
                }
                case GLFW.GLFW_KEY_N -> visualizer.singleStep();
                case GLFW.GLFW_KEY_C -> visualizer.runToConvergence();
                case GLFW.GLFW_KEY_R -> visualizer.reset();
                case GLFW.GLFW_KEY_F -> visualizer.fitToView();
                case GLFW.GLFW_KEY_P -> visualizer.viewState().setOrthographic(
                        !visualizer.viewState().orthographic());
                case GLFW.GLFW_KEY_TAB -> visualizer.selectNextNode(
                        (mods & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1);
                case GLFW.GLFW_KEY_LEFT -> visualizer.viewState().orbit(-3, 0);
                case GLFW.GLFW_KEY_RIGHT -> visualizer.viewState().orbit(3, 0);
                case GLFW.GLFW_KEY_UP -> visualizer.viewState().orbit(0, -3);
                case GLFW.GLFW_KEY_DOWN -> visualizer.viewState().orbit(0, 3);
                case GLFW.GLFW_KEY_W -> visualizer.viewState().pan(0, 0.2, 0);
                case GLFW.GLFW_KEY_S -> visualizer.viewState().pan(0, -0.2, 0);
                case GLFW.GLFW_KEY_A -> visualizer.viewState().pan(-0.2, 0, 0);
                case GLFW.GLFW_KEY_D -> visualizer.viewState().pan(0.2, 0, 0);
                case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD ->
                        visualizer.viewState().zoom(0.12);
                case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT ->
                        visualizer.viewState().zoom(-0.12);
                default -> { }
            }
        });
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--") || i + 1 >= args.length)
                throw new IllegalArgumentException("Expected --option value.");
            result.put(args[i].substring(2), args[++i]);
        }
        return result;
    }
}
