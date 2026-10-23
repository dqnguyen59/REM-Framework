package remframework.geometry.discovery.research.diamond;

import java.util.*;
import java.nio.file.*;
import org.joml.Vector3d;
import remframework.geometry.discovery.*;

/** Exploratory equal-radius interpolated receivers. Sampling, reflections and runtime parity remain unvalidated. */
public final class DiamondDirectionalComparison {
    private DiamondDirectionalComparison() { }
    public record Receiver(String direction,List<Integer> nodes,List<Double> weights,double farthestSampleDistance) { }
    public record Measurement(String direction,double arrivalTime,double peak,double sampleDistance) { }
    public record Result(List<Measurement> measurements,GeometryConfigurationStatus status) { }
    public static List<Vector3d> directions() {
        List<Vector3d> result=new ArrayList<>();
        for(int x=-1;x<=1;x++) for(int y=-1;y<=1;y++) for(int z=-1;z<=1;z++)
            if(x!=0 || y!=0 || z!=0) result.add(new Vector3d(x,y,z).normalize());
        return List.copyOf(result);
    }
    public static Receiver receiver(GeometryDiscoveryModel model,Vector3d point,String direction) {
        var nearest=model.nodes().stream().filter(n->n.neighborIds().size()==4)
            .sorted(Comparator.comparingDouble(n->n.position().distanceSquared(point))).limit(8).toList();
        List<Integer> ids=new ArrayList<>();List<Double> weights=new ArrayList<>();double sum=0;
        for(var n:nearest) { ids.add(n.id());double w=1/Math.max(1e-20,n.position().distanceSquared(point));weights.add(w);sum+=w; }
        double total=sum;weights.replaceAll(w->w/total);
        return new Receiver(direction,List.copyOf(ids),List.copyOf(weights),nearest.getLast().position().distance(point));
    }
    public static Result run() { return run(7,.1,false); }
    public static Result run(int cells,double dt,boolean refined) { return run(cells,10,.5,dt,refined,0); }
    public static Result run(int cells,double length,double k,double dt,boolean refined,double width) {
        return run(cells,length,k,dt,refined,width,20);
    }
    public static Result run(int cells,double length,double k,double dt,boolean refined,double width,double duration) {
        if(!Double.isFinite(duration) || duration<=0 || duration>100) throw new IllegalArgumentException("Duration (0,100] required");
        if(!Double.isFinite(width) || width<0 || !Double.isFinite(length) || length<=0) throw new IllegalArgumentException("Invalid profile or spacing");
        if(cells<4 || cells>20 || !Double.isFinite(dt) || dt<=0 || dt>.1) throw new IllegalArgumentException("cells 4..20; dt (0,.1]");
        var model=DiamondReference.create(cells,length);var dynamics=new DiamondMechanics(model,k,dt);
        var rotation=new org.joml.Quaterniond().rotationTo(new Vector3d(1,1,1).normalize(),new Vector3d(1,0,0));
        var center=rotation.transform(new Vector3d(4*(cells/2),4*(cells/2),4*(cells/2)).mul(length/Math.sqrt(3)));
        int source=model.nodes().stream().min(Comparator.comparingDouble(n->n.position().distanceSquared(center))).orElseThrow().id();
        var origin=model.nodes().get(source).position();List<Receiver> probes=new ArrayList<>();
        int index=0;for(var direction:directions()) {
            int components=(Math.abs(direction.x)>0?1:0)+(Math.abs(direction.y)>0?1:0)+(Math.abs(direction.z)>0?1:0);
            String group=components==1?"axis":components==2?"face":"body";
            var aligned=new org.joml.Quaterniond().rotationTo(new Vector3d(1,1,1).normalize(),new Vector3d(1,0,0)).transform(new Vector3d(direction));
            var point=aligned.mul(20).add(origin);var name=group+"-"+(index++);
            probes.add(refined?DiamondReceiverSampling.receiver(model,point,name,length):receiver(model,point,name));
        }
        if(width==0) dynamics.displace(source,new Vector3d(.001,0,0));
        else for(var n:model.nodes()) if(!dynamics.fixed(n.id()))
            dynamics.displace(n.id(),new Vector3d(.001*Math.exp(-n.position().distanceSquared(origin)/(2*width*width)),0,0));
        List<Vector3d> initialProbes=new ArrayList<>();
        for(var r:probes) {
            var initial=new Vector3d();
            for(int i=0;i<r.nodes().size();i++) {int id=r.nodes().get(i);initial.add(dynamics.position(id).sub(model.nodes().get(id).position()).mul(r.weights().get(i)));}
            initialProbes.add(initial);
        }
        double[] arrival=new double[26],peak=new double[26];Arrays.fill(arrival,Double.NaN);
        for(int step=1;step<=Math.round(duration/dt);step++) {
            dynamics.step();
            for(int probe=0;probe<26;probe++) {
                var r=probes.get(probe);var displacement=new Vector3d();
                for(int n=0;n<r.nodes().size();n++) {
                    int id=r.nodes().get(n);displacement.add(dynamics.position(id).sub(model.nodes().get(id).position()).mul(r.weights().get(n)));
                }
                double amplitude=width==0?displacement.length():displacement.sub(initialProbes.get(probe)).length();peak[probe]=Math.max(peak[probe],amplitude);
                if(Double.isNaN(arrival[probe]) && amplitude>=1e-7) arrival[probe]=step*dt;
            }
        }
        List<Measurement> measurements=new ArrayList<>();boolean missing=false;
        for(int n=0;n<26;n++) {var r=probes.get(n);measurements.add(new Measurement(r.direction(),arrival[n],peak[n],r.farthestSampleDistance()));missing|=Double.isNaN(arrival[n]);}
        var diagnostic=missing?GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GEOMETRY_REALIZATION,
            GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS,"Directional comparison","Some receivers did not cross threshold. Isotropy inconclusive.")
            :GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.GEOMETRY_REALIZATION,GeometryConfigurationStatusCode.GEOMETRIC_SYMMETRY_UNKNOWN,
            "Directional comparison","Equal-radius interpolated measurements collected. Isotropy inconclusive: sampling convergence, boundary effects and canonical-runtime parity untested.");
        return new Result(List.copyOf(measurements),GeometryConfigurationStatus.of(diagnostic));
    }
    public static void main(String[] args) throws Exception {
        var result=run();var csv=new StringBuilder("direction,arrival_time,peak_displacement,farthest_interpolation_sample_distance\n");
        for(var m:result.measurements()) csv.append(String.format(Locale.ROOT,"%s,%.6g,%.9g,%.6g%n",m.direction(),m.arrivalTime(),m.peak(),m.sampleDistance()));
        Path output=Path.of(args.length==0?"diamond-directional-comparison.csv":args[0]);Files.writeString(output,csv);
        for(String group:List.of("axis","face","body")) {
            var ms=result.measurements().stream().filter(m->m.direction().startsWith(group)).toList();
            System.out.printf(Locale.ROOT,"%s: arrival %.3g..%.3g; peak %.3g..%.3g%n",group,
                ms.stream().mapToDouble(Measurement::arrivalTime).min().orElseThrow(),ms.stream().mapToDouble(Measurement::arrivalTime).max().orElseThrow(),
                ms.stream().mapToDouble(Measurement::peak).min().orElseThrow(),ms.stream().mapToDouble(Measurement::peak).max().orElseThrow());
        }
        System.out.println(result.status().explanation());System.out.println("CSV: "+output.toAbsolutePath());
    }
}
