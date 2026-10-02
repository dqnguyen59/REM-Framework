package remframework.measurement.gravity;

/**
 * One Gravity Test 1 measurement captured during a simulation update.
 */
public record GravityMeasurementSnapshot(
        long updateNumber,
        int measurementIndex,
        String measurementName,
        int sourceNodeIndexX,
        int sourceNodeIndexY,
        int sourceNodeIndexZ,
        int measurementNodeIndexX,
        int measurementNodeIndexY,
        int measurementNodeIndexZ,
        double sourcePositionX,
        double sourcePositionY,
        double sourcePositionZ,
        double measurementPositionX,
        double measurementPositionY,
        double measurementPositionZ,
        double dx,
        double dy,
        double dz,
        double sourceDistance,
        double rawGradientX,
        double rawGradientY,
        double rawGradientZ,
        double radialGradient,
        double tangentialGradientMagnitude,
        double angularErrorDegrees,
        double planarSourceDistance,
        double planarRadialGradient,
        double planarTangentialGradientMagnitude,
        double planarAngularErrorDegrees) {
}
