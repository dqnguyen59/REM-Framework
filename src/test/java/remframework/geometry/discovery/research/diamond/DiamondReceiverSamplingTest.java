package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import org.joml.Vector3d;
import static org.junit.jupiter.api.Assertions.*;
class DiamondReceiverSamplingTest {
    @Test void refinedWeightsReproduceConstantAndAffineFields() {
        var model=DiamondReference.create(7,10);var point=new Vector3d(model.nodes().get(1368).position()).add(3,4,2);
        var r=DiamondReceiverSampling.receiver(model,point,"test",10);assertEquals(1,r.weights().stream().mapToDouble(Double::doubleValue).sum(),1e-12);
        var interpolated=new Vector3d();for(int i=0;i<r.nodes().size();i++) interpolated.add(new Vector3d(model.nodes().get(r.nodes().get(i)).position()).mul(r.weights().get(i)));
        assertEquals(0,interpolated.distance(point),1e-10);
    }
    @Test void outsideReceiverIsRejected() {
        assertThrows(IllegalArgumentException.class,()->DiamondReceiverSampling.receiver(DiamondReference.create(3,10),new Vector3d(-100,0,0),"outside",10));
    }
}
