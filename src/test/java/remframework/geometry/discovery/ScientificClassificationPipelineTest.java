package remframework.geometry.discovery;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.configuration.Configuration;
import remframework.geometry.discovery.configuration.ConfigurationId;
import remframework.geometry.discovery.eligibility.ConditionOutcome;
import remframework.geometry.discovery.eligibility.TopologyEligibilityAnalyzer;
import remframework.geometry.discovery.experiment.AngleConditionMode;
import remframework.geometry.discovery.experiment.ExperimentProfile;
import remframework.geometry.discovery.measurement.regularity.TwoDRegularityCalculator;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScientificClassificationPipelineTest {
    private final TopologyEligibilityAnalyzer analyzer = new TopologyEligibilityAnalyzer();

    @Test void cycleIsEligibleOnlyForTheAreaProfile() {
        Configuration cycle = configuration(4, 2, edges(0,1,1,2,2,3,3,0));
        assertTrue(analyzer.analyze(cycle, ExperimentProfile.CLOSED_2D_AREA).eligible());
        assertFalse(analyzer.analyze(cycle, ExperimentProfile.CLOSED_3D_VOLUME).eligible());
    }

    @Test void disconnectedTwoRegularGraphRetainsExactReasons() {
        var report = analyzer.analyze(6, 2, edges(0,1,1,2,2,0,3,4,4,5,5,3),
                "two-triangles", ExperimentProfile.CLOSED_2D_AREA);
        assertFalse(report.eligible());
        assertTrue(report.failedConditions().stream().anyMatch(c -> c.condition().equals("CONNECTED_GRAPH")));
        assertEquals(2, report.cycleRank());
    }

    @Test void branchRetainsBridgeAndArticulationDiagnostics() {
        var report = analyzer.analyze(4, 2, edges(0,1,1,2,2,0,2,3),
                "branched", ExperimentProfile.CLOSED_2D_AREA);
        assertFalse(report.eligible());
        assertEquals(1, report.bridgeCount());
        assertEquals(1, report.articulationVertexCount());
    }

    @Test void stageOneNeverClaimsCoordinatePlanarityOrArea() {
        var report = analyzer.analyze(configuration(4,2,edges(0,1,1,2,2,3,3,0)),
                ExperimentProfile.CLOSED_2D_AREA);
        assertEquals(ConditionOutcome.NOT_DETERMINABLE_FROM_TOPOLOGY,
                report.conditions().stream().filter(c -> c.condition().equals("GEOMETRIC_PLANARITY")).findFirst().orElseThrow().outcome());
        assertEquals(ConditionOutcome.NOT_DETERMINABLE_FROM_TOPOLOGY,
                report.conditions().stream().filter(c -> c.condition().equals("POSITIVE_AREA")).findFirst().orElseThrow().outcome());
    }

    @Test void squareAndRhombusAreValidButOnlySquareHasZeroAngleVariance() {
        Configuration cycle=configuration(4,2,edges(0,1,1,2,2,3,3,0));
        var calculator=new TwoDRegularityCalculator();
        var square=calculator.calculate(cycle,List.of(new Vector3d(0,0,0),new Vector3d(1,0,0),
                new Vector3d(1,1,0),new Vector3d(0,1,0)),List.of(0,1,2,3),1,1e-9,1e-9,1e-12,1e-9,AngleConditionMode.MEASURE_ONLY);
        double side=Math.sqrt(1.25);
        var rhombus=calculator.calculate(cycle,List.of(new Vector3d(0,0,0),new Vector3d(1,0,0),
                new Vector3d(1.5,1,0),new Vector3d(.5,1,0)),List.of(0,1,2,3),side,.2,1e-9,1e-12,1e-9,AngleConditionMode.MEASURE_ONLY);
        assertTrue(square.valid()); assertEquals(0,square.interiorAngles().variance(),1e-15);
        assertTrue(rhombus.valid()); assertTrue(rhombus.interiorAngles().variance()>0);
    }

    @Test void nonplanarBowTieAndCollinearBoundariesFailIndependentGates() {
        Configuration cycle=configuration(4,2,edges(0,1,1,2,2,3,3,0)); var c=new TwoDRegularityCalculator();
        var nonplanar=c.calculate(cycle,List.of(new Vector3d(0,0,0),new Vector3d(1,0,0),new Vector3d(1,1,.1),new Vector3d(0,1,0)),List.of(0,1,2,3),1,.2,1e-6,1e-9,1e-6,AngleConditionMode.MEASURE_ONLY);
        var bowtie=c.calculate(cycle,List.of(new Vector3d(0,0,0),new Vector3d(1,1,0),new Vector3d(0,1,0),new Vector3d(1,0,0)),List.of(0,1,2,3),1,.5,1e-9,1e-9,1e-6,AngleConditionMode.MEASURE_ONLY);
        var line=c.calculate(cycle,List.of(new Vector3d(0,0,0),new Vector3d(1,0,0),new Vector3d(2,0,0),new Vector3d(3,0,0)),List.of(0,1,2,3),1,3,1e-9,1e-9,1e-6,AngleConditionMode.MEASURE_ONLY);
        assertFalse(nonplanar.planar()); assertFalse(bowtie.simpleBoundary()); assertFalse(line.positiveArea());
    }

    @Test void measureOnlyDoesNotRejectAnglesButExplicitRequirementDoes() {
        Configuration cycle=configuration(4,2,edges(0,1,1,2,2,3,3,0)); List<Vector3d> p=List.of(new Vector3d(0,0,0),new Vector3d(1,0,0),new Vector3d(1.5,1,0),new Vector3d(.5,1,0));
        var c=new TwoDRegularityCalculator();
        assertTrue(c.calculate(cycle,p,List.of(0,1,2,3),1.1,.2,1e-9,1e-9,1e-9,AngleConditionMode.MEASURE_ONLY).valid());
        assertFalse(c.calculate(cycle,p,List.of(0,1,2,3),1.1,.2,1e-9,1e-9,1e-9,AngleConditionMode.REQUIRE_EQUAL_ANGLES).valid());
    }

    private static Configuration configuration(int n,int k,List<DiscoveryConnection> edges){
        var d=GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.GRAPH_GENERATION,GeometryConfigurationStatusCode.GENERATED,"generated","test");
        return new Configuration(new ConfigurationId("cfg-test"),n,k,edges,null,"canonical","iso",1,GraphSymmetryClassification.UNKNOWN,true,0,"TEST",true,GeometryConfigurationStatus.of(d),List.of(d),Instant.EPOCH,Map.of());
    }
    private static List<DiscoveryConnection> edges(int... ids){java.util.ArrayList<DiscoveryConnection> r=new java.util.ArrayList<>();for(int i=0;i<ids.length;i+=2)r.add(new DiscoveryConnection(ids[i],ids[i+1]));return r;}
}
