package remframework.measurement.detector;

import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic exact-radius cubic orbits for Experiment 3D.
 */
public final class Experiment3DGeometry {
    public record Orbit(String id, Vector3i primitive, int expectedSize) {
        public Orbit {
            primitive = new Vector3i(primitive);
        }

        @Override
        public Vector3i primitive() {
            return new Vector3i(primitive);
        }
    }

    public record Direction(String label, String orbitId, Vector3i offset,
                            String oppositeLabel) {
        public Direction {
            offset = new Vector3i(offset);
        }

        @Override
        public Vector3i offset() {
            return new Vector3i(offset);
        }
    }

    public static final int SQUARED_RADIUS = 625;
    public static final double RADIUS = 25.0;

    private static final List<Orbit> ORBITS = List.of(
            new Orbit("ORBIT_25_0_0", new Vector3i(25, 0, 0), 6),
            new Orbit("ORBIT_24_7_0", new Vector3i(24, 7, 0), 24),
            new Orbit("ORBIT_20_15_0", new Vector3i(20, 15, 0), 24),
            new Orbit("ORBIT_20_12_9", new Vector3i(20, 12, 9), 48),
            new Orbit("ORBIT_16_15_12", new Vector3i(16, 15, 12), 48));

    private Experiment3DGeometry() {
    }

    public static List<Orbit> orbits() {
        return ORBITS;
    }

    public static List<Direction> directions() {
        List<Direction> result = new ArrayList<>(150);
        Set<Vector3i> all = new LinkedHashSet<>();
        for (Orbit orbit : ORBITS) {
            List<Vector3i> offsets = signedPermutations(orbit.primitive());
            if (offsets.size() != orbit.expectedSize()) {
                throw new IllegalStateException(
                        orbit.id() + " generated " + offsets.size()
                                + " directions, expected "
                                + orbit.expectedSize());
            }
            for (Vector3i offset : offsets) {
                if (!all.add(new Vector3i(offset))) {
                    throw new IllegalStateException(
                            "Duplicate exact-shell offset " + offset);
                }
                result.add(new Direction(label(offset), orbit.id(), offset,
                        label(new Vector3i(offset).negate())));
            }
        }
        if (result.size() != 150) {
            throw new IllegalStateException(
                    "Exact shell generated " + result.size()
                            + " directions, expected 150");
        }
        return List.copyOf(result);
    }

    public static List<Vector3i> signedPermutations(Vector3i primitive) {
        Set<Vector3i> result = new LinkedHashSet<>();
        int[] values = {primitive.x, primitive.y, primitive.z};
        int[][] permutations = {
                {0, 1, 2}, {0, 2, 1}, {1, 0, 2},
                {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        for (int[] permutation : permutations) {
            for (int sx : signs(values[permutation[0]])) {
                for (int sy : signs(values[permutation[1]])) {
                    for (int sz : signs(values[permutation[2]])) {
                        result.add(new Vector3i(
                                sx * values[permutation[0]],
                                sy * values[permutation[1]],
                                sz * values[permutation[2]]));
                    }
                }
            }
        }
        return result.stream().sorted(Comparator
                .comparingInt((Vector3i value) -> value.x)
                .thenComparingInt(value -> value.y)
                .thenComparingInt(value -> value.z))
                .map(Vector3i::new).toList();
    }

    public static Map<Vector3i, Direction> byOffset() {
        Map<Vector3i, Direction> result = new LinkedHashMap<>();
        for (Direction direction : directions()) {
            result.put(direction.offset(), direction);
        }
        return Map.copyOf(result);
    }

    public static double cubicInvariantI4(Vector3i offset) {
        Vector3d unit = new Vector3d(offset).normalize();
        return Math.pow(unit.x, 4) + Math.pow(unit.y, 4)
                + Math.pow(unit.z, 4);
    }

    private static int[] signs(int value) {
        return value == 0 ? new int[]{1} : new int[]{-1, 1};
    }

    private static String label(Vector3i value) {
        return component("X", value.x) + "_" + component("Y", value.y)
                + "_" + component("Z", value.z);
    }

    private static String component(String axis, int value) {
        char sign = value > 0 ? 'P' : value < 0 ? 'N' : 'Z';
        return String.format("%s%c%02d", axis, sign, Math.abs(value));
    }
}
