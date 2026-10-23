package remframework.geometry.discovery.research.diamond;

import java.util.*;
import java.nio.file.*;
import org.joml.Vector3d;

/** Infinite periodic stated-equation acoustic branch, in crystal coordinates; no finite receivers or integrator. */
public final class DiamondDispersionAnalysis {
    private DiamondDispersionAnalysis() { }
    public record Mode(double omega,double phaseSpeed,Vector3d groupVelocity) { }
    public record Spread(double phase,double group) { }
    public static Mode acoustic(Vector3d wavevector,double length,double k) {
        if(!wavevector.isFinite() || wavevector.length()==0 || !Double.isFinite(length) || length<=0 || !Double.isFinite(k) || k<=0 || k>1)
            throw new IllegalArgumentException("Nonzero finite wavevector, positive length and k in (0,1] required");
        double scale=length/Math.sqrt(3);Vector3d[] bonds={new Vector3d(1,1,1).mul(scale),new Vector3d(1,-1,-1).mul(scale),new Vector3d(-1,1,-1).mul(scale),new Vector3d(-1,-1,1).mul(scale)};
        double sum=0;var derivative=new Vector3d();
        for(int i=0;i<4;i++) for(int j=i+1;j<4;j++) {
            var pair=new Vector3d(bonds[i]).sub(bonds[j]);double phase=wavevector.dot(pair);
            double sine=Math.sin(phase/2);sum+=sine*sine;
            derivative.add(new Vector3d(pair).mul(Math.sin(phase)));
        }
        double modulus=Math.sqrt(Math.max(0,16-4*sum));
        if(modulus<1e-10) throw new IllegalArgumentException("Acoustic/optical degeneracy; group velocity undefined");
        double omega=k*Math.sqrt(sum/(4+modulus));
        var group=derivative.mul(k*k/(8*omega*modulus));
        return new Mode(omega,omega/wavevector.length(),group);
    }
    public static List<Vector3d> sampledDirections(int count) {
        if(count<2) throw new IllegalArgumentException("At least two directions");
        var directions=new ArrayList<Vector3d>(DiamondDirectionalComparison.directions());
        double angle=Math.PI*(3-Math.sqrt(5));
        for(int i=0;i<count;i++) {double z=1-2*(i+.5)/count,r=Math.sqrt(1-z*z),phi=i*angle;directions.add(new Vector3d(r*Math.cos(phi),r*Math.sin(phi),z));}
        return List.copyOf(directions);
    }
    public static Spread spread(double wavelengthOverLength,int samples) {
        double minPhase=Double.POSITIVE_INFINITY,maxPhase=0,minGroup=Double.POSITIVE_INFINITY,maxGroup=0;
        for(var direction:sampledDirections(samples)) {
            var mode=acoustic(new Vector3d(direction).mul(2*Math.PI/wavelengthOverLength),1,1);
            minPhase=Math.min(minPhase,mode.phaseSpeed());maxPhase=Math.max(maxPhase,mode.phaseSpeed());
            double speed=mode.groupVelocity().length();minGroup=Math.min(minGroup,speed);maxGroup=Math.max(maxGroup,speed);
        }
        return new Spread((maxPhase-minPhase)/maxPhase,(maxGroup-minGroup)/maxGroup);
    }
    public static void main(String[] args) throws Exception {
        var report=new StringBuilder("Infinite diamond acoustic dispersion: stated equation only\nWavelength/connection length,phase-speed spread percent,group-speed spread percent\n");
        for(double ratio:List.of(4.0,8.0,16.0,32.0,64.0,128.0)) {
            var result=spread(ratio,4096);
            report.append(String.format(Locale.ROOT,"%.0f,%.9f,%.9f%n",ratio,100*result.phase(),100*result.group()));
        }
        var low=spread(16,1024);var high=spread(16,4096);
        report.append(String.format(Locale.ROOT,"Angular sampling change at ratio16: phase %.9g percentage points; group %.9g percentage points%n",100*Math.abs(low.phase()-high.phase()),100*Math.abs(low.group()-high.group())));
        report.append("Long-wavelength limit: phase and group speed k*length/sqrt(6), independent of direction.\nFinite wavelengths remain direction-dependent; optical branch and canonical runtime parity not validated.\n");
        Path path=Path.of(args.length==0?"diamond-dispersion-analysis.txt":args[0]);Files.writeString(path,report);System.out.print(report);
    }
}
