package remframework.geometry.discovery.research.diamond;
import java.util.*;
import java.nio.file.*;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import remframework.geometry.discovery.*;
/** Actual finite packet motion using paired real quadratures; intensity centroid is not energy. */
public final class DiamondCanonicalPacketAudit {
    private DiamondCanonicalPacketAudit() { }
    public record Result(Vector3d measured,Vector3d predicted,double intensityRatio,double boundaryDrift,GeometryConfigurationStatus status) { }
    public static Result run(int cells,double width,double ratio,Vector3d direction,int frames) {
        if(cells<8||cells>20||!Double.isFinite(width)||width<=0||!Double.isFinite(ratio)||ratio<4||!direction.isFinite()||direction.length()==0||frames<1||frames>40)
            throw new IllegalArgumentException("Invalid packet limits");
        var model=DiamondReference.create(cells,1);double k=.5,amplitude=.001;
        var cosine=new DiamondCanonicalAdapter(model,k,1,DiamondCanonicalAdapter.Normalization.RESEARCH_FOUR);
        var sine=new DiamondCanonicalAdapter(model,k,1,DiamondCanonicalAdapter.Normalization.RESEARCH_FOUR);
        var rotation=new Quaterniond().rotationTo(new Vector3d(1,1,1).normalize(),new Vector3d(1,0,0));
        var inverse=new Quaterniond(rotation).conjugate();
        var origin=rotation.transform(new Vector3d(4*(cells/2),4*(cells/2),4*(cells/2)).mul(1/Math.sqrt(3)));
        var q=new Vector3d(direction).normalize().mul(2*Math.PI/ratio);double real=0,imag=0;
        for(var bond:List.of(new Vector3d(1,1,1),new Vector3d(1,-1,-1),new Vector3d(-1,1,-1),new Vector3d(-1,-1,1))) {
            double phase=q.dot(bond.mul(1/Math.sqrt(3)));real+=Math.cos(phase);imag+=Math.sin(phase);
        }
        double shift=Math.atan2(imag,real);double omega=DiamondDispersionAnalysis.acoustic(q,1,k).omega(),a=omega*omega;
        double theta=DiamondCanonicalModeAudit.acoustic(q,1,k).phaseSpeed()*q.length();
        double imaginary=Math.sqrt(1-.5*a)*Math.sin(theta);
        for(var n:model.nodes()) if(n.neighborIds().size()==4) {
            var crystal=inverse.transform(new Vector3d(n.position()).sub(origin));
            double envelope=amplitude*Math.exp(-crystal.lengthSquared()/(2*width*width));
            double phase=q.dot(crystal)-(n.id()%2==0?0:shift),c=Math.cos(phase),s=Math.sin(phase);
            cosine.displace(n.id(),new Vector3d(envelope*c,0,0));sine.displace(n.id(),new Vector3d(envelope*s,0,0));
            cosine.initializeVelocity(n.id(),new Vector3d(envelope*(.75*a*c+imaginary*s),0,0));
            sine.initializeVelocity(n.id(),new Vector3d(envelope*(.75*a*s-imaginary*c),0,0));
        }
        var initial=centroid(model,cosine,sine,inverse,origin);double boundary=0;
        try {
            for(int frame=0;frame<frames;frame++) {cosine.step();sine.step();}
        } catch(IllegalStateException failure) {
            return new Result(new Vector3d(),DiamondCanonicalGroupAudit.groupVelocity(q,1,k),Double.NaN,boundary,
                cosine.status().code()==GeometryConfigurationStatusCode.NUMERICAL_FAILURE?cosine.status():sine.status());
        }
        for(var n:model.nodes()) if(n.neighborIds().size()<4) boundary=Math.max(boundary,Math.max(cosine.position(n.id()).distance(n.position()),sine.position(n.id()).distance(n.position())));
        var end=centroid(model,cosine,sine,inverse,origin);
        var velocity=new Vector3d(end.center()).sub(initial.center()).div(frames);
        var status=GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.GEOMETRY_REALIZATION,
            GeometryConfigurationStatusCode.GEOMETRIC_SYMMETRY_UNKNOWN,"Finite travelling packet","Quadrature displacement-intensity centroid only; bandwidth, attenuation and boundary sensitivity remain."));
        return new Result(velocity,DiamondCanonicalGroupAudit.groupVelocity(q,1,k),end.intensity()/initial.intensity(),boundary,status);
    }
    private record Centroid(Vector3d center,double intensity) { }
    private static Centroid centroid(GeometryDiscoveryModel model,DiamondCanonicalAdapter cosine,DiamondCanonicalAdapter sine,Quaterniond inverse,Vector3d origin) {
        var center=new Vector3d();double total=0;
        for(var n:model.nodes()) {
            double c=cosine.position(n.id()).x-n.position().x,s=sine.position(n.id()).x-n.position().x,weight=c*c+s*s;
            center.add(inverse.transform(new Vector3d(n.position()).sub(origin)).mul(weight));total+=weight;
        }
        if(!Double.isFinite(total)||total<=0) throw new IllegalStateException("Invalid packet intensity");
        return new Centroid(center.div(total),total);
    }
    public static void main(String[] args) throws Exception {
        var report=new StringBuilder("Actual Node v0.1 travelling Gaussian packets; research /4,gamma1,k.5,length1,wavelength8,20 frames. Two real quadratures remove carrier oscillations from displacement-intensity centroid. Not energy or wavefront speed.\n");
        for(var d:List.of(new Vector3d(1,0,0),new Vector3d(1,1,0).normalize(),new Vector3d(1,1,1).normalize())) {
            Result previous=null;
            for(int cells:new int[]{16,20}) {
                var r=run(cells,4,8,d,20);
                report.append(String.format(Locale.ROOT,"Direction %s; cells%d;width4: measured speed%.9f; predicted%.9f; vector mismatch%.6f%%; intensity ratio%.6f; boundary drift%.3g; %s%n",d,cells,r.measured().length(),r.predicted().length(),100*r.measured().distance(r.predicted())/r.predicted().length(),r.intensityRatio(),r.boundaryDrift(),r.status().code()));
                if(previous!=null) report.append(String.format(Locale.ROOT,"Domain speed-vector difference %.6f%%%n",100*r.measured().distance(previous.measured())/r.predicted().length()));
                previous=r;System.out.println("Completed packet cells"+cells);
            }
            var broad=run(20,6,8,d,20);
            report.append(String.format(Locale.ROOT,"Width6,cells20: speed%.9f; vector mismatch%.6f%%; intensity ratio%.6f; width-change vector difference%.6f%%; %s%n",broad.measured().length(),100*broad.measured().distance(broad.predicted())/broad.predicted().length(),broad.intensityRatio(),100*broad.measured().distance(previous.measured())/broad.predicted().length(),broad.status().code()));
        }
        report.append("Carrier-mode velocity initialization multiplied by Gaussian envelope is approximate. Finite bandwidth and frequency-dependent attenuation can shift the centroid. Width6 boundary convergence is untested at the current20-cell limit. Three symmetry directions do not establish isotropy. Canonical mechanics unchanged.\n");
        Path path=Path.of(args.length==0?"diamond-canonical-packet-audit.txt":args[0]);Files.writeString(path,report);System.out.print(report);
    }
}
