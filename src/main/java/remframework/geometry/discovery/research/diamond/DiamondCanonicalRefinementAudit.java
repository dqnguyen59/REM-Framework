package remframework.geometry.discovery.research.diamond;
import java.util.*;
import java.nio.file.*;
/** Spatial sensitivity of the actual Node kernel; parameters are isolated research settings. */
public final class DiamondCanonicalRefinementAudit {
    private DiamondCanonicalRefinementAudit() { }
    private static DiamondDirectionalComparison.Result directional(DiamondCanonicalPropagationAudit.Result r) {
        return new DiamondDirectionalComparison.Result(r.measurements(),r.status());
    }
    public static void main(String[] args) throws Exception {
        var report=new StringBuilder("Actual Node v0.1; research /4; gamma1; width15; radius20; 27 kernel frames; k*spacing3.75; cells*spacing60\n");
        DiamondDirectionalComparison.Result previous=null,finest=null;
        for(var r:DiamondSpatialRefinementAudit.resolutions()) {
            var raw=DiamondCanonicalPropagationAudit.run(r.cells(),r.length(),r.k(),15,27);
            var current=directional(raw);
            report.append(String.format(Locale.ROOT,"Spacing %.6g; nodes%d; spread %.6f%%; boundary drift %.3g; status %s%n",r.length(),raw.nodes(),100*DiamondLongScaleAudit.spread(current),raw.boundaryDrift(),raw.status().code()));
            if(previous!=null) report.append(String.format(Locale.ROOT,"Adjacent-resolution peak difference %.6f%%%n",100*DiamondFineDomainAudit.peakDifference(previous,current)));
            previous=current;finest=current;System.out.println("Completed spacing "+r.length());
        }
        var enlargedRaw=DiamondCanonicalPropagationAudit.run(20,3.75,1,15,27);
        var enlarged=directional(enlargedRaw);
        report.append(String.format(Locale.ROOT,"Fine domain 16 -> 20 cells: peak difference %.9f%%; spread %.6f%%; boundary drift %.3g; status %s%n",100*DiamondFineDomainAudit.peakDifference(finest,enlarged),100*DiamondLongScaleAudit.spread(enlarged),enlargedRaw.boundaryDrift(),enlargedRaw.status().code()));
        report.append("Finite-window peak change is not wavefront speed. Changing k also changes the discrete kernel update. Spatial convergence and REM isotropy remain unproved. Canonical cubic mechanics unchanged.\n");
        Path path=Path.of(args.length==0?"diamond-canonical-refinement-audit.txt":args[0]);Files.writeString(path,report);System.out.print(report);
        var csv=new StringBuilder("direction,arrival_frame,peak_change,farthest_stencil_distance\n");
        for(var m:enlarged.measurements()) csv.append(String.format(Locale.ROOT,"%s,%.9g,%.9g,%.9g%n",m.direction(),m.arrivalTime(),m.peak(),m.sampleDistance()));
        Files.writeString(path.resolveSibling("diamond-canonical-refinement-directional.csv"),csv);
    }
}
