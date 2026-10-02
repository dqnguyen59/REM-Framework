package remframework.measurement.detector;

import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Pure geometry and cycle helpers used by the Gravity Test 2E pilot.
 */
public final class GravityTest2EPilotMath {
    private GravityTest2EPilotMath() {
    }

    public static int centerOfOddLattice(int size) {
        if (size <= 0 || size % 2 == 0) {
            throw new IllegalArgumentException(
                    "Cubic lattice size must be positive and odd: " + size);
        }
        return (size - 1) / 2;
    }

    public static Vector3d radialUnitVector(
            Vector3i source, Vector3i detector) {
        Vector3d radial = new Vector3d(
                detector.x - source.x,
                detector.y - source.y,
                detector.z - source.z);
        if (radial.lengthSquared() == 0.0) {
            throw new IllegalArgumentException(
                    "Detector must not occupy the source node");
        }
        return radial.normalize();
    }

    public static double radialProjection(
            Vector3d value, Vector3d radialUnitVector) {
        return value.dot(radialUnitVector);
    }

    public static double transverseMagnitude(
            Vector3d value, Vector3d radialUnitVector) {
        double radial = radialProjection(value, radialUnitVector);
        double transverseSquared = value.lengthSquared() - radial * radial;
        return Math.sqrt(Math.max(0.0, transverseSquared));
    }

    public static int reflectionEstimate(double reflectedPath) {
        if (!Double.isFinite(reflectedPath) || reflectedPath < 0.0) {
            throw new IllegalArgumentException(
                    "Reflected path must be finite and non-negative");
        }
        return (int) Math.ceil(reflectedPath) + 1;
    }

    public static int reflectionSafeCycles(
            int arrivalUpdate, int reflectionUpdate, int sourcePeriod) {
        if (arrivalUpdate < 0 || reflectionUpdate < 0 || sourcePeriod <= 0) {
            throw new IllegalArgumentException("Invalid cycle inputs");
        }
        return Math.max(0,
                (reflectionUpdate - 1 - arrivalUpdate) / sourcePeriod);
    }

    public static int nextCompleteCycle(
            int meaningfulArrivalCycleNumber) {
        if (meaningfulArrivalCycleNumber < 0) {
            throw new IllegalArgumentException(
                    "Arrival cycle must be non-negative");
        }
        return meaningfulArrivalCycleNumber + 1;
    }

    public static boolean validPositiveFitValue(double value) {
        return Double.isFinite(value) && value > 0.0;
    }
}
