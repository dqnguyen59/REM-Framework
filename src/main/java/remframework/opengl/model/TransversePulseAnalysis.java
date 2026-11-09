package remframework.opengl.model;

import org.joml.Vector3d;
import java.util.*;

/** Component-resolved pulse diagnostics. Times are measured in simulation updates. */
public final class TransversePulseAnalysis {
    private TransversePulseAnalysis() { }
    public record Metrics(double arrival,double peak,double peakTime,double rms,double duration,double period,
                          double leakage,String status) { }
    public record Transit(double lag,double correlation,double speed) { }

    public static List<Vector3d> plane(Vector3d polarization,int count) {
        if(count<8||!polarization.isFinite()||Math.abs(polarization.length()-1)>1e-9)
            throw new IllegalArgumentException("Unit polarization and at least eight rays required");
        var seed=Math.abs(polarization.z)<.9?new Vector3d(0,0,1):new Vector3d(0,1,0);
        var u=seed.cross(polarization,new Vector3d()).normalize();
        var v=polarization.cross(u,new Vector3d());
        var result=new ArrayList<Vector3d>();
        for(int i=0;i<count;i++) {double a=2*Math.PI*i/count;result.add(new Vector3d(u).mul(Math.cos(a)).fma(Math.sin(a),v));}
        return List.copyOf(result);
    }

    public static Metrics analyze(double[] signed,double[] otherSquared,double floor) {
        return analyze(signed,otherSquared,floor,4);
    }

    /** Requires a full quiet interval after the pulse peak, not just one zero crossing. */
    public static Metrics analyze(double[] signed,double[] otherSquared,double floor,int quietUpdates) {
        if(quietUpdates<1)throw new IllegalArgumentException("Positive quiet interval required");
        double peak=0,energy=0,other=0;
        for(int i=0;i<signed.length;i++) {
            if(!Double.isFinite(signed[i])||!Double.isFinite(otherSquared[i]))
                return new Metrics(Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,"nonfinite");
            peak=Math.max(peak,Math.abs(signed[i]));energy+=signed[i]*signed[i];other+=otherSquared[i];
        }
        if(peak<floor)return new Metrics(Double.NaN,peak,Double.NaN,Math.sqrt(energy/signed.length),Double.NaN,Double.NaN,Double.NaN,"below_floor");
        double arrival=Double.NaN,first=Double.NaN,last=Double.NaN;
        var crossings=new ArrayList<Double>();
        for(int i=1;i<signed.length;i++) {
            double a=Math.abs(signed[i-1]),b=Math.abs(signed[i]);
            if(Double.isNaN(arrival)&&a<.5*peak&&b>=.5*peak)arrival=i-1+(.5*peak-a)/(b-a);
            if(b>=.1*peak){if(Double.isNaN(first))first=i;last=i;}
            if(signed[i-1]<0&&signed[i]>=0&&Math.max(a,b)>floor)
                crossings.add(i-1-signed[i-1]/(signed[i]-signed[i-1]));
        }
        double period=Double.NaN;
        if(crossings.size()>1){var periods=new ArrayList<Double>();for(int i=1;i<crossings.size();i++)periods.add(crossings.get(i)-crossings.get(i-1));Collections.sort(periods);period=periods.get(periods.size()/2);}
        double tail=0;for(int i=Math.max(0,signed.length-quietUpdates);i<signed.length;i++)tail=Math.max(tail,Math.abs(signed[i]));
        int peakIndex=0;for(int i=1;i<signed.length;i++)if(Math.abs(signed[i])>Math.abs(signed[peakIndex]))peakIndex=i;
        return new Metrics(arrival,peak,peakIndex,Math.sqrt(energy/signed.length),last-first,period,
                Math.sqrt(other/energy),tail>.01*peak?"window_truncated":"complete");
    }

    /** Positive-lag normalized correlation, with a parabolic fractional-update refinement. */
    public static Transit transit(double[] near,double[] far,double distance) {
        int max=near.length/2;double[] c=new double[max+1];int best=0;
        double en=0,ef=0;for(double x:near)en+=x*x;for(double x:far)ef+=x*x;
        if(en==0||ef==0)return new Transit(Double.NaN,Double.NaN,Double.NaN);
        for(int lag=0;lag<=max;lag++){double dot=0;for(int t=0;t+lag<far.length;t++)dot+=near[t]*far[t+lag];c[lag]=dot/Math.sqrt(en*ef);if(c[lag]>c[best])best=lag;}
        double lag=best;
        if(best>0&&best<max){double curvature=c[best-1]-2*c[best]+c[best+1];if(curvature<0)lag+=.5*(c[best-1]-c[best+1])/curvature;}
        return new Transit(lag,c[best],best==0||best==max||c[best]<.8?Double.NaN:distance/lag);
    }
}
