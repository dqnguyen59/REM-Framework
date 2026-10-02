package remframework.lattice;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LatticeModelLoader {
    public record LoadResult(LatticeModel model, Map<String, Object> metadata,
                             ValidationResult validation) {
    }

    public LoadResult load(Path path) {
        ValidationResult validation = new ValidationResult();
        Map<String, Object> metadata = new LinkedHashMap<>();
        try {
            TomlParseResult toml = Toml.parse(path);
            for (TomlParseError error : toml.errors()) validation.error(error.toString());
            readMetadata(toml, metadata, validation);
            if (!validation.isValid()) return new LoadResult(null, metadata, validation);

            String type = toml.getString("model.type");
            LatticeModel model = switch (type == null ? "" : type) {
                case "cubic" -> loadCubic(toml, validation);
                case "explicit" -> loadExplicit(toml, validation);
                default -> {
                    validation.error("Unsupported or missing model.type: " + type);
                    yield null;
                }
            };
            if (model != null) new LatticeModelValidator().validate(model, toml, validation);
            return new LoadResult(model, metadata, validation);
        } catch (IOException | RuntimeException exception) {
            validation.error("Could not parse " + path.getFileName() + ": " + exception.getMessage());
            return new LoadResult(null, metadata, validation);
        }
    }

    private void readMetadata(TomlParseResult toml, Map<String, Object> metadata,
                              ValidationResult validation) {
        String name = toml.getString("name");
        String description = toml.getString("description");
        String version = toml.getString("formatVersion");
        if (name == null || name.isBlank()) validation.error("Required metadata 'name' is missing.");
        if (description == null || description.isBlank())
            validation.error("Required metadata 'description' is missing.");
        if (!"1.0".equals(version)) validation.error("Unsupported formatVersion: " + version);
        metadata.put("name", name);
        metadata.put("description", description);
        metadata.put("formatVersion", version);
        metadata.put("generated", Boolean.TRUE.equals(toml.getBoolean("generated")));
        metadata.put("systemTemplate", Boolean.TRUE.equals(toml.getBoolean("systemTemplate")));
    }

    private LatticeModel loadCubic(TomlParseResult toml, ValidationResult validation) {
        Double spacing = toml.getDouble("model.spacing");
        String dimensionSource = toml.getString("model.dimensionSource");
        String boundary = toml.getString("model.boundary");
        Vector3i dimensions = null;
        if ("fixed".equals(dimensionSource)) dimensions = vector3i(toml.getArray("model.dimensions"),
                "model.dimensions", validation);
        else if (!"project".equals(dimensionSource))
            validation.error("model.dimensionSource must be 'project' or 'fixed'.");
        List<Vector3i> offsets = vectors3i(toml.getArray("model.neighborOffsets"),
                "model.neighborOffsets", validation);
        if (spacing == null) validation.error("Required model.spacing is missing.");
        if (boundary == null || (!boundary.equals("project") && !boundary.equals("open")
                && !boundary.equals("periodic")))
            validation.error("model.boundary must be 'project', 'open', or 'periodic'.");
        if (spacing == null || offsets == null) return null;
        boolean periodic = "periodic".equals(boundary);
        return new CubicLatticeModel(spacing, offsets, periodic, dimensions);
    }

    private LatticeModel loadExplicit(TomlParseResult toml, ValidationResult validation) {
        Double distance = toml.getDouble("model.equilibriumDistance");
        Vector3i dimensions = vector3i(toml.getArray("model.dimensions"),
                "model.dimensions", validation);
        TomlArray array = toml.getArray("nodes");
        if (array == null) validation.error("Explicit model requires [[nodes]] entries.");
        List<LatticeModel.NodeDefinition> nodes = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.size(); i++) {
                TomlTable node = array.getTable(i);
                String id = node.getString("id");
                Vector3i index = vector3i(node.getArray("index"), "nodes[" + i + "].index", validation);
                Vector3d position = vector3d(node.getArray("position"),
                        "nodes[" + i + "].position", validation);
                TomlArray neighborArray = node.getArray("neighbors");
                Map<Integer, String> neighbors = new LinkedHashMap<>();
                if (neighborArray != null) {
                    for (int slot = 0; slot < neighborArray.size(); slot++) {
                        String neighbor = neighborArray.getString(slot);
                        if (neighbor != null && !neighbor.isBlank()) neighbors.put(slot, neighbor);
                    }
                }
                nodes.add(new LatticeModel.NodeDefinition(id, index, position, neighbors));
            }
        }
        if (distance == null) validation.error("Required model.equilibriumDistance is missing.");
        if (distance == null || dimensions == null) return null;
        return new ExplicitLatticeModel(distance, dimensions, List.copyOf(nodes));
    }

    private List<Vector3i> vectors3i(TomlArray array, String key, ValidationResult validation) {
        if (array == null) {
            validation.error("Required " + key + " is missing.");
            return null;
        }
        List<Vector3i> result = new ArrayList<>();
        for (int i = 0; i < array.size(); i++)
            result.add(vector3i(array.getArray(i), key + "[" + i + "]", validation));
        return result.stream().filter(v -> v != null).toList();
    }

    private Vector3i vector3i(TomlArray a, String key, ValidationResult validation) {
        if (a == null || a.size() != 3) {
            validation.error(key + " must contain exactly three integers.");
            return null;
        }
        try {
            return new Vector3i(Math.toIntExact(a.getLong(0)), Math.toIntExact(a.getLong(1)),
                    Math.toIntExact(a.getLong(2)));
        } catch (RuntimeException e) {
            validation.error(key + " must contain exactly three integers.");
            return null;
        }
    }

    private Vector3d vector3d(TomlArray a, String key, ValidationResult validation) {
        if (a == null || a.size() != 3) {
            validation.error(key + " must contain exactly three numbers.");
            return null;
        }
        try {
            return new Vector3d(number(a, 0), number(a, 1), number(a, 2));
        } catch (RuntimeException e) {
            validation.error(key + " must contain exactly three numbers.");
            return null;
        }
    }

    private double number(TomlArray a, int i) {
        Object value = a.get(i);
        if (!(value instanceof Number number)) throw new IllegalArgumentException();
        return number.doubleValue();
    }
}
