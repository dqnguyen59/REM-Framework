package remframework.geometry.discovery.configuration;

import java.time.Duration;
import java.util.Map;

/** Immutable live progress published by a Stage 1 worker operation. */
public record GenerationProgress(GenerationStrategy strategy, int workerCount, int activeWorkers,
        Duration elapsed, long statesExamined, double statesPerSecond, long statesPruned,
        int frontierSize, long completedCandidates, long duplicateCandidates,
        int retainedConfigurations, Map<String, Long> pruningCounts,
        UniformSphericalSearchLimits limits, boolean running, boolean cancelled) {
    public GenerationProgress { pruningCounts = Map.copyOf(pruningCounts); }
}
