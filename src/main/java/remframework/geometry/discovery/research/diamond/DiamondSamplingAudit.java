package remframework.geometry.discovery.research.diamond;
import java.util.*;
import java.nio.file.*;
public final class DiamondSamplingAudit {
    private DiamondSamplingAudit() { }
    public static void main(String[] args) throws Exception {
        var old=DiamondDirectionalComparison.run(7,.1,false);var refined=DiamondDirectionalComparison.run(7,.1,true);
        var large=DiamondDirectionalComparison.run(8,.1,true);var fine=DiamondDirectionalComparison.run(8,.05,true);
        String report=compare("Interpolation change",old,refined)+compare("Boundary size 7 vs 8",refined,large)+compare("Time step .1 vs .05",large,fine)
            +"Isotropy remains inconclusive; no canonical runtime parity or sampling convergence established.\n";
        Path path=Path.of(args.length==0?"diamond-sampling-audit.txt":args[0]);Files.writeString(path,report);System.out.print(report);
    }
    private static String compare(String name,DiamondDirectionalComparison.Result a,DiamondDirectionalComparison.Result b) {
        double arrival=0,peak=0;int missing=0;
        for(int i=0;i<a.measurements().size();i++) {
            var x=a.measurements().get(i);var y=b.measurements().get(i);
            if(!Double.isFinite(x.arrivalTime()) || !Double.isFinite(y.arrivalTime())) missing++;
            else arrival=Math.max(arrival,Math.abs(x.arrivalTime()-y.arrivalTime()));
            peak=Math.max(peak,Math.abs(x.peak()-y.peak())/Math.max(1e-20,Math.max(x.peak(),y.peak())));
        }
        return String.format(Locale.ROOT,"%s: max arrival difference %.4g; max peak difference %.4g%%; missing arrivals %d%n",name,arrival,100*peak,missing);
    }
}
