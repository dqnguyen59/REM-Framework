package remframework.measurement.detector;

import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic collinear exact-radius shells for Experiment 3E.
 */
public final class Experiment3EGeometry {
    public record Orbit(String id, Vector3i primitive, int expectedSize) {
        public Orbit {
            primitive = new Vector3i(primitive);
        }

        @Override
        public Vector3i primitive() {
            return new Vector3i(primitive);
        }
    }

    public record Direction(String label, String baseDirectionLabel,
                            String orbitId, int scaleFactor,
                            Vector3i baseOffset, Vector3i offset,
                            String oppositeLabel,
                            String oppositeBaseDirectionLabel) {
        public Direction {
            baseOffset = new Vector3i(baseOffset);
            offset = new Vector3i(offset);
        }

        @Override
        public Vector3i baseOffset() {
            return new Vector3i(baseOffset);
        }

        @Override
        public Vector3i offset() {
            return new Vector3i(offset);
        }
    }

    public static final int BASE_SQUARED_RADIUS = 169;
    public static final int BASE_RADIUS = 13;
    public static final List<Integer> SCALE_FACTORS = List.of(1, 2, 3);
    private static final List<Orbit> ORBITS = List.of(
            new Orbit("ORBIT_13_0_0", new Vector3i(13, 0, 0), 6),
            new Orbit("ORBIT_12_5_0", new Vector3i(12, 5, 0), 24),
            new Orbit("ORBIT_12_4_3", new Vector3i(12, 4, 3), 48));

    private Experiment3EGeometry() {
    }

    public static List<Orbit> orbits() {
        return ORBITS;
    }

    public static List<Direction> directions() {
        List<Direction> result = new ArrayList<>(234);
        Set<Vector3i> positions = new LinkedHashSet<>();
        for (Orbit orbit : ORBITS) {
            List<Vector3i> bases = signedPermutations(orbit.primitive());
            if (bases.size() != orbit.expectedSize()) {
                throw new IllegalStateException(orbit.id() + " generated "
                        + bases.size() + " directions, expected "
                        + orbit.expectedSize());
            }
            for (Vector3i base : bases) {
                if (base.lengthSquared() != BASE_SQUARED_RADIUS) {
                    throw new IllegalStateException(
                            "Invalid base direction " + base);
                }
                String baseLabel = label(base);
                for (int scale : SCALE_FACTORS) {
                    Vector3i offset = new Vector3i(base).mul(scale);
                    if (!positions.add(new Vector3i(offset))) {
                        throw new IllegalStateException(
                                "Duplicate scaled offset " + offset);
                    }
                    result.add(new Direction(
                            orbit.id() + "__" + baseLabel + "__R"
                                    + String.format("%03d",
                                    BASE_RADIUS * scale),
                            baseLabel, orbit.id(), scale, base, offset,
                            orbit.id() + "__"
                                    + label(new Vector3i(base).negate())
                                    + "__R" + String.format("%03d",
                                    BASE_RADIUS * scale),
                            label(new Vector3i(base).negate())));
                }
            }
        }
        if (result.size() != 234) {
            throw new IllegalStateException(
                    "Experiment 3E generated " + result.size()
                            + " detectors, expected 234");
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
