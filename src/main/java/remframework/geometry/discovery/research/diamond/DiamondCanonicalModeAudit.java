package remframework.geometry.discovery.research.diamond;
import java.util.*;
import java.nio.file.*;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import remframework.geometry.discovery.*;
/** Acoustic plane-mode spectrum of the unchanged discrete Node v0.1 update. */
public final class DiamondCanonicalModeAudit {
    private DiamondCanonicalModeAudit() { }
    public record Mode(double phaseSpeed,double attenuationPerFrame) { }
    public static Mode acoustic(Vector3d q,double length,double k) {
        double omega=DiamondDispersionAnalysis.acoustic(q,length,k).omega();
        double a=omega*omega,det=1-.5*a,radius=Math.sqrt(det);
        double cosine=(2-1.5*a)/(2*radius);
        if(!Double.isFinite(cosine)||Math.abs(cosine)>1) throw new IllegalArgumentException("Nonoscillatory discrete mode");
        double theta=2*Math.asin(Math.sqrt(Math.max(0,(1-cosine)/2)));
        return new Mode(theta/q.length(),radius);
    }
    /** Interior comparison before a boundary disturbance can reach the sampled node. */
    public static double kernelResidual(Vector3d direction,double ratio) {
        var model=DiamondReference.create(5,10);double k=.5,amplitude=.001;
        var adapter=new DiamondCanonicalAdapter(model,k,1,DiamondCanonicalAdapter.Normalization.RESEARCH_FOUR);
        var rotation=new Quaterniond().rotationTo(new Vector3d(1,1,1).normalize(),new Vector3d(1,0,0));
        var center=rotation.transform(new Vector3d(8,8,8).mul(10/Math.sqrt(3)));
        int source=model.nodes().stream().filter(n->n.id()%2==0).min(Comparator.comparingDouble(n->n.position().distanceSquared(center))).orElseThrow().id();
        var origin=model.nodes().get(source).position();var inverse=new Quaterniond(rotation).conjugate();
        var q=new Vector3d(direction).normalize().mul(2*Math.PI/(ratio*10));double real=0,imag=0;
        for(var bond:List.of(new Vector3d(1,1,1),new Vector3d(1,-1,-1),new Vector3d(-1,1,-1),new Vector3d(-1,-1,1))) {
            double phase=q.dot(bond.mul(10/Math.sqrt(3)));real+=Math.cos(phase);imag+=Math.sin(phase);
        }
        double shift=Math.atan2(imag,real);
        for(var n:model.nodes()) if(n.neighborIds().size()==4) {
            var crystal=inverse.transform(new Vector3d(n.position()).sub(origin));
            adapter.displace(n.id(),new Vector3d(amplitude*Math.cos(q.dot(crystal)-(n.id()%2==0?0:shift)),0,0));
        }
        double omega=DiamondDispersionAnalysis.acoustic(q,10,k).omega(),a=omega*omega;
        double previous=amplitude,current=amplitude,maxError=0;
        for(int frame=1;frame<=3;frame++) {
            double next=frame==1?amplitude*(1-1.5*a):(2-1.5*a)*current+(-1+.5*a)*previous;
            adapter.step();maxError=Math.max(maxError,Math.abs(adapter.position(source).x-origin.x-next));
            previous=current;current=next;
        }
        return maxError;
    }
    public static void main(String[] args) throws Exception {
        var report=new StringBuilder("Actual Node v0.1 acoustic mode: research /4, gamma1, k.5. Infinite-lattice discrete spectrum; independent interior kernel cross-check.\nWavelength/length,phase-speed spread percent,attenuation-factor spread percent\n");
        for(double ratio:new double[]{4,8,16,32,64,128}) {
            double min=Double.POSITIVE_INFINITY,max=0,minR=1,maxR=0;
            for(var d:DiamondDispersionAnalysis.sampledDirections(4096)) {
                var mode=acoustic(new Vector3d(d).mul(2*Math.PI/ratio),1,.5);
                min=Math.min(min,mode.phaseSpeed());max=Math.max(max,mode.phaseSpeed());minR=Math.min(minR,mode.attenuationPerFrame());maxR=Math.max(maxR,mode.attenuationPerFrame());
            }
            report.append(String.format(Locale.ROOT,"%.0f,%.9f,%.9f%n",ratio,100*(max-min)/max,100*(maxR-minR)/maxR));
        }
        double error=0;for(var d:DiamondDirectionalComparison.directions()) error=Math.max(error,kernelResidual(d,8));
        var code=error<=1e-10?GeometryConfigurationStatusCode.GEOMETRIC_SYMMETRY_UNKNOWN:GeometryConfigurationStatusCode.NUMERICAL_FAILURE;
        var status=GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.GEOMETRY_REALIZATION,code,"Canonical acoustic modes","Finite-wavelength directional response; isotropy not established."));
        report.append(String.format(Locale.ROOT,"26-direction,3-frame interior kernel residual %.9g; %s%n",error,status.code()));
        report.append("Spectrum: a=k^2*(1-|S|/4); determinant1-a/2; cos(theta)=(2-3a/2)/(2*sqrt(1-a/2)). Long-wave phase speed k*length/sqrt(6). Attenuation is inherent in this discrete update. Receiver interpolation and finite boundaries do not enter spectrum; angular samples do not certify extrema. Full application compatibility and REM isotropy remain unproved.\n");
        Path path=Path.of(args.length==0?"diamond-canonical-mode-audit.txt":args[0]);Files.writeString(path,report);System.out.print(report);
    }
}

