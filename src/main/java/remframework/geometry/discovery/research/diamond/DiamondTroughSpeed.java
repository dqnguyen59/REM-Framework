package remframework.geometry.discovery.research.diamond;

/** Separate candidate: dominant negative displacement lobe, ignoring positive lobes.<br><br>
 * Reuses peak completeness, ambiguity, shape and timing checks on the negative component.
 * This feature choice does not establish isotropy or replace the absolute-peak experiment.
 */
public final class DiamondTroughSpeed {
    private DiamondTroughSpeed() { }
    public static DiamondTwoRadiusSpeed.Measurement measure(double[] inner,double[] outer,double separation) {
        return DiamondTwoRadiusSpeed.measure(negativeComponent(inner),negativeComponent(outer),separation);
    }
    private static double[] negativeComponent(double[] signal) {
        double[] result=new double[signal.length];
        for(int i=0;i<signal.length;i++)result[i]=Double.isFinite(signal[i])?Math.max(0,-signal[i]):Double.NaN;
        return result;
    }
    public static String summary(java.util.List<DiamondTwoRadiusSpeed.Measurement> measurements) {
        return "Candidate troughs: "+DiamondTwoRadiusSpeed.summary(measurements).replace("Two-radius peaks: ","").replace("Two-radius peak speed", "speed")+" "+toleranceSummary(measurements);
    }
    /** Provisional numerical target only; sampling and boundary validation are still required. */
    public static String toleranceSummary(java.util.List<DiamondTwoRadiusSpeed.Measurement> measurements) {
        if(measurements.size()!=26 || measurements.stream().anyMatch(m->!m.valid() || !Double.isFinite(m.speed()) || m.speed()<=0))
            return "1% target: inconclusive (complete valid measurements required).";
        var speeds=measurements.stream().mapToDouble(DiamondTwoRadiusSpeed.Measurement::speed).summaryStatistics();
        double spread=100*(speeds.getMax()-speeds.getMin())/speeds.getAverage();
        return "1% target: "+(spread<=1?"numerically met; validation pending.":"not met.");
    }}