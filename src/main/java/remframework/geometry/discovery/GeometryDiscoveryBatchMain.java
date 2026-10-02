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

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GeometryDiscoveryBatchMain {
    private GeometryDiscoveryBatchMain() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parse(args);
        long[] seeds = range(options.getOrDefault("seeds", "1:5"));
        GeometryBatchParameters parameters = new GeometryBatchParameters(
                options.getOrDefault("model", "tetrahedron"), seeds[0], seeds[1],
                Double.parseDouble(options.getOrDefault("target", "10")),
                doubles(options.getOrDefault("learning-rates", "0.04,0.08")),
                doubles(options.getOrDefault("repulsion-strengths", "0.1,0.25")),
                doubles(options.getOrDefault("maximum-corrections", "2.0")),
                Integer.parseInt(options.getOrDefault("iterations", "20000")),
                Double.parseDouble(options.getOrDefault("tolerance", "1e-7")),
                Path.of(options.getOrDefault("output",
                        "lattice/experiments/" + options.getOrDefault("model", "tetrahedron"))),
                Boolean.parseBoolean(options.getOrDefault("export-candidates", "false")));
        GeometryDiscoveryBatchRunner.Output output =
                new GeometryDiscoveryBatchRunner().run(parameters);
        System.out.println("Geometry Discovery Batch complete");
        System.out.println("Runs: " + output.aggregate().totalRuns());
        System.out.println("Convergence rate: " + output.aggregate().convergenceRate());
        System.out.println("CSV: " + output.csvPath());
        System.out.println("Aggregate: " + output.aggregatePath());
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--") || i + 1 >= args.length)
                throw new IllegalArgumentException("Expected --option value.");
            result.put(args[i].substring(2), args[++i]);
        }
        return result;
    }

    private static long[] range(String value) {
        String[] parts = value.split(":", -1);
        if (parts.length != 2) throw new IllegalArgumentException("--seeds must be first:last");
        long first = Long.parseLong(parts[0]);
        long last = Long.parseLong(parts[1]);
        if (last < first) throw new IllegalArgumentException("Seed range is descending.");
        return new long[]{first, last};
    }

    private static List<Double> doubles(String value) {
        return Arrays.stream(value.split(",")).map(String::trim)
                .map(Double::parseDouble).toList();
    }
}
