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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TopologySearchBatchMain {
    private TopologySearchBatchMain() { }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = TopologySearchMain.parse(args);
        String model = options.getOrDefault("input-model", "small-cubic");
        int nodes = Integer.parseInt(options.getOrDefault("nodes", "12"));
        int iterations = Integer.parseInt(options.getOrDefault("iterations", "100"));
        Path output = Path.of(options.getOrDefault("output", "lattice/topology-search/batch"));
        Files.createDirectories(output);
        List<Long> seeds = seeds(options.getOrDefault("seeds", "1:5"));
        List<TopologySearchMode> modes = modes(options.getOrDefault("search-modes",
                "hill-climbing,annealing"));
        List<Double> weightScales = decimals(options.getOrDefault("weight-scales", "1"));
        List<TopologyScoringProfile> profiles = profiles(
                options.getOrDefault("scoring-profiles",
                        options.getOrDefault("scoring-profile", "general")));
        StringBuilder csv = new StringBuilder(
                "runId,seed,searchMode,scoringProfile,weightScale,initialTopology,initialScore,bestScore,scoreImprovement,"
                        + "converged,acceptedMutationCount,invalidMutationCount,duplicateCount,"
                        + "meanDirectionalBias,tensorIsotropyError,angularReferenceApplicable,"
                        + "angularReferenceError,maximumUncoveredAngle,oppositeDirectionMismatch,"
                        + "dimensionMismatchCount,runtimeMilliseconds,bestCandidatePath\n");
        int runId = 0;
        for (long seed : seeds) for (TopologySearchMode mode : modes)
                for (TopologyScoringProfile profile : profiles)
                    for (double weightScale : weightScales) {
            GeometryDiscoverySettings geometry = GeometryDiscoverySettings.defaults(1)
                    .withMaximumIterations(Integer.parseInt(
                            options.getOrDefault("relaxation-iterations", "500")))
                    .withRandomSeed(seed);
            TopologySearchSettings base = TopologySearchSettings.defaults(seed, geometry);
            TopologySearchSettings settings = new TopologySearchSettings(mode, seed, iterations, 1,
                    1, 6, -1, 10, base.initialTemperature(), base.coolingRate(),
                    base.minimumTemperature(), base.scoreImprovementTolerance(), geometry,
                    TopologySearchMain.applyWeightOverrides(
                            TopologyScoreWeights.forProfile(profile).scaled(weightScale), options),
                    profile, base.diagnosticSettings(), Math.max(1, iterations));
            GeometryDiscoveryModel initial = TopologySearchExamples.create(model, nodes, 1, seed);
            TopologySearchResult result = new TopologySearchEngine().search(initial, settings);
            Path runDir = output.resolve(String.format(Locale.ROOT, "run-%04d", ++runId));
            Path best = new TopologySearchExporter().export(result, runDir);
            TopologySearchMetrics metrics = result.metrics();
            DirectionalBalanceMetrics diagnostics = result.bestCandidate().directionalMetrics();
            csv.append(runId).append(',').append(seed).append(',').append(mode).append(',')
                    .append(profile).append(',')
                    .append(weightScale).append(',')
                    .append(initial.name()).append(',')
                    .append(result.initialCandidate().score().total()).append(',')
                    .append(result.bestCandidate().score().total()).append(',')
                    .append(result.initialCandidate().score().total()
                            - result.bestCandidate().score().total()).append(',')
                    .append(result.bestCandidate().geometryResult().converged()).append(',')
                    .append(metrics.acceptedMutationCount()).append(',')
                    .append(metrics.invalidMutationCount()).append(',')
                    .append(metrics.duplicateTopologyCount()).append(',')
                    .append(diagnostics.meanNormalizedDirectionalBias()).append(',')
                    .append(diagnostics.meanTensorIsotropyError()).append(',')
                    .append(diagnostics.angularReferenceApplicableNodeCount() > 0).append(',')
                    .append(diagnostics.meanKnownReferenceAngularError()).append(',')
                    .append(diagnostics.maximumUncoveredAngle()).append(',')
                    .append(diagnostics.meanOppositeDirectionMismatch()).append(',')
                    .append(diagnostics.localGlobalDimensionMismatchCount()).append(',')
                    .append(metrics.elapsedMilliseconds()).append(',').append(best).append('\n');
        }
        Path summary = output.resolve("topology-batch-summary.csv");
        Files.writeString(summary, csv, StandardCharsets.UTF_8);
        System.out.println("Completed " + runId + " topology searches: " + summary);
    }

    static List<Long> seeds(String value) {
        if (value.contains(":")) {
            String[] bounds = value.split(":", -1);
            long first = Long.parseLong(bounds[0]), last = Long.parseLong(bounds[1]);
            List<Long> result = new ArrayList<>();
            for (long seed = first; seed <= last; seed++) result.add(seed);
            return result;
        }
        List<Long> result = new ArrayList<>();
        for (String item : value.split(",")) result.add(Long.parseLong(item.trim()));
        return result;
    }

    static List<Double> decimals(String value) {
        List<Double> result = new ArrayList<>();
        for (String item : value.split(",")) result.add(Double.parseDouble(item.trim()));
        return result;
    }

    private static List<TopologySearchMode> modes(String value) {
        List<TopologySearchMode> result = new ArrayList<>();
        for (String item : value.split(","))
            result.add(item.trim().toLowerCase(Locale.ROOT).startsWith("anneal")
                    ? TopologySearchMode.SIMULATED_ANNEALING : TopologySearchMode.HILL_CLIMBING);
        return result;
    }

    private static List<TopologyScoringProfile> profiles(String value) {
        List<TopologyScoringProfile> result = new ArrayList<>();
        for (String item : value.split(",")) result.add(TopologyScoringProfile.parse(item.trim()));
        return result;
    }
}
