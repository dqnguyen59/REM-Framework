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
import java.util.List;

public final class DynamicElasticResultLoader {
    public LoadedDynamicElasticResult load(Path path) throws IOException {
        TomlParseResult toml = Toml.parse(path);
        if (toml.hasErrors())
            throw new IllegalArgumentException("Invalid dynamic elastic result: "
                    + toml.errors());
        TomlArray nodeTables = toml.getArray("finalNodes");
        TomlArray edgeTables = toml.getArray("connections");
        if (nodeTables == null || edgeTables == null)
            throw new IllegalArgumentException(
                    "Result must contain finalNodes and connections.");
        List<DiscoveryNode> nodes = new ArrayList<>();
        for (int i = 0; i < nodeTables.size(); i++) {
            TomlTable node = nodeTables.getTable(i);
            int id = Math.toIntExact(node.getLong("id"));
            TomlArray p = node.getArray("position");
            if (p == null || p.size() != 3)
                throw new IllegalArgumentException("Node " + id + " has invalid coordinates.");
            nodes.add(new DiscoveryNode(id, new Vector3d(number(p, 0),
                    number(p, 1), number(p, 2)), Boolean.TRUE.equals(node.getBoolean("fixed"))));
        }
        List<DiscoveryConnection> edges = new ArrayList<>();
        for (int i = 0; i < edgeTables.size(); i++) {
            TomlTable edge = edgeTables.getTable(i);
            edges.add(new DiscoveryConnection(Math.toIntExact(edge.getLong("first")),
                    Math.toIntExact(edge.getLong("second"))));
        }
        TomlTable configuration = toml.getTable("configuration");
        if (configuration == null)
            throw new IllegalArgumentException("Result has no configuration table.");
        double target = decimal(configuration, "targetDistance");
        DynamicElasticSettings defaults = DynamicElasticSettings.defaults(target,
                configuration.getLong("randomSeed"));
        DynamicElasticSpatialSettings spatial = new DynamicElasticSpatialSettings(
                bool(configuration, "nonNeighborExclusionEnabled",
                        defaults.spatial().nonNeighborExclusionEnabled()),
                optionalDecimal(configuration, "minimumNonNeighborDistance",
                        defaults.spatial().minimumNonNeighborDistance()),
                bool(configuration, "edgeCrossingPreventionEnabled",
                        defaults.spatial().edgeCrossingPreventionEnabled()),
                optionalDecimal(configuration, "edgeClearanceDistance",
                        defaults.spatial().edgeClearanceDistance()),
                bool(configuration, "localSpreadEnabled",
                        defaults.spatial().localSpreadEnabled()),
                optionalDecimal(configuration, "spatialConstraintStrength",
                        defaults.spatial().strength()));
        DynamicElasticSettings settings = new DynamicElasticSettings(target,
                decimal(configuration, "requiredAccuracy"),
                decimal(configuration, "springStrength"),
                decimal(configuration, "updateStrength"),
                decimal(configuration, "velocityRetention"),
                decimal(configuration, "linearVelocityDamping"),
                decimal(configuration, "angularVelocityDamping"),
                decimal(configuration, "maximumVelocity"),
                decimal(configuration, "maximumForce"),
                decimal(configuration, "maximumEquilibriumSpeed"),
                Math.toIntExact(configuration.getLong("requiredStableIterations")),
                Math.toIntExact(configuration.getLong("maximumIterations")),
                configuration.getLong("workerUpdatesPerPublication") == null
                        ? defaults.workerUpdatesPerPublication()
                        : Math.toIntExact(configuration.getLong(
                                "workerUpdatesPerPublication")),
                defaults.randomSeed(), spatial,
                new DynamicElasticCanonicalGeometrySettings(
                        bool(configuration, "canonicalAngleConstraintsEnabled", false),
                        optionalDecimal(configuration, "targetAngleDegrees", 90),
                        optionalDecimal(configuration, "angleToleranceDegrees", 2),
                        optionalDecimal(configuration, "angleStrength", .01),
                        bool(configuration, "faceDiagonalsEnabled", false)));
        String runId = toml.getString("runId");
        GeometryDiscoveryModel model = new GeometryDiscoveryModel(
                runId == null ? path.getFileName().toString() : runId,
                "Stored fixed-degree Dynamic Elastic Discovery result", nodes, edges);
        return new LoadedDynamicElasticResult(model, settings,
                path.toAbsolutePath().normalize());
    }

    private double number(TomlArray array, int index) {
        Object value = array.get(index);
        if (!(value instanceof Number number))
            throw new IllegalArgumentException("Coordinate must be numeric.");
        return number.doubleValue();
    }
    private double decimal(TomlTable table, String key) {
        Object value = table.get(key);
        if (!(value instanceof Number number))
            throw new IllegalArgumentException("Missing numeric setting " + key + ".");
        return number.doubleValue();
    }
    private double optionalDecimal(TomlTable table, String key, double fallback) {
        Object value = table.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
    private boolean bool(TomlTable table, String key, boolean fallback) {
        Boolean value = table.getBoolean(key);
        return value == null ? fallback : value;
    }
}
