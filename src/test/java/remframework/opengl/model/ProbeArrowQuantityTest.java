package remframework.opengl.model;

import org.joml.Vector3d;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import remframework.opengl.model.entity.Node;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProbeArrowQuantityTest {
    private Node node(int index,Vector3d velocity) {
        var node=new Node(null,null,new Vector3d(),new Vector3d(),1);
        node.setIndex(index,0,0);node.getVelocity().set(velocity);return node;
    }
    @Test void rawVelocityAndCurrentImbalanceAreDistinctFromCycleSummaryAndNeverMutateInputs() {
        var node=node(0,new Vector3d(1,2,3));
        var probe=new Probe(new ProbeDefinition("raw",node.getIndex(),new Vector4f(1)),node,null);
        probe.getOccupancies().getFirst().saveGradientToHistory(new Vector3d(-3,2,-1));
        var current=ProbeArrowQuantity.CURRENT_IMBALANCE.read(probe,new Vector3d());
        assertEquals(new Vector3d(-3,2,-1),current);
        var velocity=ProbeArrowQuantity.VELOCITY.read(probe,new Vector3d());
        assertEquals(new Vector3d(1,2,3),velocity);
        assertEquals(new Vector3d(),ProbeArrowQuantity.CYCLE_IMBALANCE.read(probe,new Vector3d()));
        current.normalize();velocity.zero();
        assertEquals(new Vector3d(1,2,3),node.getVelocity());
        assertEquals(new Vector3d(-3,2,-1),ProbeArrowQuantity.CURRENT_IMBALANCE.read(probe,new Vector3d()));
    }
    @Test void interpolatedVelocityAndCurrentGradientUseTheSamePhysicalWeights() {
        var a=node(0,new Vector3d(4,0,0));var b=node(1,new Vector3d(0,4,0));
        var sources=List.of(new ProbeDefinition.InterpolationSource(a.getIndex(),.25),new ProbeDefinition.InterpolationSource(b.getIndex(),.75));
        var target=new ProbeDefinition.Target(0,0,"XY",1,new Vector3d(.75,0,0),new Vector3d(1,0,0),1,0,null,0,-1);
        var placement=new ProbeDefinition.Placement(List.of(target),0,"XY");
        var definition=ProbeDefinition.interpolated("weighted",ProbeDetectorType.DIAMOND_INTERPOLATED,new Vector4f(1),placement,sources);
        var probe=new InterpolatedProbe(definition,List.of(a,b),null);
        probe.getOccupancies().get(0).saveGradientToHistory(new Vector3d(0,0,4));
        probe.getOccupancies().get(1).saveGradientToHistory(new Vector3d(0,0,-4));
        assertEquals(new Vector3d(1,3,0),ProbeArrowQuantity.VELOCITY.read(probe,new Vector3d()));
        assertEquals(new Vector3d(0,0,-2),ProbeArrowQuantity.CURRENT_IMBALANCE.read(probe,new Vector3d()));
    }
}
