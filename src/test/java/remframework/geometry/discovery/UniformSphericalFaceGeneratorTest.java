package remframework.geometry.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.experiment.*;
import remframework.geometry.discovery.eligibility.TopologyEligibilityAnalyzer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import remframework.geometry.discovery.persistence.ConfigurationStore;
import remframework.geometry.discovery.reference.*;
import remframework.geometry.discovery.realization.session.*;

class UniformSphericalFaceGeneratorTest {
    @TempDir Path temp;
    @Test void specializedSmallCasesExactlyMatchExistingTopologySets() {
        compare(4,3,3,ReferenceTopology.TETRAHEDRON_GRAPH);
        GenerationResult cube=compare(6,3,4,ReferenceTopology.CUBE_GRAPH);
        assertEquals("cfg-c83f892b5931",cube.configurations().getFirst().id().toString());
        assertEquals(6,faces(cube.configurations().getFirst()).size());
    }

    @Test void twelvePentagonalFacesGenerateBeyondGeneralBound() throws Exception {
        ConfigurationRequest request=request(12,3,5,false);
        GenerationResult result=new ConfigurationGenerator().generate(request);
        assertEquals(GenerationStrategy.UNIFORM_SPHERICAL_FACE_GENERATION,result.generationDiagnostics().strategy());
        assertNotEquals(GeometryConfigurationStatusCode.ENUMERATION_LIMIT_REACHED,result.status().code());
        assertEquals(GenerationCompleteness.COMPLETE_FOR_SELECTED_PARAMETER_CLASS,result.generationDiagnostics().completeness(),result.generationDiagnostics().toString());
        assertEquals(1,result.configurations().size());
        Configuration configuration=result.configurations().getFirst();
        assertEquals(20,configuration.nodeCount());assertEquals(30,configuration.edges().size());
        assertTrue(configuration.adjacency().stream().allMatch(neighbors->neighbors.size()==3));
        var report=new TopologyEligibilityAnalyzer().analyze(configuration,
                ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME,FaceComposition.uniform(5));
        assertEquals(12,report.topologicalFaces().size());
        assertTrue(report.topologicalFaces().stream().allMatch(face->face.size()==5));
        assertEquals(2,configuration.nodeCount()-configuration.edges().size()+report.topologicalFaces().size());
        String firstId=configuration.id().toString();
        CombinatorialFaceComplex complex=CombinatorialFaceComplex.decode(configuration.nodeCount(),configuration.provenance().get("combinatorialFaces"));
        var validation=new CombinatorialSurfaceValidator().validate(complex,30,12,3,5,2);
        assertTrue(validation.valid(),validation.failures().toString());
        int[] permutation=java.util.stream.IntStream.range(0,20).map(i->19-i).toArray();
        CombinatorialFaceComplex relabelled=new CombinatorialFaceComplex(20,complex.faces().stream().map(face->face.stream().map(v->permutation[v]).toList()).toList());
        assertEquals(new CombinatorialSurfaceCanonicalizer().canonicalize(complex).code(),new CombinatorialSurfaceCanonicalizer().canonicalize(relabelled).code());
        Path set=new ConfigurationStore(temp).save(result);ConfigurationStore store=new ConfigurationStore(temp);Configuration reopened=store.reopen(set).getFirst();
        assertEquals(firstId,reopened.id().toString());assertEquals(12,faces(reopened).size());
        assertEquals(GenerationStrategy.UNIFORM_SPHERICAL_FACE_GENERATION,store.reopenRequest(set).normalizedGraphRequest().selectedStrategy());
        String persistedSummary=java.nio.file.Files.readString(set.resolve("generation-summary.toml"));
        assertTrue(persistedSummary.contains("generation_strategy = \"UNIFORM_SPHERICAL_FACE_GENERATION\""));
        assertTrue(persistedSummary.contains("construction_method = \"DUAL_TRIANGULATED_SURFACE\""));
        TestRequest stage2=new TestRequest(configuration.id(),GeometryEmbeddingDimension.THREE_D,1,42,1,null,GeometrySymmetryPolicy.MEASURE_ONLY,1,
                ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME,null,null,null,0,0,0,0).withFaceComposition(FaceComposition.uniform(5)).withParameterDerivation(request);
        IncrementalSession session=new IncrementalSession(configuration,stage2);session.run();assertNotNull(session.result());assertEquals(12,new TopologyEligibilityAnalyzer().deriveTopologicalFaces(configuration).size());
    }

    @Test void configuredLimitIsIncompleteAndGeneralUnsupportedRequestStaysGeneral(){GenerationResult limited=new ConfigurationGenerator().generate(request(12,3,5,false).withEnumerationTimeLimit(Duration.ofNanos(1)));assertEquals(GenerationCompleteness.INCOMPLETE_SEARCH_LIMIT_REACHED,limited.generationDiagnostics().completeness());ConfigurationRequest general=new ConfigurationRequest(10,3,GeometryEmbeddingDimension.THREE_D,GraphSymmetryFilter.BOTH,100,Duration.ofSeconds(1),null,null,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME);assertEquals(GeometryConfigurationStatusCode.ENUMERATION_LIMIT_REACHED,new ConfigurationGenerator().generate(general).status().code());}

    @Test void canonicalPruningIsDeterministicOnSmallClass(){GenerationResult a=new ConfigurationGenerator().generate(request(6,3,4,true)),b=new ConfigurationGenerator().generate(request(6,3,4,true));assertEquals(a.generationDiagnostics().pruningCounts(),b.generationDiagnostics().pruningCounts());assertEquals(a.configurations().stream().map(Configuration::id).toList(),b.configurations().stream().map(Configuration::id).toList());}

    private GenerationResult compare(int f,int k,int m,ReferenceTopology reference){ConfigurationRequest base=request(f,k,m,false);ConfigurationRequest exhaustive=base.withGenerationStrategy(GenerationStrategy.EXHAUSTIVE_BACKTRACKING);ConfigurationRequest specialized=base.withGenerationStrategy(GenerationStrategy.UNIFORM_SPHERICAL_FACE_GENERATION);GenerationResult oldResult=new ConfigurationGenerator().generate(exhaustive),newResult=new ConfigurationGenerator().generate(specialized);assertEquals(GenerationStrategy.UNIFORM_SPHERICAL_FACE_GENERATION,newResult.generationDiagnostics().strategy());assertEquals(oldResult.configurations().stream().filter(c->new TopologyEligibilityAnalyzer().analyze(c,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME,FaceComposition.uniform(m)).faceComposition().match()==FaceCompositionMatch.MATCH).map(Configuration::id).collect(java.util.stream.Collectors.toSet()),newResult.configurations().stream().map(Configuration::id).collect(java.util.stream.Collectors.toSet()));assertEquals(1,newResult.configurations().size());assertEquals(reference,new ReferenceTopologyRecognizer().recognize(newResult.configurations().getFirst()).topology());return newResult;}
    private static ConfigurationRequest request(int f,int k,int m,boolean explicit){ConfigurationRequest value=ConfigurationRequest.byFaceCount(f,k,GeometryEmbeddingDimension.THREE_D,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME).withFaceComposition(FaceComposition.uniform(m));return explicit?value.withGenerationStrategy(GenerationStrategy.UNIFORM_SPHERICAL_FACE_GENERATION):value;}
    private static List<List<Integer>> faces(Configuration configuration){return new TopologyEligibilityAnalyzer().deriveTopologicalFaces(configuration);}
}
