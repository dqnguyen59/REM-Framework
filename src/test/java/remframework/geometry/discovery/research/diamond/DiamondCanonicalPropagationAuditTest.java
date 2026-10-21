package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DiamondCanonicalPropagationAuditTest {
    @Test void pointPulseTransmitsWithFixedBoundary() {
        var result=DiamondCanonicalPropagationAudit.run(5,10,.5,0,20);
        assertEquals(20,result.frames());assertTrue(result.responding()>4);assertEquals(0,result.boundaryDrift());
        assertEquals(26,result.measurements().size());for(var m:result.measurements()) assertTrue(Double.isFinite(m.peak()));
    }
    @Test void broadProfileHasFiniteDirectionalMeasurements() {
        var result=DiamondCanonicalPropagationAudit.run(6,10,.5,15,20);
        assertEquals(0,result.boundaryDrift());for(var m:result.measurements()) {assertTrue(Double.isFinite(m.arrivalTime()));assertTrue(m.peak()>0);}
    }
    @Test void invalidFramesAndProfileAreRejected() {
        assertThrows(IllegalArgumentException.class,()->DiamondCanonicalPropagationAudit.run(5,10,.5,15,0));
        assertThrows(IllegalArgumentException.class,()->DiamondCanonicalPropagationAudit.run(5,10,.5,Double.NaN,20));
    }
}
