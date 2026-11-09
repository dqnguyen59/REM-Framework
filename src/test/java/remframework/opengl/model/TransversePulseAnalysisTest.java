package remframework.opengl.model;

import org.junit.jupiter.api.Test;
import org.joml.Vector3d;
import remframework.geometry.discovery.research.diamond.DiamondDirectionalComparison;
import remframework.opengl.model.entity.Identity;
import static org.junit.jupiter.api.Assertions.*;

class TransversePulseAnalysisTest {
    @Test void oneCycleForceEndsAfterEightUpdatesWithoutShorteningTheSixteenUpdateTailCheck() {
        var id=new Identity("short-pulse");id.startDiagnosticPulse(new Vector3d(0,0,1),.01,8,1);
        double[] signal=new double[24];
        for(int t=1;t<=24;t++)signal[t-1]=id.generateExcitation().z;
        assertTrue(java.util.Arrays.stream(signal,0,7).anyMatch(x->Math.abs(x)>1e-6));
        for(int t=7;t<signal.length;t++)assertEquals(0,signal[t],0);
        assertEquals("window_truncated",TransversePulseAnalysis.analyze(java.util.Arrays.copyOf(signal,16),new double[16],1e-10,16).status());
        assertEquals("complete",TransversePulseAnalysis.analyze(signal,new double[24],1e-10,16).status());
    }
    @Test void completePulseRequiresAnEntireQuietIntervalAndCannotUseUnrecordedZeros() {
        double[] signal=new double[65],other=new double[65];signal[20]=1;signal[31]=.3;signal[46]=-.5;
        assertEquals("complete",TransversePulseAnalysis.analyze(java.util.Arrays.copyOf(signal,30),new double[30],1e-8,4).status());
        assertEquals("window_truncated",TransversePulseAnalysis.analyze(java.util.Arrays.copyOf(signal,30),new double[30],1e-8,16).status());
        assertEquals("window_truncated",TransversePulseAnalysis.analyze(java.util.Arrays.copyOf(signal,58),new double[58],1e-8,16).status());
        assertEquals("complete",TransversePulseAnalysis.analyze(signal,other,1e-8,16).status());
        assertEquals("below_floor",TransversePulseAnalysis.analyze(new double[65],other,1e-8,16).status());
        assertThrows(IllegalArgumentException.class,()->TransversePulseAnalysis.analyze(signal,other,1e-8,0));
    }
    @Test void everyPlaneIsPerpendicularAndCoversBothSigns() {
        for(var p:DiamondDirectionalComparison.directions()) {
            var directions=TransversePulseAnalysis.plane(p,16);var sum=new Vector3d();
            for(var n:directions){assertEquals(1,n.length(),1e-12);assertEquals(0,p.dot(n),1e-12);sum.add(n);}
            assertEquals(0,sum.length(),1e-12);
        }
    }
    @Test void recoversFractionalArrivalAndKnownTransitWithoutAmplitudeBias() {
        double[] near=new double[101],far=new double[101],other=new double[101];
        for(int i=0;i<near.length;i++){near[i]=Math.exp(-Math.pow((i-30)/5.0,2));far[i]=.4*Math.exp(-Math.pow((i-37.4)/5.0,2));}
        var a=TransversePulseAnalysis.analyze(near,other,1e-8);var b=TransversePulseAnalysis.analyze(far,other,1e-8);
        assertEquals("complete",a.status());assertEquals(7.4,b.arrival()-a.arrival(),.06);
        var transit=TransversePulseAnalysis.transit(near,far,4);assertEquals(7.4,transit.lag(),.02);assertEquals(4/7.4,transit.speed(),.003);assertTrue(transit.correlation()>.99);
        far[100]=.2;assertEquals("window_truncated",TransversePulseAnalysis.analyze(far,other,1e-8).status());
        assertEquals("below_floor",TransversePulseAnalysis.analyze(new double[101],other,1e-8).status());
    }
    @Test void finiteIdentityPulseUsesRequestedPolarizationAndRestoresOrbit() {
        var id=new Identity("test");id.setXyzOrbit(true);id.getAmplitude().set(2);id.getPhaseIncrement().set(10,1,.1);
        var p=new Vector3d(1,2,3).normalize();id.startDiagnosticPulse(p,.01,8,2);
        for(int t=1;t<=16;t++){var f=new Vector3d(id.generateExcitation());assertEquals(0,f.cross(p,new Vector3d()).length(),1e-12);assertTrue(f.length()<=.01);}
        assertEquals(0,id.generateExcitation().length(),0);id.endDiagnosticPulse();assertEquals(2,id.generateExcitation().length(),1e-12);
    }
}
