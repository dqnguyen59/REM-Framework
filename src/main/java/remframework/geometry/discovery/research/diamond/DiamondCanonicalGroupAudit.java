package remframework.geometry.discovery.research.diamond;
import java.util.*;
import java.nio.file.*;
import org.joml.Vector3d;
import remframework.geometry.discovery.*;
/** Gradient of acoustic discrete frequency; packet prediction, not an energy-transport measurement. */
public final class DiamondCanonicalGroupAudit {
    private DiamondCanonicalGroupAudit() { }
    public static Vector3d groupVelocity(Vector3d q,double length,double k) {
        var continuous=DiamondDispersionAnalysis.acoustic(q,length,k);
        double a=continuous.omega()*continuous.omega();
        if(a<=0 || a>=16.0/9) throw new IllegalArgumentException("Oscillatory acoustic mode required");
        double factor=(1-.375*a)/((1-.5*a)*Math.sqrt(1-.5625*a));
        return new Vector3d(continuous.groupVelocity()).mul(factor);
    }
    public static void main(String[] args) throws Exception {
        var report=new StringBuilder("Discrete Node v0.1 acoustic group velocity: research /4, gamma1, k.5. Wavelength/length,speed spread percent,max ray deviation degrees\n");
        for(double ratio:new double[]{4,8,16,32,64,128}) {
            double min=Double.POSITIVE_INFINITY,max=0,angle=0;
            for(var d:DiamondDispersionAnalysis.sampledDirections(4096)) {
                var velocity=groupVelocity(new Vector3d(d).mul(2*Math.PI/ratio),1,.5);
                double speed=velocity.length();min=Math.min(min,speed);max=Math.max(max,speed);
                angle=Math.max(angle,Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,velocity.dot(d)/speed)))));
            }
            report.append(String.format(Locale.ROOT,"%.0f,%.9f,%.9f%n",ratio,100*(max-min)/max,angle));
        }
        var status=GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.GEOMETRY_REALIZATION,
            GeometryConfigurationStatusCode.GEOMETRIC_SYMMETRY_UNKNOWN,"Discrete acoustic group velocity","Spectral packet prediction only; finite packet transport and full REM isotropy untested."));
        report.append("v_group=grad(theta)=v_continuous*(1-3a/8)/((1-a/2)*sqrt(1-9a/16)); a=omega_continuous^2. Units: length per kernel frame. Long-wave limit k*length/sqrt(6).\n");
        report.append(status.code()+": finite angular samples; attenuation can distort packets, so this is not a measured packet or energy speed. Canonical mechanics unchanged.\n");
        Path path=Path.of(args.length==0?"diamond-canonical-group-audit.txt":args[0]);Files.writeString(path,report);System.out.print(report);
    }
}
