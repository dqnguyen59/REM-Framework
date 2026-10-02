package remframework.geometry.discovery.measurement.regularity;

import java.util.List;

public record FaceMetrics(String faceId, int edgeCount, double area, double perimeter,
        Statistics interiorAngles, Statistics turningAngles, Statistics edgeLengths,
        double targetInteriorAngle,double maximumInteriorTargetDeviation,
        List<Double> centroidCoordinates, List<Integer> canonicalVertexCycle,
        double planarityError, FaceOrientationDisposition orientationState,
        boolean accepted, String acceptanceReason) {
    public FaceMetrics { centroidCoordinates = List.copyOf(centroidCoordinates);
        canonicalVertexCycle=List.copyOf(canonicalVertexCycle); }
    public FaceMetrics(String faceId,int edgeCount,double area,double perimeter,
            Statistics interiorAngles,Statistics turningAngles,Statistics edgeLengths,
            double targetInteriorAngle,double maximumInteriorTargetDeviation,
            List<Double> centroidCoordinates) {
        this(faceId,edgeCount,area,perimeter,interiorAngles,turningAngles,edgeLengths,
                targetInteriorAngle,maximumInteriorTargetDeviation,centroidCoordinates,List.of(),
                Double.NaN,FaceOrientationDisposition.INDETERMINATE,true,"Accepted face metrics.");
    }

    public List<Double> measuredInteriorAngles() { return interiorAngles.values(); }
}
