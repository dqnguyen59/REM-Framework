package remframework.geometry.discovery;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.experiment.*;

class DualDirectedGenerationTest {
    @Test void icosahedralClassUsesDualDirectedGeneration(){
        ConfigurationRequest request=ConfigurationRequest.byFaceCount(20,5,GeometryEmbeddingDimension.THREE_D,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME).withFaceComposition(FaceComposition.uniform(3));
        assertEquals(GenerationStrategy.DUAL_DIRECTED_UNIFORM_SPHERICAL_GENERATION,request.normalizedGraphRequest().selectedStrategy());
        GenerationResult result=new ConfigurationGenerator().generate(request);
        assertFalse(result.configurations().isEmpty(),result.status().explanation());
        Configuration c=result.configurations().getFirst();assertEquals(12,c.nodeCount());assertEquals(30,c.edges().size());assertEquals(20,c.provenance().get("combinatorialFaces").split(";").length);assertTrue(c.provenance().get("generationStrategy").contains("DUAL_DIRECTED"));
    }
}
