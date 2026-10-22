package remframework.geometry.discovery.research.diamond;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.joml.Vector3d;
import remframework.geometry.discovery.*;
import remframework.opengl.model.Lattice;
import remframework.opengl.model.entity.*;
import remframework.opengl.helpers.Neighbor;
import remframework.settings.Project;

/** Controlled initial condition only; evolution uses the unchanged runtime Node integrator. */
public final class DiamondRuntimeDisturbance {
    public record Request(double wavelength,double amplitude,double receiverOffset,double geometryWavelength) {
        public Request(double wavelength,double amplitude,double receiverOffset) {this(wavelength,amplitude,receiverOffset,wavelength);}
        public double supportRadius() {return 1.5*geometryWavelength;}
        public Request(double wavelength,double amplitude) {this(wavelength,amplitude,0);}
    }
    private final AtomicReference<Request> pending=new AtomicReference<>();
    private volatile String summary="Long-wave experiment not started";
    private volatile GeometryConfigurationStatus status;
    private volatile boolean running;
    private volatile int steps, safeSteps;
    private double k,gamma;
    private volatile boolean retainedResults;
    public String resultsLabel() {return retainedResults?"Previous run (retained)":running?"Current run":"Completed run";}
    private DiamondRuntimeReceivers receivers,outerReceivers;
    private int outerSafeSteps;
    private volatile String troughSummary="Candidate trough experiment not started";
    private volatile List<DiamondTwoRadiusSpeed.Measurement> troughMeasurements=List.of();
    public String troughSummary() {return troughSummary;}
    public GeometryConfigurationStatus troughStatus() {
        var code=troughMeasurements.isEmpty()?GeometryConfigurationStatusCode.GEOMETRY_TEST_NOT_STARTED:
            troughMeasurements.size()==26&&troughMeasurements.stream().allMatch(DiamondTwoRadiusSpeed.Measurement::valid)?GeometryConfigurationStatusCode.GEOMETRIC_SYMMETRY_UNKNOWN:GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS;
        return GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.GEOMETRY_REALIZATION,code,"Candidate signed-trough timing",troughSummary));
    }
    public List<DiamondTwoRadiusSpeed.Measurement> troughMeasurements() {return troughMeasurements;}
    private volatile String twoRadiusSummary="Two-radius experiment not started";
    private volatile java.util.List<DiamondTwoRadiusSpeed.Measurement> pairedMeasurements=java.util.List.of();
    private volatile double[][] diagnosticInner=new double[0][],diagnosticOuter=new double[0][];
    public double[] innerTrace(int direction) {return diagnosticInner[direction].clone();}
    public double[] outerTrace(int direction) {return diagnosticOuter[direction].clone();}
    public String directionDiagnostic(int direction) {
        if(direction>=pairedMeasurements.size())return "No completed receiver traces";
        var vector=DiamondDirectionalComparison.directions().get(direction);var result=pairedMeasurements.get(direction);
        return String.format(java.util.Locale.ROOT,"Direction (%.3f, %.3f, %.3f): %s",vector.x,vector.y,vector.z,result.valid()?"Matched peak; speed "+result.speed():result.failure())+"; candidate trough: "+(direction<troughMeasurements.size()?(troughMeasurements.get(direction).valid()?String.format(Locale.ROOT,"speed %.6f",troughMeasurements.get(direction).speed()):troughMeasurements.get(direction).failure()):"not recorded");
    }
    public String twoRadiusSummary() {return twoRadiusSummary;}
    public java.util.List<DiamondTwoRadiusSpeed.Measurement> pairedMeasurements() {return pairedMeasurements;}
    public java.util.List<DiamondThresholdSensitivity.Comparison> thresholdComparisons() {return receivers==null || receivers.failure()!=null?java.util.List.of():receivers.thresholdComparisons();}
    public java.util.List<DiamondRuntimeReceivers.Arrival> arrivals() {return receivers==null?java.util.List.of():receivers.results();}
    public String summary() {return summary;}
    public GeometryConfigurationStatus status() {return status;}
    public int steps() {return steps;}
    public int safeSteps() {return safeSteps;}
    public boolean running() {return running;}
    public void request(double wavelength,double amplitude) {pending.set(new Request(wavelength,amplitude));}
    /** Explicit research-only receiver-radius shift; both rings move together, retaining separation 4. */
    public void request(double wavelength,double amplitude,double receiverOffset) {pending.set(new Request(wavelength,amplitude,receiverOffset));}
    /** Controlled comparison: envelope and receivers are fixed by geometryWavelength, independently of carrier wavelength. */
    public void requestFixedGeometry(double wavelength,double amplitude,double geometryWavelength,double receiverOffset) {
        pending.set(new Request(wavelength,amplitude,receiverOffset,geometryWavelength));
    }
    public void cancel() {pending.set(null);running=false;retainedResults=receivers!=null;summary="Experiment cancelled (lattice/pulse changed)";}
    /** Releases interpolation bindings to the graph being replaced. */
    public void releaseLattice() {
        cancel();
        receivers = null;
        outerReceivers = null;
    }
    public static double displacement(double radius,Request request) {
        double sigma=request.geometryWavelength()/2, support=request.supportRadius();
        if(radius>=support) return 0;
        double taper=Math.pow(1-radius*radius/(support*support),2);
        return request.amplitude()*Math.exp(-radius*radius/(2*sigma*sigma))*Math.cos(2*Math.PI*radius/request.wavelength())*taper;
    }
    private void report(GeometryConfigurationStatusCode code,String message) {
        summary=message;status=GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.info(
            GeometryConfigurationStage.GEOMETRY_REALIZATION,code,"Controlled diamond disturbance",message));
    }
    private void reject(GeometryConfigurationStatusCode code,String message) {
        running=false;retainedResults=receivers!=null;report(code,message);Project.getInstance().getRemModel().stopSimulation();
    }
    /** Runs before physics workers, so resetting and initialization cannot race a Node update. */
    public void initializePending(Lattice lattice) {
        Request request=pending.getAndSet(null);if(request==null) return;
        running=false;
        if(lattice.isDiamondIdentityExcitationEnabled()) {
            reject(GeometryConfigurationStatusCode.INVALID_PARAMETERS,"Disable Identity excitation before a controlled packet measurement.");return;
        }
        var project=Project.getInstance();k=project.getConstantFrequency();gamma=project.getConstantElectricFactor();
        if(!Double.isFinite(request.geometryWavelength()) || request.geometryWavelength()<2 || !Double.isFinite(request.receiverOffset()) || request.receiverOffset()<0 || request.receiverOffset()>1 || !lattice.isMechanicsOnly() || !Double.isFinite(request.wavelength()) || request.wavelength()<2
            || !Double.isFinite(request.amplitude()) || request.amplitude()<=0 || request.amplitude()>.01
            || !Double.isFinite(k) || !Double.isFinite(gamma) || k<=0 || gamma<=0 || gamma*k*k>1) {
            reject(GeometryConfigurationStatusCode.INVALID_PARAMETERS,"Use wavelength >= 2 (long-wave comparison >= 8), amplitude (0, 0.01], receiver offset [0, 1], and 0 < gamma*k^2 <= 1 in diamond mechanics.");return;
        }
        var nodes=lattice.getNodeArray();var center=lattice.getPhysicalSliceCenter();double support=request.supportRadius();
        for(Object value:nodes) {Node n=(Node)value;
            if(n.getType()==EntityType.FIXED && n.getEquilibriumPosition().distance(center)<=support+1) {
                reject(GeometryConfigurationStatusCode.INVALID_PARAMETERS,"Domain too small for wavelength "+request.wavelength()+". Increase dimensions; source radius is "+support+" bond lengths.");return;
            }
        }
        receivers=new DiamondRuntimeReceivers(lattice,request.geometryWavelength(),request.amplitude(),request.receiverOffset());
        if(receivers.failure()!=null) {reject(GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS,receivers.failure()+"; increase dimensions.");return;}
        outerReceivers=new DiamondRuntimeReceivers(lattice,request.geometryWavelength(),request.amplitude(),4+request.receiverOffset());
        twoRadiusSummary="Radii "+(support+4+request.receiverOffset())+" / "+(support+8+request.receiverOffset())+"; collecting matched peaks";
        pairedMeasurements=java.util.List.of();troughMeasurements=List.of();troughSummary="Candidate troughs: recording";
        List<Node> sources=new ArrayList<>(),boundaries=new ArrayList<>();
        for(Object value:nodes) {Node node=(Node)value;
            if(node.getType()==EntityType.FIXED) boundaries.add(node);
            else if(node.getEquilibriumPosition().distance(center)<support) sources.add(node);
        }
        try {
            safeSteps=DiamondReflectionWindow.safeSteps(sources,boundaries,receivers.stencilNodes(),node->{
                var neighbors=new ArrayList<Node>(4);for(Neighbor slot:Neighbor.values()){Node other=node.getNeighbor(slot);if(other!=null)neighbors.add(other);}return neighbors;
            });
        } catch(IllegalArgumentException error) {reject(GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS,error.getMessage());return;}
        outerSafeSteps=0;
        if(outerReceivers.failure()==null) {
            outerSafeSteps=DiamondReflectionWindow.safeSteps(sources,boundaries,outerReceivers.stencilNodes(),node->{
                var neighbors=new ArrayList<Node>(4);for(Neighbor slot:Neighbor.values()){Node other=node.getNeighbor(slot);if(other!=null)neighbors.add(other);}return neighbors;
            });
        } else twoRadiusSummary="Outer receivers unavailable; increase dimensions. "+outerReceivers.failure();
        if(safeSteps<1) {reject(GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS,"No reflection-free receiver window; increase dimensions.");return;}
        for(Object value:nodes) {Node n=(Node)value;
            n.setPosition(new Vector3d(n.getEquilibriumPosition()));n.updatePosition();n.getVelocity().zero();n.getAcceleration().zero();n.getForce().zero();
            n.getEField().v.zero();n.getEField().a.zero();n.getBField().v.zero();n.getBField().a.zero();
            if(n.getType()!=EntityType.FIXED) {
                double offset=displacement(n.getEquilibriumPosition().distance(center),request);
                n.setPosition(new Vector3d(n.getEquilibriumPosition()).add(offset,0,0));n.updatePosition();
            }
        }
        steps=0;retainedResults=false;running=true;
        report(GeometryConfigurationStatusCode.GENERATED,"Wavelength "+request.wavelength()+"; amplitude "+request.amplitude()+"; steps 0/"+safeSteps+". X-polarized radial packet; no isotropy measurement yet.");
    }
    /** Called once after all Node workers finish, independent of rendered FPS. */
    public void completedStep() {
        if(!running) return;var project=Project.getInstance();
        if(k!=project.getConstantFrequency() || gamma!=project.getConstantElectricFactor()) {
            reject(GeometryConfigurationStatusCode.INVALID_PARAMETERS,"Mechanics parameters changed; repeat experiment from equilibrium.");return;
        }
        steps++;
        if(receivers!=null) receivers.sample(steps);
        if(outerReceivers!=null && outerReceivers.failure()==null && steps<=outerSafeSteps)outerReceivers.sample(steps);
        if(steps>=safeSteps) {running=false;project.getRemModel().stopSimulation();
            if(outerReceivers!=null && outerReceivers.failure()==null) {
                var inner=receivers.recordedSignals();var outer=outerReceivers.recordedSignals();var paired=new ArrayList<DiamondTwoRadiusSpeed.Measurement>();
                for(int p=0;p<26;p++){int count=Math.min(inner[p].length,outer[p].length);paired.add(DiamondTwoRadiusSpeed.measure(Arrays.copyOf(inner[p],count),Arrays.copyOf(outer[p],count),4));}
                var troughs=new ArrayList<DiamondTwoRadiusSpeed.Measurement>();
                for(int p=0;p<26;p++){int count=Math.min(inner[p].length,outer[p].length);troughs.add(DiamondTroughSpeed.measure(Arrays.copyOf(inner[p],count),Arrays.copyOf(outer[p],count),4));}
                troughMeasurements=List.copyOf(troughs);troughSummary=DiamondTroughSpeed.summary(troughMeasurements);
                diagnosticInner=inner;diagnosticOuter=outer;
                pairedMeasurements=List.copyOf(paired);twoRadiusSummary=DiamondTwoRadiusSpeed.summary(pairedMeasurements);
            }
            report(receivers!=null && receivers.results().size()==26 && receivers.results().stream().allMatch(a->Double.isFinite(a.step())) && receivers.thresholdComparisons().stream().allMatch(row->row.arrivals()==row.receivers()) && pairedMeasurements.size()==26 && pairedMeasurements.stream().allMatch(DiamondTwoRadiusSpeed.Measurement::valid) ? GeometryConfigurationStatusCode.GEOMETRIC_SYMMETRY_UNKNOWN : GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS,"Paused at "+steps+" reflection-free receiver steps. "+(receivers==null?"No receivers.":receivers.summary()));}
    }
}
