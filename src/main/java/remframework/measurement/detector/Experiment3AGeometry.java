package remframework.measurement.detector;

import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable high-symmetry direction geometry for Experiment 3A.
 */
public final class Experiment3AGeometry {
    public enum DirectionClass {
        AXIS, FACE_DIAGONAL, BODY_DIAGONAL
    }

    public record DirectionSpec(
            String label,
            Vector3i requestedDirection,
            DirectionClass directionClass,
            String oppositeLabel) {
        public DirectionSpec {
            requestedDirection = new Vector3i(requestedDirection);
        }

        @Override
        public Vector3i requestedDirection() {
            return new Vector3i(requestedDirection);
        }

        public Vector3d unitDirection() {
            return new Vector3d(
                    requestedDirection.x,
                    requestedDirection.y,
                    requestedDirection.z).normalize();
        }
    }

    private Experiment3AGeometry() {
    }

    public static List<DirectionSpec> highSymmetryDirections() {
        List<DirectionSpec> result = new ArrayList<>(26);
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    int nonZero = (x == 0 ? 0 : 1)
                            + (y == 0 ? 0 : 1) + (z == 0 ? 0 : 1);
                    if (nonZero == 0) continue;
                    Vector3i direction = new Vector3i(x, y, z);
                    DirectionClass directionClass = switch (nonZero) {
                        case 1 -> DirectionClass.AXIS;
                        case 2 -> DirectionClass.FACE_DIAGONAL;
                        default -> DirectionClass.BODY_DIAGONAL;
                    };
                    result.add(new DirectionSpec(
                            label(direction), direction, directionClass,
                            label(new Vector3i(direction).negate())));
                }
            }
        }
        return List.copyOf(result);
    }

    public static Vector3i integerOffset(
            DirectionSpec direction, int requestedRadius) {
        if (requestedRadius <= 0) {
            throw new IllegalArgumentException(
                    "Requested radius must be positive");
        }
        Vector3d ideal = direction.unitDirection().mul(requestedRadius);
        return new Vector3i(
                (int) Math.round(ideal.x),
                (int) Math.round(ideal.y),
                (int) Math.round(ideal.z));
    }

    public static Map<Vector3i, DirectionSpec> uniquePlacements(
            int requestedRadius) {
        Map<Vector3i, DirectionSpec> placements = new HashMap<>();
        for (DirectionSpec direction : highSymmetryDirections()) {
            Vector3i offset = integerOffset(direction, requestedRadius);
            DirectionSpec duplicate = placements.put(
                    new Vector3i(offset), direction);
            if (duplicate != null) {
                throw new IllegalArgumentException(
                        "Experiment 3A directions " + duplicate.label()
                                + " and " + direction.label()
                                + " map to duplicate offset " + offset);
            }
        }
        return Map.copyOf(placements);
    }

    public static double thetaDegrees(Vector3d radialUnit) {
        return Math.toDegrees(Math.acos(
                Math.clamp(radialUnit.z, -1.0, 1.0)));
    }

    public static double phiDegrees(Vector3d radialUnit) {
        double value = Math.toDegrees(Math.atan2(
                radialUnit.y, radialUnit.x));
        return value >= 180.0 ? value - 360.0 : value;
    }

    public static double radialProjection(
            Vector3d value, Vector3d actualRadialUnit) {
        return value.dot(actualRadialUnit);
    }

    public static double evenDifference(double first, double opposite) {
        return Math.abs(first - opposite)
                / parityDenominator(first, opposite);
    }

    public static double oddResidual(double first, double opposite) {
        return Math.abs(first + opposite)
                / parityDenominator(first, opposite);
    }

    public static List<Integer> completeCycleEndUpdates(
            int updateCount, int sourcePeriod) {
        if (updateCount < 0 || sourcePeriod <= 0) {
            throw new IllegalArgumentException("Invalid cycle inputs");
        }
        List<Integer> result = new ArrayList<>();
        for (int update = sourcePeriod;
                update <= updateCount; update += sourcePeriod) {
            result.add(update);
        }
        return List.copyOf(result);
    }

    private static double parityDenominator(double first, double opposite) {
        return Math.max((Math.abs(first) + Math.abs(opposite)) / 2.0,
                Math.ulp(1.0));
    }

    private static String label(Vector3i direction) {
        StringBuilder result = new StringBuilder();
        appendAxis(result, direction.x, "X");
        appendAxis(result, direction.y, "Y");
        appendAxis(result, direction.z, "Z");
        return result.toString();
    }

    private static void appendAxis(
            StringBuilder result, int component, String axis) {
        if (component > 0) result.append('P').append(axis);
        if (component < 0) result.append('N').append(axis);
    }
}
