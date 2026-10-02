package remframework.opengl.controller;

/** Owns at most one asynchronous frame task and supports non-blocking completion polling. */
final class NonBlockingFrameTask {
    private Thread pendingThread;
    private volatile Throwable failure;

    public boolean isPending() {
        return pendingThread != null;
    }

    public void start(Runnable task) {
        if (pendingThread != null)
            throw new IllegalStateException("A frame calculation is already pending.");
        failure = null;
        pendingThread = Thread.startVirtualThread(() -> {
            try {
                task.run();
            } catch (Throwable taskFailure) {
                failure = taskFailure;
            }
        });
    }

    /**
     * Completes an already-finished task without waiting.
     *
     * @return true when a pending task was completed, otherwise false
     */
    public boolean tryComplete() {
        Thread thread = pendingThread;
        if (thread == null || thread.isAlive()) return false;
        pendingThread = null;
        propagateFailure();
        return true;
    }

    /** Waits for a pending task during shutdown or another terminal transition. */
    public void awaitComplete() {
        Thread thread = pendingThread;
        if (thread == null) return;
        boolean interrupted = false;
        while (thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        pendingThread = null;
        Throwable taskFailure = takeFailure();
        if (interrupted) {
            Thread.currentThread().interrupt();
            IllegalStateException interruption = new IllegalStateException(
                    "Interrupted while waiting for calcNextFrame().");
            if (taskFailure != null) interruption.addSuppressed(taskFailure);
            throw interruption;
        }
        propagate(taskFailure);
    }

    private void propagateFailure() {
        propagate(takeFailure());
    }

    private Throwable takeFailure() {
        Throwable taskFailure = failure;
        failure = null;
        return taskFailure;
    }

    private static void propagate(Throwable taskFailure) {
        if (taskFailure instanceof RuntimeException runtimeException) throw runtimeException;
        if (taskFailure instanceof Error error) throw error;
        if (taskFailure != null)
            throw new IllegalStateException("calcNextFrame() failed.", taskFailure);
    }
}
