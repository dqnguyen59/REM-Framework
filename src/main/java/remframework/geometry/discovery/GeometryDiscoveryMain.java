package remframework.geometry.discovery;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GeometryDiscoveryMain {
    private GeometryDiscoveryMain() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parse(args);
        String modelName = options.getOrDefault("model", "tetrahedron");
        double target = Double.parseDouble(options.getOrDefault("target", "10.0"));
        GeometryDiscoverySettings settings = GeometryDiscoverySettings.defaults(target);
        if (options.containsKey("iterations"))
            settings = settings.withMaximumIterations(
                    Integer.parseInt(options.get("iterations")));
        if (options.containsKey("seed"))
            settings = settings.withRandomSeed(Long.parseLong(options.get("seed")));
        Path output = Path.of(options.getOrDefault("output",
                "lattice/candidates/" + modelName + "-candidate.toml"));
        GeometryDiscoveryModel model = GeometryDiscoveryExamples.create(modelName, target);

        System.out.println("Geometry Discovery Engine 0.1");
        System.out.println("Input model: " + model.name());
        System.out.println("Settings: " + settings);
        GeometryDiscoveryResult result = new GeometryDiscoveryEngine().relax(model, settings,
                (iteration, metrics) -> System.out.printf(
                        "Progress: iteration=%d edgeError=%.9g collisions=%d%n",
                        iteration, metrics.totalEdgeLengthError(),
                        metrics.collisionViolationCount()));
        System.out.println("Converged: " + result.converged());
        System.out.println("Final metrics: " + result.metrics());
        for (String diagnostic : result.diagnostics())
            System.out.println("Diagnostic: " + diagnostic);
        System.out.println("Runtime compatible: " + result.runtimeCompatibility().compatible());
        System.out.println("Runtime compatibility: " + result.runtimeCompatibility().reason());
        Path exported = new GeometryDiscoveryExporter().export(result, output);
        System.out.println("Export path: " + exported);
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            if (!argument.startsWith("--"))
                throw new IllegalArgumentException("Expected --option value, got: " + argument);
            String key = argument.substring(2);
            if (key.isBlank() || i + 1 >= args.length || args[i + 1].startsWith("--"))
                throw new IllegalArgumentException("Missing value for " + argument);
            result.put(key, args[++i]);
        }
        for (String key : result.keySet())
            if (!java.util.Set.of("model", "target", "iterations", "output", "seed").contains(key))
                throw new IllegalArgumentException("Unknown option --" + key);
        return result;
    }
}
