package remframework.geometry.discovery;

import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.eligibility.TopologyEligibilityAnalyzer;
import remframework.geometry.discovery.experiment.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.viewport.RenderModel;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionResponsivenessTest {
    @Test void oneAndMultipleWorkersRetainIdenticalSmallTopologySets(){compare(4,3,3);compare(6,3,4);}

    @Test void oneAndMultipleWorkersRetainIdenticalN20TopologySet(){compare(12,3,5);}

    @Test void cancellationIsCooperativeAndIncomplete(){AtomicBoolean cancelled=new AtomicBoolean();ConfigurationRequest request=request(12,3,5);GenerationExecution execution=new GenerationExecution(GenerationWorkerCount.explicit(2),cancelled::get,progress->{if(progress.statesExamined()>10)cancelled.set(true);});GenerationResult result=new ConfigurationGenerator().generate(request,execution);assertEquals(GenerationCompleteness.INCOMPLETE_SEARCH_LIMIT_REACHED,result.generationDiagnostics().completeness());assertTrue(cancelled.get());}

    @Test void workflowGenerationAndStage2InitializationUseBackgroundThreads() throws Exception {PhasedWorkflow workflow=new PhasedWorkflow();try{String caller=Thread.currentThread().getName();ConfigurationRequest tetra=request(4,3,3);workflow.generateConfigurationsAsync(tetra,GenerationWorkerCount.explicit(2));await(()->!workflow.generationRunning());assertFalse(workflow.generation().isEmpty());assertNotEquals(caller,workflow.stage1ThreadName());Configuration configuration=workflow.generation().orElseThrow().configurations().getFirst();long builds=RenderModel.faceComplexBuildCount();workflow.selectForGeometryTest(configuration.id());RenderModel preview=workflow.renderModel();assertNotNull(preview);assertNull(workflow.incrementalSession());assertEquals(0,workflow.geometryTestSessionState()==SessionState.READY?0:1);TestRequest test=test(configuration,tetra);workflow.startSelectedGeometryTest(test);assertEquals(Stage2InitializationState.INITIALIZING,workflow.stage2InitializationState());assertSame(preview,workflow.renderModel());await(()->workflow.stage2InitializationState()!=Stage2InitializationState.INITIALIZING);assertEquals(Stage2InitializationState.READY,workflow.stage2InitializationState());assertNotEquals(caller,workflow.stage2ThreadName());assertEquals(0,workflow.incrementalSession().iteration());workflow.renderModel();workflow.renderModel();assertEquals(builds+1,RenderModel.faceComplexBuildCount());}finally{workflow.close();}}

    @Test void replacingGenerationPreventsRetiredPublication() throws Exception {PhasedWorkflow workflow=new PhasedWorkflow();try{workflow.generateConfigurationsAsync(request(12,3,5),GenerationWorkerCount.explicit(1));workflow.generateConfigurationsAsync(request(4,3,3),GenerationWorkerCount.explicit(1));await(()->!workflow.generationRunning());GenerationResult result=workflow.generation().orElseThrow();assertEquals(4,result.request().parameterDerivation().derivedVertexCount());}finally{workflow.close();}}

    private static void compare(int faces,int degree,int size){ConfigurationRequest request=request(faces,degree,size);GenerationResult one=new ConfigurationGenerator().generate(request,new GenerationExecution(GenerationWorkerCount.explicit(1),()->false,null));GenerationResult many=new ConfigurationGenerator().generate(request,new GenerationExecution(GenerationWorkerCount.explicit(Math.min(4,Math.max(2,Runtime.getRuntime().availableProcessors()))),()->false,null));assertEquals(ids(one),ids(many));assertEquals(one.generationDiagnostics().completeness(),many.generationDiagnostics().completeness());for(Configuration configuration:one.configurations()){Configuration other=many.configurations().stream().filter(value->value.id().equals(configuration.id())).findFirst().orElseThrow();assertEquals(configuration.provenance().get("combinatorialFaces"),other.provenance().get("combinatorialFaces"));assertEquals(new TopologyEligibilityAnalyzer().analyze(configuration,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME,FaceComposition.uniform(size)).status(),new TopologyEligibilityAnalyzer().analyze(other,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME,FaceComposition.uniform(size)).status());}}
    private static Set<ConfigurationId> ids(GenerationResult result){return result.configurations().stream().map(Configuration::id).collect(java.util.stream.Collectors.toSet());}
    private static ConfigurationRequest request(int faces,int degree,int size){return ConfigurationRequest.byFaceCount(faces,degree,GeometryEmbeddingDimension.THREE_D,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME).withFaceComposition(FaceComposition.uniform(size)).withGenerationStrategy(GenerationStrategy.UNIFORM_SPHERICAL_FACE_GENERATION);}
    private static TestRequest test(Configuration configuration,ConfigurationRequest generation){return new TestRequest(configuration.id(),GeometryEmbeddingDimension.THREE_D,1,42,1,null,GeometrySymmetryPolicy.MEASURE_ONLY,10,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME,null,null,null,0,0,0,0).withFaceComposition(generation.faceComposition()).withParameterDerivation(generation);}
    private static void await(java.util.function.BooleanSupplier condition)throws Exception{long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();while(!condition.getAsBoolean()&&System.nanoTime()<deadline)Thread.sleep(5);assertTrue(condition.getAsBoolean());}
}
