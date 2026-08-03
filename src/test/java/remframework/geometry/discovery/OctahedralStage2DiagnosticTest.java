package remframework.geometry.discovery;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.experiment.*;
import remframework.geometry.discovery.realization.session.*;

class OctahedralStage2DiagnosticTest {
    @Test void printOctahedralStage2Path(){
        ConfigurationRequest generation=ConfigurationRequest.exhaustive(6,4,GeometryEmbeddingDimension.THREE_D);
        var result=new ConfigurationGenerator().generate(generation);
        Configuration c=result.configurations().stream().filter(x->x.id().toString().equals("cfg-6693d4cdf76c")).findFirst().orElseThrow();
        var faces=new remframework.geometry.discovery.eligibility.TopologyEligibilityAnalyzer().deriveTopologicalFaces(c);
        assertEquals(8,faces.size());assertTrue(faces.stream().allMatch(f->f.size()==3));assertEquals(2,c.nodeCount()-c.edges().size()+faces.size());
        TestRequest request=new TestRequest(c.id(),GeometryEmbeddingDimension.THREE_D,1,42,1,null,GeometrySymmetryPolicy.MEASURE_ONLY,100,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME,null,null,null,1e-3,1e-7,1e-9,1e-6,null,0,0,null,BenchmarkPreset.NONE,ThreeDConstraintStrengths.defaults()).withContinuation(ContinuationSettings.manual());
        IncrementalSession session=new IncrementalSession(c,request);
        assertNull(session.initializationFailure());
        assertEquals(TopologyInitialization.RANDOM_SPHERICAL,request.continuation().initialization());assertEquals(6,session.currentCoordinates().size());assertTrue(session.currentCoordinates().stream().allMatch(v->v.isFinite()));assertEquals(ManualExecutionState.MANUAL_IDLE,session.manualExecutionState());assertEquals(0,session.iteration());
        session.setManualRelaxation(ManualConstraint.EDGE_LENGTH,true);assertTrue(session.step());session.setManualRelaxation(ManualConstraint.EDGE_LENGTH,false);assertEquals(ManualExecutionState.MANUAL_IDLE,session.manualExecutionState());
    }
}
