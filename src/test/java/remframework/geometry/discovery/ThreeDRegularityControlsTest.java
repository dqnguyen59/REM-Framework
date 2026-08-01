package remframework.geometry.discovery;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.experiment.*;
import remframework.geometry.discovery.measurement.regularity.*;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import remframework.gui.view.frames.GeometryDiscoveryControlFrame;

class ThreeDRegularityControlsTest {
    @Test void tetrahedronUsesDifferentPlanarFacePlanesAndEqualRadii(){var fixture=tetrahedron();ThreeDRegularityMetrics m=measure(fixture);assertTrue(m.facePlanes().stream().allMatch(FacePlaneDiagnostic::planar));assertTrue(m.facePlanes().stream().map(FacePlaneDiagnostic::outwardNormal).distinct().count()>1);assertEquals(0,m.facePlaneRadii().variance(),1e-14);assertEquals(0,m.normalCenterAlignments().variance(),1e-14);}

    @Test void cubeHasSixPlanarFacesEqualPlaneRadiiAndSeparateAlignment(){var fixture=cube(1,1,1);ThreeDRegularityMetrics m=measure(fixture);assertEquals(6,m.facePlanes().size());assertTrue(m.facePlanes().stream().allMatch(FacePlaneDiagnostic::planar));assertEquals(0,m.facePlaneRadii().variance(),1e-14);assertEquals(0,m.maximumNormalAlignmentDeviationFromOne(),1e-14);assertEquals(0,m.vertexRadii().variance(),1e-14);assertEquals(0,m.interiorDihedralAngles().variance(),1e-14);}

    @Test void displacedQuadrilateralVertexFailsFacePlanarity(){var fixture=cube(1,1,1);List<Vector3d> points=fixture.points.stream().map(Vector3d::new).toList();points.get(6).z+=.2;ThreeDRegularityMetrics m=new ThreeDRegularityCalculator().calculate(fixture.graph,points,fixture.boundary,1,GeometryCenterDefinition.VERTEX_CENTROID,1e-6);assertTrue(m.facePlanes().stream().anyMatch(face->!face.planar()));}

    @Test void cuboidHasUnequalFacePlaneRadiiAndIsNotCalledAnInsphere(){ThreeDRegularityMetrics m=measure(cube(1,2,3));assertTrue(m.facePlaneRadii().variance()>0);ThreeDExperimentConditions conditions=new ThreeDExperimentConditions(null,null,null,null,null,null,FacePlaneRadiusMode.REQUIRE_EQUAL_FACE_PLANE_RADII,null,null,1e-7,1e-7,1e-7);ThreeDConditionReport report=new ThreeDConditionEvaluator().evaluate(true,m,conditions);assertEquals(ConditionConclusion.FAIL,report.conclusions().get("facePlaneRadii"));assertFalse(report.summary().toLowerCase().contains("insphere"));}

    @Test void radiiAreRigidTransformInvariantAndScaleLinearly(){var fixture=cube(1,2,3);ThreeDRegularityMetrics baseline=measure(fixture);Quaterniond rotation=new Quaterniond().fromAxisAngleRad(new Vector3d(1,2,3).normalize(),.73);List<Vector3d> moved=fixture.points.stream().map(point->new Vector3d(point).rotate(rotation).add(9,-4,2)).toList();ThreeDRegularityMetrics transformed=new ThreeDRegularityCalculator().calculate(fixture.graph,moved,fixture.boundary,1,GeometryCenterDefinition.VERTEX_CENTROID,1e-7);for(int i=0;i<baseline.facePlaneRadii().values().size();i++)assertEquals(baseline.facePlaneRadii().values().get(i),transformed.facePlaneRadii().values().get(i),1e-12);List<Vector3d> scaled=fixture.points.stream().map(point->new Vector3d(point).mul(5)).toList();ThreeDRegularityMetrics scale=new ThreeDRegularityCalculator().calculate(fixture.graph,scaled,fixture.boundary,5,GeometryCenterDefinition.VERTEX_CENTROID,1e-7);for(int i=0;i<baseline.facePlaneRadii().values().size();i++)assertEquals(5*baseline.facePlaneRadii().values().get(i),scale.facePlaneRadii().values().get(i),1e-12);}

    @Test void invalidFaceSetIsNotApplicable(){var fixture=tetrahedron();ClosedBoundaryResult invalid=new ClosedBoundaryResult(false,false,false,false,false,0,List.of(),fixture.boundary.status(),List.of());ThreeDRegularityMetrics m=new ThreeDRegularityCalculator().calculate(fixture.graph,fixture.points,invalid,1);assertFalse(m.applicable());assertTrue(m.facePlaneRadii().values().isEmpty());}

    @Test void measureOnlyDoesNotFailConstrainedExperiment(){ThreeDRegularityMetrics m=measure(cube(1,2,3));ThreeDConditionReport report=new ThreeDConditionEvaluator().evaluate(true,m,ThreeDExperimentConditions.defaults());assertTrue(report.basicValidCandidate());assertTrue(report.constrainedExperimentPassed());assertEquals(ConditionConclusion.MEASURE_ONLY,report.conclusions().get("facePlaneRadii"));}

    @Test void profileControlsAreDimensionSpecific(){var twoD=GeometryDiscoveryControlFrame.profileConditionControls(ExperimentProfile.CLOSED_2D_AREA);var threeD=GeometryDiscoveryControlFrame.profileConditionControls(ExperimentProfile.CLOSED_3D_VOLUME);assertTrue(twoD.contains("Global planarity mode"));assertFalse(twoD.contains("Face-plane-radius condition"));assertFalse(threeD.contains("Global planarity mode"));assertTrue(threeD.contains("Face-plane-radius condition"));assertTrue(threeD.contains("Center definition"));assertTrue(threeD.contains("Face-planarity tolerance"));assertEquals(FacePlaneRadiusMode.REQUIRE_EQUAL_FACE_PLANE_RADII,new ThreeDExperimentConditions(null,null,null,null,null,null,FacePlaneRadiusMode.REQUIRE_EQUAL_FACE_PLANE_RADII,null,null,0,0,0).facePlaneRadii());}

    private ThreeDRegularityMetrics measure(Fixture fixture){return new ThreeDRegularityCalculator().calculate(fixture.graph,fixture.points,fixture.boundary,1,GeometryCenterDefinition.VERTEX_CENTROID,1e-7);}
    private Fixture tetrahedron(){double h=Math.sqrt(2.0/3),y=Math.sqrt(3.0)/2;List<Vector3d> p=List.of(new Vector3d(0,0,0),new Vector3d(1,0,0),new Vector3d(.5,y,0),new Vector3d(.5,y/3,h));List<DiscoveryConnection> edges=List.of(e(0,1),e(0,2),e(0,3),e(1,2),e(1,3),e(2,3));return fixture(p,edges,List.of(face("a",0,2,1),face("b",0,1,3),face("c",1,2,3),face("d",2,0,3)),3);}
    private Fixture cube(double x,double y,double z){List<Vector3d> p=List.of(new Vector3d(-x,-y,-z),new Vector3d(x,-y,-z),new Vector3d(x,y,-z),new Vector3d(-x,y,-z),new Vector3d(-x,-y,z),new Vector3d(x,-y,z),new Vector3d(x,y,z),new Vector3d(-x,y,z));List<DiscoveryConnection> edges=List.of(e(0,1),e(1,2),e(2,3),e(3,0),e(4,5),e(5,6),e(6,7),e(7,4),e(0,4),e(1,5),e(2,6),e(3,7));return fixture(p,edges,List.of(face("bottom",0,3,2,1),face("top",4,5,6,7),face("front",0,1,5,4),face("right",1,2,6,5),face("back",2,3,7,6),face("left",3,0,4,7)),3);}
    private Fixture fixture(List<Vector3d> p,List<DiscoveryConnection> edges,List<DerivedFace> faces,int degree){GeometryConfigurationDiagnostic d=GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.COMPLETE,GeometryConfigurationStatusCode.CLOSED_BOUNDARY,"closed","test");Configuration g=new Configuration(new ConfigurationId("fixture-"+p.size()+"-"+faces.size()),p.size(),degree,edges,null,"fixture","fixture",1,GraphSymmetryClassification.UNKNOWN,true,0,"TEST",true,GeometryConfigurationStatus.of(d),List.of(d),Instant.EPOCH,Map.of());ClosedBoundaryResult b=new ClosedBoundaryResult(true,true,true,true,false,2,faces,GeometryConfigurationStatus.of(d),List.of(d));return new Fixture(g,p,b);}
    private static DerivedFace face(String id,Integer... nodes){return new DerivedFace(id,List.of(nodes),true,true,0);}
    private static DiscoveryConnection e(int a,int b){return new DiscoveryConnection(a,b);}
    private record Fixture(Configuration graph,List<Vector3d> points,ClosedBoundaryResult boundary){}
}
