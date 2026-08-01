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

import org.joml.Vector3d;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GeometryDiscoveryCandidateLoader {
    public LoadedDiscoveryCandidate load(Path path) throws IOException {
        TomlParseResult toml = Toml.parse(path);
        if (toml.hasErrors())
            throw new IllegalArgumentException("Invalid candidate TOML: " + toml.errors());
        if (!"explicit".equals(toml.getString("model.type")))
            throw new IllegalArgumentException("Candidate model.type must be 'explicit'.");
        TomlArray array = toml.getArray("nodes");
        if (array == null) throw new IllegalArgumentException("Candidate has no [[nodes]] entries.");
        List<DiscoveryNode> nodes = new ArrayList<>();
        List<List<Integer>> neighborLists = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            TomlTable table = array.getTable(i);
            int id = Integer.parseInt(table.getString("id"));
            TomlArray p = table.getArray("position");
            if (p == null || p.size() != 3)
                throw new IllegalArgumentException("Node " + id + " has no 3D position.");
            nodes.add(new DiscoveryNode(id, new Vector3d(number(p, 0), number(p, 1),
                    number(p, 2)), id == 0));
            List<Integer> neighbors = new ArrayList<>();
            TomlArray neighborArray = table.getArray("neighbors");
            if (neighborArray != null) {
                for (int slot = 0; slot < neighborArray.size(); slot++) {
                    String value = neighborArray.getString(slot);
                    if (value != null && !value.isBlank())
                        neighbors.add(Integer.parseInt(value));
                }
            }
            neighborLists.add(neighbors);
        }
        List<DiscoveryConnection> connections = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            for (Integer neighbor : neighborLists.get(i)) {
                DiscoveryConnection connection =
                        new DiscoveryConnection(nodes.get(i).id(), neighbor);
                if (seen.add(connection.undirectedKey())) connections.add(connection);
            }
        }
        String name = string(toml, "name", path.getFileName().toString());
        String description = string(toml, "description", "Loaded discovery candidate");
        GeometryDiscoveryModel model =
                new GeometryDiscoveryModel(name, description, nodes, connections);
        double target = decimal(toml, "discovery.targetDistance",
                decimal(toml, "model.equilibriumDistance", 1.0));
        GeometryDiscoverySettings settings = GeometryDiscoverySettings.defaults(target);
        boolean converged = bool(toml, "discovery.converged", false);
        int iterations = (int) integer(toml, "discovery.iterations", 0);
        GeometryDiscoveryMetrics calculated = new GeometryDiscoveryMetricsCalculator()
                .calculate(model, settings, iterations, converged);
        RuntimeCompatibility compatibility = new GeometryRuntimeCompatibilityAnalyzer()
                .analyze(model, Math.max(settings.dimensionalityTolerance(),
                        settings.convergenceTolerance() * 10));
        DiscoveryValidationResult validation =
                new GeometryDiscoveryValidator().validate(model, settings);
        GeometryDiscoveryResult stored = new GeometryDiscoveryResult(model.copy(), settings,
                calculated, validation, compatibility, List.of("Loaded from " + path));
        return new LoadedDiscoveryCandidate(model, settings, stored,
                path.toAbsolutePath().normalize());
    }

    private double number(TomlArray array, int index) {
        Object value = array.get(index);
        if (!(value instanceof Number number))
            throw new IllegalArgumentException("Position contains a non-number.");
        return number.doubleValue();
    }

    private String string(TomlParseResult toml, String key, String fallback) {
        String value = toml.getString(key);
        return value == null ? fallback : value;
    }

    private double decimal(TomlParseResult toml, String key, double fallback) {
        Double value = toml.getDouble(key);
        if (value != null) return value;
        Long integer = toml.getLong(key);
        return integer == null ? fallback : integer.doubleValue();
    }

    private long integer(TomlParseResult toml, String key, long fallback) {
        Long value = toml.getLong(key);
        return value == null ? fallback : value;
    }

    private boolean bool(TomlParseResult toml, String key, boolean fallback) {
        Boolean value = toml.getBoolean(key);
        return value == null ? fallback : value;
    }
}
