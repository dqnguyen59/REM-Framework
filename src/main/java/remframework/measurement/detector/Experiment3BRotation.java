package remframework.measurement.detector;

import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Exact source-relative coordinate conventions for Experiment 3B.
 */
public final class Experiment3BRotation {
    public enum SourceAxis {
        X(new Vector3i(1, 0, 0), new Vector3i(0, 1, 0),
                new Vector3i(0, 0, 1)),
        Y(new Vector3i(0, 1, 0), new Vector3i(0, 0, 1),
                new Vector3i(1, 0, 0)),
        Z(new Vector3i(0, 0, 1), new Vector3i(1, 0, 0),
                new Vector3i(0, 1, 0));

        private final Vector3i longitudinal;
        private final Vector3i transverseU;
        private final Vector3i transverseV;

        SourceAxis(Vector3i longitudinal, Vector3i transverseU,
                   Vector3i transverseV) {
            this.longitudinal = longitudinal;
            this.transverseU = transverseU;
            this.transverseV = transverseV;
        }

        public Vector3d longitudinal() {
            return new Vector3d(longitudinal);
        }

        public Vector3d transverseU() {
            return new Vector3d(transverseU);
        }

        public Vector3d transverseV() {
            return new Vector3d(transverseV);
        }
    }

    private Experiment3BRotation() {
    }

    public static Vector3i canonicalComponents(
            Vector3i latticeDirection, SourceAxis sourceAxis) {
        return new Vector3i(
                dot(latticeDirection, sourceAxis.longitudinal),
                dot(latticeDirection, sourceAxis.transverseU),
                dot(latticeDirection, sourceAxis.transverseV));
    }

    public static Vector3i latticeComponents(
            Vector3i canonicalDirection, SourceAxis sourceAxis) {
        Vector3d result = new Vector3d(sourceAxis.longitudinal())
                .mul(canonicalDirection.x)
                .fma(canonicalDirection.y, sourceAxis.transverseU())
                .fma(canonicalDirection.z, sourceAxis.transverseV());
        return new Vector3i(
                (int) result.x, (int) result.y, (int) result.z);
    }

    public static double sourcePolarDegrees(
            Vector3d radialUnit, SourceAxis sourceAxis) {
        return Math.toDegrees(Math.acos(Math.clamp(
                radialUnit.dot(sourceAxis.longitudinal()), -1.0, 1.0)));
    }

    public static double sourceAzimuthDegrees(
            Vector3d radialUnit, SourceAxis sourceAxis) {
        double degrees = Math.toDegrees(Math.atan2(
                radialUnit.dot(sourceAxis.transverseV()),
                radialUnit.dot(sourceAxis.transverseU())));
        return degrees >= 180.0 ? degrees - 360.0 : degrees;
    }

    public static double longitudinal(
            Vector3d value, SourceAxis sourceAxis) {
        return value.dot(sourceAxis.longitudinal());
    }

    public static double transverseMagnitude(
            Vector3d value, SourceAxis sourceAxis) {
        double longitudinal = longitudinal(value, sourceAxis);
        return Math.sqrt(Math.max(
                0.0, value.lengthSquared() - longitudinal * longitudinal));
    }

    public static double coefficientOfVariation(double... values) {
        validateValues(values);
        double mean = 0.0;
        for (double value : values) mean += value;
        mean /= values.length;
        double variance = 0.0;
        for (double value : values) {
            double difference = value - mean;
            variance += difference * difference;
        }
        variance /= values.length;
        return Math.sqrt(variance) / Math.max(
                Math.abs(mean), Math.ulp(1.0));
    }

    public static double maximumRelativeSpread(double... values) {
        validateValues(values);
        double mean = 0.0;
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            mean += value;
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        mean /= values.length;
        return (maximum - minimum) / Math.max(
                Math.abs(mean), Math.ulp(1.0));
    }

    private static void validateValues(double[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("At least one value is required");
        }
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Rotational comparison requires finite values");
            }
        }
    }

    private static int dot(Vector3i first, Vector3i second) {
        return first.x * second.x + first.y * second.y
                + first.z * second.z;
    }
}
