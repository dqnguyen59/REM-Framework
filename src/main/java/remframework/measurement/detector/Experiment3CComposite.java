package remframework.measurement.detector;

/**
 * Analysis-only scalar operations used to validate Experiment 3C.
 */
public final class Experiment3CComposite {
    private Experiment3CComposite() {
    }

    public static double arithmeticMean(double... values) {
        validate(values);
        double sum = 0.0;
        for (double value : values) sum += value;
        return sum / values.length;
    }

    public static double pooledRms(double... values) {
        validate(values);
        double sumSquares = 0.0;
        for (double value : values) sumSquares += value * value;
        return Math.sqrt(sumSquares / values.length);
    }

    public static double coefficientOfVariation(double... values) {
        double mean = arithmeticMean(values);
        double variance = 0.0;
        for (double value : values) {
            double difference = value - mean;
            variance += difference * difference;
        }
        return Math.sqrt(variance / values.length)
                / Math.max(Math.abs(mean), Math.ulp(1.0));
    }

    public static double relativeSpread(double... values) {
        validate(values);
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        return (maximum - minimum) / Math.max(
                Math.abs(arithmeticMean(values)), Math.ulp(1.0));
    }

    public static double radiusNormalize(
            double value, double actualRadius, double exponent) {
        if (!Double.isFinite(value) || !Double.isFinite(actualRadius)
                || !Double.isFinite(exponent) || actualRadius <= 0.0) {
            throw new IllegalArgumentException(
                    "Radius normalization requires finite positive geometry");
        }
        return value * Math.pow(actualRadius, exponent);
    }

    private static void validate(double[] values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("At least one value is required");
        }
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Composite statistics require finite values");
            }
        }
    }
}
