package remframework.geometry.discovery.research.diamond;
import java.util.*;
import java.nio.file.*;
import org.joml.Vector3d;
import remframework.geometry.discovery.*;
/** Long-wave packet sensitivity using existing finite-domain measurement architecture. */
public final class DiamondLongWavePacketAudit {
    private DiamondLongWavePacketAudit() { }
    private static final List<Vector3d> DIRECTIONS=List.of(new Vector3d(1,0,0),new Vector3d(1,1,0).normalize(),new Vector3d(1,1,1).normalize());
    private static List<DiamondCanonicalPacketAudit.Result> run(int cells,double ratio,int frames) {
        var results=new ArrayList<DiamondCanonicalPacketAudit.Result>();
        for(var d:DIRECTIONS) results.add(DiamondCanonicalPacketAudit.run(cells,4,ratio,d,frames));
        return results;
    }
    private static double spread(List<DiamondCanonicalPacketAudit.Result> results) {
        double min=Double.POSITIVE_INFINITY,max=0;
        for(var r:results) {double speed=r.measured().length();min=Math.min(min,speed);max=Math.max(max,speed);}
        return (max-min)/max;
    }
    private static double difference(List<DiamondCanonicalPacketAudit.Result> a,List<DiamondCanonicalPacketAudit.Result> b) {
        double max=0;for(int i=0;i<a.size();i++) max=Math.max(max,a.get(i).measured().distance(b.get(i).measured())/b.get(i).predicted().length());return max;
    }
    public static void main(String[] args) throws Exception {
        var report=new StringBuilder("Long-wave acceptance scope: mechanical propagation only; exact all-wavelength isotropy is not required. Actual Node v0.1; research /4,gamma1,k.5,length1,width4; three symmetry directions.\n");
        for(double ratio:new double[]{8,16,32}) {
            var small=run(16,ratio,20);var large=run(20,ratio,20);var early=run(20,ratio,10);
            double mismatch=0;for(var r:large) mismatch=Math.max(mismatch,r.measured().distance(r.predicted())/r.predicted().length());
            report.append(String.format(Locale.ROOT,"Wavelength/length%.0f: 20-frame spread%.6f%%; 10-frame spread%.6f%%; domain-vector sensitivity%.6f%%; time-window vector sensitivity%.6f%%; max spectral mismatch%.6f%%; carrier q*width%.6f%n",ratio,100*spread(large),100*spread(early),100*difference(small,large),100*difference(early,large),100*mismatch,2*Math.PI*4/ratio));
            for(int i=0;i<large.size();i++) {var r=large.get(i);report.append(String.format(Locale.ROOT,"  Direction%d: speed%.9f; predicted%.9f; intensity ratio%.6f; boundary drift%.3g; %s%n",i,r.measured().length(),r.predicted().length(),r.intensityRatio(),r.boundaryDrift(),r.status().code()));}
            System.out.println("Completed wavelength/length "+ratio);
        }
        var limit=GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GEOMETRY_REALIZATION,
            GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS,"Long-wave packet validation","Fixed width cannot maintain a narrow relative bandwidth as wavelength grows. Measurements retained; no converged packet-speed or full REM isotropy claim."));
        report.append(limit.code()+": Gaussian spectral width is approximately1/width; relative bandwidth increases as wavelength grows at fixed width. Larger envelopes/domains or full-band mode initialization are needed for a controlled narrowband long-wave test. Three directions do not certify isotropy. Canonical mechanics unchanged.\n");
        Path path=Path.of(args.length==0?"diamond-long-wave-packet-audit.txt":args[0]);Files.writeString(path,report);System.out.print(report);
    }
}
