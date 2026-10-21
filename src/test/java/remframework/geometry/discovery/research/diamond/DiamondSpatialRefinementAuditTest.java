package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DiamondSpatialRefinementAuditTest {
    @Test void refinementPreservesPropagationScaleAndPhysicalExtent() {
        for(var r:DiamondSpatialRefinementAudit.resolutions()) {assertEquals(3.75,r.k()*r.length(),1e-12);assertEquals(60,r.cells()*r.length(),1e-12);assertTrue(r.k()<=1);}
    }
    @Test void invalidObservationDurationIsRejected() {
        assertThrows(IllegalArgumentException.class,()->DiamondDirectionalComparison.run(8,5,1,.1,true,15,Double.NaN));
    }
}
