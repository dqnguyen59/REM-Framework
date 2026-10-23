package remframework.opengl.model;

import org.joml.Vector3d;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import remframework.opengl.helpers.Neighbor;
import remframework.opengl.model.entity.Node;
import java.time.Duration;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ProbePlacementCompatibilityTest {
    private Node node() {return new Node(null,null,new Vector3d(),new Vector3d(),1);}

    @Test void diamondCyclesAreRejectedBeforeAnyCubicTraversal() {
        Node a=node(),b=node();a.setGeometricDistanceImbalance(true);b.setGeometricDistanceImbalance(true);
        a.setNeighbor(Neighbor.RIGHT,b);b.setNeighbor(Neighbor.RIGHT,a);
        assertTimeoutPreemptively(Duration.ofSeconds(1),()-> {
            var result=ProbePlacement.place(a,10,5,2,5,0,true,new Vector4f(1),Set.of(),"test");
            assertFalse(result.valid());assertTrue(result.status().contains("diamond"));
            assertTrue(ProbePlacement.activeProbeSpace(a,10,0,true,ProbePlacement.DEFAULT_TOLERANCES).contains("diamond"));
        });
    }

    @Test void malformedCubicCycleFailsInsteadOfLoopingForever() {
        Node a=node(),b=node();a.setNeighbor(Neighbor.RIGHT,b);b.setNeighbor(Neighbor.RIGHT,a);
        assertTimeoutPreemptively(Duration.ofSeconds(1),()->
            assertThrows(IllegalArgumentException.class,()->
                ProbePlacement.activeProbeSpace(a,10,0,true,ProbePlacement.DEFAULT_TOLERANCES)));
    }
}
