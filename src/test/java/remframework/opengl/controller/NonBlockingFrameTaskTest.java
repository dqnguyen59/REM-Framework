package remframework.opengl.controller;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.EntityType;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NonBlockingFrameTaskTest {
    @Test
    void pollingNeverWaitsForRunningCalculation() throws Exception {
        NonBlockingFrameTask task = new NonBlockingFrameTask();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);

        task.start(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            } finally {
                finished.countDown();
            }
        });

        assertTrue(started.await(2, TimeUnit.SECONDS));
        assertFalse(task.tryComplete());
        assertTrue(task.isPending());

        release.countDown();
        assertTrue(finished.await(2, TimeUnit.SECONDS));
        completeEventually(task);
        assertFalse(task.isPending());
    }

    @Test
    void completedFailureIsPropagatedByNonBlockingPoll() throws Exception {
        NonBlockingFrameTask task = new NonBlockingFrameTask();
        CountDownLatch finished = new CountDownLatch(1);
        task.start(() -> {
            try {
                throw new IllegalStateException("intentional frame failure");
            } finally {
                finished.countDown();
            }
        });

        assertTrue(finished.await(2, TimeUnit.SECONDS));
        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> completeEventually(task));
        assertEquals("intentional frame failure", failure.getMessage());
        assertFalse(task.isPending());
    }

    @Test
    void completedBackBufferRemainsInvisibleUntilPublication() throws Exception {
        int originalSwapIndex = Entity.getSwapBufferIndex();
        Entity entity = new Entity(null, null, new Vector3d(), new Vector3d(),
                1.0, EntityType.ENTITY);
        entity.synchronizeTransformMatrixBuffers();
        NonBlockingFrameTask task = new NonBlockingFrameTask();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        try {
            entity.setPosition(5, 6, 7);
            task.start(() -> {
                try {
                    release.await();
                    entity.updateMatrix();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                } finally {
                    finished.countDown();
                }
            });

            assertEquals(0.0, entity.getTransformMatrix4f().m30(), 1.0e-6);
            assertFalse(task.tryComplete());
            release.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            completeEventually(task);
            assertEquals(0.0, entity.getTransformMatrix4f().m30(), 1.0e-6);

            Entity.swapBuffer();
            assertEquals(5.0, entity.getTransformMatrix4f().m30(), 1.0e-6);
            assertEquals(6.0, entity.getTransformMatrix4f().m31(), 1.0e-6);
            assertEquals(7.0, entity.getTransformMatrix4f().m32(), 1.0e-6);
        } finally {
            release.countDown();
            if (task.isPending()) task.awaitComplete();
            if (Entity.getSwapBufferIndex() != originalSwapIndex) Entity.swapBuffer();
        }
    }

    private static void completeEventually(NonBlockingFrameTask task) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (task.tryComplete()) return;
            Thread.yield();
        }
        throw new AssertionError("Frame task did not terminate in time.");
    }
}
