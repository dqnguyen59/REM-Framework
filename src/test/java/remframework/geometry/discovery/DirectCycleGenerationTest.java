package remframework.geometry.discovery;

import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.experiment.ExperimentProfile;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class DirectCycleGenerationTest {
    @Test void constructsC9C20AndC100Exactly(){for(int n:new int[]{9,20,100})assertCycle(n);}

    @Test void directCycleIdentityIsDeterministicAndPreservesSmallCycleIdentity(){
        GenerationResult first=generate(9,2),second=generate(9,2);assertEquals(first.configurations().getFirst().id(),second.configurations().getFirst().id());
        assertEquals("cfg-6d9d964b1991",generate(4,2).configurations().getFirst().id().toString());
    }

    @Test void unsupportedGeneralLargeRequestsRemainBounded(){assertEquals(GeometryConfigurationStatusCode.ENUMERATION_LIMIT_REACHED,generate(10,3).status().code());}

    private void assertCycle(int n){GenerationResult result=generate(n,2);assertEquals(GeometryConfigurationStatusCode.ENUMERATION_COMPLETE,result.status().code());assertTrue(result.metrics().enumerationComplete());assertEquals(1,result.metrics().configurationsExamined());assertEquals(1,result.metrics().uniqueConfigurationsRetained());assertEquals(1,result.configurations().size());Configuration c=result.configurations().getFirst();assertEquals("DIRECT_CYCLE_CONSTRUCTION",c.generationMethod());assertEquals(n,c.nodeCount());assertEquals(n,c.edges().size());assertTrue(c.connected());assertEquals(2L*n,c.automorphismGroupSize());assertEquals(GraphSymmetryClassification.HIGHLY_SYMMETRIC,c.graphSymmetry());assertTrue(c.adjacency().stream().allMatch(neighbors->neighbors.size()==2));assertEquals(n,c.edges().stream().map(DiscoveryConnection::undirectedKey).distinct().count());}
    private GenerationResult generate(int n,int k){return new ConfigurationGenerator().generate(new ConfigurationRequest(n,k,GeometryEmbeddingDimension.TWO_D,GraphSymmetryFilter.BOTH,100,Duration.ofSeconds(5),null,null,ExperimentProfile.CLOSED_2D_AREA,1000));}
}
