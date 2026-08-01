package remframework.geometry.discovery.persistence;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Small, human-readable result summary store kept separate from topology TOML. */
public final class TestResultStore {
    public Path save(Path configurationSetDirectory, TestResult result) throws IOException {
        Path directory = configurationSetDirectory.resolve("geometry-tests");
        Files.createDirectories(directory);
        Path file = directory.resolve(result.configurationId() + "-" + Instant.now().toEpochMilli() + ".toml");
        String text = "format_version = 3\nconfiguration_id = \"" + result.configurationId() + "\"\n"
                + "experiment_profile = \"" + (result.experimentProfile() == null ? "LEGACY_UNSPECIFIED" : result.experimentProfile()) + "\"\n"
                + "eligibility_status = \"" + (result.eligibility() == null ? "NOT_RECORDED" : result.eligibility().status()) + "\"\n"
                + "eligibility_reason = \"" + escape(result.eligibility() == null ? "Legacy result" : result.eligibility().conclusion()) + "\"\n"
                + "status = \"" + result.status().code() + "\"\n"
                + "explanation = \"" + escape(result.status().explanation()) + "\"\n"
                + "realization_found = " + (result.realization() != null && result.realization().converged()) + "\n"
                + "unique_faces = " + (result.faceDerivation() != null && result.faceDerivation().unique()) + "\n"
                + "closed_boundary = " + (result.boundary() != null && result.boundary().closed()) + "\n"
                + "area_available = " + result.area().available() + "\n"
                + "volume_available = " + result.volume().available() + "\n"
                + "saved_at = \"" + Instant.now() + "\"\n";
        if (result.eligibility() != null) {
            int conditionIndex = 0;
            for (var condition : result.eligibility().conditions()) {
                text += "eligibility_condition_" + conditionIndex + " = \""
                        + escape(condition.condition() + "|" + condition.outcome() + "|" + condition.explanation()) + "\"\n";
                conditionIndex++;
            }
        }
        if (result.twoDRegularity() != null) {
            var metrics = result.twoDRegularity();
            text += "regularity_2d_area = " + metrics.area() + "\n"
                    + "regularity_2d_perimeter = " + metrics.perimeter() + "\n"
                    + "regularity_2d_circularity = " + metrics.circularity() + "\n"
                    + "regularity_2d_edge_variance = " + metrics.edgeLengths().variance() + "\n"
                    + "regularity_2d_angle_variance = " + metrics.interiorAngles().variance() + "\n"
                    + "regularity_2d_centroid_radius_variance = " + metrics.centroidRadii().variance() + "\n";
        }
        if (result.threeDRegularity() != null) {
            var metrics = result.threeDRegularity();
            text += "regularity_3d_edge_variance = " + metrics.edgeLengths().variance() + "\n"
                    + "regularity_3d_dihedral_variance = " + metrics.interiorDihedralAngles().variance() + "\n"
                    + "regularity_3d_vertex_radius_variance = " + metrics.vertexRadii().variance() + "\n"
                    + "regularity_3d_face_center_radius_variance = " + metrics.faceCenterRadii().variance() + "\n"
                    + "regularity_3d_edge_midpoint_radius_variance = " + metrics.edgeMidpointRadii().variance() + "\n";
        }
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

    public StoredResult load(Path file) throws IOException {
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
        return new StoredResult(new ConfigurationId(unquote(values.get("configuration_id"))),
                GeometryConfigurationStatusCode.valueOf(unquote(values.get("status"))),
                unquote(values.get("explanation")), Boolean.parseBoolean(values.get("realization_found")),
                Boolean.parseBoolean(values.get("unique_faces")), Boolean.parseBoolean(values.get("closed_boundary")),
                Boolean.parseBoolean(values.get("area_available")), Boolean.parseBoolean(values.get("volume_available")),
                Instant.parse(unquote(values.get("saved_at"))), coordinates);
    }

    private String unquote(String value) { return value != null && value.startsWith("\"") ? value.substring(1, value.length() - 1) : value; }
    private String escape(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
