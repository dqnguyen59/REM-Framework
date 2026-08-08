package remframework.measurement.detector;

import org.ini4j.Wini;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.opengl.helpers.Neighbor;
import remframework.opengl.model.entity.*;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExtendedIdentityTest {
    @Test void radiusTraversalIsDeterministicAndUsesGraphDistance() {
        Node center=node(0); Node right=node(1); Node right2=node(2); Node top=node(3); Node outside=node(4);
        link(center,Neighbor.RIGHT,right); link(right,Neighbor.RIGHT,right2);
        link(center,Neighbor.TOP,top);
        link(right2,Neighbor.TOP,outside);
        Occupancy occupancy=new Occupancy(new Identity("source"),center);
        occupancy.setGraphRadius(0);
        assertEquals(List.of(center),occupancy.getOccupiedNodes());
        occupancy.setGraphRadius(1);
        assertEquals(List.of(center,right,top),occupancy.getOccupiedNodes());
        List<Node> first=occupancy.getOccupiedNodes();
        assertSame(first,occupancy.getOccupiedNodes());
        occupancy.setGraphRadius(2);
        assertEquals(List.of(center,right,top,right2),occupancy.getOccupiedNodes());
        assertEquals(4,occupancy.getOccupiedRegionDiagnostics().occupiedNodeCount());
        assertEquals(1,occupancy.getOccupiedRegionDiagnostics().boundaryNodeCount());
        assertEquals(1,occupancy.getOccupiedRegionDiagnostics().boundaryCrossingEdgeCount());
    }

    @Test void centerChangeInvalidatesCachedRegionAndRadiusIsValidated() {
        Node first=node(0); Node second=node(1); link(first,Neighbor.RIGHT,second);
        Occupancy occupancy=new Occupancy(new Identity("source"),first);
        occupancy.setGraphRadius(1);
        assertEquals(2,occupancy.getOccupiedNodes().size());
        occupancy.setNode(second);
        assertEquals(List.of(second,first),occupancy.getOccupiedNodes());
        assertThrows(IllegalArgumentException.class,()->occupancy.setGraphRadius(-1));
    }

    @Test void missingGraphRadiusLoadsAsZeroAndPersistsSelectedRadius() {
        Wini ini=new Wini();
        ini.put("Oscillator0","name","source");
        ini.put("Oscillator0","nodeIndex.x",0); ini.put("Oscillator0","nodeIndex.y",0); ini.put("Oscillator0","nodeIndex.z",0);
        Occupancy loaded=new Occupancy(); loaded.loadFromFile(ini,"Oscillator",0);
        assertEquals(0,loaded.getGraphRadius());
        loaded.setGraphRadius(2); Wini saved=new Wini(); loaded.saveToFile(saved,"Oscillator",0);
        assertEquals(2,saved.get("Oscillator0","graphRadius",Integer.class));
    }

    @Test void oneOscillatorAdvanceAppliesIdenticalExcitationToEveryOccupiedNode() {
        Node center=node(0); Node right=node(1); link(center,Neighbor.RIGHT,right);
        Identity identity=new Identity("source"); identity.getActive().x=true;
        identity.getAmplitude().x=3; identity.getPhaseIncrement().x=90;
        Occupancy occupancy=new Occupancy(identity,center); occupancy.setGraphRadius(1);
        occupancy.updateOscillation();
        assertEquals(new Vector3d(3,0,0),center.getForce());
        assertEquals(center.getForce(),right.getForce());
        assertEquals(90,identity.getPhase().x);
    }

    @Test void radiusZeroMatchesTheSingleNodeSourcePath() {
        Node center=node(0); Identity identity=new Identity("source");
        identity.getActive().x=true; identity.getAmplitude().x=7; identity.getPhaseIncrement().x=30;
        Occupancy occupancy=new Occupancy(identity,center); occupancy.setGraphRadius(0);
        occupancy.updateOscillation();
        assertEquals(Math.sin(Math.toRadians(30))*7,center.getForce().x,1e-12);
        assertEquals(0,center.getForce().y,1e-12);
        assertEquals(0,center.getForce().z,1e-12);
        assertEquals(30,identity.getPhase().x);
    }

    private static Node node(int x){return new Node(null,null,new Vector3d(x,0,0),new Vector3d(),1);}
    private static void link(Node a,Neighbor direction,Node b){a.setNeighbor(direction,b);Neighbor opposite=switch(direction){case RIGHT->Neighbor.LEFT;case LEFT->Neighbor.RIGHT;case TOP->Neighbor.BOTTOM;case BOTTOM->Neighbor.TOP;case FRONT->Neighbor.BACK;case BACK->Neighbor.FRONT;};b.setNeighbor(opposite,a);}
}
