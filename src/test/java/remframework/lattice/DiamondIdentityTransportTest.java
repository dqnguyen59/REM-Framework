package remframework.lattice;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.opengl.controller.DiamondIdentityTransport;
import remframework.opengl.helpers.Neighbor;
import remframework.opengl.model.entity.*;
import static org.junit.jupiter.api.Assertions.*;

class DiamondIdentityTransportTest {
    private Occupancy star(Quaterniond rotation,int shift) {
        Node center=node(new Vector3d());center.setGeometricDistanceImbalance(true);
        int[][] directions={{1,1,1},{1,-1,-1},{-1,1,-1},{-1,-1,1}};
        for(int i=0;i<4;i++) {
            var d=directions[i];Node next=node(rotation.transform(new Vector3d(d[0],d[1],d[2]).normalize()));
            next.setGeometricDistanceImbalance(true);next.setIndex(i+1,0,0);
            center.setNeighbor(Neighbor.values()[(i+shift)%6],next);
        }
        return new Occupancy(new Identity("local transport"),center);
    }
    private Node node(Vector3d position) {return new Node(null,null,position,new Vector3d(),1);}

    @Test void uniqueNeighborConsumesOnlyThresholdAlongBondAndPreservesMomentum() {
        Occupancy o=star(new Quaterniond(),0);Node source=o.getNode(),target=source.getNeighbors()[0];
        Vector3d direction=new Vector3d(target.getPosition()).normalize();
        Vector3d before=new Vector3d(direction).mul(2).add(.1,-.1,0);
        o.getPressure().set(before);o.getTransportMomentum().set(.2,.3,.4);
        o.getOccupiedNodes();
        DiamondIdentityTransport.update(o,.5);
        assertSame(target,o.getNode());assertEquals(target.getIndex(),o.getNodeIndex());
        assertTrue(o.getPressure().distance(new Vector3d(before).fma(-.5,direction))<1e-12);
        assertEquals(new Vector3d(.2,.3,.4),o.getTransportMomentum());
        assertTrue(o.getOccupiedNodes().contains(target));
        var m=o.diamondTransportMeasurement();assertEquals(1,m.transfers());assertEquals(1,m.updates());
        assertTrue(m.displacement().distance(direction)<1e-12);assertEquals(1,m.pathLength(),1e-12);
        m.displacement().zero();assertEquals(1,o.diamondTransportMeasurement().displacement().length(),1e-12);
        o.resetDiamondTransportMeasurement();assertEquals(0,o.diamondTransportMeasurement().transfers());
        assertTrue(o.getPressure().distance(new Vector3d(before).fma(-.5,direction))<1e-12);
    }

    @Test void rotationsAndStoragePermutationsRotateHopAndResidualTogether() {
        Vector3d basePressure=new Vector3d(1,1,1).normalize().mul(2).add(.1,-.1,0);
        for(int i=0;i<46;i++) {
            Quaterniond r=new Quaterniond().rotationXYZ(.17*i,.31*i,-.23*i);int shift=i%6;
            Occupancy o=star(r,shift);Node expected=o.getNode().getNeighbors()[shift];
            o.getPressure().set(r.transform(new Vector3d(basePressure)));
            DiamondIdentityTransport.update(o,.5);
            assertSame(expected,o.getNode());
            Vector3d residual=new Vector3d(basePressure).fma(-.5,new Vector3d(1,1,1).normalize());
            assertTrue(o.getPressure().distance(r.transform(residual))<1e-12);
        }
    }

    @Test void thresholdsTiesAndBlockedNeighborsRetainPressureWithoutReverseFallback() {
        Occupancy o=star(new Quaterniond(),0);Node source=o.getNode();
        o.getPressure().set(.1,.1,.1);Vector3d before=new Vector3d(o.getPressure());
        DiamondIdentityTransport.update(o,1);assertSame(source,o.getNode());assertEquals(before,o.getPressure());
        o.getPressure().set(2,0,0);before.set(o.getPressure());
        DiamondIdentityTransport.update(o,.5);assertSame(source,o.getNode());assertEquals(before,o.getPressure());
        assertTrue(o.diamondTransportMeasurement().status().contains("Equal best"));
        source.getNeighbors()[0].setType(EntityType.FIXED);source.getNeighbors()[1].setType(EntityType.WALL);
        DiamondIdentityTransport.update(o,.5);assertSame(source,o.getNode());assertEquals(before,o.getPressure());
        assertEquals(0,o.diamondTransportMeasurement().transfers());
    }

    @Test void oneTransitionPerUpdateUsesOnlyTheCurrentDirectGraph() {
        Occupancy o=star(new Quaterniond(),0);Node source=o.getNode(),target=source.getNeighbors()[0];
        Vector3d direction=new Vector3d(target.getPosition());
        Node further=node(new Vector3d(direction).mul(2));further.setGeometricDistanceImbalance(true);further.setIndex(9,0,0);
        target.setNeighbor(Neighbor.values()[4],further);
        o.getPressure().set(new Vector3d(direction).mul(10));
        DiamondIdentityTransport.update(o,1);assertSame(target,o.getNode());
        DiamondIdentityTransport.update(o,1);assertSame(further,o.getNode());
        assertEquals(2,o.diamondTransportMeasurement().transfers());assertEquals(8,o.getPressure().length(),1e-12);
    }

    @Test void invalidThresholdOrBondDoesNotConsumePressureOrChangeLocation() {
        Occupancy o=star(new Quaterniond(),0);Node source=o.getNode();o.getPressure().set(2,2,2);
        assertThrows(IllegalArgumentException.class,()->DiamondIdentityTransport.update(o,0));
        assertThrows(IllegalArgumentException.class,()->DiamondIdentityTransport.update(o,Double.NaN));
        source.getNeighbors()[0].setPosition(new Vector3d());source.getNeighbors()[0].updatePosition();
        assertThrows(IllegalStateException.class,()->DiamondIdentityTransport.update(o,.5));
        assertSame(source,o.getNode());assertEquals(new Vector3d(2,2,2),o.getPressure());
        assertEquals(0,o.diamondTransportMeasurement().updates());
    }
}
