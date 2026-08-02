package remframework.geometry.discovery.configuration;

/** Worker-count policy for dedicated topology generation pools. */
public record GenerationWorkerCount(boolean automatic, int explicitCount) {
    public GenerationWorkerCount {
        if (!automatic && explicitCount < 1)
            throw new IllegalArgumentException("An explicit generation worker count must be positive.");
    }

    public static GenerationWorkerCount auto() { return new GenerationWorkerCount(true, 0); }
    public static GenerationWorkerCount explicit(int count) { return new GenerationWorkerCount(false, count); }
    public int resolve() {
        return automatic ? Math.max(1, Runtime.getRuntime().availableProcessors() - 1) : explicitCount;
    }
    @Override public String toString() { return automatic ? "AUTO (" + resolve() + ")" : Integer.toString(explicitCount); }
}
