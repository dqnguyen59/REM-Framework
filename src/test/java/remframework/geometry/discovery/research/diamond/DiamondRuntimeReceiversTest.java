package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import org.joml.Vector3d;
import static org.junit.jupiter.api.Assertions.*;
class DiamondRuntimeReceiversTest {
    @Test void crossingInterpolatesSimulationSteps() {
        assertEquals(4.5,DiamondRuntimeReceivers.crossingTime(5,0,2,1));
        assertEquals(5,DiamondRuntimeReceivers.crossingTime(5,0,1,1));
    }
    @Test void reusedSamplerRetainsEqualRadiusAndLinearFieldAccuracy() {
        var model=DiamondReference.create(12,1);
        var sampler=new DiamondReceiverSampling.Sampler(model.nodes().size(),i->model.nodes().get(i).position(),i->model.nodes().get(i).neighborIds().size()==4,1);
        var center=new Vector3d();for(var n:model.nodes())center.add(n.position());center.div(model.nodes().size());
        for(var direction:DiamondDirectionalComparison.directions()) {
            var target=new Vector3d(direction).mul(5).add(center);var sample=sampler.receiver(target,"test");
            var reconstructed=new Vector3d();double total=0;
            for(int i=0;i<sample.nodes().size();i++){double w=sample.weights().get(i);total+=w;reconstructed.fma(w,model.nodes().get(sample.nodes().get(i)).position());}
            assertEquals(1,total,1e-12);assertTrue(target.distance(reconstructed)<1e-10);assertEquals(5,target.distance(center),1e-12);
        }
    }
}
