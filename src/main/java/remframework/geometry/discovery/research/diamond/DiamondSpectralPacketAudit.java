package remframework.geometry.discovery.research.diamond;
import java.util.*;
import java.nio.file.*;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import remframework.geometry.discovery.*;
/** Finite Fourier mixture with matched acoustic position and velocity for every component. */
public final class DiamondSpectralPacketAudit {
    private DiamondSpectralPacketAudit() { }
    private record Component(Vector3d q,double weight,double shift,double a,double theta,double rho) { }
    public record Result(Vector3d measured,Vector3d mixturePrediction,double relativeFieldError,double intensityRatio,
                         GeometryConfigurationStatus status) { }
    private static List<Component> components(double ratio,Vector3d direction,double width,int halfGrid) {
        var center=new Vector3d(direction).normalize().mul(2*Math.PI/ratio);
        var result=new ArrayList<Component>();double total=0;
        // Fixed normalized Fourier cutoff 2.4; sampling resolution is explicit.
        for(int x=-halfGrid;x<=halfGrid;x++) for(int y=-halfGrid;y<=halfGrid;y++) for(int z=-halfGrid;z<=halfGrid;z++) {
            var offset=new Vector3d(x,y,z).mul(2.4/(width*halfGrid));var q=new Vector3d(center).add(offset);
            double weight=Math.exp(-.5*width*width*offset.lengthSquared());total+=weight;
            double real=0,imag=0;
            for(var bond:List.of(new Vector3d(1,1,1),new Vector3d(1,-1,-1),new Vector3d(-1,1,-1),new Vector3d(-1,-1,1))) {
                double phase=q.dot(bond.mul(1/Math.sqrt(3)));real+=Math.cos(phase);imag+=Math.sin(phase);
            }
            var continuous=DiamondDispersionAnalysis.acoustic(q,1,.5);
            var discrete=DiamondCanonicalModeAudit.acoustic(q,1,.5);
            result.add(new Component(q,weight,Math.atan2(imag,real),continuous.omega()*continuous.omega(),discrete.phaseSpeed()*q.length(),discrete.attenuationPerFrame()));
        }
        var normalized=new ArrayList<Component>();
        for(var c:result) normalized.add(new Component(c.q(),c.weight()/total,c.shift(),c.a(),c.theta(),c.rho()));
        return List.copyOf(normalized);
    }
    public static Result run(int cells,double ratio,Vector3d direction,int frames) {
        return run(cells,ratio,direction,frames,4,4);
    }
    public static Result run(int cells,double ratio,Vector3d direction,int frames,double width,int halfGrid) {
        if(!Double.isFinite(width)||width<=0||width>16||halfGrid<4||halfGrid>6) throw new IllegalArgumentException("Invalid spectral bandwidth or grid");
        if(cells<8||cells>32||!Double.isFinite(ratio)||ratio<8||!direction.isFinite()||direction.length()==0||frames<1||frames>40)
            throw new IllegalArgumentException("Invalid spectral packet limits");
        var components=components(ratio,direction,width,halfGrid);var model=DiamondReference.createResearchDomain(cells,1);
        var cosine=new DiamondCanonicalAdapter(model,.5,1,DiamondCanonicalAdapter.Normalization.RESEARCH_FOUR);
        var sine=new DiamondCanonicalAdapter(model,.5,1,DiamondCanonicalAdapter.Normalization.RESEARCH_FOUR);
        var rotation=new Quaterniond().rotationTo(new Vector3d(1,1,1).normalize(),new Vector3d(1,0,0));
        var inverse=new Quaterniond(rotation).conjugate();var origin=new Vector3d(4*(cells/2),4*(cells/2),4*(cells/2)).mul(1/Math.sqrt(3));
        var coordinates=new ArrayList<Vector3d>();double[] expectedC=new double[model.nodes().size()],expectedS=new double[expectedC.length];
        var initialCenter=new Vector3d();double initialWeight=0;var expectedCenter=new Vector3d();double expectedWeight=0;
        for(var n:model.nodes()) {
            var p=inverse.transform(new Vector3d(n.position())).sub(origin);coordinates.add(p);
            double c=0,s=0,vc=0,vs=0,endC=0,endS=0;
            for(var mode:components) {
                double phase=mode.q().dot(p)-(n.id()%2==0?0:mode.shift());double co=Math.cos(phase),si=Math.sin(phase),w=.001*mode.weight();
                c+=w*co;s+=w*si;
                double imaginary=mode.rho()*Math.sin(mode.theta());
                vc+=w*(.75*mode.a()*co+imaginary*si);vs+=w*(.75*mode.a()*si-imaginary*co);
                double endPhase=phase-frames*mode.theta(),endWeight=w*Math.pow(mode.rho(),frames);
                endC+=endWeight*Math.cos(endPhase);endS+=endWeight*Math.sin(endPhase);
            }
            expectedC[n.id()]=endC;expectedS[n.id()]=endS;
            // Same interior measurement mask for initial, predicted and actual centroids.
            if(n.neighborIds().size()==4) {
                cosine.displace(n.id(),new Vector3d(c,0,0));sine.displace(n.id(),new Vector3d(s,0,0));
                cosine.initializeVelocity(n.id(),new Vector3d(vc,0,0));sine.initializeVelocity(n.id(),new Vector3d(vs,0,0));
                double weight=c*c+s*s;initialWeight+=weight;initialCenter.add(new Vector3d(p).mul(weight));
                double endWeight=endC*endC+endS*endS;expectedWeight+=endWeight;expectedCenter.add(new Vector3d(p).mul(endWeight));
            }
        }
        try {for(int frame=0;frame<frames;frame++) {cosine.step();sine.step();}}
        catch(IllegalStateException failure) {return new Result(new Vector3d(),new Vector3d(),Double.NaN,Double.NaN,
            cosine.status().code()==GeometryConfigurationStatusCode.NUMERICAL_FAILURE?cosine.status():sine.status());}
        var actualCenter=new Vector3d();double actualWeight=0,error=0;
        for(var n:model.nodes()) if(n.neighborIds().size()==4) {
            double c=cosine.position(n.id()).x-n.position().x,s=sine.position(n.id()).x-n.position().x;
            double weight=c*c+s*s;actualWeight+=weight;actualCenter.add(new Vector3d(coordinates.get(n.id())).mul(weight));
            error+=Math.pow(c-expectedC[n.id()],2)+Math.pow(s-expectedS[n.id()],2);
        }
        initialCenter.div(initialWeight);actualCenter.div(actualWeight);expectedCenter.div(expectedWeight);
        var status=GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.GEOMETRY_REALIZATION,
            GeometryConfigurationStatusCode.GEOMETRIC_SYMMETRY_UNKNOWN,"Spectral packet","Finite Fourier quadrature, boundary truncation and bandwidth remain; no isotropy claim."));
        return new Result(actualCenter.sub(initialCenter).div(frames),expectedCenter.sub(initialCenter).div(frames),Math.sqrt(error/expectedWeight),actualWeight/initialWeight,status);
    }
    public static void main(String[] args) throws Exception {
        var report=new StringBuilder("Full-component acoustic initialization; actual Node v0.1; research /4,gamma1,k.5. Finite729-mode Fourier quadrature width4,step.15,offsets -4..4; not an exact Gaussian or narrowband limit. Prediction uses the identical finite mixture, not its central group velocity.\n");
        for(double ratio:new double[]{8,16,32}) {
            var r=run(12,ratio,new Vector3d(1,0,0),10);
            report.append(summary("Axis ratio"+ratio+",cells12,10frames",r));System.out.println("Completed ratio "+ratio);
        }
        for(var d:List.of(new Vector3d(1,1,0).normalize(),new Vector3d(1,1,1).normalize())) {
            report.append(summary("Direction"+d+",ratio16,cells12,10frames",run(12,16,d,10)));System.out.println("Completed direction");
        }
        report.append(summary("Axis ratio16,cells16,10frames",run(16,16,new Vector3d(1,0,0),10)));
        report.append(summary("Axis ratio16,cells16,20frames",run(16,16,new Vector3d(1,0,0),20)));
        report.append("Finite mixture has truncation and Fourier replicas (period2*pi/.15). Initialization is exact for each infinite-lattice mode; boundary nodes remain fixed. Packet-centroid velocity is not energy or wavefront speed. Long-wave full REM isotropy remains unproved; canonical mechanics unchanged.\n");
        Path path=Path.of(args.length==0?"diamond-spectral-packet-audit.txt":args[0]);Files.writeString(path,report);System.out.print(report);
    }
    private static String summary(String name,Result r) {
        return String.format(Locale.ROOT,"%s: measured speed%.9f; mixture speed%.9f; centroid-vector difference%.6f%%; relative field error%.6f%%; intensity ratio%.6f; %s%n",name,r.measured().length(),r.mixturePrediction().length(),100*r.measured().distance(r.mixturePrediction())/r.mixturePrediction().length(),100*r.relativeFieldError(),r.intensityRatio(),r.status().code());
    }
}


