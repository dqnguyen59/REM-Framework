package remframework.geometry.discovery.measurement.regularity;

import org.joml.Vector3d;

import java.util.List;

public record TwoDRegularityMetrics(boolean valid, boolean planar, boolean simpleBoundary,
        boolean orientationConsistent, boolean positiveArea, boolean equalEdges,
        boolean equalAngles, List<Double> planeDistances, Vector3d planeNormal,
        Statistics edgeLengths, Statistics interiorAngles, Statistics turningAngles,
        Statistics centroidRadii, double targetInteriorAngle,
        double targetTurningAngle, double maximumAngleTargetDeviation,
        List<Double> interiorAngleTargetDeviations, List<Double> turningAngleTargetDeviations,
        Vector3d planePoint, double maximumPlaneDeviation, double meanPlaneDeviation,
        double rmsPlaneDeviation, double planarityTolerance,
        double area, double perimeter, double circularity, List<String> failures) {
    public TwoDRegularityMetrics {
        planeDistances = List.copyOf(planeDistances); planeNormal = new Vector3d(planeNormal);
        failures = List.copyOf(failures);
        interiorAngleTargetDeviations=List.copyOf(interiorAngleTargetDeviations);
        turningAngleTargetDeviations=List.copyOf(turningAngleTargetDeviations);
        planePoint=new Vector3d(planePoint);
    }
}
