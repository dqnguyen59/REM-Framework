package remframework.geometry.discovery.configuration;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Runtime-only execution controls; scientific request parameters are unchanged. */
public record GenerationExecution(GenerationWorkerCount workers, BooleanSupplier cancelled,
        Consumer<GenerationProgress> progress) {
    public GenerationExecution {
        workers = workers == null ? GenerationWorkerCount.auto() : workers;
        cancelled = cancelled == null ? () -> false : cancelled;
        progress = progress == null ? ignored -> { } : progress;
    }
    public static GenerationExecution defaults() {
        return new GenerationExecution(GenerationWorkerCount.auto(), () -> false, ignored -> { });
    }
}
