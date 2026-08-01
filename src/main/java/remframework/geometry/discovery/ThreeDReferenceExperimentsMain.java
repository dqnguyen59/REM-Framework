package remframework.geometry.discovery;

import org.joml.Vector3d;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.experiment.GeometryCenterDefinition;
import remframework.geometry.discovery.measurement.regularity.*;

import java.time.Instant;
import java.util.*;

/** Deterministic reference diagnostics for validating 3D regularity measurements. */
public final class ThreeDReferenceExperimentsMain {
    private ThreeDReferenceExperimentsMain() { }
    public static void main(String[] args){run("Reference cube",new Vector3d(1,0,0),new Vector3d(0,1,0),new Vector3d(0,0,1));run("Distorted equal-edge parallelepiped",new Vector3d(1,0,0),new Vector3d(.3,Math.sqrt(.91),0),new Vector3d(.2,.1,Math.sqrt(.95)));}
    private static void run(String name,Vector3d a,Vector3d b,Vector3d c){List<Vector3d> points=new ArrayList<>();for(int z=0;z<2;z++)for(int y=0;y<2;y++)for(int x=0;x<2;x++)points.add(new Vector3d(a).mul(x).add(new Vector3d(b).mul(y)).add(new Vector3d(c).mul(z)));Vector3d center=new Vector3d(a).add(b).add(c).mul(.5);points.forEach(point->point.sub(center));List<DiscoveryConnection> edges=List.of(e(0,1),e(1,3),e(3,2),e(2,0),e(4,5),e(5,7),e(7,6),e(6,4),e(0,4),e(1,5),e(2,6),e(3,7));List<DerivedFace> faces=List.of(f("bottom",0,2,3,1),f("top",4,5,7,6),f("front",0,1,5,4),f("right",1,3,7,5),f("back",3,2,6,7),f("left",2,0,4,6));var diagnostic=GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.COMPLETE,GeometryConfigurationStatusCode.CLOSED_BOUNDARY,"closed","reference");Configuration graph=new Configuration(new ConfigurationId("reference-"+name.replace(' ','-')),8,3,edges,null,name,name,1,GraphSymmetryClassification.UNKNOWN,true,0,"REFERENCE",true,GeometryConfigurationStatus.of(diagnostic),List.of(diagnostic),Instant.EPOCH,Map.of());ClosedBoundaryResult boundary=new ClosedBoundaryResult(true,true,true,true,false,2,faces,GeometryConfigurationStatus.of(diagnostic),List.of(diagnostic));ThreeDRegularityMetrics metrics=new ThreeDRegularityCalculator().calculate(graph,points,boundary,1,GeometryCenterDefinition.VERTEX_CENTROID,1e-7);double volume=Math.abs(a.dot(new Vector3d(b).cross(c)));System.out.printf("%s planarFaces=%d/6 edgeVariance=%.9g facePlaneRadii=%s facePlaneRadiusVariance=%.9g normalAlignments=%s normalAlignmentVariance=%.9g vertexRadiusVariance=%.9g dihedralVariance=%.9g volume=%.9g regular=%s%n",name,metrics.facePlanes().stream().filter(FacePlaneDiagnostic::planar).count(),metrics.edgeLengths().variance(),metrics.facePlaneRadii().values(),metrics.facePlaneRadii().variance(),metrics.normalCenterAlignments().values(),metrics.normalCenterAlignments().variance(),metrics.vertexRadii().variance(),metrics.interiorDihedralAngles().variance(),volume,metrics.facePlaneRadii().variance()<1e-12&&metrics.normalCenterAlignments().variance()<1e-12&&metrics.interiorDihedralAngles().variance()<1e-12);}
    private static DiscoveryConnection e(int a,int b){return new DiscoveryConnection(a,b);}private static DerivedFace f(String id,Integer... nodes){return new DerivedFace(id,List.of(nodes),true,true,0);}
}
