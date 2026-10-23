package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DiamondTroughSpeedTest {
    private double[] pulse(double center,double amplitude) {
        var trace=new double[120];for(int t=0;t<trace.length;t++)trace[t]=-amplitude*Math.exp(-Math.pow((t-center)/5,2));return trace;
    }
    @Test void matchesTroughDespiteLargerLaterPositiveLobe() {
        var inner=pulse(30.25,1);var outer=pulse(50.25,.3);
        for(int t=0;t<120;t++){inner[t]+=2*Math.exp(-Math.pow((t-100)/5.0,2));outer[t]+=.6*Math.exp(-Math.pow((t-115)/5.0,2));}
        var result=DiamondTroughSpeed.measure(inner,outer,4);
        assertTrue(result.valid(),result.failure());assertEquals(20,result.delay(),.01);
        assertFalse(DiamondTwoRadiusSpeed.measure(inner,outer,4).valid());
    }
    @Test void rejectsMissingCutoffAmbiguousAndReversedTroughs() {
        assertFalse(DiamondTroughSpeed.measure(new double[120],pulse(50,1),4).valid());
        assertFalse(DiamondTroughSpeed.measure(pulse(30,1),pulse(118,1),4).valid());
        assertFalse(DiamondTroughSpeed.measure(pulse(50,1),pulse(30,1),4).valid());
        var ambiguous=pulse(30,1);var second=pulse(60,.9);for(int i=0;i<120;i++)ambiguous[i]+=second[i];
        assertFalse(DiamondTroughSpeed.measure(ambiguous,pulse(50,1),4).valid());
        var nonfinite=pulse(30,1);nonfinite[5]=Double.NaN;assertFalse(DiamondTroughSpeed.measure(nonfinite,pulse(50,1),4).valid());
    }
    @Test void toleranceRequiresCompleteMeasurementsAndDoesNotClaimValidation() {
        var equal=new java.util.ArrayList<DiamondTwoRadiusSpeed.Measurement>();
        for(int i=0;i<26;i++)equal.add(new DiamondTwoRadiusSpeed.Measurement(20,.2,null));
        assertTrue(DiamondTroughSpeed.toleranceSummary(equal).contains("validation pending"));
        equal.set(0,new DiamondTwoRadiusSpeed.Measurement(10,.4,null));
        assertTrue(DiamondTroughSpeed.toleranceSummary(equal).contains("not met"));
        equal.set(0,new DiamondTwoRadiusSpeed.Measurement(Double.NaN,Double.NaN,"cutoff"));
        assertTrue(DiamondTroughSpeed.toleranceSummary(equal).contains("inconclusive"));
    }}