package remframework.measurement.detector;

import org.ini4j.Wini;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses and validates passive detector definitions from a project file.
 */
public final class DetectorConfiguration {
    private DetectorConfiguration() {
    }

    public static List<Detector> load(Wini ini, Vector3i dimension) {
        String sizeValue = ini.get("Detectors", "Size");
        int size = sizeValue == null || sizeValue.isBlank()
                   ? 0 : parseInt(sizeValue, "Detectors.Size");
        List<Detector> result = new ArrayList<>(size);
        Set<String> names = new HashSet<>();
        for (int i = 0; i < size; i++) {
            String section = "Detector" + i;
            String name = required(ini, section, "name");
            if (!names.add(name)) {
                throw new IllegalArgumentException(
                        "Duplicate detector name: " + name);
            }
            int x = requiredInt(ini, section, "nodeIndex.x");
            int y = requiredInt(ini, section, "nodeIndex.y");
            int z = requiredInt(ini, section, "nodeIndex.z");
            if (x < 0 || x >= dimension.x
                    || y < 0 || y >= dimension.y
                    || z < 0 || z >= dimension.z) {
                throw new IllegalArgumentException(
                        "Detector " + name + " node index (" + x + ", " + y
                                + ", " + z + ") is outside lattice dimension ("
                                + dimension.x + ", " + dimension.y + ", "
                                + dimension.z + ")");
            }
            result.add(new Detector(name, new Vector3i(x, y, z),
                    optionalInt(ini, section, "requestedRadius"),
                    optionalDouble(ini, section, "requestedAngleDegrees"),
                    optional(ini, section, "direction"),
                    optional(ini, section, "description")));
        }
        loadRadialAxisDetectors(ini, dimension, result, names);
        return List.copyOf(result);
    }

    private static void loadRadialAxisDetectors(
            Wini ini, Vector3i dimension, List<Detector> result,
            Set<String> names) {
        String radiiValue = ini.get("RadialAxisDetectors", "radii");
        if (radiiValue == null || radiiValue.isBlank()) return;
        int sourceX = requiredInt(ini, "RadialAxisDetectors", "source.x");
        int sourceY = requiredInt(ini, "RadialAxisDetectors", "source.y");
        int sourceZ = requiredInt(ini, "RadialAxisDetectors", "source.z");
        boolean includeZAxes = Boolean.parseBoolean(
                optional(ini, "RadialAxisDetectors", "includeZAxes"));
        for (String radiusText : radiiValue.split(",")) {
            int radius = parseInt(radiusText.trim(),
                    "RadialAxisDetectors.radii");
            addAxisDetector(result, names, dimension, sourceX, sourceY,
                    sourceZ, radius, "PX", radius, 0, 0, 0.0);
            addAxisDetector(result, names, dimension, sourceX, sourceY,
                    sourceZ, radius, "NX", -radius, 0, 0, 180.0);
            addAxisDetector(result, names, dimension, sourceX, sourceY,
                    sourceZ, radius, "PY", 0, radius, 0, 90.0);
            addAxisDetector(result, names, dimension, sourceX, sourceY,
                    sourceZ, radius, "NY", 0, -radius, 0, 270.0);
            if (includeZAxes) {
                addAxisDetector(result, names, dimension, sourceX, sourceY,
                        sourceZ, radius, "PZ", 0, 0, radius, null);
                addAxisDetector(result, names, dimension, sourceX, sourceY,
                        sourceZ, radius, "NZ", 0, 0, -radius, null);
            }
        }
    }

    private static void addAxisDetector(
            List<Detector> result, Set<String> names, Vector3i dimension,
            int sourceX, int sourceY, int sourceZ, int radius,
            String direction, int dx, int dy, int dz, Double angle) {
        if (radius <= 0) {
            throw new IllegalArgumentException(
                    "Radial detector radius must be positive: " + radius);
        }
        String name = String.format("%s_R%03d", direction, radius);
        if (!names.add(name)) {
            throw new IllegalArgumentException(
                    "Duplicate detector name: " + name);
        }
        int x = sourceX + dx;
        int y = sourceY + dy;
        int z = sourceZ + dz;
        if (x < 0 || x >= dimension.x || y < 0 || y >= dimension.y
                || z < 0 || z >= dimension.z) {
            throw new IllegalArgumentException(
                    "Detector " + name + " node index (" + x + ", " + y
                            + ", " + z + ") is outside lattice dimension ("
                            + dimension.x + ", " + dimension.y + ", "
                            + dimension.z + ")");
        }
        result.add(new Detector(name, new Vector3i(x, y, z), radius,
                angle, direction, "Exact lattice-axis radial detector"));
    }

    private static String required(Wini ini, String section, String key) {
        String value = ini.get(section, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required detector property " + section + "." + key);
        }
        return value.trim();
    }

    private static int requiredInt(Wini ini, String section, String key) {
        return parseInt(required(ini, section, key), section + "." + key);
    }

    private static Integer optionalInt(Wini ini, String section, String key) {
        String value = ini.get(section, key);
        return value == null || value.isBlank()
               ? null : parseInt(value, section + "." + key);
    }

    private static Double optionalDouble(Wini ini, String section, String key) {
        String value = ini.get(section, key);
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Invalid number for " + section + "." + key + ": " + value,
                    exception);
        }
    }

    private static String optional(Wini ini, String section, String key) {
        String value = ini.get(section, key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int parseInt(String value, String property) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Invalid integer for " + property + ": " + value, exception);
        }
    }
}
