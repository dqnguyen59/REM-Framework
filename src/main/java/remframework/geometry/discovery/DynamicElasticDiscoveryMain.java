package remframework.geometry.discovery;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Standalone fixed-degree Dynamic Elastic Discovery entry point. */
public final class DynamicElasticDiscoveryMain {
    private DynamicElasticDiscoveryMain() { }

    public static void main(String[] arguments) throws Exception {
        Map<String, String> options = parse(arguments);
        int degree = Integer.parseInt(options.getOrDefault("neighbors", "2"));
        int nodes = Integer.parseInt(options.getOrDefault(
                "nodes", degree == 2 ? "3" : "4"));
        CanonicalDynamicElasticTopology canonical = canonical(options.get("canonical-topology"));
        if (canonical != null) {
            degree = canonical.exactDegree();
            if (canonical != CanonicalDynamicElasticTopology.CYCLE)
                nodes = canonical.fixedNodeCount();
        }
        long seed = Long.parseLong(options.getOrDefault("seed", "42"));
        double target = Double.parseDouble(
                options.getOrDefault("target-distance", "10"));
        double accuracy = Double.parseDouble(
                options.getOrDefault("required-accuracy", "98")) / 100;
        DynamicElasticSettings settings = DynamicElasticSettings
                .defaults(target, seed).withRequiredAccuracy(accuracy);
        boolean spatialEnabled = Boolean.parseBoolean(
                options.getOrDefault("spatial-constraints", "true"));
        if (!spatialEnabled)
            settings = settings.withSpatial(DynamicElasticSpatialSettings.disabled(target));
        if (options.containsKey("maximum-nodes")) {
            runBounded(options, degree, nodes, settings);
            return;
        }
        FixedDegreeValidation validation =
                new FixedDegreeValidator().validate(nodes, degree);
        if (!validation.valid()) {
            System.err.println(String.join(System.lineSeparator(), validation.messages()));
            System.err.println("Nearest lower valid N: " + validation.nearestLowerValid());
            System.err.println("Nearest higher valid N: " + validation.nearestHigherValid());
            return;
        }
        FixedDegreeGraph graph = canonical == null
                ? new FixedDegreeGraphGenerator().generate(nodes, degree, seed, 0)
                : canonical.create(nodes);
        if (canonical == CanonicalDynamicElasticTopology.CUBE && spatialEnabled)
            settings = settings.withCanonicalGeometry(
                            DynamicElasticCanonicalGeometrySettings.forTopology(canonical));
        else if (canonical == CanonicalDynamicElasticTopology.TETRAHEDRON)
            settings = settings.withCanonicalGeometry(
                    DynamicElasticCanonicalGeometrySettings.forTopology(canonical));
        DynamicElasticSession session = new DynamicElasticSession(graph, settings);
        System.out.println("REM Framework Dynamic Elastic Discovery");
        System.out.println("K=" + degree + ", N=" + nodes + ", edges="
                + graph.connections().size() + ", seed=" + seed);
        System.out.println("Topology source=" + graph.topologySource()
                + ", canonical=" + (graph.canonicalTopology() == null ? ""
                : graph.canonicalTopology().displayName())
                + ", spatial constraints=" + spatialEnabled);
        while (!session.finished()) {
            session.step();
            if (session.iteration() % 1000 == 0) print(session.metrics());
        }
        DynamicElasticResult result = session.result();
        print(result.metrics());
        DynamicElasticResultStore store = options.containsKey("output")
                ? new DynamicElasticResultStore(Path.of(options.get("output")))
                : DynamicElasticResultStore.defaults();
        DynamicElasticResultRow row = store.append(result);
        System.out.println("Result: " + result.resultStatus());
        System.out.println("Detailed result: " + row.resultFile());
    }

    private static void runBounded(Map<String, String> options, int degree, int start,
                                   DynamicElasticSettings settings) throws Exception {
        int maximum = Integer.parseInt(options.get("maximum-nodes"));
        int attempts = Integer.parseInt(
                options.getOrDefault("topology-attempts", "1"));
        int seeds = Integer.parseInt(options.getOrDefault("seeds-per-topology", "1"));
        int maximumUnique = Integer.parseInt(
                options.getOrDefault("maximum-unique-topologies", "100"));
        DynamicElasticSearchPolicy policy = switch (
                options.getOrDefault("policy", "first-per-node-count")) {
            case "stop-first" -> DynamicElasticSearchPolicy.STOP_AT_FIRST_STABLE_CANDIDATE;
            case "all" -> DynamicElasticSearchPolicy.FIND_ALL_STABLE_CANDIDATES;
            default ->
                    DynamicElasticSearchPolicy.FIND_FIRST_STABLE_CANDIDATE_PER_NODE_COUNT;
        };
        DynamicElasticSearchSettings search = new DynamicElasticSearchSettings(
                degree, start, maximum, attempts, seeds, policy, settings, maximumUnique);
        DynamicElasticResultStore store = options.containsKey("output")
                ? new DynamicElasticResultStore(Path.of(options.get("output")))
                : DynamicElasticResultStore.defaults();
        for (DynamicElasticSearchRecord record :
                new DynamicElasticBoundedSearch().run(search)) {
            if (record.result() == null) {
                System.out.println("Skipping N=" + record.nodeCount() + ": "
                        + record.skippedReason());
                continue;
            }
            DynamicElasticResultRow row = store.append(record.result());
            System.out.println("N=" + record.nodeCount() + ", attempt="
                    + record.topologyAttempt() + ", seed=" + record.seed()
                    + ", status=" + record.result().resultStatus()
                    + ", file=" + row.resultFile());
        }
    }

    private static void print(DynamicElasticMetrics metrics) {
        System.out.printf("iteration=%d, satisfied=%d/%d, accuracy=%.4f%%, "
                        + "maximumSpeed=%.7g, stable=%d/%d, state=%s%n",
                metrics.iteration(), metrics.connectionsSatisfied(),
                metrics.connectionCount(), metrics.meanDistanceAccuracy() * 100,
                metrics.maximumNodeSpeed(), metrics.stableIterations(),
                metrics.requiredStableIterations(), metrics.state());
        System.out.printf("minimumNonNeighbor=%.7g, clearanceViolations=%d, "
                        + "meanLocalSpread=%.7g, minimumLocalSpread=%.7g, "
                        + "angles=%d/%d, maximumAngleError=%.7g, "
                        + "elasticEquilibrium=%s, spatiallyValid=%s, canonicalValid=%s%n",
                metrics.minimumObservedNonNeighborDistance(),
                metrics.edgeClearanceViolations(), metrics.meanLocalSpread(),
                metrics.minimumLocalSpread(), metrics.localAnglesSatisfied(),
                metrics.localAngleCount(), metrics.maximumAngleErrorDegrees(),
                metrics.elasticEquilibrium(), metrics.spatiallyValid(),
                metrics.canonicalGeometryValid());
    }
    private static CanonicalDynamicElasticTopology canonical(String value) {
        if (value == null || value.isBlank()) return null;
        return CanonicalDynamicElasticTopology.valueOf(
                value.replace('-', '_').toUpperCase(java.util.Locale.ROOT));
    }
    private static Map<String, String> parse(String[] arguments) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < arguments.length; i++) {
            String argument = arguments[i];
            if (!argument.startsWith("--")) continue;
            if (i + 1 >= arguments.length)
                throw new IllegalArgumentException("Missing value for " + argument);
            result.put(argument.substring(2), arguments[++i]);
        }
        return result;
    }
}
