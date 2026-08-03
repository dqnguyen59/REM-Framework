package remframework.geometry.discovery;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.experiment.*;
import remframework.geometry.discovery.persistence.TestResultStore;
import remframework.geometry.discovery.realization.session.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SpectralStage2RealizationTest {
    @TempDir Path temporary;

    @Test void frozenSpectralPolicyQualifiesEveryTopologyAndHeldOutSeed()throws Exception{
        for(int[] parameters:List.of(new int[]{4,3},new int[]{6,4},new int[]{12,5})){
            Problem problem=problem(parameters[0],parameters[1]);
            String expected=switch(parameters[0]){case 4->"cfg-8d94d51e4538";case 6->"cfg-c83f892b5931";default->"cfg-b10320c7778b";};
            assertEquals(expected,problem.configuration.id().toString());
            for(long seed:List.of(42L,142L,143L,144L)){
                IncrementalSession session=new IncrementalSession(problem.configuration,request(problem,seed));session.run();
                assertNotNull(session.result());assertEquals(GeometryConfigurationStatusCode.VALID_CANDIDATE,session.result().status().code());
                assertEquals(TopologyInitialization.SPECTRAL_EMBEDDING,session.result().continuation().initialization());
                assertTrue(session.constraintPresentation().qualified());assertEquals(0,session.orientationConvexityDiagnostics().violatingPairs());
                assertFalse(session.result().boundary().selfIntersecting());assertTrue(session.result().volume().absoluteVolume()>0);
            }
        }
    }

    @Test void spectralEmbeddingIsDeterministicAndTopologyRelabelingInvariant(){
        Problem problem=problem(12,5);SpectralTopologyInitializer initializer=new SpectralTopologyInitializer();
        List<Vector3d> first=initializer.initialize(problem.configuration,1),repeat=initializer.initialize(problem.configuration,1);
        assertEquals(first,repeat);int n=problem.configuration.nodeCount();int[] permutation=new int[n];for(int i=0;i<n;i++)permutation[i]=(i*7+3)%n;
        Configuration relabeled=relabel(problem.configuration,permutation);List<Vector3d> changed=initializer.initialize(relabeled,1);
        for(int a=0;a<n;a++)for(int b=0;b<n;b++)assertEquals(first.get(a).distance(first.get(b)),changed.get(permutation[a]).distance(changed.get(permutation[b])),1e-8);
    }

    @Test void faceOrderingAndRigidTransformsDoNotChangeScientificDistances(){
        Problem problem=problem(6,4);List<Vector3d> points=new SpectralTopologyInitializer().initialize(problem.configuration,1);
        List<Double> before=pairDistances(points);List<Vector3d> transformed=points.stream().map(p->new Quaterniond().rotateXYZ(.4,-.7,1.1).transform(new Vector3d(p)).add(3,-2,5)).toList();
        List<Double> after=pairDistances(transformed);for(int i=0;i<before.size();i++)assertEquals(before.get(i),after.get(i),1e-12);
        List<List<Integer>> faces=new ArrayList<>(new remframework.geometry.discovery.eligibility.TopologyEligibilityAnalyzer().deriveTopologicalFaces(problem.configuration));Collections.reverse(faces);
        Configuration reordered=new Configuration(problem.configuration.id(),problem.configuration.nodeCount(),problem.configuration.exactDegree(),problem.configuration.edges(),null,problem.configuration.canonicalGraphLabel(),problem.configuration.isomorphismClassId(),problem.configuration.automorphismGroupSize(),problem.configuration.graphSymmetry(),true,1,problem.configuration.generationMethod(),true,problem.configuration.status(),problem.configuration.diagnostics(),problem.configuration.creationTimestamp(),Map.of("combinatorialFaces",new CombinatorialFaceComplex(problem.configuration.nodeCount(),faces).encode()));
        assertEquals(points,new SpectralTopologyInitializer().initialize(reordered,1));
    }

    @Test void selectedInitializationPersistsWithQualifiedResult()throws Exception{
        Problem problem=problem(4,3);IncrementalSession session=new IncrementalSession(problem.configuration,request(problem,42));session.run();Path file=new TestResultStore().save(temporary,session.result());String text=Files.readString(file);assertTrue(text.contains("realization_initialization = \"SPECTRAL_EMBEDDING\""));assertEquals(GeometryConfigurationStatusCode.VALID_CANDIDATE,new TestResultStore().load(file).status());
    }

    @Test void animatedDisplayIsDeterministicNonScientificAndEndsAtInstantCoordinates(){
        for(int[] parameters:List.of(new int[]{4,3},new int[]{6,4},new int[]{12,5})){
            Problem problem=problem(parameters[0],parameters[1]);IncrementalSession instant=new IncrementalSession(problem.configuration,request(problem,42));
            SpectralInitializationSettings animation=new SpectralInitializationSettings(SpectralPresentationMode.ANIMATED_DISPLAY,.1,10,20,10,.08);
            IncrementalSession animated=new IncrementalSession(problem.configuration,request(problem,42).withContinuation(ContinuationSettings.simultaneous().withInitialization(TopologyInitialization.SPECTRAL_EMBEDDING).withSpectralInitialization(animation)));
            assertEquals(InitializationPhase.DISPLAY_ANIMATION,animated.initializationSnapshot().phase());assertEquals(0,animated.iteration());assertNotEquals(instant.currentCoordinates(),animated.currentCoordinates());
            animated.resume();animated.step();assertEquals(0,animated.iteration());assertEquals(InitializationPhase.DYNAMIC_ELASTIC_REALIZATION,animated.initializationSnapshot().phase());assertEquals(instant.currentCoordinates(),animated.currentCoordinates());
        }
    }

    @Test void guidedRelaxationStartsRandomPublishesRevisionsAndHandsOverAtZeroGuidance(){
        for(int[] parameters:List.of(new int[]{4,3},new int[]{6,4},new int[]{12,5})){
            Problem problem=problem(parameters[0],parameters[1]);SpectralInitializationSettings guided=new SpectralInitializationSettings(SpectralPresentationMode.GUIDED_RELAXATION,1,60,20,10,.5);
            IncrementalSession session=new IncrementalSession(problem.configuration,request(problem,42).withContinuation(ContinuationSettings.simultaneous().withInitialization(TopologyInitialization.SPECTRAL_EMBEDDING).withSpectralInitialization(guided)));
            assertEquals(InitializationPhase.RANDOM_START,session.initializationSnapshot().phase());assertNotEquals(session.randomStartCoordinates(),session.spectralTargetCoordinates());int revision=session.initializationSnapshot().revision();session.resume();for(int i=0;i<31;i++)session.step();
            assertTrue(session.initializationSnapshot().revision()>revision+10);assertEquals(InitializationPhase.DYNAMIC_ELASTIC_REALIZATION,session.initializationSnapshot().phase());assertEquals(0,session.initializationSnapshot().spectralGuidanceFactor());assertEquals(1,session.initializationSnapshot().geometricConstraintFactor());assertEquals(0,session.iteration());assertTrue(session.scientificStartCoordinates().stream().mapToDouble(p->p.distance(session.spectralTargetCoordinates().get(session.scientificStartCoordinates().indexOf(p)))).max().orElseThrow()<1e-6);for(int i=0;i<2_100&&session.result()==null;i++)session.step();assertNotNull(session.result(),"Guided realization did not terminate: "+session.maximumEdgeError()+" / "+session.threeDConstraintDiagnostics());assertEquals(GeometryConfigurationStatusCode.VALID_CANDIDATE,session.result().status().code(),parameters[0]+" faces: "+session.result().status().explanation());
        }
    }

    @Test void changingSeedChangesOnlyTheVisibleRandomStart(){
        Problem problem=problem(12,5);SpectralInitializationSettings animation=new SpectralInitializationSettings(SpectralPresentationMode.ANIMATED_DISPLAY,1,10,10,10,.08);ContinuationSettings continuation=ContinuationSettings.simultaneous().withInitialization(TopologyInitialization.SPECTRAL_EMBEDDING).withSpectralInitialization(animation);
        IncrementalSession first=new IncrementalSession(problem.configuration,request(problem,42).withContinuation(continuation)),second=new IncrementalSession(problem.configuration,request(problem,43).withContinuation(continuation));assertNotEquals(first.randomStartCoordinates(),second.randomStartCoordinates());assertEquals(first.spectralTargetCoordinates(),second.spectralTargetCoordinates());assertEquals(first.configuration().id(),second.configuration().id());
    }

    private static Problem problem(int faces,int size){ConfigurationRequest request=ConfigurationRequest.byFaceCount(faces,3,GeometryEmbeddingDimension.THREE_D,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME).withFaceComposition(FaceComposition.uniform(size)).withGenerationStrategy(GenerationStrategy.UNIFORM_SPHERICAL_FACE_GENERATION);return new Problem(request,new ConfigurationGenerator().generate(request).configurations().getFirst(),size);}
    private static TestRequest request(Problem p,long seed){ThreeDExperimentConditions conditions=new ThreeDExperimentConditions(FacePlanarityMode.REQUIRE_PLANAR_FACES,FaceAngleConditionMode.REQUIRE_EQUAL_ANGLES_WITHIN_EACH_FACE,DihedralConditionMode.MEASURE_ONLY,RegularityMode.MEASURE_ONLY,RegularityMode.MEASURE_ONLY,RegularityMode.MEASURE_ONLY,FacePlaneRadiusMode.REQUIRE_EQUAL_FACE_PLANE_RADII,RegularityMode.MEASURE_ONLY,GeometryCenterDefinition.VERTEX_CENTROID,1e-4,1e-3,1e-3);return new TestRequest(p.configuration.id(),GeometryEmbeddingDimension.THREE_D,1,seed,1,null,GeometrySymmetryPolicy.MEASURE_ONLY,2_000,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME,null,null,null,1e-3,1e-7,1e-9,1e-6,null,0,0,conditions,BenchmarkPreset.NONE,ThreeDConstraintStrengths.defaults()).withFaceComposition(FaceComposition.uniform(p.faceSize)).withParameterDerivation(p.request).withContinuation(ContinuationSettings.simultaneous().withInitialization(TopologyInitialization.SPECTRAL_EMBEDDING));}
    private static Configuration relabel(Configuration c,int[] p){List<DiscoveryConnection> edges=c.edges().stream().map(e->new DiscoveryConnection(p[e.firstNodeId()],p[e.secondNodeId()])).toList();return new Configuration(c.id(),c.nodeCount(),c.exactDegree(),edges,null,c.canonicalGraphLabel(),c.isomorphismClassId(),c.automorphismGroupSize(),c.graphSymmetry(),true,c.generationIndex(),c.generationMethod(),c.enumerationComplete(),c.status(),c.diagnostics(),c.creationTimestamp(),Map.of());}
    private static List<Double> pairDistances(List<Vector3d> p){List<Double> result=new ArrayList<>();for(int i=0;i<p.size();i++)for(int j=i+1;j<p.size();j++)result.add(p.get(i).distance(p.get(j)));return result;}
    private record Problem(ConfigurationRequest request,Configuration configuration,int faceSize){ }
}
