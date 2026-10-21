package remframework.geometry.discovery.research.diamond;
import java.util.*;
import org.joml.Vector3d;
import remframework.opengl.model.Lattice;
import remframework.opengl.model.entity.*;
/** Equal-radius physical receivers. Threshold arrival is an apparent speed, not phase/group speed. */
public final class DiamondRuntimeReceivers {
    public record Arrival(String direction,double radius,double step,double apparentSpeed,double stencilRadius) { }
    private record Probe(DiamondDirectionalComparison.Receiver stencil) { }
    private final Node[] nodes;
    private final List<Probe> probes=new ArrayList<>();
    private final double[] arrivals=new double[26], previous=new double[26];
    private final double radius, threshold;
    private String failure;
    private final DiamondThresholdSensitivity sensitivity;
    public List<DiamondThresholdSensitivity.Comparison> thresholdComparisons() {return sensitivity.compare();}
    public double[][] recordedSignals() {return sensitivity.recordedSignals();}
    public DiamondRuntimeReceivers(Lattice lattice,double wavelength,double amplitude) {this(lattice,wavelength,amplitude,0);}
    public DiamondRuntimeReceivers(Lattice lattice,double wavelength,double amplitude,double radiusOffset) {
        nodes=Arrays.stream(lattice.getNodeArray()).map(n->(Node)n).toArray(Node[]::new);
        radius=1.5*wavelength+4+radiusOffset;threshold=amplitude*1e-4;sensitivity=new DiamondThresholdSensitivity(26,amplitude,radius);Arrays.fill(arrivals,Double.NaN);
        // Existing sampler expects untranslated crystal coordinates; undo runtime centering.
        var inverse=new org.joml.Quaterniond().rotationTo(new Vector3d(1,1,1).normalize(),new Vector3d(1,0,0)).conjugate();
        var minimum=new Vector3d(Double.POSITIVE_INFINITY);
        for(var node:nodes) minimum.min(inverse.transform(new Vector3d(node.getEquilibriumPosition())));
        var shift=new org.joml.Quaterniond().rotationTo(new Vector3d(1,1,1).normalize(),new Vector3d(1,0,0)).transform(minimum).negate();
        var sampler=new DiamondReceiverSampling.Sampler(nodes.length,id->new Vector3d(nodes[id].getEquilibriumPosition()).add(shift),id->nodes[id].getType()!=EntityType.FIXED,1);
        int id=0;
        try {
            for(var direction:DiamondDirectionalComparison.directions()) {
                var point=new Vector3d(direction).mul(radius).add(lattice.getPhysicalSliceCenter()).add(shift);
                var stencil=sampler.receiver(point,"direction "+id++);
                for(int sample:stencil.nodes()) if(nodes[sample].getEquilibriumPosition().distance(lattice.getPhysicalSliceCenter())<=1.5*wavelength)
                    throw new IllegalArgumentException("Receiver stencil intersects the initialized packet");
                probes.add(new Probe(stencil));
            }
        } catch(IllegalArgumentException error) {failure=error.getMessage();probes.clear();}
    }
    public List<Node> stencilNodes() {return probes.stream().flatMap(p->p.stencil().nodes().stream()).distinct().map(id->nodes[id]).toList();}
    public String failure() {return failure;}
    public void sample(int step) {
        if(failure!=null) return;
        double[] signal=new double[26];
        for(int p=0;p<probes.size();p++) {
            var stencil=probes.get(p).stencil();double displacement=0;
            for(int i=0;i<stencil.nodes().size();i++) {
                var node=nodes[stencil.nodes().get(i)];
                displacement+=stencil.weights().get(i)*(node.getPosBuff().x-node.getEquilibriumPosition().x);
            }
            signal[p]=displacement;
            double value=Math.abs(displacement);
            if(Double.isNaN(arrivals[p]) && value>=threshold) arrivals[p]=crossingTime(step,previous[p],value,threshold);
            previous[p]=value;
        }
        sensitivity.record(step,signal);
    }
    public static double crossingTime(int step,double before,double after,double threshold) {
        return step-1+(threshold-before)/(after-before);
    }
    public List<Arrival> results() {
        var result=new ArrayList<Arrival>();
        for(int p=0;p<probes.size();p++) {var s=probes.get(p).stencil();double t=arrivals[p];result.add(new Arrival(s.direction(),radius,t,radius/t,s.farthestSampleDistance()));}
        return List.copyOf(result);
    }
    public String summary() {
        if(failure!=null) return "Receivers unavailable: "+failure+". Increase dimensions.";
        var results=results();long count=results.stream().filter(a->Double.isFinite(a.step())).count();
        if(count!=26) return "Arrivals "+count+"/26 at radius "+radius+"; insufficient reflection-free receiver time. Increase dimensions. Isotropy inconclusive.";
        double min=results.stream().mapToDouble(Arrival::apparentSpeed).min().orElseThrow(),max=results.stream().mapToDouble(Arrival::apparentSpeed).max().orElseThrow(),mean=results.stream().mapToDouble(Arrival::apparentSpeed).average().orElseThrow();
        return String.format(Locale.ROOT,"26/26 arrivals; apparent speed spread %.3f%% ((max-min)/mean). Threshold = 0.01%% of source amplitude. Compare thresholds below; interpolation/wavelength convergence untested. Isotropy inconclusive.",100*(max-min)/mean);
    }
}
