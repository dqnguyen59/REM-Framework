package remframework.geometry.discovery.measurement.regularity;

import java.util.List;

public record FaceMetrics(String faceId, int edgeCount, double area, double perimeter,
        Statistics interiorAngles, Statistics turningAngles, Statistics edgeLengths,
        double targetInteriorAngle,double maximumInteriorTargetDeviation,
        List<Double> centroidCoordinates) {
    public FaceMetrics { centroidCoordinates = List.copyOf(centroidCoordinates); }
}
