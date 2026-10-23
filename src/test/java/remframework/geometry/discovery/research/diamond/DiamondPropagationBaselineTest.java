package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import org.joml.Vector3d;
import static org.junit.jupiter.api.Assertions.*;
class DiamondPropagationBaselineTest {
    @Test void viewerRunsAndReturnsToFixedReference() {
        var viewer=new remframework.geometry.discovery.GeometryDiscoveryVisualizer(DiamondReference.create(3,10),
            remframework.geometry.discovery.GeometryDiscoverySettings.defaults(10),"test");
        viewer.startDiamondMotion();viewer.updateFrame();assertEquals(1,viewer.diamondMotionSteps());
        assertEquals(1000,viewer.session().model().nodes().size());
        viewer.toggleDiamondMotion();viewer.updateFrame();assertEquals(1,viewer.diamondMotionSteps());
        viewer.showDiamondReference(10);assertFalse(viewer.diamondMotionActive());assertEquals(216,viewer.session().model().nodes().size());
    }
    @Test void firstPositionUpdateReachesOnlyImmediateNeighbors() {
        var model=DiamondReference.create(3,10);var run=new DiamondMechanics(model,.5,.1);
        int source=model.nodes().stream().filter(n->n.neighborIds().size()==4 && n.neighborIds().stream().allMatch(id->model.nodes().get(id).neighborIds().size()==4)).findFirst().orElseThrow().id();
        run.displace(source,new Vector3d(.001,0,0));run.step();int responses=0;
        for(int id=0;id<run.nodeCount();id++) if(id!=source) {
            double displacement=run.position(id).distance(model.nodes().get(id).position());
            if(model.nodes().get(source).neighborIds().contains(id) && !run.fixed(id)) {assertTrue(displacement>1e-8);responses++;}
            else assertEquals(0,displacement,1e-12);
        }
        assertTrue(responses>0);
    }
    @Test void baselineTransmitsWithoutBoundaryMotion() {
        var result=DiamondPropagationBaseline.run(200,.001);
        assertEquals(200,result.updates());assertTrue(result.respondingNodes()>4);
        assertEquals(0,result.maximumBoundaryDrift());assertTrue(Double.isFinite(result.maximumDisplacement()));
        assertTrue(result.maximumDisplacement()<.01);
    }
}
