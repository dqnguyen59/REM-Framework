package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DiamondCanonicalTemporalAuditTest {
    @Test void scalingPreservesIntervalAndScalesCoefficientQuadratically() {
        for(double h:new double[]{1,.5,.25}) {
            var p=DiamondCanonicalTemporalAudit.plan(.75,h);
            assertEquals(27,p.frames()*p.scale());
            assertEquals(.75*.75*h*h,p.kernelK()*p.kernelK(),1e-15);
        }
    }
    @Test void unsupportedScaleAndFrequencyFailExplicitly() {
        assertThrows(IllegalArgumentException.class,()->DiamondCanonicalTemporalAudit.plan(1,.3));
        assertThrows(IllegalArgumentException.class,()->DiamondCanonicalTemporalAudit.plan(Double.NaN,.5));
    }
}
