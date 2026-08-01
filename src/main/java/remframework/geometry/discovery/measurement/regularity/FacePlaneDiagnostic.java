package remframework.geometry.discovery.measurement.regularity;

import org.joml.Vector3d;

import java.util.List;

public record FacePlaneDiagnostic(String faceId, Vector3d referencePoint, Vector3d outwardNormal,
        List<Double> vertexPlaneDeviations, double maximumDeviation, double meanDeviation,
        double rmsDeviation, double tolerance, boolean planar, boolean degenerate,
        FaceOrientationDisposition orientation, double facePlaneRadius,
        double normalCenterAlignment) {
    public FacePlaneDiagnostic { referencePoint=new Vector3d(referencePoint);outwardNormal=new Vector3d(outwardNormal);vertexPlaneDeviations=List.copyOf(vertexPlaneDeviations); }
}
