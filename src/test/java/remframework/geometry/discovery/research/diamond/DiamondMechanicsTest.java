package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import org.joml.Vector3d;
import static org.junit.jupiter.api.Assertions.*;
class DiamondMechanicsTest {
    @Test void viewerSelectsFixedDiamondAndRescalesWithoutChangingTopology() {
        var viewer=new remframework.geometry.discovery.GeometryDiscoveryVisualizer(
            remframework.geometry.discovery.GeometryDiscoveryExamples.create("tetrahedron",10),
            remframework.geometry.discovery.GeometryDiscoverySettings.defaults(10),"test");
        viewer.showDiamondReference(10);var edges=viewer.session().model().connections();
        viewer.showDiamondReference(5);assertEquals(edges,viewer.session().model().connections());
        assertEquals(5,viewer.result().metrics().minimumConnectedNeighborDistance(),1e-10);
        assertEquals(5,viewer.result().metrics().maximumConnectedNeighborDistance(),1e-10);
        assertTrue(viewer.session().model().nodes().stream().allMatch(n->n.fixedPosition()));
    }
    @Test void fixedReferenceHasEqualLengthsAndInteriorTetrahedralDirections() {
        var model=DiamondReference.create(3,10);assertEquals(216,model.nodes().size());assertEquals(333,model.connections().size());
        assertEquals(125,model.nodes().stream().filter(n->n.neighborIds().size()==4).count());
        for(var e:model.connections()) assertEquals(10,model.nodes().get(e.firstNodeId()).position().distance(model.nodes().get(e.secondNodeId()).position()),1e-10);
        for(var n:model.nodes()) {assertTrue(n.fixedPosition());var neighbors=n.neighborIds().stream().toList();
            if(neighbors.size()==4) for(int i=0;i<4;i++) for(int j=i+1;j<4;j++) {
                var a=new Vector3d(model.nodes().get(neighbors.get(i)).position()).sub(n.position()).normalize();
                var b=new Vector3d(model.nodes().get(neighbors.get(j)).position()).sub(n.position()).normalize();
                assertEquals(-1.0/3,a.dot(b),1e-12);
            }
        }
    }
    @Test void equilibriumAndFixedBoundaryRemainUnchanged() {
        var model=DiamondReference.create(3,10);var run=new DiamondMechanics(model,.5,.1);
        for(int t=0;t<100;t++) run.step();
        for(int id=0;id<run.nodeCount();id++) assertEquals(0,run.position(id).distance(model.nodes().get(id).position()),1e-10);
    }
    @Test void displacedNodeUsesFourNeighborNormalizationAndSynchronousVerlet() {
        var model=DiamondReference.create(3,10);var run=new DiamondMechanics(model,.5,.1);
        int id=model.nodes().stream().filter(n->n.neighborIds().size()==4).findFirst().orElseThrow().id();
        var delta=new Vector3d(.001,.002,-.001);run.displace(id,delta);var p=run.position(id);var a=new Vector3d(delta).mul(-.25);
        assertEquals(0,run.acceleration(id).distance(a),1e-12);run.step();
        assertEquals(0,run.position(id).distance(new Vector3d(p).add(new Vector3d(a).mul(.005))),1e-12);
        for(int t=0;t<99;t++) run.step();
        for(int n=0;n<run.nodeCount();n++) {assertTrue(run.position(n).isFinite());if(run.fixed(n)) assertEquals(model.nodes().get(n).position(),run.position(n));}
    }
    @Test void invalidInputsAndBoundaryDisplacementAreRejected() {
        assertThrows(IllegalArgumentException.class,()->DiamondReference.create(0,10));
        assertThrows(IllegalArgumentException.class,()->new DiamondMechanics(DiamondReference.create(2,10),0,.1));
        var run=new DiamondMechanics(DiamondReference.create(2,10),.5,.1);
        assertThrows(IllegalArgumentException.class,()->run.displace(0,new Vector3d(1,0,0)));
    }
}
