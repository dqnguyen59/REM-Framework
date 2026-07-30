package remframework.geometry.discovery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GeometryDiscoveryBatchRunner {
    public record Output(List<GeometryBatchRun> runs, GeometryBatchAggregate aggregate,
                         Path csvPath, Path aggregatePath) {
    }

    public List<GeometryBatchCombination> combinations(GeometryBatchParameters parameters) {
        List<GeometryBatchCombination> result = new ArrayList<>();
        int runId = 1;
        for (long seed = parameters.firstSeed(); seed <= parameters.lastSeed(); seed++)
            for (double learningRate : parameters.learningRates())
                for (double repulsion : parameters.repulsionStrengths())
                    for (double correction : parameters.maximumCorrections())
                        result.add(new GeometryBatchCombination(runId++, seed, learningRate,
                                repulsion, correction));
        return List.copyOf(result);
    }

    public Output run(GeometryBatchParameters parameters) throws IOException {
        Path outputDirectory = parameters.outputDirectory().toAbsolutePath().normalize();
        if (outputDirectory.getFileName() != null
                && outputDirectory.getFileName().toString().equalsIgnoreCase("cubic-lattice.toml"))
            throw new IllegalArgumentException("Batch output cannot target cubic-lattice.toml.");
        Files.createDirectories(outputDirectory);
        List<GeometryBatchRun> runs = new ArrayList<>();
        for (GeometryBatchCombination combination : combinations(parameters)) {
            GeometryDiscoverySettings settings =
                    GeometryDiscoverySettings.defaults(parameters.targetDistance())
                            .withRandomSeed(combination.seed())
                            .withLearningRate(combination.learningRate())
                            .withRepulsionStrength(combination.repulsionStrength())
                            .withMaximumCorrection(combination.maximumCorrection())
                            .withMaximumIterations(parameters.maximumIterations())
                            .withConvergenceTolerance(parameters.convergenceTolerance());
            long started = System.nanoTime();
            GeometryDiscoveryResult result = new GeometryDiscoveryEngine().relax(
                    GeometryDiscoveryExamples.create(parameters.modelName(),
                            parameters.targetDistance()), settings);
            long elapsed = System.nanoTime() - started;
            Path candidate = null;
            if (parameters.exportCandidates()) {
                candidate = new GeometryDiscoveryExporter().export(result,
                        outputDirectory.resolve(String.format(Locale.ROOT,
                                "run-%05d-candidate.toml", combination.runId())));
            }
            runs.add(new GeometryBatchRun(combination, result, elapsed, candidate));
        }
        GeometryBatchAggregate aggregate = aggregate(runs);
        Path csv = GeometryDiscoveryMetricsReportWriter.safeUniquePath(
                outputDirectory.resolve("runs.csv"));
        Path summary = GeometryDiscoveryMetricsReportWriter.safeUniquePath(
                outputDirectory.resolve("aggregate.txt"));
        Files.writeString(csv, csv(parameters.modelName(), runs), StandardCharsets.UTF_8);
        Files.writeString(summary, aggregateText(aggregate), StandardCharsets.UTF_8);
        return new Output(List.copyOf(runs), aggregate, csv, summary);
    }

    public GeometryBatchAggregate aggregate(List<GeometryBatchRun> runs) {
        if (runs.isEmpty())
            return new GeometryBatchAggregate(0, 0, 0, 0, -1, -1, -1, "none");
        int converged = (int) runs.stream().filter(run -> run.result().converged()).count();
        double meanIterations = runs.stream().mapToInt(
                run -> run.result().metrics().iterationCount()).average().orElse(0);
        GeometryBatchRun best = runs.stream().min(Comparator
                .comparingDouble(run -> run.result().metrics().totalEdgeLengthError())).orElseThrow();
        GeometryBatchRun worst = runs.stream().max(Comparator
                .comparingDouble(run -> run.result().metrics().totalEdgeLengthError())).orElseThrow();
        int fastest = runs.stream().filter(run -> run.result().converged())
                .min(Comparator.comparingLong(GeometryBatchRun::elapsedNanoseconds))
                .map(run -> run.combination().runId()).orElse(-1);

        Map<String, int[]> rates = new LinkedHashMap<>();
        for (GeometryBatchRun run : runs) {
            GeometryBatchCombination c = run.combination();
            String key = "learningRate=" + c.learningRate() + ",repulsionStrength="
                    + c.repulsionStrength() + ",maximumCorrection=" + c.maximumCorrection();
            int[] counts = rates.computeIfAbsent(key, ignored -> new int[2]);
            counts[0]++;
            if (run.result().converged()) counts[1]++;
        }
        String highest = rates.entrySet().stream().max(Comparator
                .<Map.Entry<String, int[]>>comparingDouble(
                        entry -> (double) entry.getValue()[1] / entry.getValue()[0])
                .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                .map(Map.Entry::getKey).orElse("none");
        return new GeometryBatchAggregate(runs.size(), converged,
                (double) converged / runs.size(), meanIterations,
                best.combination().runId(), worst.combination().runId(), fastest, highest);
    }

    private String csv(String model, List<GeometryBatchRun> runs) {
        StringBuilder out = new StringBuilder("runId,model,seed,targetDistance,learningRate,"
                + "repulsionStrength,maximumCorrection,maximumIterations,convergenceTolerance,"
                + "converged,iterations,totalEdgeError,meanEdgeDistance,minEdgeDistance,"
                + "maxEdgeDistance,edgeStandardDeviation,minimumNodeSeparation,"
                + "separationViolations,extentX,extentY,extentZ,dimensionality,"
                + "runtimeCompatible,elapsedNanoseconds,exportedCandidatePath\n");
        for (GeometryBatchRun run : runs) {
            GeometryDiscoverySettings s = run.result().settings();
            GeometryDiscoveryMetrics m = run.result().metrics();
            out.append(run.combination().runId()).append(',').append(model).append(',')
                    .append(run.combination().seed()).append(',').append(s.targetDistance()).append(',')
                    .append(s.learningRate()).append(',').append(s.repulsionStrength()).append(',')
                    .append(s.maximumCorrection()).append(',').append(s.maximumIterations()).append(',')
                    .append(s.convergenceTolerance()).append(',').append(m.converged()).append(',')
                    .append(m.iterationCount()).append(',').append(m.totalEdgeLengthError()).append(',')
                    .append(m.meanConnectedNeighborDistance()).append(',')
                    .append(m.minimumConnectedNeighborDistance()).append(',')
                    .append(m.maximumConnectedNeighborDistance()).append(',')
                    .append(m.standardDeviationConnectedNeighborDistance()).append(',')
                    .append(m.minimumDistinctNodeDistance()).append(',')
                    .append(m.collisionViolationCount()).append(',').append(m.extentX()).append(',')
                    .append(m.extentY()).append(',').append(m.extentZ()).append(',')
                    .append(csvQuote(m.effectiveDimensionality())).append(',')
                    .append(run.result().runtimeCompatibility().compatible()).append(',')
                    .append(run.elapsedNanoseconds()).append(',')
                    .append(csvQuote(run.exportedCandidate() == null ? ""
                            : run.exportedCandidate().toString())).append('\n');
        }
        return out.toString();
    }

    private String aggregateText(GeometryBatchAggregate a) {
        return """
                totalRuns=%d
                convergedRuns=%d
                convergenceRate=%s
                meanIterationCount=%s
                bestRunByEdgeError=%d
                worstRunByEdgeError=%d
                fastestConvergedRun=%d
                highestConvergenceParameterCombination=%s
                """.formatted(a.totalRuns(), a.convergedRuns(), a.convergenceRate(),
                a.meanIterationCount(), a.bestRunId(), a.worstRunId(),
                a.fastestConvergedRunId(), a.highestConvergenceParameterCombination());
    }

    private String csvQuote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
