package remframework.geometry.discovery.research.diamond;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiamondSphericalSweepTest {
    @Test void trajectoryClosesReversesAndKeepsConstantRadius() {
        var sweep=new DiamondSphericalSweep(512,8,3);
        var torque=new Vector3d();var moments=new Vector3d();
        for(int i=0;i<512;i++) {
            var p=sweep.direction(i);
            assertTrue(p.isFinite());assertEquals(1,p.length(),1e-12);
            assertEquals(p,sweep.direction(i+512));
            assertEquals(p,sweep.direction(512-i));
            torque.add(new Vector3d(p).cross(sweep.direction(i+1)));
            moments.add(p.x*p.x,p.y*p.y,p.z*p.z);
        }
        assertEquals(0,torque.length(),1e-12);
        moments.div(512);
        assertEquals(1.0/3,moments.x,.003);assertEquals(1.0/3,moments.y,.003);assertEquals(1.0/3,moments.z,.003);
        assertEquals(new Vector3d(0,0,1),sweep.direction(0));
        assertEquals(0,new Vector3d(0,0,-1).distance(sweep.direction(256)),1e-12);
    }
    @Test void latitudeAdvancesInEqualAreaBandsAndSamplingIsValidated() {
        var sweep=new DiamondSphericalSweep(128,4,1);
        for(int i=1;i<=64;i++)assertEquals(-4.0/128,sweep.direction(i).z-sweep.direction(i-1).z,1e-12);
        assertThrows(IllegalArgumentException.class,()->new DiamondSphericalSweep(127,3,1));
        assertThrows(IllegalArgumentException.class,()->new DiamondSphericalSweep(64,4,1));
        assertThrows(IllegalArgumentException.class,()->new DiamondSphericalSweep(128,4,0));
    }
}
