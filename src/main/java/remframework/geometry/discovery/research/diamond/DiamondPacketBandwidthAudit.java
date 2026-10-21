package remframework.geometry.discovery.research.diamond;
import java.util.*;
import java.nio.file.*;
import org.joml.Vector3d;
import remframework.geometry.discovery.*;
/** Matched relative bandwidth and nominal travel interval with explicit domain/sampling controls. */
public final class DiamondPacketBandwidthAudit {
    private DiamondPacketBandwidthAudit() { }
    private static final List<Vector3d> DIRECTIONS=List.of(new Vector3d(1,0,0),new Vector3d(1,1,0).normalize(),new Vector3d(1,1,1).normalize());
    private static double spread(List<DiamondSpectralPacketAudit.Result> results) {
        double min=Double.POSITIVE_INFINITY,max=0;for(var r:results) {min=Math.min(min,r.measured().length());max=Math.max(max,r.measured().length());}return (max-min)/max;
    }
    public static void main(String[] args) throws Exception {
        var report=new StringBuilder("Matched relative bandwidth: width=wavelength/2, carrier q*width=pi; relative spectral scale1/pi (not asymptotically narrow). Fourier cutoff2.4/width. Research /4,gamma1,k.5,length1. Time interval frames/wavelength=2.5. Three symmetry directions.\n");
        for(double ratio:new double[]{8,16}) {
            int frames=(int)(2.5*ratio);double width=ratio/2;
            var small=new ArrayList<DiamondSpectralPacketAudit.Result>();var large=new ArrayList<DiamondSpectralPacketAudit.Result>();
            double boundarySensitivity=0,mixtureMismatch=0,fieldError=0;
            for(var d:DIRECTIONS) {
                var a=DiamondSpectralPacketAudit.run(16,ratio,d,frames,width,4);
                var b=DiamondSpectralPacketAudit.run(20,ratio,d,frames,width,4);small.add(a);large.add(b);
                boundarySensitivity=Math.max(boundarySensitivity,a.measured().distance(b.measured())/b.mixturePrediction().length());
                mixtureMismatch=Math.max(mixtureMismatch,b.measured().distance(b.mixturePrediction())/b.mixturePrediction().length());fieldError=Math.max(fieldError,b.relativeFieldError());
                report.append(String.format(Locale.ROOT,"Ratio%.0f;direction%s;20cells: speed%.9f; mixture%.9f; %s%n",ratio,d,b.measured().length(),b.mixturePrediction().length(),b.status().code()));
                System.out.println("Completed ratio"+ratio+" direction");
            }
            report.append(String.format(Locale.ROOT,"Ratio%.0f,width%.0f,frames%d: spread16cells%.6f%%; spread20cells%.6f%%; max domain sensitivity%.6f%%; max mixture mismatch%.6f%%; max field error%.6f%%%n",ratio,width,frames,100*spread(small),100*spread(large),100*boundarySensitivity,100*mixtureMismatch,100*fieldError));
            var finer=DiamondSpectralPacketAudit.run(20,ratio,new Vector3d(1,0,0),frames,width,6);
            var baseline=large.get(0);
            report.append(String.format(Locale.ROOT,"Axis quadrature9^3 ->13^3: velocity difference%.6f%%; finer mixture mismatch%.6f%%; finer field error%.6f%%%n",100*finer.measured().distance(baseline.measured())/finer.mixturePrediction().length(),100*finer.measured().distance(finer.mixturePrediction())/finer.mixturePrediction().length(),100*finer.relativeFieldError()));
        }
        var limit=GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GEOMETRY_REALIZATION,
            GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS,"Controlled packet bandwidth","Ratio32 would require width16 and80 frames under this matched protocol, outside the40-frame audit limit; larger domains are also needed. Available measurements retained; convergence not established."));
        report.append(limit.code()+": "+"Ratio32 not executed under unmatched settings. No isotropy acceptance tolerance assumed. Canonical mechanics unchanged.\n");
        Path path=Path.of(args.length==0?"diamond-packet-bandwidth-audit.txt":args[0]);Files.writeString(path,report);System.out.print(report);
    }
}
