package remframework.geometry.discovery.research.diamond;
import java.util.*;
/** Replays the same completed-step receiver signals at declared relative thresholds. */
public final class DiamondThresholdSensitivity {
    public record Comparison(double fraction,int arrivals,int receivers,double speedSpreadPercent) {
        public String label() {return String.format(Locale.ROOT,"%.3g%%",100*fraction);}
        public String value() {return arrivals==receivers ? String.format(Locale.ROOT,"%d/%d; spread %.3f%%",arrivals,receivers,speedSpreadPercent) : arrivals+"/"+receivers+"; inconclusive";}
    }
    private static final double[] FRACTIONS={1e-5,1e-4,1e-3,1e-2};
    private final List<double[]> signals=new ArrayList<>();
    private final int receivers;
    private final double amplitude,radius;
    public DiamondThresholdSensitivity(int receivers,double amplitude,double radius) {
        if(receivers<1 || !Double.isFinite(amplitude) || amplitude<=0 || !Double.isFinite(radius) || radius<=0)
            throw new IllegalArgumentException("Positive receiver count, amplitude and radius required");
        this.receivers=receivers;this.amplitude=amplitude;this.radius=radius;signals.add(new double[receivers]);
    }
    public synchronized void record(int step,double[] signal) {
        if(step!=signals.size() || signal.length!=receivers) throw new IllegalArgumentException("Contiguous completed steps required");
        for(double value:signal) if(!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite receiver signal");
        signals.add(signal.clone());
    }
    public synchronized double[][] recordedSignals() {
        double[][] copy=new double[receivers][signals.size()];
        for(int t=0;t<signals.size();t++)for(int p=0;p<receivers;p++)copy[p][t]=signals.get(t)[p];
        return copy;
    }
    public synchronized List<Comparison> compare() {
        var result=new ArrayList<Comparison>();
        for(double fraction:FRACTIONS) {
            int count=0;double min=Double.POSITIVE_INFINITY,max=0,sum=0,threshold=amplitude*fraction;
            for(int receiver=0;receiver<receivers;receiver++) {
                for(int step=1;step<signals.size();step++) {
                    double before=Math.abs(signals.get(step-1)[receiver]),after=Math.abs(signals.get(step)[receiver]);
                    if(before<threshold && after>=threshold) {
                        double t=DiamondRuntimeReceivers.crossingTime(step,before,after,threshold),speed=radius/t;
                        min=Math.min(min,speed);max=Math.max(max,speed);sum+=speed;count++;break;
                    }
                }
            }
            result.add(new Comparison(fraction,count,receivers,count==receivers?100*(max-min)/(sum/count):Double.NaN));
        }
        return List.copyOf(result);
    }
}
