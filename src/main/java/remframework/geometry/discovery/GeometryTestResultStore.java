package remframework.geometry.discovery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Small, human-readable result summary store kept separate from topology TOML. */
public final class GeometryTestResultStore {
    public Path save(Path configurationSetDirectory, GeometryTestResult result) throws IOException {
        Path directory = configurationSetDirectory.resolve("geometry-tests");
        Files.createDirectories(directory);
        Path file = directory.resolve(result.configurationId() + "-" + Instant.now().toEpochMilli() + ".toml");
        String text = "configuration_id = \"" + result.configurationId() + "\"\n"
                + "status = \"" + result.status().code() + "\"\n"
                + "explanation = \"" + escape(result.status().explanation()) + "\"\n"
                + "realization_found = " + (result.realization() != null && result.realization().converged()) + "\n"
                + "unique_faces = " + (result.faceDerivation() != null && result.faceDerivation().unique()) + "\n"
                + "closed_boundary = " + (result.boundary() != null && result.boundary().closed()) + "\n"
                + "area_available = " + result.area().available() + "\n"
                + "volume_available = " + result.volume().available() + "\n"
                + "saved_at = \"" + Instant.now() + "\"\n";
        if (result.realization() != null) {
            for (int i = 0; i < result.realization().finalCoordinates().size(); i++) {
                var coordinate = result.realization().finalCoordinates().get(i);
                text += "coordinate_" + i + " = [" + coordinate.x + ", "
                        + coordinate.y + ", " + coordinate.z + "]\n";
            }
        }
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    public GeometryTestStoredResult load(Path file) throws IOException {
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            int separator = line.indexOf('=');
            if (separator > 0) values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }
        java.util.List<org.joml.Vector3d> coordinates = new java.util.ArrayList<>();
        for (int index = 0; values.containsKey("coordinate_" + index); index++) {
            String[] parts = values.get("coordinate_" + index).replace("[", "")
                    .replace("]", "").split(",");
            coordinates.add(new org.joml.Vector3d(Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim())));
        }
        return new GeometryTestStoredResult(new GeometryConfigurationId(unquote(values.get("configuration_id"))),
                GeometryConfigurationStatusCode.valueOf(unquote(values.get("status"))),
                unquote(values.get("explanation")), Boolean.parseBoolean(values.get("realization_found")),
                Boolean.parseBoolean(values.get("unique_faces")), Boolean.parseBoolean(values.get("closed_boundary")),
                Boolean.parseBoolean(values.get("area_available")), Boolean.parseBoolean(values.get("volume_available")),
                Instant.parse(unquote(values.get("saved_at"))), coordinates);
    }

    private String unquote(String value) { return value != null && value.startsWith("\"") ? value.substring(1, value.length() - 1) : value; }
    private String escape(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
