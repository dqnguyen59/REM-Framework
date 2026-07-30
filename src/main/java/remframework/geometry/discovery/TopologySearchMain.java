package remframework.geometry.discovery;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class TopologySearchMain {
    private TopologySearchMain() { }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parse(args);
        String modelName = options.getOrDefault("input-model", "small-cubic");
        int nodes = integer(options, "nodes", 12);
        long seed = number(options, "seed", 42);
        double target = decimal(options, "target-distance", 1);
        GeometryDiscoverySettings geometry = GeometryDiscoverySettings.defaults(target)
                .withMaximumIterations(integer(options, "relaxation-iterations", 1_000))
                .withRandomSeed(seed);
        TopologySearchMode mode = options.getOrDefault("search-mode", "hill-climbing")
                .toLowerCase(Locale.ROOT).startsWith("anneal")
                ? TopologySearchMode.SIMULATED_ANNEALING : TopologySearchMode.HILL_CLIMBING;
        TopologySearchSettings defaults = TopologySearchSettings.defaults(seed, geometry);
        TopologyScoringProfile profile = TopologyScoringProfile.parse(
                options.getOrDefault("scoring-profile", "general"));
        TopologyScoreWeights weights = applyWeightOverrides(
                TopologyScoreWeights.forProfile(profile), options);
        TopologySearchSettings settings = new TopologySearchSettings(mode, seed,
                integer(options, "iterations", defaults.maximumSearchIterations()),
                integer(options, "mutations-per-iteration", 1),
                integer(options, "minimum-degree", defaults.minimumDegree()),
                integer(options, "maximum-degree", defaults.maximumDegree()), -1,
                integer(options, "archive-size", defaults.archiveSize()),
                decimal(options, "initial-temperature", defaults.initialTemperature()),
                decimal(options, "cooling-rate", defaults.coolingRate()),
                defaults.minimumTemperature(), defaults.scoreImprovementTolerance(), geometry,
                weights, profile, defaults.diagnosticSettings(),
                integer(options, "checkpoint-interval", 100));
        GeometryDiscoveryModel model;
        if (options.containsKey("input-file"))
            model = new GeometryDiscoveryCandidateLoader()
                    .load(Path.of(options.get("input-file"))).model();
        else model = TopologySearchExamples.create(modelName, nodes, target, seed);
        String initializationName = options.getOrDefault("coordinate-initialization", "existing")
                .replace('-', '_').toUpperCase(Locale.ROOT);
        TopologyInitialization initialization = TopologyInitialization.valueOf(initializationName);
        model = new TopologyCoordinateInitializer().initialize(model, initialization, target, seed);
        System.out.printf(Locale.ROOT,
                "Topology search: model=%s nodes=%d mode=%s profile=%s seed=%d%n",
                model.name(), model.nodes().size(), mode, profile, seed);
        TopologySearchSession session = new TopologySearchSession(model, settings);
        TopologySearchResult result = session.run(s -> {
            TopologySearchMetrics m = s.metrics();
            System.out.printf(Locale.ROOT,
                    "iteration=%d current=%.8g best=%.8g accepted=%d rejected=%d duplicates=%d temperature=%.5g elapsedMs=%d%n",
                    s.iteration(), s.currentCandidate().score().total(),
                    s.bestCandidate().score().total(), m.acceptedMutationCount(),
                    m.rejectedMutationCount(), m.duplicateTopologyCount(), s.temperature(),
                    m.elapsedMilliseconds());
        });
        Path output = Path.of(options.getOrDefault("output", "lattice/topology-search/run-001"));
        Path best = new TopologySearchExporter().export(result, output);
        System.out.printf(Locale.ROOT, "Initial score: %.10g%nBest score: %.10g%nBest candidate: %s%n",
                result.initialCandidate().score().total(), result.bestCandidate().score().total(), best);
    }

    static Map<String, String> parse(String[] args) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) throw new IllegalArgumentException("Expected --option.");
            String key = args[i].substring(2);
            String value = i + 1 < args.length && !args[i + 1].startsWith("--") ? args[++i] : "true";
            result.put(key, value);
        }
        return result;
    }
    static TopologyScoreWeights applyWeightOverrides(TopologyScoreWeights weights,
                                                     Map<String, String> options) {
        TopologyScoreWeights result = weights;
        for (Map.Entry<String, String> option : options.entrySet())
            if (option.getKey().startsWith("weight-"))
                result = result.override(option.getKey().substring("weight-".length()),
                        Double.parseDouble(option.getValue()));
        return result;
    }
    private static int integer(Map<String, String> o, String k, int d) {
        return Integer.parseInt(o.getOrDefault(k, Integer.toString(d)));
    }
    private static long number(Map<String, String> o, String k, long d) {
        return Long.parseLong(o.getOrDefault(k, Long.toString(d)));
    }
    private static double decimal(Map<String, String> o, String k, double d) {
        return Double.parseDouble(o.getOrDefault(k, Double.toString(d)));
    }
}
