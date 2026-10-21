package remframework.geometry.discovery.research.diamond;
import java.util.*;
import java.nio.file.*;
/** Matched physical extent and k*spacing, with fixed profile and equal-radius receivers. */
public final class DiamondSpatialRefinementAudit {
    private DiamondSpatialRefinementAudit() { }
    public record Resolution(int cells,double length,double k) { }
    public static List<Resolution> resolutions() { return List.of(new Resolution(8,7.5,.5),new Resolution(12,5,.75),new Resolution(16,3.75,1)); }
    public static void main(String[] args) throws Exception {
        var report=new StringBuilder("Spatial refinement: radius20, width15, dt.1, k*length3.75, duration26.7 (267 steps)\n");
        DiamondDirectionalComparison.Result previous=null,finest=null;
        for(var r:resolutions()) {
            var current=DiamondDirectionalComparison.run(r.cells(),r.length(),r.k(),.1,true,15,26.7);
            report.append(String.format(Locale.ROOT,"Spacing %.6g (%d nodes): directional spread %.6f%%%n",r.length(),8*r.cells()*r.cells()*r.cells(),100*DiamondLongScaleAudit.spread(current)));
            if(previous!=null) report.append(String.format(Locale.ROOT,"Max adjacent-resolution peak difference: %.6f%%%n",100*DiamondFineDomainAudit.peakDifference(previous,current)));
            previous=current;finest=current;System.out.println("Completed spacing "+r.length());
        }
        var enlarged=DiamondDirectionalComparison.run(20,3.75,1,.1,true,15,26.7);
        report.append(String.format(Locale.ROOT,"Fine domain enlargement 16 -> 20 cells: peak difference %.9f%%; directional spread %.6f%%%n",100*DiamondFineDomainAudit.peakDifference(finest,enlarged),100*DiamondLongScaleAudit.spread(enlarged)));
        var timestep=DiamondDirectionalComparison.run(16,3.75,1,.05,true,15,26.7);
        report.append(String.format(Locale.ROOT,"Fine timestep .1 -> .05: peak difference %.6f%%%n",100*DiamondFineDomainAudit.peakDifference(finest,timestep)));
        report.append("GEOMETRIC_SYMMETRY_UNKNOWN: no exact isotropy or canonical runtime parity established.\n");
        Path path=Path.of(args.length==0?"diamond-spatial-refinement-audit.txt":args[0]);Files.writeString(path,report);System.out.print(report);
    }
}
