package remframework.geometry.discovery;

import org.joml.Vector3d;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class DynamicElasticResultStore {
    public static final String HEADER = "timestamp,runId,neighborsPerNode,nodeCount,"
            + "topologySignature,seed,targetDistance,requiredAccuracy,stable,resultStatus,"
            + "connectionsSatisfied,connectionCount,meanDistanceAccuracy,maximumNodeSpeed,"
            + "iterations,elapsedMilliseconds,resultFile";
    private final Path root;

    public DynamicElasticResultStore(Path root) { this.root = root; }
    public static DynamicElasticResultStore defaults() {
        return new DynamicElasticResultStore(Path.of("lattice/discovery-results"));
    }

    public synchronized DynamicElasticResultRow append(DynamicElasticResult result)
            throws IOException {
        Files.createDirectories(root.resolve("runs"));
        String runId = Instant.now().toEpochMilli() + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        Path detail = root.resolve("runs").resolve(runId + ".toml");
        Files.writeString(detail, serialize(result, runId), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        DynamicElasticMetrics m = result.metrics();
        DynamicElasticResultRow row = new DynamicElasticResultRow(Instant.now(), runId,
                result.graph().degree(), result.graph().nodeCount(),
                result.graph().topologySignature(), result.settings().randomSeed(),
                result.settings().targetDistance(), result.settings().requiredAccuracy(),
                result.resultStatus() == DynamicElasticResultStatus.STABLE_CANDIDATE_FOUND,
                result.resultStatus(), m.connectionsSatisfied(), m.connectionCount(),
                m.meanDistanceAccuracy(), m.maximumNodeSpeed(), m.iteration(),
                result.elapsedMilliseconds(), detail);
        Path csv = root.resolve("stable-results.csv");
        if (Files.notExists(csv))
            Files.writeString(csv, HEADER + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.writeString(csv, csv(row) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        return row;
    }

    public List<DynamicElasticResultRow> loadRows() throws IOException {
        Path csv = root.resolve("stable-results.csv");
        if (Files.notExists(csv)) return List.of();
        List<DynamicElasticResultRow> rows = new ArrayList<>();
        for (String line : Files.readAllLines(csv, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.equals(HEADER)) continue;
            try {
                List<String> v = parseCsv(line);
                if (v.size() != 17) continue;
                rows.add(new DynamicElasticResultRow(Instant.parse(v.get(0)), v.get(1),
                        Integer.parseInt(v.get(2)), Integer.parseInt(v.get(3)), v.get(4),
                        Long.parseLong(v.get(5)), Double.parseDouble(v.get(6)),
                        Double.parseDouble(v.get(7)), Boolean.parseBoolean(v.get(8)),
                        DynamicElasticResultStatus.valueOf(v.get(9)), Integer.parseInt(v.get(10)),
                        Integer.parseInt(v.get(11)), Double.parseDouble(v.get(12)),
                        Double.parseDouble(v.get(13)), Integer.parseInt(v.get(14)),
                        Long.parseLong(v.get(15)), Path.of(v.get(16))));
            } catch (RuntimeException ignored) {
                // Defensive row loading: malformed rows do not hide valid rows.
            }
        }
        return List.copyOf(rows);
    }

    public String serialize(DynamicElasticResult result, String runId) {
        StringBuilder out = new StringBuilder("# REM Framework Dynamic Elastic Discovery Result\n");
        line(out, "runId", runId);
        line(out, "timestamp", Instant.now().toString());
        out.append("neighborsPerNode = ").append(result.graph().degree()).append('\n');
        out.append("nodeCount = ").append(result.graph().nodeCount()).append('\n');
        line(out, "topologySignature", result.graph().topologySignature());
        line(out, "resultStatus", result.resultStatus().name());
        line(out, "failureReason", result.failureReason());
        DynamicElasticSettings s = result.settings();
        out.append("\n[configuration]\n");
        number(out, "targetDistance", s.targetDistance());
        number(out, "requiredAccuracy", s.requiredAccuracy());
        number(out, "springStrength", s.springStrength());
        number(out, "updateStrength", s.updateStrength());
        number(out, "velocityRetention", s.velocityRetention());
        number(out, "linearVelocityDamping", s.linearVelocityDamping());
        number(out, "angularVelocityDamping", s.angularVelocityDamping());
        number(out, "maximumVelocity", s.maximumVelocity());
        number(out, "maximumForce", s.maximumForce());
        number(out, "maximumEquilibriumSpeed", s.maximumEquilibriumSpeed());
        out.append("requiredStableIterations = ").append(s.requiredStableIterations()).append('\n');
        out.append("maximumIterations = ").append(s.maximumIterations()).append('\n');
        out.append("workerUpdatesPerPublication = ")
                .append(s.workerUpdatesPerPublication()).append('\n');
        out.append("randomSeed = ").append(s.randomSeed()).append('\n');
        DynamicElasticMetrics m = result.metrics();
        out.append("\n[metrics]\n");
        out.append("connectionsSatisfied = ").append(m.connectionsSatisfied()).append('\n');
        out.append("connectionCount = ").append(m.connectionCount()).append('\n');
        number(out, "meanDistanceAccuracy", m.meanDistanceAccuracy());
        number(out, "maximumNodeSpeed", m.maximumNodeSpeed());
        out.append("iterations = ").append(m.iteration()).append('\n');
        out.append("stableIterations = ").append(m.stableIterations()).append('\n');
        for (DiscoveryConnection edge : result.graph().connections()) {
            out.append("\n[[connections]]\nfirst = ").append(edge.firstNodeId())
                    .append("\nsecond = ").append(edge.secondNodeId()).append('\n');
        }
        appendNodes(out, "initialNodes", result.initialNodes());
        appendNodes(out, "finalNodes", result.finalNodes());
        return out.toString();
    }

    private void appendNodes(StringBuilder out, String table, List<DynamicElasticNode> nodes) {
        for (DynamicElasticNode node : nodes) {
            out.append("\n[[").append(table).append("]]\nid = ").append(node.id()).append('\n');
            vector(out, "position", node.position());
            vector(out, "velocity", node.velocity());
            out.append("fixed = ").append(node.fixed()).append('\n');
        }
    }
    private String csv(DynamicElasticResultRow r) {
        return String.join(",", csvValue(r.timestamp().toString()), csvValue(r.runId()),
                Integer.toString(r.neighborsPerNode()), Integer.toString(r.nodeCount()),
                csvValue(r.topologySignature()), Long.toString(r.seed()),
                Double.toString(r.targetDistance()), Double.toString(r.requiredAccuracy()),
                Boolean.toString(r.stable()), r.status().name(),
                Integer.toString(r.connectionsSatisfied()), Integer.toString(r.connectionCount()),
                Double.toString(r.meanDistanceAccuracy()), Double.toString(r.maximumNodeSpeed()),
                Integer.toString(r.iterations()), Long.toString(r.elapsedMilliseconds()),
                csvValue(r.resultFile().toString()));
    }
    private String csvValue(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0)
            return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }
    private List<String> parseCsv(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (c == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else value.append(c);
        }
        if (quoted) throw new IllegalArgumentException("Unterminated CSV quote.");
        values.add(value.toString());
        return values;
    }
    private void vector(StringBuilder out, String key, Vector3d v) {
        out.append(key).append(" = [").append(format(v.x)).append(", ")
                .append(format(v.y)).append(", ").append(format(v.z)).append("]\n");
    }
    private void line(StringBuilder out, String key, String value) {
        out.append(key).append(" = \"").append(value.replace("\\", "\\\\")
                .replace("\"", "\\\"")).append("\"\n");
    }
    private void number(StringBuilder out, String key, double value) {
        out.append(key).append(" = ").append(format(value)).append('\n');
    }
    private String format(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite result value.");
        return String.format(Locale.ROOT, "%.17g", value);
    }
}
