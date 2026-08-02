package remframework.geometry.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import remframework.geometry.discovery.persistence.ConfigurationStore;
import remframework.gui.view.frames.GeometryDiscoveryControlFrame;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.experiment.*;

import java.util.Map;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

class PrimaryParameterModeTest {
    @TempDir Path temp;
    @Test void nodeAndFaceDrivenTetrahedronNormalizeEquivalently() {
        ConfigurationRequest node=node(4,3,3);
        ConfigurationRequest face=ConfigurationRequest.byFaceCount(4,3,
                GeometryEmbeddingDimension.THREE_D,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME)
                .withFaceComposition(FaceComposition.uniform(3));
        assertDerived(node,4,6,4);assertDerived(face,4,6,4);
        assertNormalizedEquivalent(node,face);
    }

    @Test void nodeAndFaceDrivenCubeRetainCanonicalIdentity() {
        ConfigurationRequest node=node(8,3,4);
        ConfigurationRequest face=ConfigurationRequest.byFaceCount(6,3,
                GeometryEmbeddingDimension.THREE_D,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME)
                .withFaceComposition(FaceComposition.uniform(4));
        assertNormalizedEquivalent(node,face);
        var generator=new ConfigurationGenerator();
        assertTrue(generator.generate(node).configurations().stream().anyMatch(c->c.id().toString().equals("cfg-c83f892b5931")));
        assertTrue(generator.generate(face).configurations().stream().anyMatch(c->c.id().toString().equals("cfg-c83f892b5931")));
    }

    @Test void faceCountTwentyNodeCasePassesArithmeticButNotGenerationCapability() {
        ConfigurationRequest request=ConfigurationRequest.byFaceCount(12,3,
                GeometryEmbeddingDimension.THREE_D,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME)
                .withFaceComposition(FaceComposition.uniform(5));
        assertDerived(request,20,30,12);
        assertTrue(request.parameterDerivation().arithmeticFeasible());
        assertFalse(request.parameterDerivation().generationStrategyAvailable());
        assertEquals(GeometryConfigurationStatusCode.ENUMERATION_LIMIT_REACHED,
                new ConfigurationGenerator().generate(request).status().code());
    }

    @Test void exactMixedCompositionDerivesSixVertices() {
        ConfigurationRequest request=composition(3,Map.of(3,2,4,3));
        assertDerived(request,6,9,5);
        assertTrue(request.parameterDerivation().arithmeticFeasible());
    }

    @Test void exactPentagonalCompositionPassesArithmeticAtTwentyVertices() {
        ConfigurationRequest request=composition(3,Map.of(5,12));
        assertDerived(request,20,30,12);
        assertTrue(request.parameterDerivation().arithmeticFeasible());
        assertFalse(request.parameterDerivation().generationStrategyAvailable());
    }

    @Test void invalidExactCompositionsHavePreciseReasons() {
        assertReason(composition(3,Map.of(3,1)),GeometryConfigurationStatusCode.NON_INTEGER_DERIVED_EDGE_COUNT);
        assertReason(composition(3,Map.of(4,1)),GeometryConfigurationStatusCode.NON_INTEGER_DERIVED_VERTEX_COUNT);
        assertReason(composition(3,Map.of(3,2)),GeometryConfigurationStatusCode.EULER_CHARACTERISTIC_MISMATCH);
    }

    @Test void faceCountDegreeTwoIsUnderdeterminedWithoutDivision() {
        ConfigurationRequest request=ConfigurationRequest.byFaceCount(4,2,
                GeometryEmbeddingDimension.THREE_D,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME);
        assertFalse(request.parameterDerivation().arithmeticFeasible());
        assertTrue(request.parameterDerivation().explanation().contains("FAIL"));
        assertNull(request.parameterDerivation().derivedVertexCount());
    }

    @Test void impossibleDegreeAndOverflowAreDeterministic() {
        assertReason(composition(4,Map.of(3,4)),GeometryConfigurationStatusCode.DEGREE_GRAPH_IMPOSSIBLE);
        ConfigurationRequest overflow=composition(3,Map.of(Integer.MAX_VALUE,Long.MAX_VALUE));
        assertReason(overflow,GeometryConfigurationStatusCode.INVALID_PRIMARY_PARAMETERS);
    }

    @Test void allPrimaryModesRoundTripAndDerivedDuplicatesAreRecomputed() throws Exception {
        for(ConfigurationRequest request:java.util.List.of(node(4,3,3),
                ConfigurationRequest.byFaceCount(4,3,GeometryEmbeddingDimension.THREE_D,
                        ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME).withFaceComposition(FaceComposition.uniform(3)),
                composition(3,Map.of(3,2,4,3)))){
            ConfigurationStore store=new ConfigurationStore(temp.resolve(request.primaryParameterMode().name()));
            Path set=store.save(new ConfigurationGenerator().generate(request));
            Path persisted=set.resolve("request.toml");
            String text=Files.readString(persisted).replaceFirst("derived_vertex_count = \\d+","derived_vertex_count = 999");
            Files.writeString(persisted,text);
            ConfigurationRequest reopened=store.reopenRequest(set);
            assertEquals(request.primaryParameterMode(),reopened.primaryParameterMode());
            assertEquals(request.nodeCount(),reopened.nodeCount());
            assertEquals(request.parameterDerivation().derivedEdgeCount(),reopened.parameterDerivation().derivedEdgeCount());
            assertEquals(request.exactFaceCounts(),reopened.exactFaceCounts());
        }
    }

    @Test void legacyVersionThreeDefaultsExplicitlyToNodeCount() throws Exception {
        Path set=temp.resolve("legacy");Files.createDirectories(set);
        Files.writeString(set.resolve("request.toml"),"""
                format_version = 3
                node_count = 4
                exact_degree = 3
                intended_dimension = "THREE_D"
                experiment_profile = "CONVEX_POLYHEDRAL_VOLUME"
                symmetry_filter = "BOTH"
                maximum_configurations = 100
                maximum_direct_cycle_nodes = 10000
                enumeration_time_millis = 30000
                generation_strategy = "EXHAUSTIVE_BACKTRACKING"
                """);
        ConfigurationRequest reopened=new ConfigurationStore(temp).reopenRequest(set);
        assertEquals(PrimaryParameterMode.NODE_COUNT,reopened.primaryParameterMode());
        assertEquals(4,reopened.nodeCount());assertEquals(6,reopened.parameterDerivation().derivedEdgeCount());
    }

    @Test void uiDeclaresModeSpecificInputsReadOnlyDerivationsAndGenerationGate() {
        assertEquals(java.util.List.of("Node count N","Exact degree K"),
                GeometryDiscoveryControlFrame.primaryAuthoritativeControls(PrimaryParameterMode.NODE_COUNT));
        assertEquals(java.util.List.of("Total face count F","Exact degree K"),
                GeometryDiscoveryControlFrame.primaryAuthoritativeControls(PrimaryParameterMode.FACE_COUNT));
        assertEquals(3,GeometryDiscoveryControlFrame.primaryDerivedReadOnlyFields(
                PrimaryParameterMode.FACE_COMPOSITION).size());
        assertTrue(GeometryDiscoveryControlFrame.generationEnabled(node(4,3,3).parameterDerivation()));
        assertFalse(GeometryDiscoveryControlFrame.generationEnabled(composition(3,Map.of(3,1)).parameterDerivation()));
    }

    private static ConfigurationRequest node(int n,int k,int m){return new ConfigurationRequest(n,k,
            GeometryEmbeddingDimension.THREE_D,GraphSymmetryFilter.BOTH,10_000,
            java.time.Duration.ofSeconds(30),null,null,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME)
            .withFaceComposition(FaceComposition.uniform(m));}
    private static ConfigurationRequest composition(int k,Map<Integer,? extends Number> values){return ConfigurationRequest.byFaceComposition(
            ExactFaceCounts.of(values),k,GeometryEmbeddingDimension.THREE_D,
            ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME);}
    private static void assertDerived(ConfigurationRequest r,long v,long e,long f){assertEquals(v,r.parameterDerivation().derivedVertexCount());assertEquals(e,r.parameterDerivation().derivedEdgeCount());assertEquals(f,r.parameterDerivation().derivedFaceCount());}
    private static void assertNormalizedEquivalent(ConfigurationRequest a,ConfigurationRequest b){assertEquals(a.nodeCount(),b.nodeCount());assertEquals(a.exactDegree(),b.exactDegree());assertEquals(a.parameterDerivation().derivedEdgeCount(),b.parameterDerivation().derivedEdgeCount());assertEquals(a.parameterDerivation().derivedFaceCount(),b.parameterDerivation().derivedFaceCount());}
    private static void assertReason(ConfigurationRequest request,GeometryConfigurationStatusCode reason){assertFalse(request.parameterDerivation().arithmeticFeasible());assertTrue(request.parameterDerivation().reasons().contains(reason),request.parameterDerivation().toString());}
}
