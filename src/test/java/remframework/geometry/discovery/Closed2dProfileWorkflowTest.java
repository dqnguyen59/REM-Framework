package remframework.geometry.discovery;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.experiment.*;
import remframework.geometry.discovery.measurement.regularity.TwoDRegularityCalculator;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.workflow.PhasedWorkflow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Closed2dProfileWorkflowTest {
    @TempDir Path temp;

    @Test void strictCoordinatesCannotMoveOutOfPlaneAndOneStepIsOneUpdate(){
        Configuration cycle=cycle(5);IncrementalSession session=new IncrementalSession(cycle,request(cycle,PlanarityRealizationMode.STRICT_2D_COORDINATES,AngleConditionMode.MEASURE_ONLY));
        List<Vector3d> before=session.currentCoordinates();session.step();
        assertEquals(1,session.iteration());assertTrue(session.currentCoordinates().stream().allMatch(p->p.z==0));
        assertNotEquals(before,session.currentCoordinates());
    }

    @Test void planeConstraintReducesDeviationAndMeasureOnlyDoesNotChangeForces(){
        FixedDegreeGraph graph=fixedCycle(5);List<DynamicElasticNode> nodes=List.of(
                node(0,0,0,0,true),node(1,1,0,0,true),node(2,1.3,1,.8,false),node(3,.3,1.5,-.6,false),node(4,-.4,.5,.4,false));
        DynamicElasticSettings settings=DynamicElasticSettings.defaults(1,7).withRequiredStableIterations(1000);
        DynamicElasticSession constrained=new DynamicElasticSession(graph,settings,nodes);
        constrained.configureTwoDimensionalProfile(false,true,.4,1e-7,false,108,1,.01);
        double before=constrained.maximumPlaneDeviation();for(int i=0;i<80;i++)constrained.step();assertTrue(constrained.maximumPlaneDeviation()<before);
        DynamicElasticSession baseline=new DynamicElasticSession(graph,settings,nodes),measureOnly=new DynamicElasticSession(graph,settings,nodes);
        measureOnly.configureTwoDimensionalProfile(false,false,0,1e-7,false,108,1,0);
        baseline.step();measureOnly.step();
        assertEquals(baseline.nodes().stream().map(DynamicElasticNode::position).toList(),measureOnly.nodes().stream().map(DynamicElasticNode::position).toList());
    }

    @Test void nonplanarPentagonHasEdgeRealizationButFailsFullQualification(){
        Configuration graph=cycle(5);List<Vector3d> p=List.of(new Vector3d(0,0,0),new Vector3d(1,0,0),new Vector3d(1.4,.8,.2),new Vector3d(.5,1.4,-.2),new Vector3d(-.3,.7,.1));
        var m=new TwoDRegularityCalculator().calculate(graph,p,List.of(0,1,2,3,4),1,.5,1e-5,1e-9,1e-6,AngleConditionMode.MEASURE_ONLY);
        assertFalse(m.planar());assertFalse(m.valid());assertTrue(m.maximumPlaneDeviation()>m.planarityTolerance());
    }

    @Test void angleTargetsModesAndAggregatesAreExplicit(){
        TwoDRegularityCalculator calculator=new TwoDRegularityCalculator();Configuration square=cycle(4);
        List<Vector3d> squarePoints=regularPolygon(4),pentagonPoints=regularPolygon(5);
        var s=calculator.calculate(square,squarePoints,List.of(0,1,2,3),squarePoints.get(0).distance(squarePoints.get(1)),1e-9,1e-9,1e-12,1e-9,AngleConditionMode.MEASURE_ONLY);
        var p=calculator.calculate(cycle(5),pentagonPoints,List.of(0,1,2,3,4),pentagonPoints.get(0).distance(pentagonPoints.get(1)),1e-9,1e-9,1e-12,1e-9,AngleConditionMode.NONE);
        assertEquals(Math.PI/2,s.targetInteriorAngle(),1e-12);assertEquals(3*Math.PI/5,p.targetInteriorAngle(),1e-12);
        assertEquals(4,s.interiorAngleTargetDeviations().size());assertEquals(0,s.interiorAngles().variance(),1e-15);
        assertEquals(s.interiorAngles().mean(),Math.PI/2,1e-12);assertEquals(0,s.maximumAngleTargetDeviation(),1e-12);
        List<Vector3d> rhombus=List.of(new Vector3d(0,0,0),new Vector3d(1,0,0),new Vector3d(1.5,1,0),new Vector3d(.5,1,0));
        assertTrue(calculator.calculate(square,rhombus,List.of(0,1,2,3),1.1,.2,1e-9,1e-9,1e-9,AngleConditionMode.MEASURE_ONLY).valid());
        assertFalse(calculator.calculate(square,rhombus,List.of(0,1,2,3),1.1,.2,1e-9,1e-9,1e-9,AngleConditionMode.REQUIRE_EQUAL_ANGLES).valid());
        assertTrue(calculator.calculate(square,rhombus,List.of(0,1,2,3),1.1,.2,1e-9,1e-9,1e-9,AngleConditionMode.NONE).valid());
    }

    @Test void generationTransactionClearsMemoryButNotPersistedFiles() throws Exception {
        PhasedWorkflow workflow=new PhasedWorkflow();Path marker=temp.resolve("persisted-result.toml");Files.writeString(marker,"preserve");
        GenerationResult first=workflow.generateConfigurations(generationRequest(4));ConfigurationId old=first.configurations().getFirst().id();workflow.selectForGeometryTest(old);
        assertNotNull(workflow.renderModel());GenerationResult second=workflow.generateConfigurations(generationRequest(5));
        assertEquals(1,second.configurations().size());assertEquals(1,workflow.tableRows().size());assertTrue(workflow.selectedConfiguration().isEmpty());assertNull(workflow.renderModel());assertTrue(workflow.geometryTests(old).isEmpty());assertTrue(Files.exists(marker));workflow.close();
    }

    @Test void workflowStepUpdatesRenderRevisionAndPausePreservesState() throws Exception {
        PhasedWorkflow workflow=new PhasedWorkflow();Configuration graph=workflow.generateConfigurations(generationRequest(4)).configurations().getFirst();workflow.selectForGeometryTest(graph.id());workflow.startSelectedGeometryTest(request(graph,PlanarityRealizationMode.STRICT_2D_COORDINATES,AngleConditionMode.MEASURE_ONLY));workflow.pauseSelectedGeometryTest();
        long revision=workflow.renderModel().revision();List<Vector3d> paused=workflow.incrementalSession().currentCoordinates();workflow.advanceGeometryTestFrame();Thread.sleep(30);assertEquals(paused,workflow.incrementalSession().currentCoordinates());
        workflow.stepSelectedGeometryTest();assertEquals(1,workflow.incrementalSession().iteration());assertTrue(workflow.renderModel().revision()>revision);workflow.resumeSelectedGeometryTest();assertEquals(ExecutionState.RUNNING,workflow.geometryExecutionState());workflow.close();
    }

    private ConfigurationRequest generationRequest(int n){return new ConfigurationRequest(n,2,GeometryEmbeddingDimension.TWO_D,GraphSymmetryFilter.BOTH,10,Duration.ofSeconds(5),null,null,ExperimentProfile.CLOSED_2D_AREA);}
    private TestRequest request(Configuration c,PlanarityRealizationMode plane,AngleConditionMode angle){return new TestRequest(c.id(),GeometryEmbeddingDimension.TWO_D,1,42,1,null,GeometrySymmetryPolicy.MEASURE_ONLY,10000,ExperimentProfile.CLOSED_2D_AREA,angle,RegularityMode.MEASURE_ONLY,OptimizationObjective.NONE,1e-3,1e-7,1e-9,1e-6,plane,.08,.02);}
    private static DynamicElasticNode node(int id,double x,double y,double z,boolean fixed){return new DynamicElasticNode(id,new Vector3d(x,y,z),new Vector3d(),fixed);}
    private static FixedDegreeGraph fixedCycle(int n){return new FixedDegreeGraph(n,2,edges(n),"cycle");}
    private static Configuration cycle(int n){var d=GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.GRAPH_GENERATION,GeometryConfigurationStatusCode.GENERATED,"generated","test");return new Configuration(new ConfigurationId("cfg-cycle-"+n),n,2,edges(n),null,"cycle-"+n,"cycle-"+n,2*n,GraphSymmetryClassification.HIGHLY_SYMMETRIC,true,0,"TEST",true,GeometryConfigurationStatus.of(d),List.of(d),Instant.EPOCH,Map.of());}
    private static List<DiscoveryConnection> edges(int n){java.util.ArrayList<DiscoveryConnection> e=new java.util.ArrayList<>();for(int i=0;i<n;i++)e.add(new DiscoveryConnection(i,(i+1)%n));return e;}
    private static List<Vector3d> regularPolygon(int n){return java.util.stream.IntStream.range(0,n).mapToObj(i->new Vector3d(Math.cos(2*Math.PI*i/n),Math.sin(2*Math.PI*i/n),0)).toList();}
}
