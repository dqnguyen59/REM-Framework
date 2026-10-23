package remframework.geometry.discovery.research.diamond;
import java.util.*;
import java.nio.file.*;
import org.joml.Vector3d;
/** Larger fixed-boundary domains for the matched-bandwidth wavelength16 experiment. */
public final class DiamondPacketLargeDomainAudit {
    private DiamondPacketLargeDomainAudit() { }
    public static void main(String[] args) throws Exception {
        var report=new StringBuilder("Large-domain actual Node v0.1 packet: research /4,gamma1,k.5,length1,wavelength16,width8,40frames,729 Fourier modes. Fixed incomplete boundary retained.\n");
        var directions=List.of(new Vector3d(1,0,0),new Vector3d(1,1,0).normalize(),new Vector3d(1,1,1).normalize());
        double[] min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},max={0,0};double domainDifference=0;
        for(var direction:directions) {
            DiamondSpectralPacketAudit.Result previous=null;
            for(int cells:new int[]{24,28,32}) {
                // Intermediate24-cell control needed only on the axis;28/32 checked for all three directions.
                if(cells==24 && direction.y!=0) continue;
                var r=DiamondSpectralPacketAudit.run(cells,16,direction,40,8,4);
                report.append(String.format(Locale.ROOT,"Direction%s; cells%d,nodes%d: speed%.9f; mixture%.9f; centroid mismatch%.6f%%; field error%.6f%%; %s%n",direction,cells,8*cells*cells*cells,r.measured().length(),r.mixturePrediction().length(),100*r.measured().distance(r.mixturePrediction())/r.mixturePrediction().length(),100*r.relativeFieldError(),r.status().code()));
                if(previous!=null) {
                    double change=r.measured().distance(previous.measured())/r.mixturePrediction().length();
                    report.append(String.format(Locale.ROOT,"Adjacent-domain vector change%.6f%%%n",100*change));
                    if(cells==32) domainDifference=Math.max(domainDifference,change);
                }
                if(cells>=28) {int index=cells==28?0:1;min[index]=Math.min(min[index],r.measured().length());max[index]=Math.max(max[index],r.measured().length());}
                previous=r;System.out.println("Completed cells"+cells+" direction"+direction);
            }
        }
        report.append(String.format(Locale.ROOT,"Three-direction spread28cells%.6f%%;32cells%.6f%%; max28->32 domain sensitivity%.6f%%%n",100*(max[0]-min[0])/max[0],100*(max[1]-min[1])/max[1],100*domainDifference));
        report.append("No accepted isotropy tolerance assumed. Three directions and finite quadrature do not certify isotropy. Canonical mechanics and normal20-cell viewer/reference limit unchanged.\n");
        Path path=Path.of(args.length==0?"diamond-packet-large-domain-audit.txt":args[0]);Files.writeString(path,report);System.out.print(report);
    }
}
