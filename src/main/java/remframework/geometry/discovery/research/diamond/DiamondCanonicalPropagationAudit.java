package remframework.geometry.discovery.research.diamond;

import java.util.*;
import java.nio.file.*;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import remframework.geometry.discovery.*;

/** Direct Node v0.1 propagation, with research four normalization and explicitly fixed gamma=1. */
public final class DiamondCanonicalPropagationAudit {
    private DiamondCanonicalPropagationAudit() { }
    public record Result(int nodes,int frames,int responding,double boundaryDrift,List<DiamondDirectionalComparison.Measurement> measurements,
                         GeometryConfigurationStatus status) { }
    public static Result run(int cells,double length,double k,double width,int frames) {
        if(cells<4 || cells>20 || !Double.isFinite(width) || width<0 || width>30 || frames<1 || frames>200)
            throw new IllegalArgumentException("Invalid audit limits");
        var model=DiamondReference.create(cells,length);var adapter=new DiamondCanonicalAdapter(model,k,1,DiamondCanonicalAdapter.Normalization.RESEARCH_FOUR);
        var rotation=new Quaterniond().rotationTo(new Vector3d(1,1,1).normalize(),new Vector3d(1,0,0));
        var center=rotation.transform(new Vector3d(4*(cells/2),4*(cells/2),4*(cells/2)).mul(length/Math.sqrt(3)));
        int source=model.nodes().stream().min(Comparator.comparingDouble(n->n.position().distanceSquared(center))).orElseThrow().id();
        var origin=model.nodes().get(source).position();
        if(width==0) adapter.displace(source,new Vector3d(.001,0,0));
        else for(var n:model.nodes()) if(n.neighborIds().size()==4)
            adapter.displace(n.id(),new Vector3d(.001*Math.exp(-n.position().distanceSquared(origin)/(2*width*width)),0,0));
        var initial=new ArrayList<Vector3d>();for(var n:model.nodes()) initial.add(adapter.position(n.id()));
        var probes=new ArrayList<DiamondDirectionalComparison.Receiver>();int index=0;
        for(var d:DiamondDirectionalComparison.directions()) {
            int components=(d.x!=0?1:0)+(d.y!=0?1:0)+(d.z!=0?1:0);
            String group=components==1?"axis":components==2?"face":"body";
            var point=rotation.transform(new Vector3d(d)).mul(20).add(origin);
            probes.add(DiamondReceiverSampling.receiver(model,point,group+"-"+(index++),length));
        }
        double[] arrivals=new double[26],peaks=new double[26];Arrays.fill(arrivals,Double.NaN);
        Set<Integer> responding=new HashSet<>();double boundary=0;int completed=0;boolean numerical=false;
        try {
            for(int frame=1;frame<=frames;frame++) {
                adapter.step();completed++;
                for(var n:model.nodes()) {
                    if(n.neighborIds().size()<4) boundary=Math.max(boundary,adapter.position(n.id()).distance(n.position()));
                    else if(n.id()!=source && adapter.position(n.id()).distance(initial.get(n.id()))>1e-9) responding.add(n.id());
                }
                for(int probe=0;probe<26;probe++) {
                    var r=probes.get(probe);var delta=new Vector3d();
                    for(int n=0;n<r.nodes().size();n++) {int id=r.nodes().get(n);delta.add(adapter.position(id).sub(initial.get(id)).mul(r.weights().get(n)));}
                    double amplitude=delta.length();peaks[probe]=Math.max(peaks[probe],amplitude);
                    if(Double.isNaN(arrivals[probe]) && amplitude>=1e-7) arrivals[probe]=frame;
                }
            }
        } catch(IllegalStateException failure) {numerical=true;}
        var measurements=new ArrayList<DiamondDirectionalComparison.Measurement>();
        for(int n=0;n<26;n++) measurements.add(new DiamondDirectionalComparison.Measurement(probes.get(n).direction(),arrivals[n],peaks[n],probes.get(n).farthestSampleDistance()));
        GeometryConfigurationStatus status;
        if(numerical) status=adapter.status();
        else if(boundary!=0 || responding.isEmpty()) status=GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.stop(
            GeometryConfigurationStage.GEOMETRY_REALIZATION,GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS,"Direct-kernel propagation","Transmission not established or fixed boundary changed."));
        else status=GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.GEOMETRY_REALIZATION,
            GeometryConfigurationStatusCode.GEOMETRIC_SYMMETRY_UNKNOWN,"Direct-kernel propagation","Transmission measured; four-neighbor research parameters only. Mechanical isotropy not established."));
        return new Result(model.nodes().size(),completed,responding.size(),boundary,List.copyOf(measurements),status);
    }
    private static DiamondDirectionalComparison.Result directional(Result r) {return new DiamondDirectionalComparison.Result(r.measurements(),r.status());}
    private static String summary(String name,Result result) {
        return String.format(Locale.ROOT,"%s: nodes%d; frames%d; responding%d; boundary drift%.3g; directional peak spread%.6f%%; %s%n",name,result.nodes(),result.frames(),result.responding(),result.boundaryDrift(),
            100*DiamondLongScaleAudit.spread(directional(result)),result.status().code());
    }
    public static void main(String[] args) throws Exception {
        var pulse=run(5,10,.5,0,20);System.out.println("Completed direct pulse baseline");
        var coarse=run(6,10,.5,15,20);var coarseLarge=run(8,10,.5,15,20);System.out.println("Completed coarse domains");
        var fine=run(12,5,1,15,20);var fineLarge=run(16,5,1,15,20);System.out.println("Completed fine domains");
        String report="Actual Node v0.1; research /4; gamma1; pulse .001; broad width15; radius20; 20 kernel frames\n"
            +summary("Point displacement",pulse)+summary("Coarse",coarse)+summary("Coarse enlarged",coarseLarge)+summary("Fine",fine)+summary("Fine enlarged",fineLarge)
            +String.format(Locale.ROOT,"Coarse boundary peak difference: %.9f%%%nFine boundary peak difference: %.9f%%%nCoarse-to-fine enlarged peak difference: %.6f%%%n",
                100*DiamondFineDomainAudit.peakDifference(directional(coarse),directional(coarseLarge)),
                100*DiamondFineDomainAudit.peakDifference(directional(fine),directional(fineLarge)),
                100*DiamondFineDomainAudit.peakDifference(directional(coarseLarge),directional(fineLarge)))
            +"Threshold receiver response is not wavefront speed. Broad-profile response is change from initial state. Canonical cubic code unchanged; complete application/transport parity and isotropy unproved.\n";
        Path path=Path.of(args.length==0?"diamond-canonical-propagation-audit.txt":args[0]);Files.writeString(path,report);System.out.print(report);
        var csv=new StringBuilder("direction,arrival_frame,peak_change,farthest_stencil_distance\n");
        for(var m:fineLarge.measurements()) csv.append(String.format(Locale.ROOT,"%s,%.9g,%.9g,%.9g%n",m.direction(),m.arrivalTime(),m.peak(),m.sampleDistance()));
        Files.writeString(path.resolveSibling("diamond-canonical-directional.csv"),csv);
    }
}
