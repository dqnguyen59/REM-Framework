package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DiamondRotatingOrbitTest {
    @Test void nestedOrbitClosesAtCombinedPeriodAndHasNoPoleRetrace() {
        var orbit=new DiamondRotatingOrbit(64,4);assertEquals(1024,orbit.period());
        for(int t=0;t<orbit.period();t++) {
            var v=orbit.direction(t);assertEquals(1,v.length(),1e-12);
            assertEquals(v,orbit.direction(t+orbit.period()));
        }
        assertTrue(orbit.direction(64).distance(orbit.direction(0))>.1);
        assertTrue(orbit.direction(256).distance(orbit.direction(0))>.1);
        assertTrue(orbit.direction(100).distance(orbit.direction(orbit.period()-100))>.1);
        assertThrows(IllegalArgumentException.class,()->new DiamondRotatingOrbit(8,4));
    }
}
