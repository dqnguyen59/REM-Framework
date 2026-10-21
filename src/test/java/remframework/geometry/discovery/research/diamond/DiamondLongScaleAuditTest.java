package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DiamondLongScaleAuditTest {
    @Test void smoothProfileProducesFiniteReceiversAndSpread() {
        var result=DiamondDirectionalComparison.run(4,10,.5,.1,true,10);
        assertEquals(26,result.measurements().size());
        for(var m:result.measurements()) {assertTrue(Double.isFinite(m.peak()));assertTrue(m.peak()>0);}
        assertTrue(DiamondLongScaleAudit.spread(result)>=0);assertTrue(DiamondLongScaleAudit.spread(result)<=1);
    }
    @Test void invalidWidthsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->DiamondDirectionalComparison.run(4,10,.5,.1,true,Double.NaN));
    }
}
