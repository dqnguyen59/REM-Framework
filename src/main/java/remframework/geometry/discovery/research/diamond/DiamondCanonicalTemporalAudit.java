package remframework.geometry.discovery.research.diamond;
import java.util.*;
import java.nio.file.*;
/** Controlled coefficient scaling of the unchanged Node kernel, not an application timestep setting. */
public final class DiamondCanonicalTemporalAudit {
    private DiamondCanonicalTemporalAudit() { }
    public record Plan(double scale,double kernelK,int frames) { }
    public static Plan plan(double k,double scale) {
        if(!Double.isFinite(k)||k<=0||k>1 || (scale!=1 && scale!=.5 && scale!=.25))
            throw new IllegalArgumentException("Supported research scales: 1, .5, .25; k in (0,1]");
        return new Plan(scale,k*scale,(int)(27/scale));
    }
    private static DiamondDirectionalComparison.Result run(int cells,double length,double k,double scale) {
        var p=plan(k,scale);
        var r=DiamondCanonicalPropagationAudit.run(cells,length,p.kernelK(),15,p.frames());
        return new DiamondDirectionalComparison.Result(r.measurements(),r.status());
    }
    public static void main(String[] args) throws Exception {
        var report=new StringBuilder("Actual Node v0.1; research /4; gamma1; width15; radius20; nominal interval27. Coefficient scaling: kernel k=k*h; frames=27/h; beta_h=h^2*beta. This is not an application timestep.\n");
        DiamondDirectionalComparison.Result previous=null;
        for(double h:new double[]{1,.5,.25}) {
            var r=run(16,3.75,1,h);
            report.append(String.format(Locale.ROOT,"Fine h%.2f: frames%d; spread %.6f%%; status %s%n",h,plan(1,h).frames(),100*DiamondLongScaleAudit.spread(r),r.status().code()));
            if(previous!=null) report.append(String.format(Locale.ROOT,"Adjacent-scale peak difference %.6f%%%n",100*DiamondFineDomainAudit.peakDifference(previous,r)));
            previous=r;System.out.println("Completed fine h="+h);
        }
        previous=null;
        for(var resolution:DiamondSpatialRefinementAudit.resolutions()) {
            var r=run(resolution.cells(),resolution.length(),resolution.k(),.25);
            report.append(String.format(Locale.ROOT,"Spatial spacing%.6g at h.25: spread %.6f%%; status %s%n",resolution.length(),100*DiamondLongScaleAudit.spread(r),r.status().code()));
            if(previous!=null) report.append(String.format(Locale.ROOT,"Adjacent-spacing peak difference %.6f%%%n",100*DiamondFineDomainAudit.peakDifference(previous,r)));
            previous=r;System.out.println("Completed spatial spacing "+resolution.length());
        }
        var enlarged=run(20,3.75,1,.25);
        report.append(String.format(Locale.ROOT,"Fine h.25 domain16 ->20: peak difference %.9f%%; spread %.6f%%; status %s%n",100*DiamondFineDomainAudit.peakDifference(previous,enlarged),100*DiamondLongScaleAudit.spread(enlarged),enlarged.status().code()));
        report.append("Coefficient scaling changes finite-step damping as well as temporal resolution. Residual spatial/temporal error remains; REM isotropy and full application compatibility are unproved.\n");
        Path path=Path.of(args.length==0?"diamond-canonical-temporal-audit.txt":args[0]);Files.writeString(path,report);System.out.print(report);
    }
}
