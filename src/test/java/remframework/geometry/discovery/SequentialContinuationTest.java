package remframework.geometry.discovery;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.experiment.*;
import remframework.geometry.discovery.realization.session.*;

class SequentialContinuationTest {
    @Test void builtInScheduleIsOrderedAndConditional(){
        ThreeDExperimentConditions c=new ThreeDExperimentConditions(FacePlanarityMode.REQUIRE_PLANAR_FACES,FaceAngleConditionMode.REQUIRE_EQUAL_ANGLES_WITHIN_EACH_FACE,DihedralConditionMode.MEASURE_ONLY,RegularityMode.MEASURE_ONLY,RegularityMode.MEASURE_ONLY,RegularityMode.MEASURE_ONLY,FacePlaneRadiusMode.REQUIRE_EQUAL_FACE_PLANE_RADII,RegularityMode.MEASURE_ONLY,GeometryCenterDefinition.VERTEX_CENTROID,1e-4,1e-3,1e-3);
        ContinuationSchedule s=ContinuationSchedule.closedUniformFaceSequential(c,3,100,10);
        assertEquals(List.of("EDGE_EQUALITY","OUTWARD_ORIENTATION","FACE_PLANARITY","FACE_ANGLE_EQUALITY","FACE_PLANE_RADII","JOINT_QUALIFICATION"),s.stages().stream().map(ContinuationStageDefinition::id).toList());
        assertTrue(s.stages().get(3).retained().containsAll(Set.of(ContinuationConstraint.EDGE_EQUALITY,ContinuationConstraint.OUTWARD_ORIENTATION,ContinuationConstraint.FACE_PLANARITY)));
    }
    @Test void firstStageAloneIsActiveAndRampIsDeterministic(){
        Configuration tetra=new ConfigurationGenerator().generate(ConfigurationRequest.exhaustive(4,3,GeometryEmbeddingDimension.THREE_D)).configurations().getFirst();
        ThreeDExperimentConditions c=new ThreeDExperimentConditions(FacePlanarityMode.REQUIRE_PLANAR_FACES,FaceAngleConditionMode.REQUIRE_EQUAL_ANGLES_WITHIN_EACH_FACE,DihedralConditionMode.MEASURE_ONLY,RegularityMode.MEASURE_ONLY,RegularityMode.MEASURE_ONLY,RegularityMode.MEASURE_ONLY,FacePlaneRadiusMode.MEASURE_ONLY,RegularityMode.MEASURE_ONLY,GeometryCenterDefinition.VERTEX_CENTROID,1e-4,1e-3,1e-3);
        TestRequest request=new TestRequest(tetra.id(),GeometryEmbeddingDimension.THREE_D,1,42,1,null,GeometrySymmetryPolicy.MEASURE_ONLY,1000).withContinuation(ContinuationSettings.sequential(c,3,100,10));
        IncrementalSession session=new IncrementalSession(tetra,request);session.step();ContinuationSnapshot snapshot=session.continuationSnapshot();
        assertEquals(RealizationStrategy.SEQUENTIAL_CONTINUATION,snapshot.strategy());assertEquals(List.of(ContinuationConstraint.EDGE_EQUALITY),snapshot.active());assertEquals(.1,snapshot.rampFactor(),1e-12);
    }
    @Test void coherentOrientationAndConvexityAreRigidMotionInvariant(){
        List<List<Integer>> faces=List.of(List.of(0,2,1),List.of(0,1,3),List.of(1,2,3),List.of(2,0,3));
        List<Vector3d> p=List.of(new Vector3d(1,1,1),new Vector3d(1,-1,-1),new Vector3d(-1,1,-1),new Vector3d(-1,-1,1));
        OrientationConvexityCalculator calculator=new OrientationConvexityCalculator();OrientationConvexityDiagnostics a=calculator.calculate(faces,p,1e-9);
        List<List<Integer>> reversed=faces.stream().map(face->{List<Integer>x=new ArrayList<>(face);Collections.reverse(x);return List.copyOf(x);}).toList();OrientationConvexityDiagnostics b=calculator.calculate(reversed,p,1e-9);
        assertTrue(a.orientation()==OrientationState.COHERENT_AND_OUTWARD||a.orientation()==OrientationState.COHERENT_BUT_INWARD);assertTrue(b.orientation()==OrientationState.COHERENT_AND_OUTWARD||b.orientation()==OrientationState.COHERENT_BUT_INWARD);assertEquals(0,a.outwardError());assertTrue(a.convexWithinTolerance());
        double angle=.7,cos=Math.cos(angle),sin=Math.sin(angle);List<Vector3d> moved=p.stream().map(v->new Vector3d(cos*v.x-sin*v.y,sin*v.x+cos*v.y,v.z).add(3,-2,4)).toList();OrientationConvexityDiagnostics c=calculator.calculate(faces,moved,1e-9);assertEquals(a.maximumConvexityViolation(),c.maximumConvexityViolation(),1e-9);assertEquals(a.outwardError(),c.outwardError(),1e-9);
    }
    @Test void cubeSequentialContinuationIsDeterministicAndPreservesTopology(){
        Configuration cube=new ConfigurationGenerator().generate(ConfigurationRequest.exhaustive(8,3,GeometryEmbeddingDimension.THREE_D)).configurations().stream().filter(x->x.id().toString().equals("cfg-c83f892b5931")).findFirst().orElseThrow();
        TestRequest base=new TestRequest(cube.id(),GeometryEmbeddingDimension.THREE_D,1,42,1,null,GeometrySymmetryPolicy.MEASURE_ONLY,100_000).withBenchmark(BenchmarkPreset.CUBE_CONSTRAINED_BENCHMARK);
        IncrementalSession session=new IncrementalSession(cube,base.withContinuation(ContinuationSettings.sequential(base.threeDConditions(),base.relaxationSettings().requiredStableIterations(),20_000,100)));session.run();
        assertEquals("cfg-c83f892b5931",cube.id().toString());assertEquals(RealizationStrategy.SEQUENTIAL_CONTINUATION,session.continuationSnapshot().strategy());assertFalse(session.continuationSnapshot().history().isEmpty());assertTrue(session.continuationSnapshot().history().stream().noneMatch(x->x.state()==ContinuationStageState.FAILED));assertNotNull(session.result());assertTrue(session.result().selectedBenchmarkPassed(),session.continuationSnapshot()+" result="+session.result().status());
    }
    @Test void boundedN20RunReportsAnHonestStageOutcome(){
        ConfigurationRequest generation=ConfigurationRequest.byFaceCount(12,3,GeometryEmbeddingDimension.THREE_D,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME).withFaceComposition(FaceComposition.uniform(5));Configuration n20=new ConfigurationGenerator().generate(generation).configurations().getFirst();ThreeDExperimentConditions conditions=new ThreeDExperimentConditions(FacePlanarityMode.REQUIRE_PLANAR_FACES,FaceAngleConditionMode.REQUIRE_EQUAL_ANGLES_WITHIN_EACH_FACE,DihedralConditionMode.MEASURE_ONLY,RegularityMode.MEASURE_ONLY,RegularityMode.MEASURE_ONLY,RegularityMode.MEASURE_ONLY,FacePlaneRadiusMode.REQUIRE_EQUAL_FACE_PLANE_RADII,RegularityMode.MEASURE_ONLY,GeometryCenterDefinition.VERTEX_CENTROID,1e-4,1e-3,1e-3);TestRequest request=new TestRequest(n20.id(),GeometryEmbeddingDimension.THREE_D,1,42,1,null,GeometrySymmetryPolicy.MEASURE_ONLY,10_000).withFaceComposition(FaceComposition.uniform(5)).withParameterDerivation(generation).withContinuation(ContinuationSettings.sequential(conditions,5,500,20));IncrementalSession session=new IncrementalSession(n20,request);session.run();ContinuationSnapshot snapshot=session.continuationSnapshot();assertEquals("cfg-b10320c7778b",n20.id().toString());assertFalse(snapshot.history().isEmpty());assertTrue(snapshot.history().stream().allMatch(x->x.state()==ContinuationStageState.SATISFIED||x.state()==ContinuationStageState.PLATEAUED||x.state()==ContinuationStageState.FAILED));
    }
}
