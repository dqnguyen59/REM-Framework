package remframework.lattice;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.opengl.controller.DiamondOccupancyPressure;
import remframework.opengl.helpers.Neighbor;
import remframework.opengl.model.entity.*;

import static org.junit.jupiter.api.Assertions.*;

class DiamondOccupancyPressureTest {
    private Occupancy occupancy(Quaterniond rotation) {
        Node center=new Node(null,null,new Vector3d(),new Vector3d(),1);
        center.setGeometricDistanceImbalance(true);
        int[][] directions={{1,1,1},{1,-1,-1},{-1,1,-1},{-1,-1,1}};
        for(int i=0;i<4;i++) {
            var d=directions[i];
            Vector3d equilibrium=rotation.transform(new Vector3d(d[0],d[1],d[2]).normalize());
            Node neighbor=new Node(null,null,equilibrium,new Vector3d(),1);
            if(i==0) {neighbor.setPosition(new Vector3d(equilibrium).mul(1.1));neighbor.updatePosition();}
            center.setNeighbor(Neighbor.values()[i],neighbor);
        }
        Identity identity=new Identity("pressure test");identity.getAmplitude().set(.2,0,0);
        return new Occupancy(identity,center);
    }

    @Test void persistentPressureMatchesDiscreteAccumulationAndNeverTransfers() {
        Occupancy o=occupancy(new Quaterniond());
        Node original=o.getNode();
        Vector3d p0=new Vector3d(.3,-.2,.1),m0=new Vector3d(.04,.02,-.01);
        o.getPressure().set(p0);o.getTransportMomentum().set(m0);
        Vector3d gradient=o.getNode().readGradientImbalance().mul(2);
        Vector3d a=new Vector3d(gradient).mul(3*gradient.length()/.2);
        int n=20;
        for(int i=0;i<n;i++)DiamondOccupancyPressure.update(o,2,3,.5);
        assertTrue(o.getAcceleration().distance(a)<1e-12);
        assertTrue(o.getTransportMomentum().distance(new Vector3d(m0).fma(n*.5,a))<1e-12);
        assertTrue(o.getPressure().distance(new Vector3d(p0).fma(n,m0).fma(.5*n*(n+1)/2,a))<1e-11);
        assertSame(original,o.getNode());assertEquals(n,o.getCumulativeSampleCountForAudit());
        assertEquals(0,o.getCompletedCycleCountForAudit());
    }

    @Test void all26DirectionsAndAdditionalRotationsGiveEquivalentPressureHistories() {
        var directions=new java.util.ArrayList<Vector3d>();
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)
            if(x!=0||y!=0||z!=0)directions.add(new Vector3d(x,y,z).normalize());
        for(int i=0;i<20;i++)directions.add(new Vector3d(Math.sin(i+.2),Math.cos(i*.7),Math.sin(i*.3+.5)).normalize());
        for(Vector3d direction:directions) {
            Quaterniond rotation=new Quaterniond().rotationTo(new Vector3d(1,1,1).normalize(),direction);
            Occupancy base=occupancy(new Quaterniond()),rotated=occupancy(rotation);
            base.getPressure().set(.1,.2,-.3);base.getTransportMomentum().set(.02,-.03,.01);
            rotated.getPressure().set(rotation.transform(new Vector3d(base.getPressure())));
            rotated.getTransportMomentum().set(rotation.transform(new Vector3d(base.getTransportMomentum())));
            for(int step=0;step<30;step++) {
                DiamondOccupancyPressure.update(base,2,3,.5);
                DiamondOccupancyPressure.update(rotated,2,3,.5);
                assertTrue(rotated.getPressure().distance(rotation.transform(new Vector3d(base.getPressure())))<1e-10);
                assertTrue(rotated.getAcceleration().distance(rotation.transform(new Vector3d(base.getAcceleration())))<1e-12);
                assertTrue(rotated.getTransportMomentum().distance(rotation.transform(new Vector3d(base.getTransportMomentum())))<1e-11);
            }
        }
    }

    @Test void equilibriumRetainsPressureAndZeroAmplitudeRetainsMomentum() {
        Occupancy o=occupancy(new Quaterniond());
        Node neighbor=o.getNode().getNeighbors()[0];neighbor.setPosition(neighbor.getEquilibriumPosition());neighbor.updatePosition();
        Vector3d initial=new Vector3d(.3,-.2,.1);o.getPressure().set(initial);
        for(int i=0;i<20;i++)DiamondOccupancyPressure.update(o,2,3,.5);
        assertTrue(o.getPressure().distance(initial)<1e-12);
        o.getIdentity().getAmplitude().zero();o.getTransportMomentum().set(.01,.02,.03);
        DiamondOccupancyPressure.update(o,2,3,.5);
        assertEquals(new Vector3d(),o.getAcceleration());
        assertEquals(new Vector3d(.01,.02,.03),o.getTransportMomentum());
        assertTrue(o.getPressure().distance(new Vector3d(initial).add(.01,.02,.03))<1e-12);
    }

    @Test void passiveOccupantsOnlyRecordAndInvalidCandidatesDoNotPartiallyCommit() {
        Occupancy active=occupancy(new Quaterniond());
        Occupant passive=new Occupant() {public boolean canExcite(){return false;}public boolean canTransfer(){return false;}};
        Occupancy o=new Occupancy(passive,active.getNode());
        o.getPressure().set(1,2,3);o.getTransportMomentum().set(4,5,6);
        DiamondOccupancyPressure.update(o,2,3,.5);
        assertEquals(new Vector3d(1,2,3),o.getPressure());assertEquals(new Vector3d(4,5,6),o.getTransportMomentum());
        assertEquals(1,o.getCumulativeSampleCountForAudit());
        active.getPressure().set(1,2,3);
        assertThrows(IllegalArgumentException.class,()->DiamondOccupancyPressure.update(active,-1,3,.5));
        assertThrows(IllegalStateException.class,()->DiamondOccupancyPressure.update(active,Double.MAX_VALUE,Double.MAX_VALUE,.5));
        assertEquals(new Vector3d(1,2,3),active.getPressure());assertEquals(new Vector3d(),active.getTransportMomentum());
        assertEquals(0,active.getCumulativeSampleCountForAudit());
    }
}
