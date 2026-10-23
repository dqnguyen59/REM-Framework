package remframework.opengl.controller;

import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import remframework.opengl.model.Camera;
import remframework.opengl.model.Scene;
import remframework.opengl.view.GLWindow;
import remframework.opengl.view.renderer.Renderer;
import remframework.helpers.PerformanceMonitor;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Renders 3D into shared textures on a second OpenGL context.<br><br>
 * The display context only samples the most recently completed texture; it never joins this
 * worker. Two color textures let the worker render into one image while the display presents the
 * other image.
 */
final class Offscreen3DRenderer {
    private static final String VERTEX_SHADER = """
            #version 330 core
            out vec2 uv;
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                // Both the FBO and the default framebuffer use OpenGL's bottom-left origin.
                // Do not invert Y here: doing so mirrors the 3D scene vertically.
                uv = vec2(p.x, p.y);
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String FRAGMENT_SHADER = """
            #version 330 core
            in vec2 uv;
            uniform sampler2D frameTexture;
            out vec4 colour;
            void main() { colour = texture(frameTexture, uv); }
            """;

    private final GLWindow displayWindow;
    private final AtomicReference<Request> pendingRequest = new AtomicReference<>();
    private final Object wake = new Object();
    private volatile PublishedFrame publishedFrame = PublishedFrame.EMPTY;
    private volatile Throwable failure;
    private volatile boolean running;
    private Thread workerThread;
    private long workerWindow;
    private int compositorProgram;
    private int compositorVao;
    private int compositorSamplerLocation;

    Offscreen3DRenderer(GLWindow displayWindow) {
        this.displayWindow = displayWindow;
    }

    void start() {
        if (running) return;
        createCompositor();
        // GLFW window creation belongs on the display thread. On Windows the shared
        // display context must not be current on a different thread during creation.
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GL11.GL_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GL11.GL_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_SAMPLES, displayWindow.getAppSettings().getAntiAliasing());
        workerWindow = GLFW.glfwCreateWindow(1, 1, "REM 3D", MemoryUtil.NULL,
                displayWindow.getWindowHandle());
        if (workerWindow == MemoryUtil.NULL) {
            throw new IllegalStateException("Unable to create shared 3D context.");
        }
        failure = null;
        running = true;
        workerThread = Thread.ofPlatform().name("rem-3d-renderer").start(this::runWorker);
    }

    void request(Renderer.RenderState state, Camera sourceCamera, Scene scene,
            int width, int height) {
        if (!running) return;
        Camera camera = copyCamera(sourceCamera);
        pendingRequest.set(new Request(state, camera, scene, Math.max(1, width), Math.max(1, height)));
        synchronized (wake) {
            wake.notifyAll();
        }
    }

    void presentLatest(int width, int height) {
        throwIfFailed();
        PublishedFrame frame = publishedFrame;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL11.glViewport(0, 0, width, height);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        if (frame.textureId() == 0) return;
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL20.glUseProgram(compositorProgram);
        GL20.glUniform1i(compositorSamplerLocation, 0);
        GL30.glBindVertexArray(compositorVao);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, frame.textureId());
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);
    }

    void stop() {
        running = false;
        pendingRequest.set(null);
        synchronized (wake) {
            wake.notifyAll();
        }
        if (workerThread != null) {
            boolean interrupted = false;
            while (workerThread.isAlive()) {
                try {
                    workerThread.join();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
            workerThread = null;
        }
        if (workerWindow != MemoryUtil.NULL) {
            GLFW.glfwDestroyWindow(workerWindow);
            workerWindow = MemoryUtil.NULL;
        }
        if (compositorVao != 0) GL30.glDeleteVertexArrays(compositorVao);
        if (compositorProgram != 0) GL20.glDeleteProgram(compositorProgram);
        compositorVao = 0;
        compositorProgram = 0;
        compositorSamplerLocation = -1;
        publishedFrame = PublishedFrame.EMPTY;
    }

    void throwIfFailed() {
        Throwable error = failure;
        if (error == null) return;
        if (error instanceof RuntimeException runtime) throw runtime;
        if (error instanceof Error fatal) throw fatal;
        throw new IllegalStateException("Offscreen 3D renderer failed.", error);
    }

    private void createCompositor() {
        compositorProgram = GL20.glCreateProgram();
        int vertex = compile(VERTEX_SHADER, GL20.GL_VERTEX_SHADER);
        int fragment = compile(FRAGMENT_SHADER, GL20.GL_FRAGMENT_SHADER);
        GL20.glAttachShader(compositorProgram, vertex);
        GL20.glAttachShader(compositorProgram, fragment);
        GL20.glLinkProgram(compositorProgram);
        if (GL20.glGetProgrami(compositorProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException(GL20.glGetProgramInfoLog(compositorProgram));
        }
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        compositorSamplerLocation = GL20.glGetUniformLocation(compositorProgram, "frameTexture");
        compositorVao = GL30.glGenVertexArrays();
    }

    private static int compile(String source, int type) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));
        }
        return shader;
    }

    private void runWorker() {
        long window = workerWindow;
        Renderer renderer = null;
        int[] framebuffers = new int[2];
        int[] depthBuffers = new int[2];
        int[] textures = new int[2];
        int currentWidth = 0;
        int currentHeight = 0;
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            renderer = new Renderer();
            renderer.init();

            while (running) {
                Request request = pendingRequest.getAndSet(null);
                if (request == null) {
                    synchronized (wake) {
                        if (running && pendingRequest.get() == null) wake.wait(25L);
                    }
                    continue;
                }
                if (request.width() != currentWidth || request.height() != currentHeight) {
                    currentWidth = request.width();
                    currentHeight = request.height();
                    for (int index = 0; index < 2; index++) {
                        textures[index] = GL11.glGenTextures();
                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[index]);
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA8, currentWidth,
                                currentHeight, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
                        framebuffers[index] = GL30.glGenFramebuffers();
                        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffers[index]);
                        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                                GL11.GL_TEXTURE_2D, textures[index], 0);
                        depthBuffers[index] = GL30.glGenRenderbuffers();
                        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthBuffers[index]);
                        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8,
                                currentWidth, currentHeight);
                        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                                GL30.GL_RENDERBUFFER, depthBuffers[index]);
                    }
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                }

                int writeIndex = publishedFrame.index() == 0 ? 1 : 0;
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffers[writeIndex]);
                GL11.glViewport(0, 0, currentWidth, currentHeight);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glClearColor(0f, 0f, 0f, 0f);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
                // Scene owns native instance buffers. A layout rebuild must not free
                // them until glBufferData has finished reading their client memory.
                synchronized (request.scene()) {
                    renderer.apply(request.state());
                    renderer.render(request.camera(), request.scene());
                }
                GL11.glFlush();
                GL11.glFinish();
                publishedFrame = new PublishedFrame(writeIndex, textures[writeIndex], currentWidth, currentHeight);
                PerformanceMonitor.record3DFrame();
            }
        } catch (Throwable error) {
            if (running) failure = error;
        } finally {
            try {
                if (renderer != null) renderer.cleanup();
                for (int index = 0; index < 2; index++) {
                    if (framebuffers[index] != 0) GL30.glDeleteFramebuffers(framebuffers[index]);
                    if (depthBuffers[index] != 0) GL30.glDeleteRenderbuffers(depthBuffers[index]);
                    if (textures[index] != 0) GL11.glDeleteTextures(textures[index]);
                }
            } finally {
                GLFW.glfwMakeContextCurrent(MemoryUtil.NULL);
                GL.setCapabilities(null);
            }
        }
    }

    private static Camera copyCamera(Camera source) {
        Camera copy = new Camera();
        copy.setPosition(source.getPosition().x, source.getPosition().y, source.getPosition().z);
        copy.setRotation(source.getRotation().x, source.getRotation().y, source.getRotation().z);
        copy.setFieldOfView(source.getFieldOfView());
        copy.setZ_near(source.getZ_near());
        copy.setZ_far(source.getZ_far());
        copy.getProjectionMatrix().set(new Matrix4f(source.getProjectionMatrix()));
        return copy;
    }

    private record Request(Renderer.RenderState state, Camera camera, Scene scene, int width, int height) {}
    private record PublishedFrame(int index, int textureId, int width, int height) {
        private static final PublishedFrame EMPTY = new PublishedFrame(0, 0, 0, 0);
    }
}
