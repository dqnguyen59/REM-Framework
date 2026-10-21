package remframework.geometry.discovery.research.diamond;
import java.util.*;
/** Two-radius travel time of a fully recorded dominant displacement peak; not a dispersion proof. */
public final class DiamondTwoRadiusSpeed {
    public record Measurement(double delay,double speed,String failure) {public boolean valid(){return failure==null;}}
    private DiamondTwoRadiusSpeed() { }
    private record Peak(int index,double time,double value) { }
    public static String rejectionReason(double[] signal) {
        if(signal.length<9)return "Fewer than 9 recorded samples";
        int at=0;for(int i=0;i<signal.length;i++){if(!Double.isFinite(signal[i]))return "Nonfinite signal";if(Math.abs(signal[i])>Math.abs(signal[at]))at=i;}
        double maximum=Math.abs(signal[at]);
        if(maximum<1e-15)return "Signal below numerical floor";
        if(at<4)return "Peak too near recording start (step "+at+")";
        if(at+4>=signal.length)return "Peak at recording cutoff (step "+at+" of "+(signal.length-1)+")";
        if(Math.abs(signal[signal.length-1])>.5*maximum)return "Peak tail unfinished at cutoff (step "+(signal.length-1)+")";
        for(int i=1;i<signal.length-1;i++)if(Math.abs(i-at)>4 && Math.abs(signal[i])>=.8*maximum && Math.abs(signal[i])>Math.abs(signal[i-1]) && Math.abs(signal[i])>=Math.abs(signal[i+1]))return "Multiple comparable peaks";
        double left=Math.abs(signal[at-1]),right=Math.abs(signal[at+1]),fraction=.5*(left-right)/(left-2*maximum+right);
        if(!Double.isFinite(fraction) || Math.abs(fraction)>.5)return "Peak interpolation invalid";
        return null;
    }
    private static Peak peak(double[] signal) {
        int at=0;for(int i=0;i<signal.length;i++){if(!Double.isFinite(signal[i]))return null;if(Math.abs(signal[i])>Math.abs(signal[at]))at=i;}
        // Require both sides of the peak and substantial decay before the causal cutoff.
        if(at<4 || at+4>=signal.length || Math.abs(signal[at])<1e-15 || Math.abs(signal[signal.length-1])>.5*Math.abs(signal[at]))return null;
        for(int i=1;i<signal.length-1;i++) if(Math.abs(i-at)>4 && Math.abs(signal[i])>=.8*Math.abs(signal[at])
            && Math.abs(signal[i])>Math.abs(signal[i-1]) && Math.abs(signal[i])>=Math.abs(signal[i+1]))return null;
        double left=Math.abs(signal[at-1]),middle=Math.abs(signal[at]),right=Math.abs(signal[at+1]);
        double fraction=.5*(left-right)/(left-2*middle+right);
        if(!Double.isFinite(fraction) || Math.abs(fraction)>.5)return null;
        return new Peak(at,at+fraction,signal[at]);
    }
    public static Measurement measure(double[] inner,double[] outer,double separation) {
        if(inner.length!=outer.length || !Double.isFinite(separation) || separation<=0)throw new IllegalArgumentException("Equal recording windows and positive radius separation required");
        String innerReason=rejectionReason(inner),outerReason=rejectionReason(outer);
        if(innerReason!=null || outerReason!=null)return new Measurement(Double.NaN,Double.NaN,(innerReason==null?"":"Inner: "+innerReason)+(innerReason!=null && outerReason!=null?"; ":"")+(outerReason==null?"":"Outer: "+outerReason));
        var a=peak(inner);var b=peak(outer);
        if(a==null || b==null)return new Measurement(Double.NaN,Double.NaN,"Peak incomplete or ambiguous; extend reflection-free observation.");
        if(Math.signum(a.value())!=Math.signum(b.value()))return new Measurement(Double.NaN,Double.NaN,"Peak polarity mismatch.");
        double dot=0,aa=0,bb=0;
        for(int j=-4;j<=4;j++){double x=inner[a.index()+j]/a.value(),y=outer[b.index()+j]/b.value();dot+=x*y;aa+=x*x;bb+=y*y;}
        if(dot/Math.sqrt(aa*bb)<.98)return new Measurement(Double.NaN,Double.NaN,"Peak shape mismatch.");
        double delay=b.time()-a.time();
        if(delay<=0)return new Measurement(Double.NaN,Double.NaN,"No positive outward travel time.");
        return new Measurement(delay,separation/delay,null);
    }
    public static String summary(List<Measurement> measurements) {
        long valid=measurements.stream().filter(Measurement::valid).count();
        if(measurements.size()!=26 || valid!=26)return "Two-radius peaks: "+valid+"/26 valid; see direction diagnostics; isotropy inconclusive.";
        double min=measurements.stream().mapToDouble(Measurement::speed).min().orElseThrow(),max=measurements.stream().mapToDouble(Measurement::speed).max().orElseThrow(),mean=measurements.stream().mapToDouble(Measurement::speed).average().orElseThrow();
        return String.format(Locale.ROOT,"Two-radius peak speed spread %.3f%%; 26/26 matched. Wavelength/sampling convergence untested; isotropy inconclusive.",100*(max-min)/mean);
    }
}
