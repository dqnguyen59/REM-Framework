package remframework.geometry.discovery.measurement.regularity;

import java.util.List;
import java.util.Map;

/** Dihedral values use the interior convention in radians, in [0, pi]. */
public record ThreeDRegularityMetrics(Statistics edgeLengths, double maximumTargetLengthError,
        Statistics interiorDihedralAngles, Statistics vertexRadii,
        Statistics faceCenterRadii, Statistics edgeMidpointRadii,
        List<FaceMetrics> faces, Map<Integer, Statistics> faceAreaByEdgeCount,
        Map<Integer, Statistics> faceAngleVarianceByEdgeCount) {
    public ThreeDRegularityMetrics {
        faces = List.copyOf(faces); faceAreaByEdgeCount = Map.copyOf(faceAreaByEdgeCount);
        faceAngleVarianceByEdgeCount = Map.copyOf(faceAngleVarianceByEdgeCount);
    }
}
