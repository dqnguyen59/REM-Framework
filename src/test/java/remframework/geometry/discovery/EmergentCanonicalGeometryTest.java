package remframework.geometry.discovery;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EmergentCanonicalGeometryTest {
    @Test void validationAndDeterministicEnumeration() {
        ConfigurationGenerator generator=new ConfigurationGenerator();
        assertEquals(GeometryConfigurationStatusCode.INVALID_NODE_COUNT,generator.generate(request(0,0)).status().code());
        assertEquals(GeometryConfigurationStatusCode.INVALID_DEGREE,generator.generate(request(4,4)).status().code());
        assertEquals(GeometryConfigurationStatusCode.DEGREE_SUM_ODD,generator.generate(request(5,3)).status().code());
        var a=generator.generate(request(4,2));var b=generator.generate(request(4,2));
        assertTrue(a.metrics().enumerationComplete());assertEquals(1,a.configurations().size());
        assertEquals(a.configurations().stream().map(Configuration::canonicalGraphLabel).toList(),b.configurations().stream().map(Configuration::canonicalGraphLabel).toList());
        Configuration c=a.configurations().get(0);assertTrue(c.connected());assertTrue(c.adjacency().stream().allMatch(v->v.size()==2));assertTrue(c.automorphismGroupSize()>1);
    }
    @Test void cubicEightHasFiveNonIsomorphicConnectedGraphs(){var r=new ConfigurationGenerator().generate(request(8,3));assertTrue(r.metrics().enumerationComplete());assertEquals(5,r.configurations().size());}
    @Test void resourceLimitIsExplicit(){var r=new ConfigurationGenerator().generate(new ConfigurationRequest(10,3,GeometryEmbeddingDimension.THREE_D,GraphSymmetryFilter.BOTH,100,Duration.ofSeconds(1),null,null));assertFalse(r.metrics().enumerationComplete());assertEquals(GeometryConfigurationStatusCode.ENUMERATION_LIMIT_REACHED,r.status().code());}
    @Test void persistenceContainsNoCoordinates(@TempDir Path temp)throws Exception{var generated=new ConfigurationGenerator().generate(request(4,3));Path set=new ConfigurationStore(temp).save(generated);String text=java.nio.file.Files.readString(set.resolve("configurations").resolve(generated.configurations().get(0).id()+".toml"));assertFalse(text.contains("coordinate"));assertEquals(1,new ConfigurationStore(temp).reopen(set).size());}
    @Test void triangleSquareTetrahedronAndCubeMeasures(){
        GeometryMeasureCalculator calc=new GeometryMeasureCalculator();
        var triangle=manual(GeometryEmbeddingDimension.TWO_D,List.of(new Vector3d(0,0,0),new Vector3d(1,0,0),new Vector3d(.5,Math.sqrt(3)/2,0)),List.of(new DerivedFace("f",List.of(0,1,2),true,true,0)),3,3);
        assertEquals(Math.sqrt(3)/4,calc.area(triangle.r,triangle.b).absoluteArea(),1e-9);
        var square=manual(GeometryEmbeddingDimension.TWO_D,List.of(new Vector3d(0,0,0),new Vector3d(1,0,0),new Vector3d(1,1,0),new Vector3d(0,1,0)),List.of(new DerivedFace("f",List.of(0,1,2,3),true,true,0)),4,4);assertEquals(1,calc.area(square.r,square.b).absoluteArea(),1e-9);
        double h=Math.sqrt(2.0/3),y=Math.sqrt(3)/2;List<Vector3d> p=List.of(new Vector3d(0,0,0),new Vector3d(1,0,0),new Vector3d(.5,y,0),new Vector3d(.5,y/3,h));List<DerivedFace> f=List.of(face(0,1,2),face(0,3,1),face(1,3,2),face(2,3,0));var tetra=manual(GeometryEmbeddingDimension.THREE_D,p,f,4,6);assertEquals(Math.sqrt(2)/12,calc.volume(tetra.r,tetra.b).absoluteVolume(),1e-9);
        List<Vector3d> cp=List.of(new Vector3d(0,0,0),new Vector3d(1,0,0),new Vector3d(1,1,0),new Vector3d(0,1,0),new Vector3d(0,0,1),new Vector3d(1,0,1),new Vector3d(1,1,1),new Vector3d(0,1,1));List<DerivedFace> cf=List.of(face(0,3,2,1),face(4,5,6,7),face(0,1,5,4),face(1,2,6,5),face(2,3,7,6),face(3,0,4,7));var cube=manual(GeometryEmbeddingDimension.THREE_D,cp,cf,8,12);assertEquals(1,calc.volume(cube.r,cube.b).absoluteVolume(),1e-9);
    }
    private static ConfigurationRequest request(int n,int k){return new ConfigurationRequest(n,k,GeometryEmbeddingDimension.THREE_D,GraphSymmetryFilter.BOTH,1000,Duration.ofSeconds(30),null,null);}
    private static DerivedFace face(Integer... ids){return new DerivedFace("f"+List.of(ids),List.of(ids),true,true,0);}
    private record Manual(Realization r,ClosedBoundaryResult b){}
    private static Manual manual(GeometryEmbeddingDimension d,List<Vector3d> p,List<DerivedFace> f,int v,int e){var diag=GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.BOUNDARY_VALIDATION,GeometryConfigurationStatusCode.CLOSED_BOUNDARY,"closed","closed");var status=GeometryConfigurationStatus.of(diag);var r=new Realization(1,d,p,p,1,0,true,true,true,d==GeometryEmbeddingDimension.TWO_D?2:3,RealizationStatus.REALIZED,List.of(diag));var b=new ClosedBoundaryResult(true,true,true,true,false,v-e+f.size(),f,status,List.of(diag));return new Manual(r,b);}
}
