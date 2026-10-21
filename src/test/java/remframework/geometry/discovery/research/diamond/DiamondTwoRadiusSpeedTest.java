package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DiamondTwoRadiusSpeedTest {
    private double[] pulse(double center,double amplitude){double[] trace=new double[100];for(int t=0;t<trace.length;t++)trace[t]=amplitude*Math.exp(-Math.pow((t-center)/5,2));return trace;}
    @Test void matchedPeakRemovesSourceReleaseTimeAndAmplitudeBias() {
        var measurement=DiamondTwoRadiusSpeed.measure(pulse(30.25,1),pulse(50.25,.3),4);
        assertTrue(measurement.valid(),measurement.failure());assertEquals(20,measurement.delay(),.01);assertEquals(.2,measurement.speed(),.001);
        assertEquals(measurement.speed(),DiamondTwoRadiusSpeed.measure(pulse(40.25,1),pulse(60.25,.3),4).speed(),1e-10);
    }
    @Test void rejectsTruncatedReversedAndPolarityMismatchedPeaks() {
        assertFalse(DiamondTwoRadiusSpeed.measure(pulse(30,1),pulse(98,1),4).valid());
        assertFalse(DiamondTwoRadiusSpeed.measure(pulse(30,1),pulse(20,1),4).valid());
        assertFalse(DiamondTwoRadiusSpeed.measure(pulse(30,1),pulse(50,-1),4).valid());
        assertFalse(DiamondTwoRadiusSpeed.measure(new double[100],new double[100],4).valid());
    }
    @Test void rejectionReasonsDistinguishMissingSignalFromCutoff() {
        assertEquals("Signal below numerical floor",DiamondTwoRadiusSpeed.rejectionReason(new double[100]));
        assertTrue(DiamondTwoRadiusSpeed.rejectionReason(pulse(98,1)).contains("recording cutoff"));
        assertTrue(DiamondTwoRadiusSpeed.measure(pulse(30,1),pulse(98,1),4).failure().startsWith("Outer:"));
        assertNull(DiamondTwoRadiusSpeed.rejectionReason(pulse(30,1)));
    }
}
