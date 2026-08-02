package remframework.geometry.discovery.measurement.regularity;

import java.util.List;
import java.util.Map;
import org.joml.Vector3d;
import remframework.geometry.discovery.experiment.GeometryCenterDefinition;
import java.util.TreeMap;

/** Dihedral values use the interior convention in radians, in [0, pi]. */
public record ThreeDRegularityMetrics(Statistics edgeLengths, double maximumTargetLengthError,
        Statistics interiorDihedralAngles, Statistics vertexRadii,
        Statistics faceCenterRadii, Statistics edgeMidpointRadii,
        List<FaceMetrics> faces, Map<Integer, Statistics> faceAreaByEdgeCount,
        Map<Integer, Statistics> faceAngleVarianceByEdgeCount,
        boolean applicable,String applicabilityReason,GeometryCenterDefinition centerDefinition,
        Vector3d geometryCenter,List<FacePlaneDiagnostic> facePlanes,
        Statistics facePlaneRadii,double maximumFacePlaneRadiusDeviationFromMean,
        Double relativeMaximumFacePlaneRadiusDeviation,Statistics normalCenterAlignments,
        double maximumNormalAlignmentDeviationFromOne,List<DihedralEdgeAngle> dihedralEdgeAngles) {
    public ThreeDRegularityMetrics {
        faces = List.copyOf(faces); faceAreaByEdgeCount = Map.copyOf(faceAreaByEdgeCount);
        faceAngleVarianceByEdgeCount = Map.copyOf(faceAngleVarianceByEdgeCount);
        geometryCenter=new Vector3d(geometryCenter);facePlanes=List.copyOf(facePlanes);
        dihedralEdgeAngles=List.copyOf(dihedralEdgeAngles);
    }
    public int faceCountTotal(){return faces.size();}
    public Map<Integer,Long> faceEdgeCountDistribution(){Map<Integer,Long> result=new TreeMap<>();faces.forEach(face->result.merge(face.edgeCount(),1L,Long::sum));return Map.copyOf(result);}
    public double planarityMaximum(){return facePlanes.stream().mapToDouble(FacePlaneDiagnostic::maximumDeviation).max().orElse(Double.NaN);}
    public double planarityVariance(){return Statistics.of(facePlanes.stream().map(FacePlaneDiagnostic::maximumDeviation).toList()).variance();}
    public double faceAngleMaximumDeviation(){return faces.stream().mapToDouble(FaceMetrics::maximumInteriorTargetDeviation).max().orElse(Double.NaN);}
    public double faceAngleDeviationVariance(){return Statistics.of(faces.stream().map(FaceMetrics::maximumInteriorTargetDeviation).toList()).variance();}
    public double faceAreaVariance(){return Statistics.of(faces.stream().map(FaceMetrics::area).toList()).variance();}
}
