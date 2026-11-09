package remframework.opengl.model.entity;

import org.ini4j.Wini;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.research.diamond.DiamondRotatingOrbit;
import static org.junit.jupiter.api.Assertions.*;

class IdentityOrbitTest {
    @Test void equalAreaPresetHasBalancedMomentsCoverageAndRepeat() {
        var identity=new Identity("equal area");identity.setXyzOrbit(true);identity.setEqualAreaOrbit(true);
        identity.getAmplitude().set(1);identity.getPhaseIncrement().set(11.25,.703125,.0439453125);
        var first=identity.sampleOrbitExcitation(0);
        double[] mean=new double[3], square=new double[3], cross=new double[3];int[] bins=new int[128];
        for(int t=1;t<=8192;t++) {
            var v=new Vector3d(identity.generateExcitation());assertEquals(1,v.length(),1e-12);
            assertEquals(0,v.distance(identity.sampleOrbitExcitation(8192)),1e-10);
            double[] a={v.x,v.y,v.z};for(int j=0;j<3;j++){mean[j]+=a[j]/8192;square[j]+=a[j]*a[j]/8192;}
            cross[0]+=v.x*v.y/8192;cross[1]+=v.x*v.z/8192;cross[2]+=v.y*v.z/8192;
            // Pole longitude is undefined: retain the scheduled longitude for bin assignment.
            double longitude=((t* (11.25+.703125))%360)/360;
            bins[Math.min(7,(int)((v.z+1)*4))*16+(int)(longitude*16)]++;
        }
        for(int j=0;j<3;j++){assertEquals(0,mean[j],.00011);assertEquals(1.0/3,square[j],.000002);assertEquals(0,cross[j],1e-10);}
        for(int count:bins)assertTrue(count>=62&&count<=66,"Equal-area occupancy: "+count);
        assertEquals(0,first.distance(identity.sampleOrbitExcitation(0)),1e-12);
        var ini=new Wini();identity.saveToFile(ini,"Identity",0);
        var restored=new Identity("restored");restored.loadFromFile(ini,"Identity",0);
        assertTrue(restored.isEqualAreaOrbit());assertTrue(identity.clone().isEqualAreaOrbit());
        assertFalse(new Identity("legacy").isEqualAreaOrbit());
    }
    @Test void orbitMatchesTrajectoryAndRetainsMagnitudeThroughCompleteCycle() {
        var identity = new Identity("orbit");
        identity.setXyzOrbit(true);
        identity.getAmplitude().set(2);
        identity.getPhaseIncrement().set(360.0/64,360.0/256,360.0/1024);
        var orbit = new DiamondRotatingOrbit(64,4);
        for(int t=1;t<=1024;t++) {
            var force = identity.generateExcitation();
            assertEquals(2,force.length(),1e-12);
            assertEquals(0,force.distance(orbit.direction(t).mul(2)),1e-11);
        }
        identity.reset();assertEquals(new Vector3d(),identity.getPhase());
    }

    @Test void persistsClonesAndKeepsLegacyModeDefault() {
        var identity=new Identity("orbit");identity.setXyzOrbit(true);
        identity.getPhaseIncrement().set(5,1,.25);identity.getAmplitude().set(3);
        var ini=new Wini();identity.saveToFile(ini,"Identity",0);
        var loaded=new Identity("loaded");loaded.loadFromFile(ini,"Identity",0);
        assertTrue(loaded.isXyzOrbit());assertTrue(identity.clone().isXyzOrbit());
        assertEquals(identity.generateExcitation(),loaded.generateExcitation());
        var legacy=new Identity("legacy");assertFalse(legacy.isXyzOrbit());assertNull(legacy.generateExcitation());
    }
}
