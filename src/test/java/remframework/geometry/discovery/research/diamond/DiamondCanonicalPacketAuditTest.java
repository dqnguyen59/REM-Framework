package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import org.joml.Vector3d;
import static org.junit.jupiter.api.Assertions.*;
class DiamondCanonicalPacketAuditTest {
    @Test void packetTravelsForwardWithFixedBoundary() {
        var r=DiamondCanonicalPacketAudit.run(8,2,4,new Vector3d(1,0,0),8);
        assertTrue(r.measured().isFinite());assertTrue(r.measured().x>0);
        assertEquals(0,r.boundaryDrift());assertTrue(r.intensityRatio()>0);
    }
    @Test void initializationCopiesVelocityAndRejectsFixedAndNonfiniteValues() {
        var model=DiamondReference.create(3,1);var adapter=new DiamondCanonicalAdapter(model,.5,1,DiamondCanonicalAdapter.Normalization.RESEARCH_FOUR);
        int id=model.nodes().stream().filter(n->n.neighborIds().size()==4).findFirst().orElseThrow().id();
        var value=new Vector3d(.1,0,0);adapter.initializeVelocity(id,value);value.zero();assertEquals(.1,adapter.velocity(id).x);
        assertThrows(IllegalArgumentException.class,()->adapter.initializeVelocity(id,new Vector3d(Double.NaN,0,0)));
        int fixed=model.nodes().stream().filter(n->n.neighborIds().size()<4).findFirst().orElseThrow().id();
        assertThrows(IllegalArgumentException.class,()->adapter.initializeVelocity(fixed,new Vector3d()));
    }
    @Test void zeroDirectionFailsExplicitly() {
        assertThrows(IllegalArgumentException.class,()->DiamondCanonicalPacketAudit.run(8,2,4,new Vector3d(),8));
    }
}
