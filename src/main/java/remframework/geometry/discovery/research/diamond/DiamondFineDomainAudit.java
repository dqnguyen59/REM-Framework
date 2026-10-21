package remframework.geometry.discovery.research.diamond;

import java.util.*;
import java.nio.file.*;
import remframework.geometry.discovery.*;

/** Fixed-spacing domain-size sensitivity for the broad-profile experiment. */
public final class DiamondFineDomainAudit {
    private DiamondFineDomainAudit() { }
    public static double peakDifference(DiamondDirectionalComparison.Result a,DiamondDirectionalComparison.Result b) {
        if(a.measurements().size()!=b.measurements().size()) throw new IllegalArgumentException("Receiver count mismatch");
        double result=0;
        for(int i=0;i<a.measurements().size();i++) {
            var x=a.measurements().get(i);var y=b.measurements().get(i);
            if(!x.direction().equals(y.direction())) throw new IllegalArgumentException("Receiver direction mismatch");
            if(!Double.isFinite(x.peak()) || !Double.isFinite(y.peak()) || Math.max(x.peak(),y.peak())<=0)
                throw new IllegalStateException("Invalid peak measurement");
            result=Math.max(result,Math.abs(x.peak()-y.peak())/Math.max(x.peak(),y.peak()));
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        StringBuilder report=new StringBuilder("Fine-domain audit: spacing 5; k=1; dt=.1; width 15; radius 20; duration 20\n");
        DiamondDirectionalComparison.Result previous=null;int previousCells=0;
        for(int cells:List.of(8,10,12,16)) {
            var current=DiamondDirectionalComparison.run(cells,5,1,.1,true,15);
            double spread=DiamondLongScaleAudit.spread(current);
            report.append(String.format(Locale.ROOT,"%d cells (%d nodes): directional peak spread %.6f%%%n",cells,8*cells*cells*cells,100*spread));
            if(previous!=null) report.append(String.format(Locale.ROOT,"Domain change %d -> %d: max peak difference %.9f%%%n",previousCells,cells,100*peakDifference(previous,current)));
            previous=current;previousCells=cells;
            System.out.println("Completed "+cells+" cells");
        }
        report.append("Result: GEOMETRIC_SYMMETRY_UNKNOWN. Domain sensitivity only; isotropy, spatial convergence and canonical runtime parity remain unproved.\n");
        Path output=Path.of(args.length==0?"diamond-fine-domain-audit.txt":args[0]);Files.writeString(output,report);System.out.print(report);
    }
}
