package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import org.joml.Vector3d;
import static org.junit.jupiter.api.Assertions.*;
class DiamondCanonicalAdapterTest {
    @Test void viewerSelectsCanonicalKernelAndCanReturnToReference() {
        var viewer=new remframework.geometry.discovery.GeometryDiscoveryVisualizer(DiamondReference.create(3,10),
            remframework.geometry.discovery.GeometryDiscoverySettings.defaults(10),"test");
        viewer.startDiamondCanonicalMotion();viewer.updateFrame();assertTrue(viewer.diamondCanonicalActive());assertEquals(1,viewer.diamondMotionSteps());
        viewer.toggleDiamondMotion();viewer.updateFrame();assertEquals(1,viewer.diamondMotionSteps());
        viewer.showDiamondReference(10);assertFalse(viewer.diamondMotionActive());assertEquals(216,viewer.session().model().nodes().size());
    }
    @Test void realNodeUpdateMatchesDocumentedOneStepCoefficients() {
        var model=DiamondReference.create(3,10);int source=model.nodes().stream().filter(n->n.neighborIds().size()==4).findFirst().orElseThrow().id();
        for(var mode:DiamondCanonicalAdapter.Normalization.values()) {
            var adapter=new DiamondCanonicalAdapter(model,.5,1,mode);var delta=new Vector3d(.001,.002,-.001);adapter.displace(source,delta);
            var before=adapter.position(source);adapter.step();var expected=new Vector3d(delta).mul(-4*.25/mode.divisor);
            assertEquals(0,adapter.velocity(source).distance(expected),1e-12);
            assertEquals(0,adapter.position(source).sub(before).distance(new Vector3d(expected).mul(1.5)),1e-12);
            assertEquals(0,adapter.storedAcceleration(source).distance(new Vector3d(expected).mul(.5)),1e-12);
        }
    }
    @Test void equilibriumAndBoundaryPositionsRemainFixed() {
        var model=DiamondReference.create(3,10);var adapter=new DiamondCanonicalAdapter(model,.5,1,DiamondCanonicalAdapter.Normalization.RESEARCH_FOUR);
        for(int t=0;t<30;t++) adapter.step();
        for(var n:model.nodes()) assertEquals(0,adapter.position(n.id()).distance(n.position()),1e-10);
        int source=model.nodes().stream().filter(n->n.neighborIds().size()==4).findFirst().orElseThrow().id();adapter.displace(source,new Vector3d(.001,0,0));
        for(int t=0;t<30;t++) adapter.step();
        for(var n:model.nodes()) {assertTrue(adapter.position(n.id()).isFinite());if(n.neighborIds().size()<4) assertEquals(n.position(),adapter.position(n.id()));}
    }
    @Test void matchingCoefficientDoesNotMakeVerletEquivalent() {
        var model=DiamondReference.create(3,10);int source=model.nodes().stream().filter(n->n.neighborIds().size()==4).findFirst().orElseThrow().id();
        var adapter=new DiamondCanonicalAdapter(model,.5,1,DiamondCanonicalAdapter.Normalization.RESEARCH_FOUR);var verlet=new DiamondMechanics(model,.5,1);
        adapter.displace(source,new Vector3d(.001,0,0));verlet.displace(source,new Vector3d(.001,0,0));adapter.step();verlet.step();
        assertTrue(adapter.position(source).distance(verlet.position(source))>1e-5);
    }
}
