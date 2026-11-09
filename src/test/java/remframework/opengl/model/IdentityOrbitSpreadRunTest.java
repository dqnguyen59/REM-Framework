package remframework.opengl.model;

import org.junit.jupiter.api.Test;
import remframework.opengl.model.entity.Identity;
import static org.junit.jupiter.api.Assertions.*;

class IdentityOrbitSpreadRunTest {
    @Test void derivesCombinedPeriodFromActualSavedIdentityAndRejectsNonperiodicConfiguration() {
        var source=new Identity("saved source");source.setXyzOrbit(true);
        source.getPhaseIncrement().set(11.25,.703125,.0439453125);
        assertEquals(8192,IdentityOrbitSpreadRun.periodFor(source));
        source.getPhaseIncrement().set(360.0/32,360.0/48,360.0/80);
        assertEquals(480,IdentityOrbitSpreadRun.periodFor(source));
        source.getPhaseIncrement().z=0;assertThrows(IllegalArgumentException.class,()->IdentityOrbitSpreadRun.periodFor(source));
        source.getPhaseIncrement().z=Math.sqrt(2);assertThrows(IllegalArgumentException.class,()->IdentityOrbitSpreadRun.periodFor(source));
    }
}
