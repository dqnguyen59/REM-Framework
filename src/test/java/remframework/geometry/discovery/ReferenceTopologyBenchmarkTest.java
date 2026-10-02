package remframework.geometry.discovery;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.eligibility.TopologyEligibilityAnalyzer;
import remframework.geometry.discovery.experiment.*;
import remframework.geometry.discovery.reference.*;
import remframework.geometry.discovery.viewport.RenderModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceTopologyBenchmarkTest {
    private final ReferenceTopologyRecognizer recognizer = new ReferenceTopologyRecognizer();

    @Test void exactlyOneOfFiveCubicDegreeConfigurationsMatchesCube() {
        GenerationResult generation = new ConfigurationGenerator().generate(
                ConfigurationRequest.exhaustive(8,3,GeometryEmbeddingDimension.THREE_D));
        assertEquals(5, generation.configurations().size());
        List<Configuration> cubes = generation.configurations().stream()
                .filter(c -> recognizer.recognize(c).topology()==ReferenceTopology.CUBE_GRAPH).toList();
        assertEquals(1, cubes.size());
        assertEquals("cfg-c83f892b5931", cubes.getFirst().id().toString());
    }

    @Test void relabelledCubeMatchesAndOtherCubicGraphsDoNot() {
        int[] permutation={3,6,1,5,0,7,2,4};
        List<DiscoveryConnection> relabelled=ReferenceTopologyRecognizer.cubeGraphEdges().stream()
                .map(e->new DiscoveryConnection(permutation[e.firstNodeId()],permutation[e.secondNodeId()])).toList();
        assertEquals(ReferenceTopology.CUBE_GRAPH,recognizer.recognize(8,relabelled).topology());
        GenerationResult generation=new ConfigurationGenerator().generate(ConfigurationRequest.exhaustive(8,3,GeometryEmbeddingDimension.THREE_D));
        assertTrue(generation.configurations().stream().filter(c->recognizer.recognize(c).topology()!=ReferenceTopology.CUBE_GRAPH)
                .allMatch(c->recognizer.recognize(c).topology()==ReferenceTopology.UNKNOWN));
    }

    @Test void matchingChangesNeitherCanonicalIdentityNorEligibility() {
        Configuration cube=cube();
        var id=cube.id();
        var before=new TopologyEligibilityAnalyzer().analyze(cube,ExperimentProfile.CLOSED_3D_VOLUME);
        recognizer.recognize(cube);
        var after=new TopologyEligibilityAnalyzer().analyze(cube,ExperimentProfile.CLOSED_3D_VOLUME);
        assertEquals(id,cube.id());
        assertEquals(before,after);
    }

    @Test void generatedTwentyVertexTopologyIsRecognizedOnlyByExactIsomorphism(){ConfigurationRequest request=ConfigurationRequest.byFaceCount(12,3,GeometryEmbeddingDimension.THREE_D,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME).withFaceComposition(FaceComposition.uniform(5));Configuration generated=new ConfigurationGenerator().generate(request,new GenerationExecution(GenerationWorkerCount.explicit(2),()->false,null)).configurations().getFirst();assertEquals("cfg-b10320c7778b",generated.id().toString());assertEquals(ReferenceTopology.DODECAHEDRON_GRAPH,recognizer.recognize(generated).topology());assertEquals(generated.id(),generated.id());}

    @Test void labelsUseCanonicalIndicesAndFollowMovingCoordinates() {
        Configuration cube=cube();
        List<Vector3d> first=java.util.stream.IntStream.range(0,8).mapToObj(i->new Vector3d(i,0,0)).toList();
        List<Vector3d> second=java.util.stream.IntStream.range(0,8).mapToObj(i->new Vector3d(i,i%2,1)).toList();
        RenderModel a=RenderModel.realization(cube,first,1),b=RenderModel.realization(cube,second,2);
        for(int i=0;i<8;i++){assertEquals(i,a.nodeLabels().get(i).canonicalVertexIndex());assertEquals(Integer.toString(i),a.nodeLabels().get(i).text());}
        assertNotEquals(a.nodeLabels().get(3).position(),b.nodeLabels().get(3).position());
        assertEquals(b.model().nodes().get(3).position(),b.nodeLabels().get(3).position());
    }

    @Test void benchmarkPresetsUseOnlyDeclaredGeneralConditions() {
        ThreeDExperimentConditions emergent=BenchmarkPresets.conditions(BenchmarkPreset.CUBE_EMERGENT_BENCHMARK);
        assertEquals(FacePlanarityMode.REQUIRE_PLANAR_FACES,emergent.facePlanarity());
        assertEquals(FaceAngleConditionMode.MEASURE_ONLY,emergent.faceAngles());
        assertEquals(DihedralConditionMode.MEASURE_ONLY,emergent.dihedrals());
        assertEquals(FacePlaneRadiusMode.MEASURE_ONLY,emergent.facePlaneRadii());
        assertEquals(RegularityMode.MEASURE_ONLY,emergent.vertexRadii());
        ThreeDExperimentConditions constrained=BenchmarkPresets.conditions(BenchmarkPreset.CUBE_CONSTRAINED_BENCHMARK);
        assertEquals(FaceAngleConditionMode.REQUIRE_EQUAL_ANGLES_WITHIN_EACH_FACE,constrained.faceAngles());
        assertEquals(DihedralConditionMode.REQUIRE_EQUAL_DIHEDRAL_ANGLES,constrained.dihedrals());
        assertEquals(FacePlaneRadiusMode.REQUIRE_EQUAL_FACE_PLANE_RADII,constrained.facePlaneRadii());
        assertEquals(RegularityMode.MEASURE_ONLY,constrained.normalCenterAlignment());
    }

    @Test void unknownTopologyRemainsEligibleAndBenchmarkSelectionDoesNotMutateIt() {
        Configuration unknown=new ConfigurationGenerator().generate(ConfigurationRequest.exhaustive(8,3,GeometryEmbeddingDimension.THREE_D))
                .configurations().stream().filter(c->recognizer.recognize(c).topology()==ReferenceTopology.UNKNOWN).findFirst().orElseThrow();
        List<DiscoveryConnection> edges=unknown.edges();
        assertTrue(new TopologyEligibilityAnalyzer().analyze(unknown,ExperimentProfile.CLOSED_3D_VOLUME).eligible());
        TestRequestFactory.request(unknown.id()).withBenchmark(BenchmarkPreset.CUBE_CONSTRAINED_BENCHMARK);
        assertEquals(edges,unknown.edges());
    }

    private Configuration cube(){return new ConfigurationGenerator().generate(ConfigurationRequest.exhaustive(8,3,GeometryEmbeddingDimension.THREE_D)).configurations().stream().filter(c->recognizer.recognize(c).topology()==ReferenceTopology.CUBE_GRAPH).findFirst().orElseThrow();}

    private static final class TestRequestFactory {
        static remframework.geometry.discovery.realization.session.TestRequest request(ConfigurationId id){return new remframework.geometry.discovery.realization.session.TestRequest(id,GeometryEmbeddingDimension.THREE_D,1,42,1,null,GeometrySymmetryPolicy.MEASURE_ONLY,1000);}
    }
}
