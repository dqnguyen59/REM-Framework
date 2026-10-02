package remframework.opengl.model;

import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.EntityType;
import remframework.opengl.model.entity.Node;
import remframework.settings.Project;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneFramePipelineTest {
    private final Project project = Project.getInstance();
    private final Scene scene = new Scene();
    private final Lattice lattice = scene.getLattice();
    private Object[] originalNodeArray;
    private Object[] originalEntityArray;
    private Entity originalBaseEntity;
    private boolean originalSimulating;
    private int originalSwapBufferIndex;
    private float originalRenderScale;

    @BeforeEach
    void rememberSharedState() {
        originalNodeArray = lattice.getNodeArray();
        originalEntityArray = lattice.getEntityArray();
        originalBaseEntity = lattice.getBaseEntity();
        originalSimulating = project.getRemModel().isSimulating();
        originalSwapBufferIndex = Entity.getSwapBufferIndex();
        originalRenderScale = project.settings.getScale();
    }

    @AfterEach
    void restoreSharedState() {
        lattice.setNodeArray(originalNodeArray);
        lattice.setEntityArray(originalEntityArray);
        lattice.setBaseEntity(originalBaseEntity);
        scene.invalidateRenderInstanceDataLayout();
        if (originalSimulating) project.getRemModel().startSimulation();
        else project.getRemModel().stopSimulation();
        project.settings.setScale(originalRenderScale);
        if (Entity.getSwapBufferIndex() != originalSwapBufferIndex) Entity.swapBuffer();
    }

    @Test
    void latticeRootCompletesBeforeParallelChildMatricesStart() throws Exception {
        BlockingBaseEntity base = new BlockingBaseEntity();
        CountDownLatch childStarted = new CountDownLatch(1);
        TrackingEntity[] children = new TrackingEntity[64];
        Object[] entities = new Object[children.length + 1];
        entities[0] = base;
        for (int index = 0; index < children.length; index++) {
            children[index] = new TrackingEntity(base, index, childStarted);
            entities[index + 1] = children[index];
        }
        lattice.setBaseEntity(base);
        lattice.setEntityArray(entities);
        scene.invalidateRenderInstanceDataLayout();
        base.arm();

        Thread scheduler = Thread.startVirtualThread(() -> scene.updateMatrix(
                4, Math.ceilDiv(entities.length, 4)));
        try {
            assertTrue(base.awaitUpdateStart());
            assertFalse(childStarted.await(100, TimeUnit.MILLISECONDS),
                    "Child matrix work started before the root transform was stable.");
            project.settings.setScale(originalRenderScale == 0.0f
                    ? 1.0f : originalRenderScale * 2.0f);
        } finally {
            base.release();
        }
        scheduler.join();
        scene.waitForAllTasksReady();

        for (TrackingEntity child : children) {
            Matrix4d expected = new Matrix4d(base.getTransformMatrix())
                    .translate(child.getPosition())
                    .scale(base.capturedRenderScale * child.getScale());
            assertTrue(expected.equals(child.getTransformMatrix(), 1.0e-12));
        }
        assertEquals(originalRenderScale, base.capturedRenderScale, 1.0e-12);
    }

    @Test
    void packedInstanceBufferPublishesTheSameCompletedTransformAsDirectRendering() {
        Entity base = new Entity(null, null, new Vector3d(),
                new Vector3d(-90, 0, 0), 1.0, EntityType.ENTITY);
        ObjFileModel model = new ObjFileModel(0, 0);
        Entity child = new Entity(base, model, new Vector3d(4, 5, 6),
                new Vector3d(), 1.0, EntityType.ENTITY);
        lattice.setBaseEntity(base);
        lattice.setEntityArray(new Object[]{base, child});
        scene.invalidateRenderInstanceDataLayout();

        Entity.swapBuffer();
        child.setPosition(10, 11, 12);
        updateMatricesSynchronously(true);
        assertPackedTranslation(scene.getRenderInstanceData(model),
                child.getTransformMatrix4f().m30(), child.getTransformMatrix4f().m31(),
                child.getTransformMatrix4f().m32());

        Entity.swapBuffer();
        child.setPosition(20, 21, 22);
        updateMatricesSynchronously(false);
        assertPackedTranslation(scene.getRenderInstanceData(model), 10, 12, -11);

        Entity.swapBuffer();
        child.setPosition(30, 31, 32);
        updateMatricesSynchronously(false);
        assertPackedTranslation(scene.getRenderInstanceData(model), 20, 22, -21);
        assertEquals(20, child.getTransformMatrix4f().m30(), 1.0e-6);
        assertEquals(22, child.getTransformMatrix4f().m31(), 1.0e-6);
        assertEquals(-21, child.getTransformMatrix4f().m32(), 1.0e-6);
    }

    @Test
    void nodeSegmentsCoverEveryNodeExactlyOnce() {
        project.getRemModel().startSimulation();
        CountingNode[] nodes = new CountingNode[1001];
        for (int index = 0; index < nodes.length; index++) nodes[index] = new CountingNode(index);
        lattice.setNodeArray(nodes);

        int workers = 32;
        scene.processRemFieldEngine(workers, Math.ceilDiv(nodes.length, workers));
        scene.waitForAllTasksReady();

        assertTrue(Arrays.stream(nodes).allMatch(node -> node.updates.get() == 1));
        assertTrue(Arrays.stream(nodes).allMatch(
                node -> node.parameters == nodes[0].parameters));
    }

    @Test
    void workerFailureIsPropagatedToTheFrameCoordinator() {
        project.getRemModel().startSimulation();
        lattice.setNodeArray(new Object[]{new FailingNode()});

        scene.processRemFieldEngine(1, 1);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, scene::waitForAllTasksReady);
        assertEquals("intentional worker failure", failure.getMessage());
    }

    private void updateMatricesSynchronously(boolean synchronizeBothBuffers) {
        scene.updateMatrix(2, 1, synchronizeBothBuffers);
        scene.waitForAllTasksReady();
    }

    private static void assertPackedTranslation(
            FloatBuffer packed, double x, double y, double z) {
        assertEquals(x, packed.get(12), 1.0e-6);
        assertEquals(y, packed.get(13), 1.0e-6);
        assertEquals(z, packed.get(14), 1.0e-6);
    }

    private static final class BlockingBaseEntity extends Entity {
        private boolean armed;
        private double capturedRenderScale;
        private final CountDownLatch updateStarted = new CountDownLatch(1);
        private final CountDownLatch updateRelease = new CountDownLatch(1);

        private BlockingBaseEntity() {
            super(null, null, new Vector3d(), new Vector3d(-90, 0, 0),
                    1.0, EntityType.ENTITY);
        }

        private void arm() {
            armed = true;
        }

        private boolean awaitUpdateStart() throws InterruptedException {
            return updateStarted.await(2, TimeUnit.SECONDS);
        }

        private void release() {
            updateRelease.countDown();
        }

        @Override
        public void updateMatrix(double renderScale) {
            capturedRenderScale = renderScale;
            if (armed) {
                updateStarted.countDown();
                try {
                    updateRelease.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }
            super.updateMatrix(renderScale);
        }
    }

    private static final class TrackingEntity extends Entity {
        private CountDownLatch updateStarted;

        private TrackingEntity(Entity parent, int index, CountDownLatch updateStarted) {
            super(parent, null, new Vector3d(index, index + 1, index + 2),
                    new Vector3d(), 1.0, EntityType.ENTITY);
            this.updateStarted = updateStarted;
        }

        @Override
        public void updateMatrix(double renderScale) {
            super.updateMatrix(renderScale);
            if (updateStarted != null) updateStarted.countDown();
        }
    }

    private static final class CountingNode extends Node {
        private final AtomicInteger updates = new AtomicInteger();
        private FrameParameters parameters;

        private CountingNode(int index) {
            super(null, null, new Vector3d(index, 0, 0), new Vector3d(), 1.0);
        }

        @Override
        public void updateNodeProperties(FrameParameters parameters) {
            this.parameters = parameters;
            updates.incrementAndGet();
        }
    }

    private static final class FailingNode extends Node {
        private FailingNode() {
            super(null, null, new Vector3d(), new Vector3d(), 1.0);
        }

        @Override
        public void updateNodeProperties(FrameParameters ignored) {
            throw new IllegalStateException("intentional worker failure");
        }
    }
}
