package remframework.geometry.discovery.research.diamond;
import java.util.*;
import java.nio.file.*;
public final class DiamondLongScaleAudit {
    private DiamondLongScaleAudit() { }
    public static double spread(DiamondDirectionalComparison.Result r) {
        double min=r.measurements().stream().mapToDouble(DiamondDirectionalComparison.Measurement::peak).min().orElseThrow();
        double max=r.measurements().stream().mapToDouble(DiamondDirectionalComparison.Measurement::peak).max().orElseThrow();
        return (max-min)/max;
    }
    public static void main(String[] args) throws Exception {
        var report=new StringBuilder("Gaussian width; directional peak-change spread; spacing refinement difference\n");
        for(double width:List.of(5.0,10.0,15.0)) {
            var coarse=DiamondDirectionalComparison.run(4,10,.5,.1,true,width);
            var mid=DiamondDirectionalComparison.run(6,20.0/3,.75,.1,true,width);
            var fine=DiamondDirectionalComparison.run(8,5,1,.1,true,width);
            double change=0;
            for(int i=0;i<26;i++) {double a=coarse.measurements().get(i).peak(),b=fine.measurements().get(i).peak();change=Math.max(change,Math.abs(a-b)/Math.max(a,b));}
            double midChange=0;for(int i=0;i<26;i++) {double a=mid.measurements().get(i).peak(),b=fine.measurements().get(i).peak();midChange=Math.max(midChange,Math.abs(a-b)/Math.max(a,b));}
            report.append(String.format(Locale.ROOT,"Mid-to-fine peak difference width %.0f: %.4f%%%n",width,100*midChange));
            report.append(String.format(Locale.ROOT,"Width %.0f: coarse spread %.4f%%; fine spread %.4f%%; max refinement difference %.4f%%%n",width,100*spread(coarse),100*spread(fine),100*change));
        }
        var small=DiamondDirectionalComparison.run(6,10,.5,.1,true,15);
        var large=DiamondDirectionalComparison.run(8,10,.5,.1,true,15);
        double boundary=0;for(int i=0;i<26;i++) {double a=small.measurements().get(i).peak(),b=large.measurements().get(i).peak();boundary=Math.max(boundary,Math.abs(a-b)/Math.max(a,b));}
        report.append(String.format(Locale.ROOT,"Width 15 boundary-size peak difference (6 vs 8 cells): %.4g%%%n",100*boundary));
        var compact=DiamondDirectionalComparison.run(4,10,.5,.1,true,15);double compactDifference=0;
        var refined=DiamondDirectionalComparison.run(8,5,1,.1,true,15);
        var smallerStep=DiamondDirectionalComparison.run(8,5,1,.05,true,15);double timestep=0;
        for(int i=0;i<26;i++) {
            double a=compact.measurements().get(i).peak(),b=large.measurements().get(i).peak();compactDifference=Math.max(compactDifference,Math.abs(a-b)/Math.max(a,b));
            a=refined.measurements().get(i).peak();b=smallerStep.measurements().get(i).peak();timestep=Math.max(timestep,Math.abs(a-b)/Math.max(a,b));
        }
        report.append(String.format(Locale.ROOT,"Width 15 compact-domain peak difference (4 vs 8 cells): %.4g%%%nFine-grid timestep difference (.1 vs .05): %.4g%%%n",100*compactDifference,100*timestep));
        report.append("Exploratory Gaussian profiles; not monochromatic wave-speed tests. Spatial convergence and canonical runtime parity unproved.\n");
        Path output=Path.of(args.length==0?"diamond-long-scale-audit.txt":args[0]);Files.writeString(output,report);System.out.print(report);
    }
}
