package remframework.geometry.discovery;

import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.eligibility.TopologyEligibilityAnalyzer;
import remframework.geometry.discovery.experiment.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.reference.*;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class UniformFaceCompositionTest {
    private final ConfigurationGenerator generator=new ConfigurationGenerator();
    private final TopologyEligibilityAnalyzer analyzer=new TopologyEligibilityAnalyzer();

    @Test void triangularFaceClassFindsFourTriangularFaces() {
        GenerationResult generated=generator.generate(request(4,3,3));
        assertTrue(generated.metrics().enumerationComplete());
        assertEquals(1,generated.configurations().size());
        var report=analyzer.analyze(generated.configurations().getFirst(),
                ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME,FaceComposition.uniform(3)).faceComposition();
        assertEquals(FaceCompositionMatch.MATCH,report.match());
        assertEquals(4,report.actualFaceCount());
        assertEquals(java.util.Map.of(3,4),report.faceEdgeCountDistribution());
        assertTrue(report.faces().stream().allMatch(face->face.size()==3));
        assertEquals(ReferenceTopology.TETRAHEDRON_GRAPH,
                new ReferenceTopologyRecognizer().recognize(generated.configurations().getFirst()).topology());
    }

    @Test void quadrilateralFaceClassFindsCanonicalCubeTopology() {
        GenerationResult generated=generator.generate(request(8,3,4));
        Configuration cube=generated.configurations().stream()
                .filter(c->c.id().toString().equals("cfg-c83f892b5931")).findFirst().orElseThrow();
        var report=analyzer.analyze(cube,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME,
                FaceComposition.uniform(4)).faceComposition();
        assertEquals(FaceCompositionMatch.MATCH,report.match());
        assertEquals(6,report.actualFaceCount());
        assertEquals(java.util.Map.of(4,6),report.faceEdgeCountDistribution());
        assertEquals(ReferenceTopology.CUBE_GRAPH,new ReferenceTopologyRecognizer().recognize(cube).topology());
    }

    @Test void impossibleIncidenceArithmeticStopsBeforeEnumeration() {
        GenerationResult generated=generator.generate(request(8,3,3));
        assertEquals(GeometryConfigurationStatusCode.FACE_COMPOSITION_ARITHMETICALLY_IMPOSSIBLE,
                generated.status().code());
        assertEquals(0,generated.metrics().configurationsExamined());
        assertTrue(generated.configurations().isEmpty());
    }

    @Test void validGraphIsRetainedWhenRequestedFaceSizeDoesNotMatch() {
        Configuration tetra=generator.generate(ConfigurationRequest.exhaustive(4,3,
                GeometryEmbeddingDimension.THREE_D)).configurations().getFirst();
        var report=analyzer.analyze(tetra,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME,
                FaceComposition.uniform(4)).faceComposition();
        assertEquals(FaceCompositionMatch.NO_MATCH,report.match());
        assertTrue(report.reasons().contains(GeometryConfigurationStatusCode.FACE_SIZE_MISMATCH));
        assertNotNull(tetra);
    }

    @Test void discoverDoesNotRejectBasedOnUniformFaceSize() {
        Configuration tetra=generator.generate(ConfigurationRequest.exhaustive(4,3,
                GeometryEmbeddingDimension.THREE_D)).configurations().getFirst();
        var report=analyzer.analyze(tetra,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME,
                FaceComposition.discover());
        assertTrue(report.eligible());
        assertFalse(report.faceComposition().reasons().contains(
                GeometryConfigurationStatusCode.FACE_SIZE_MISMATCH));
    }

    @Test void unavailableFaceSetIsReportedAndConfigurationRemainsRetained() {
        GenerationResult generated=generator.generate(ConfigurationRequest.exhaustive(6,3,
                GeometryEmbeddingDimension.THREE_D));
        assertEquals(2,generated.configurations().size());
        var unavailable=generated.configurations().stream().map(configuration->analyzer.analyze(
                configuration,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME,
                FaceComposition.discover()).faceComposition())
                .filter(report->report.reasons().contains(
                        GeometryConfigurationStatusCode.NO_COMBINATORIAL_FACE_SET))
                .findFirst().orElseThrow();
        assertEquals(FaceCompositionMatch.UNDETERMINED,unavailable.match());
        assertEquals(2,generated.configurations().size());
    }

    @Test void arithmeticReportsAllEulerAndIncidenceTermsWithoutClaimingExistence() {
        FaceCompositionArithmetic arithmetic=FaceCompositionArithmetic.evaluate(8,3,4,
                ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME);
        assertTrue(arithmetic.passed());
        assertEquals(12,arithmetic.expectedEdgeCount());
        assertEquals(6,arithmetic.expectedFaceCount());
        assertEquals(24,arithmetic.expectedFaceEdgeIncidences());
        assertEquals(24,arithmetic.requiredEdgeFaceIncidences());
        assertEquals("NOT_YET_DETERMINED",arithmetic.matchingTopology());
    }

    @Test void constrainedCubeRemainsValidWithUniformQuadrilateralRequest() {
        Configuration cube=generator.generate(request(8,3,4)).configurations().stream()
                .filter(c->c.id().toString().equals("cfg-c83f892b5931")).findFirst().orElseThrow();
        TestRequest test=new TestRequest(cube.id(),GeometryEmbeddingDimension.THREE_D,1,42,1,
                null,GeometrySymmetryPolicy.MEASURE_ONLY,100_000,
                ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME,null,null,null,0,0,0,0)
                .withFaceComposition(FaceComposition.uniform(4))
                .withBenchmark(BenchmarkPreset.CUBE_CONSTRAINED_BENCHMARK);
        TestResult result=new TestSession(cube,test).run();
        assertEquals(GeometryConfigurationStatusCode.VALID_CANDIDATE,result.status().code());
        assertEquals(FaceCompositionMatch.MATCH,result.eligibility().faceComposition().match());
        assertTrue(result.selectedBenchmarkPassed());
    }

    @Test void triangularUniformFaceExperimentProducesValidGeneralCandidate() {
        Configuration topology=generator.generate(request(4,3,3)).configurations().getFirst();
        ThreeDExperimentConditions conditions=new ThreeDExperimentConditions(
                FacePlanarityMode.REQUIRE_PLANAR_FACES,
                FaceAngleConditionMode.REQUIRE_EQUAL_ANGLES_WITHIN_EACH_FACE,
                DihedralConditionMode.MEASURE_ONLY,RegularityMode.MEASURE_ONLY,
                RegularityMode.MEASURE_ONLY,RegularityMode.MEASURE_ONLY,
                FacePlaneRadiusMode.MEASURE_ONLY,RegularityMode.MEASURE_ONLY,
                GeometryCenterDefinition.VERTEX_CENTROID,1e-7,1e-6,1e-6);
        TestRequest test=new TestRequest(topology.id(),GeometryEmbeddingDimension.THREE_D,1,42,1,
                null,GeometrySymmetryPolicy.MEASURE_ONLY,100_000,
                ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME,AngleConditionMode.MEASURE_ONLY,
                RegularityMode.MEASURE_ONLY,OptimizationObjective.NONE,0,0,0,0,
                PlanarityRealizationMode.STRICT_2D_COORDINATES,.08,.01,conditions,
                BenchmarkPreset.NONE,ThreeDConstraintStrengths.defaults(),FaceComposition.uniform(3));
        TestResult result=new TestSession(topology,test).run();
        assertEquals(GeometryConfigurationStatusCode.VALID_CANDIDATE,result.status().code());
        assertEquals(4,result.faceDerivation().faces().size());
        assertTrue(result.boundary().closed()&&result.boundary().manifold());
        assertFalse(result.boundary().selfIntersecting());
        assertTrue(result.volume().absoluteVolume()>0);
        assertTrue(result.threeDConditionReport().constrainedExperimentPassed());
    }

    private static ConfigurationRequest request(int n,int k,int m) {
        return new ConfigurationRequest(n,k,GeometryEmbeddingDimension.THREE_D,
                GraphSymmetryFilter.BOTH,10_000,Duration.ofSeconds(30),
                "EXHAUSTIVE_BACKTRACKING",null,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME)
                .withFaceComposition(FaceComposition.uniform(m));
    }
}
