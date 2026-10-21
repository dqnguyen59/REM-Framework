package remframework.geometry.discovery.research.diamond;
import java.util.*;
import java.nio.file.*;
import org.joml.Vector3d;
import remframework.geometry.discovery.*;
/** Expanded finite angular sampling of the controlled actual-adapter packet. */
public final class DiamondPacketAngularAudit {
    private DiamondPacketAngularAudit() { }
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args.length==0?"diamond-packet-angular-audit.txt":args[0]);
        Path csvPath=path.resolveSibling("diamond-packet-angular-directions.csv");
        var directions=DiamondDispersionAnalysis.sampledDirections(8);
        var report=new StringBuilder("Actual Node v0.1 packet: research /4,gamma1,k.5,length1,wavelength16,width8,40frames,32cells(262144nodes),729 Fourier modes.26 crystal+8 general directions.\n");
        var csv=new StringBuilder("index,qx,qy,qz,vx,vy,vz,speed,predicted_speed,vector_mismatch_percent,field_error_percent,status\n");
        double min=Double.POSITIVE_INFINITY,max=0,maxMismatch=0,maxField=0,maxRayAngle=0;int failures=0,index=0;
        for(var direction:directions) {
            try {
                var r=DiamondSpectralPacketAudit.run(32,16,direction,40,8,4);
                double speed=r.measured().length(),prediction=r.mixturePrediction().length();
                if(!Double.isFinite(speed)||speed<=0||!Double.isFinite(prediction)||prediction<=0||!Double.isFinite(r.relativeFieldError()))
                    throw new IllegalStateException("Invalid finite packet measurement; adapter status "+r.status().code());
                min=Math.min(min,speed);max=Math.max(max,speed);
                double mismatch=r.measured().distance(r.mixturePrediction())/prediction;
                maxMismatch=Math.max(maxMismatch,mismatch);maxField=Math.max(maxField,r.relativeFieldError());
                double angle=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,r.measured().dot(direction)/speed))));maxRayAngle=Math.max(maxRayAngle,angle);
                csv.append(String.format(Locale.ROOT,"%d,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.9f,%.9f,%s%n",index,direction.x,direction.y,direction.z,r.measured().x,r.measured().y,r.measured().z,speed,prediction,100*mismatch,100*r.relativeFieldError(),r.status().code()));
                report.append(String.format(Locale.ROOT,"Direction%d: speed%.9f; mixture mismatch%.6f%%; ray angle%.6fdeg; %s%n",index,speed,100*mismatch,angle,r.status().code()));
            } catch(RuntimeException failure) {
                failures++;
                var status=GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GEOMETRY_REALIZATION,
                    GeometryConfigurationStatusCode.NUMERICAL_FAILURE,"Angular packet measurement",failure.getClass().getSimpleName()+": "+failure.getMessage()));
                csv.append(String.format(Locale.ROOT,"%d,%.12g,%.12g,%.12g,NaN,NaN,NaN,NaN,NaN,NaN,NaN,%s%n",index,direction.x,direction.y,direction.z,status.code()));
                report.append("Direction"+index+": "+status+"\n");
            }
            index++;Files.writeString(path,report);Files.writeString(csvPath,csv);
            System.out.println("Completed angular direction "+index+"/"+directions.size());
        }
        var finalCode=failures==0?GeometryConfigurationStatusCode.GEOMETRIC_SYMMETRY_UNKNOWN:GeometryConfigurationStatusCode.NUMERICAL_FAILURE;
        report.append(String.format(Locale.ROOT,"Completed%d/%d; failures%d; sampled speed min%.9f,max%.9f; sampled spread%.6f%%; max mixture mismatch%.6f%%; max field error%.6f%%; max ray angle%.6fdeg; %s%n",index-failures,index,failures,min,max,100*(max-min)/max,100*maxMismatch,100*maxField,maxRayAngle,finalCode));
        report.append("No global extrema or isotropy tolerance certified. Prior boundary convergence covered three directions; additional directions have not each had a domain enlargement control. Finite bandwidth and quadrature remain. Canonical mechanics unchanged.\n");
        Files.writeString(path,report);System.out.print(report);
    }
}
