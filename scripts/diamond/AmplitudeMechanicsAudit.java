import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Quaterniond;
import remframework.lattice.*;
import remframework.opengl.model.*;
import remframework.geometry.discovery.research.diamond.*;

/** Offline diagnostic only: uses the existing production compact integrator and sampler. */
public class AmplitudeMechanicsAudit {
    record Probe(int orientation,int shell,int ray,double radius,Vector3d direction,DiamondDirectionalComparison.Receiver stencil) {}
    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        Path run=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
        Properties config=new Properties();try(var r=Files.newBufferedReader(run.resolve("configuration.properties"))){config.load(r);}
        int side=Integer.parseInt(config.getProperty("dimensionX")),steps=Integer.parseInt(config.getProperty("recordingUpdates"));
        int period=Integer.parseInt(config.getProperty("periodUpdates")),cycles=Integer.parseInt(config.getProperty("pulseCycles"));
        double amplitude=Double.parseDouble(config.getProperty("amplitude")),k=Double.parseDouble(config.getProperty("k")),gamma=Double.parseDouble(config.getProperty("gamma"));
        var topology=new DiamondTopology(side,side,side);
        int source=topology.idAtIndex(new Vector3i(Integer.parseInt(config.getProperty("sourceIndexX")),Integer.parseInt(config.getProperty("sourceIndexY")),Integer.parseInt(config.getProperty("sourceIndexZ"))));
        var origin=topology.position(source,new Vector3d());var minimum=topology.position(0,new Vector3d());
        var rotation=new Quaterniond().rotationTo(new Vector3d(1,1,1).normalize(),new Vector3d(1,0,0));
        var mechanics=new CompactDiamondMechanics(side,side,side);var sampler=mechanics.sampler(false);
        var probes=new ArrayList<Probe>();var nodeIds=new TreeSet<Integer>();
        var directions=DiamondDirectionalComparison.directions();
        for(int orientation=0;orientation<26;orientation++) {
            var plane=TransversePulseAnalysis.plane(directions.get(orientation),16);
            for(int shell=0;shell<3;shell++)for(int ray=0;ray<16;ray++){
                double radius=40+shell;var direction=plane.get(ray);
                var stencil=sampler.receiver(rotation.transform(new Vector3d(direction).mul(radius).add(origin).sub(minimum)),"audit");
                probes.add(new Probe(orientation,shell,ray,radius,direction,stencil));nodeIds.addAll(stencil.nodes());
            }
        }
        var traces=new HashMap<Integer,double[]>();var rest=new HashMap<Integer,Vector3d>();
        for(int id:nodeIds){traces.put(id,new double[steps+1]);rest.put(id,topology.position(id,new Vector3d()));}
        var bandIds=new ArrayList<Integer>();var bandRadius=new ArrayList<Double>();var bandPeak=new ArrayList<Double>();var bandEquilibriumZ=new ArrayList<Double>();
        var point=new Vector3d();
        for(int id=0;id<topology.size();id++){
            var position=topology.position(id,point);double r=position.distance(origin);
            if(!mechanics.isFixed(id)&&r>=39&&r<=45){bandIds.add(id);bandRadius.add(r);bandPeak.add(0.);bandEquilibriumZ.add(position.z);}
        }
        System.out.printf("Sampling %d receivers, %d stencil nodes, %d raw equal-radius audit nodes%n",probes.size(),nodeIds.size(),bandIds.size());
        long begin=System.nanoTime();
        for(int update=1;update<=steps;update++){
            double duration=period*cycles,envelope=update<duration?Math.pow(Math.sin(Math.PI*update/duration),2):0;
            mechanics.setForce(source,new Vector3d(0,0,amplitude*envelope*Math.sin(2*Math.PI*update/period)));
            mechanics.step(k*k/4,gamma);
            for(int id:nodeIds)traces.get(id)[update]=mechanics.position(id,point).z-rest.get(id).z;
            for(int j=0;j<bandIds.size();j++)bandPeak.set(j,Math.max(bandPeak.get(j),Math.abs(mechanics.position(bandIds.get(j),point).z-bandEquilibriumZ.get(j))));
            if(update%25==0)System.out.printf("%d/%d updates, %.1f seconds%n",update,steps,(System.nanoTime()-begin)/1e9);
        }
        try(var writer=Files.newBufferedWriter(out.resolve("raw-node-peaks.csv"))){
            writer.write("node_id,basis,sublattice,radius,radius_squared_integer,dx,dy,dz,peak\n");
            for(int j=0;j<bandIds.size();j++){
                int id=bandIds.get(j);var relative=topology.position(id,new Vector3d()).sub(origin);double r=bandRadius.get(j);
                writer.write(String.format("%d,%d,%d,%.17g,%d,%.17g,%.17g,%.17g,%.17g%n",id,id%8,id%2,r,Math.round(3*r*r),relative.x,relative.y,relative.z,bandPeak.get(j)));
            }
        }
        try(var writer=Files.newBufferedWriter(out.resolve("stencil-node-traces.csv"))){
            writer.write("node_id,update,displacement\n");
            for(int id:nodeIds)for(int t=0;t<=steps;t++)writer.write(String.format("%d,%d,%.17g%n",id,t,traces.get(id)[t]));
        }
        try(var geometry=Files.newBufferedWriter(out.resolve("probe-stencils.csv"));var writer=Files.newBufferedWriter(out.resolve("replayed-probes.csv"));var signalWriter=Files.newBufferedWriter(out.resolve("replayed-traces.csv"))){
            geometry.write("orientation,shell,ray,radius,nx,ny,nz,node_id,weight,node_radius,dx,dy,dz,sublattice\n");
            writer.write("orientation,shell,ray,radius,arrival,peak,peak_update,rms,energy,weighted_node_peaks,coherence_ratio,sublattice_a_peak,sublattice_b_peak,status\n");
            signalWriter.write("orientation,shell,ray,update,signal,sublattice_a,sublattice_b\n");
            for(var probe:probes){
                double[] signal=new double[steps+1],a=new double[steps+1],b=new double[steps+1];double sumNodePeaks=0;
                for(int j=0;j<probe.stencil.nodes().size();j++){
                    int id=probe.stencil.nodes().get(j);double weight=probe.stencil.weights().get(j);var position=rest.get(id);var delta=new Vector3d(position).sub(origin);
                    geometry.write(String.format("%d,%d,%d,%.17g,%.17g,%.17g,%.17g,%d,%.17g,%.17g,%.17g,%.17g,%.17g,%d%n",probe.orientation,probe.shell,probe.ray,probe.radius,probe.direction.x,probe.direction.y,probe.direction.z,id,weight,delta.length(),delta.x,delta.y,delta.z,id%2));
                    var trace=traces.get(id);double rawPeak=0;
                    for(int t=0;t<=steps;t++){rawPeak=Math.max(rawPeak,Math.abs(trace[t]));signal[t]+=weight*trace[t];if(id%2==0)a[t]+=2*weight*trace[t];else b[t]+=2*weight*trace[t];}
                    sumNodePeaks+=weight*rawPeak;
                }
                var measured=TransversePulseAnalysis.analyze(signal,new double[steps+1],1e-10,16);
                double pa=0,pb=0,energy=0;for(int t=0;t<=steps;t++){pa=Math.max(pa,Math.abs(a[t]));pb=Math.max(pb,Math.abs(b[t]));energy+=signal[t]*signal[t];signalWriter.write(String.format("%d,%d,%d,%d,%.17g,%.17g,%.17g%n",probe.orientation,probe.shell,probe.ray,t,signal[t],a[t],b[t]));}
                writer.write(String.format("%d,%d,%d,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%s%n",probe.orientation,probe.shell,probe.ray,probe.radius,measured.arrival(),measured.peak(),measured.peakTime(),measured.rms(),energy,sumNodePeaks,measured.peak()/sumNodePeaks,pa,pb,measured.status()));
            }
        }
        Files.copy(run.resolve("configuration.properties"),out.resolve("reference-configuration.properties"),StandardCopyOption.REPLACE_EXISTING);
        System.out.printf("Completed %.1f seconds; saved to %s%n",(System.nanoTime()-begin)/1e9,out);
    }
}
