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
    public record Request(double wavelength,double amplitude,double receiverOffset,double geometryWavelength,
                          int probeShell,double positionThreshold,double gradientThreshold,DiamondExcitationAxis excitationAxis,DiamondSphericalSweep sphericalSweep,DiamondRotatingOrbit orbit) {
        public Request(double w,double a,double o,double g,int shell,double pt,double gt,DiamondExcitationAxis axis,DiamondSphericalSweep sweep) {
            this(w,a,o,g,shell,pt,gt,axis,sweep,null);
        }
        public Request { java.util.Objects.requireNonNull(excitationAxis); }
        public Request(double wavelength,double amplitude,double receiverOffset,double geometryWavelength,
                       int probeShell,double positionThreshold,double gradientThreshold,DiamondExcitationAxis axis) {
            this(wavelength,amplitude,receiverOffset,geometryWavelength,probeShell,positionThreshold,gradientThreshold,axis,null);
        }
        public Request(double wavelength,double amplitude,double receiverOffset,double geometryWavelength,
                       int probeShell,double positionThreshold,double gradientThreshold) {
            this(wavelength,amplitude,receiverOffset,geometryWavelength,probeShell,positionThreshold,gradientThreshold,DiamondExcitationAxis.AUTO);
        }
        public Request(double wavelength,double amplitude,double receiverOffset,double geometryWavelength) {
            this(wavelength,amplitude,receiverOffset,geometryWavelength,-1,1e-12,1e-12);
        }
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
    private record DrivenNode(Node node,double weight) {}
    private List<DrivenNode> drivenNodes=List.of();
    private volatile DiamondSphericalSweep activeSweep;
    private double sweepAmplitude;
    private volatile DiamondRotatingOrbit activeOrbit;
    public DiamondRotatingOrbit activeOrbit() { return activeOrbit; }
    public void requestOrbitPreview(double width,double amplitude,DiamondRotatingOrbit orbit) {
        if(!Double.isFinite(width)||width<2||!Double.isFinite(amplitude)||amplitude<=0)
            throw new IllegalArgumentException("Finite width >= 2 and positive amplitude required");
        pending.set(new Request(width,amplitude,0,width,0,1e-9,1e-9,DiamondExcitationAxis.Z,
            new DiamondSphericalSweep(orbit.period(),1,1),Objects.requireNonNull(orbit)));
    }

    public DiamondSphericalSweep activeSweep() {return activeSweep;}
    public void requestSphericalProbeRun(double width,double amplitude,int shell,double positionThreshold,double gradientThreshold,
                                        DiamondSphericalSweep sweep) {
        Objects.requireNonNull(sweep);
        if(!Double.isFinite(width)||width<2||!Double.isFinite(amplitude)||amplitude<=0||shell<0
            ||!Double.isFinite(positionThreshold)||positionThreshold<=0||!Double.isFinite(gradientThreshold)||gradientThreshold<=0)
            throw new IllegalArgumentException("Positive finite amplitude and thresholds, width >= 2, and valid shell required");
        pending.set(new Request(width,amplitude,0,width,shell,positionThreshold,gradientThreshold,DiamondExcitationAxis.Z,sweep));
    }

    private volatile boolean retainedResults;
    public String resultsLabel() {return retainedResults?"Previous run (retained)":running?"Current run":"Completed run";}
    private DiamondRuntimeReceivers receivers,outerReceivers;
    private int outerSafeSteps;
    private boolean controlledProbeRun;
    private boolean slabProbeRun;
    private DiamondExcitationAxis activeAxis=DiamondExcitationAxis.X;
    /** Controlled probe experiment: fixed source, all directions in the chosen shell. */
    public void requestProbeRun(int shell,double positionThreshold,double gradientThreshold) {
        requestProbeRun(8,shell,positionThreshold,gradientThreshold);
    }
    /** Source support scales with wavelength; amplitude comes from saved probe settings. */
    public void requestProbeRun(double wavelength,int shell,double positionThreshold,double gradientThreshold) {
        requestProbeRun(wavelength,remframework.settings.AppSettings.getInstance().getDiamondProbeAmplitude(),shell,positionThreshold,gradientThreshold);
    }
    public void requestProbeRun(double wavelength,double amplitude,int shell,double positionThreshold,double gradientThreshold) {
        requestProbeRun(wavelength,amplitude,shell,positionThreshold,gradientThreshold,DiamondExcitationAxis.AUTO);
    }
    public void requestProbeRun(double wavelength,double amplitude,int shell,double positionThreshold,double gradientThreshold,DiamondExcitationAxis axis) {
        if (!Double.isFinite(amplitude) || amplitude <= 0)
            throw new IllegalArgumentException("Amplitude must be positive and finite");
        if(!Double.isFinite(wavelength) || wavelength<2)
            throw new IllegalArgumentException("Wavelength must be finite and at least 2 bond lengths");
        if(shell<0 || !Double.isFinite(positionThreshold) || positionThreshold<=0
            || !Double.isFinite(gradientThreshold) || gradientThreshold<=0)
            throw new IllegalArgumentException("Valid shell and positive finite thresholds required");
        pending.set(new Request(wavelength,amplitude,0,wavelength,shell,positionThreshold,gradientThreshold,axis));
    }
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
    public void cancel() {pending.set(null); if(activeSweep!=null)Project.getInstance().scene.finishProbeArrivalMeasurements(); activeSweep=null;activeOrbit=null;drivenNodes=List.of();running=false;retainedResults=receivers!=null;summary="Experiment cancelled (lattice/pulse changed)";}
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
        activeSweep=null;activeOrbit=null;drivenNodes=List.of();
        controlledProbeRun=false;
        slabProbeRun=false;
        if(request.probeShell()>=0)Project.getInstance().scene.clearProbeArrivalMeasurements();
        if(lattice.isDiamondIdentityExcitationEnabled() || lattice.isDiamondPressureEnabled() || lattice.isDiamondTransportEnabled()) {
            reject(GeometryConfigurationStatusCode.INVALID_PARAMETERS,"Disable Identity excitation, occupancy pressure and transport before a controlled packet measurement.");return;
        }
        var project=Project.getInstance();k=project.getConstantFrequency();gamma=project.getConstantElectricFactor();
        if(!Double.isFinite(request.geometryWavelength()) || request.geometryWavelength()<2 || !Double.isFinite(request.receiverOffset()) || request.receiverOffset()<0 || request.receiverOffset()>1 || !lattice.isMechanicsOnly() || !Double.isFinite(request.wavelength()) || request.wavelength()<2
            || !Double.isFinite(request.amplitude()) || request.amplitude()<=0 || (request.probeShell()<0 && request.amplitude()>.01)
            || !Double.isFinite(k) || !Double.isFinite(gamma) || k<=0 || gamma<=0 || gamma*k*k>1) {
            reject(GeometryConfigurationStatusCode.INVALID_PARAMETERS,"Use wavelength >= 2, positive finite amplitude (legacy two-radius runs <= 0.01), receiver offset [0, 1], and 0 < gamma*k^2 <= 1 in diamond mechanics.");return;
        }
        activeOrbit=request.orbit();
        if(request.sphericalSweep()!=null) {initializeSpherical(lattice,request);return;}
        var nodes=lattice.getNodeArray();var center=lattice.getPhysicalSliceCenter();double support=request.supportRadius();
        var planarProbes=project.scene.getProbes().stream().filter(p->p.getDefinition()!=null
            && p.getDefinition().getPlacement()!=null
            && p.getDefinition().getPlacement().measurementSpace().equals("Diamond physical XY")
            && p.getDefinition().getPlacement().targets().stream().anyMatch(t->t.shell()==request.probeShell())).toList();
        double halfDepth=0;for(Object value:nodes)halfDepth=Math.max(halfDepth,Math.abs(((Node)value).getEquilibriumPosition().z-center.z));
        boolean xySlab=request.probeShell()>=0 && remframework.opengl.model.DiamondProbePlacement.validXYDirectionCount(planarProbes.size()) && halfDepth<=support+1;
        activeAxis=request.excitationAxis().resolve(xySlab);
        if(xySlab) {
            if(project.scene.getProbes().stream().filter(p->p.getDefinition().getPlacement()!=null)
                .flatMap(p->p.getDefinition().getPlacement().targets().stream()).anyMatch(t->t.requestedRadius()<=support+1)) {
                reject(GeometryConfigurationStatusCode.INVALID_PARAMETERS,"XY receivers must be outside source support "+support);return;
            }
            // Amplitude is the peak on actual movable lattice sites, rather than the
            // unattainable profile value at a center lying between sites.
            var unitRequest=new Request(request.wavelength(),1,request.receiverOffset(),request.geometryWavelength());
            double sampledPeak=0;
            for(Object value:nodes) {
                Node n=(Node)value;if(n.isFixed())continue;
                var p=n.getEquilibriumPosition();
                sampledPeak=Math.max(sampledPeak,Math.abs(displacement(Math.hypot(p.x-center.x,p.y-center.y),unitRequest)));
            }
            if(!Double.isFinite(sampledPeak) || sampledPeak<=0) {
                reject(GeometryConfigurationStatusCode.INVALID_PARAMETERS,"No movable lattice sites sample the packet source; increase source width or domain dimensions.");return;
            }
            // Explicit thin-slab experiment: existing fixed surfaces remain part of the response.
            for(Object value:nodes) {
                Node n=(Node)value;var p=n.getEquilibriumPosition();double radius=Math.hypot(p.x-center.x,p.y-center.y);
                n.setPosition(new Vector3d(p));n.updatePosition();n.getVelocity().zero();n.getAcceleration().zero();n.getForce().zero();
                n.resetAuxiliaryFieldMotion();
                if(!n.isFixed()){n.setPosition(activeAxis.displaced(p,request.amplitude()*(displacement(radius,unitRequest)/sampledPeak)));n.updatePosition();}
            }
            receivers=null;outerReceivers=null;pairedMeasurements=List.of();troughMeasurements=List.of();
            steps=0;safeSteps=remframework.settings.AppSettings.getInstance().getDiamondProbeMaximumUpdates();running=true;retainedResults=false;controlledProbeRun=true;slabProbeRun=true;
            project.scene.updateProbeMeasurements(remframework.opengl.controller.TransportSystem.getInstance());
            project.scene.startDiamondProbeMeasurements(project.scene.getProbes(),request.probeShell(),request.positionThreshold(),request.gradientThreshold());
            report(GeometryConfigurationStatusCode.GENERATED,"XY thin slab: "+activeAxis+" excitation; wavelength "+request.wavelength()+"; initial node peak amplitude "+request.amplitude()+"; "+safeSteps+"-update recording; fixed surface effects INCLUDED. Not a reflection-free bulk test.");return;
        }
        for(Object value:nodes) {Node n=(Node)value;
            if(n.getType()==EntityType.FIXED && n.getEquilibriumPosition().distance(center)<=support+1) {
                reject(GeometryConfigurationStatusCode.INVALID_PARAMETERS,"Domain too small for wavelength "+request.wavelength()+". Increase dimensions; source radius is "+support+" bond lengths.");return;
            }
        }
        receivers=new DiamondRuntimeReceivers(lattice,request.geometryWavelength(),request.amplitude(),request.receiverOffset(),activeAxis);
        if(receivers.failure()!=null) {reject(GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS,receivers.failure()+"; increase dimensions.");return;}
        outerReceivers=new DiamondRuntimeReceivers(lattice,request.geometryWavelength(),request.amplitude(),4+request.receiverOffset(),activeAxis);
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
        var selectedProbes=project.scene.getProbes().stream().filter(p->p.getDefinition()!=null
            && p.getDefinition().getDetectorType()==remframework.opengl.model.ProbeDetectorType.DIAMOND_INTERPOLATED
            && p.getDefinition().getPlacement().targets().stream().anyMatch(t->t.shell()==request.probeShell())).toList();
        if(request.probeShell()>=0) {
            project.scene.clearProbeArrivalMeasurements();
            boolean planar=remframework.opengl.model.DiamondProbePlacement.validXYDirectionCount(selectedProbes.size()) && selectedProbes.stream().allMatch(p->p.getDefinition().getPlacement().measurementSpace().equals("Diamond physical XY"));
            if((selectedProbes.size()!=26 && !planar) || selectedProbes.stream().anyMatch(p->p.getDefinition().getPlacement().targets().stream()
                .anyMatch(t->t.requestedRadius()<=support+1))) {
                reject(GeometryConfigurationStatusCode.INVALID_PARAMETERS,"Apply a complete 8/16/32/64-direction XY or 26-direction XYZ shell outside source support "+support+". Recommended first radius "+(support+4)+", then "+(support+8)+".");return;
            }
            var probeNodes=project.scene.getProbes().stream().flatMap(p->p.getOccupancies().stream()).map(Occupancy::getNode).distinct().toList();
            int probeSafe=DiamondReflectionWindow.safeSteps(sources,boundaries,probeNodes,node->{
                var neighbors=new ArrayList<Node>(4);for(Neighbor slot:Neighbor.values()){Node other=node.getNeighbor(slot);if(other!=null)neighbors.add(other);}return neighbors;
            });
            safeSteps=Math.min(safeSteps,probeSafe);
            if(safeSteps<1) {reject(GeometryConfigurationStatusCode.INVALID_PARAMETERS,"No reflection-free probe window; increase dimensions.");return;}
        }
        for(Object value:nodes) {Node n=(Node)value;
            n.setPosition(new Vector3d(n.getEquilibriumPosition()));n.updatePosition();n.getVelocity().zero();n.getAcceleration().zero();n.getForce().zero();
            n.resetAuxiliaryFieldMotion();
            if(n.getType()!=EntityType.FIXED) {
                double offset=displacement(n.getEquilibriumPosition().distance(center),request);
                n.setPosition(activeAxis.displaced(n.getEquilibriumPosition(),offset));n.updatePosition();
            }
        }
        steps=0;retainedResults=false;running=true;
        if(request.probeShell()>=0) {
            project.scene.updateProbeMeasurements(remframework.opengl.controller.TransportSystem.getInstance());
            project.scene.startDiamondProbeMeasurements(project.scene.getProbes(),request.probeShell(),request.positionThreshold(),request.gradientThreshold());
            controlledProbeRun=true;
        }
        report(GeometryConfigurationStatusCode.GENERATED,"Wavelength "+request.wavelength()+"; amplitude "+request.amplitude()+"; steps 0/"+safeSteps+". "+activeAxis+"-polarized radial packet; no isotropy measurement yet.");
    }
    /** Spherical source is a prescribed displacement in a compact 3D region. */
    private void initializeSpherical(Lattice lattice,Request request) {
        var project=Project.getInstance();var center=lattice.getPhysicalSliceCenter();
        double support=request.supportRadius();var sources=new ArrayList<Node>();var boundaries=new ArrayList<Node>();
        var probes=project.scene.getProbes();
        if(activeOrbit==null && (probes.isEmpty() || probes.stream().anyMatch(p->p.getDefinition().getDetectorType()!=remframework.opengl.model.ProbeDetectorType.DIAMOND_INTERPOLATED
            || p.getDefinition().getPlacement()==null || p.getDefinition().getPlacement().targets().stream().anyMatch(t->t.requestedRadius()<=support+1)))) {
            reject(GeometryConfigurationStatusCode.INVALID_PARAMETERS,"Apply diamond probe shells outside spherical source support "+support);return;
        }
        for(Object value:lattice.getNodeArray()) {
            Node node=(Node)value;
            if(node.isFixed()) {
                boundaries.add(node);
                if(node.getEquilibriumPosition().distance(center)<=support+1) {
                    reject(GeometryConfigurationStatusCode.INVALID_PARAMETERS,"Spherical source needs space in all three dimensions; source radius "+support);return;
                }
            } else if(node.getEquilibriumPosition().distance(center)<support)sources.add(node);
        }
        if(sources.isEmpty()){reject(GeometryConfigurationStatusCode.INVALID_PARAMETERS,"No movable spherical source nodes");return;}
        var probeNodes=probes.stream().flatMap(p->p.getOccupancies().stream()).map(Occupancy::getNode).distinct().toList();
        if(activeOrbit!=null) safeSteps=Integer.MAX_VALUE;
        else try {
            safeSteps=DiamondReflectionWindow.safeSteps(sources,boundaries,probeNodes,node->{
                var neighbors=new ArrayList<Node>(4);for(Neighbor slot:Neighbor.values()){Node other=node.getNeighbor(slot);if(other!=null)neighbors.add(other);}return neighbors;
            });
        } catch(IllegalArgumentException error){reject(GeometryConfigurationStatusCode.INVALID_PARAMETERS,error.getMessage());return;}
        if(activeOrbit==null) safeSteps=Math.min(safeSteps,remframework.settings.AppSettings.getInstance().getDiamondProbeMaximumUpdates());
        if(safeSteps<1){reject(GeometryConfigurationStatusCode.INVALID_PARAMETERS,"No reflection-free spherical measurement window");return;}
        double sigma=request.geometryWavelength()/2;
        double peak=0;var bindings=new ArrayList<DrivenNode>();
        for(var node:sources) {
            double r=node.getEquilibriumPosition().distance(center);
            double weight=Math.exp(-r*r/(2*sigma*sigma))*Math.pow(1-r*r/(support*support),2);
            peak=Math.max(peak,weight);bindings.add(new DrivenNode(node,weight));
        }
        final double normalization=peak;
        drivenNodes=bindings.stream().map(b->new DrivenNode(b.node,b.weight/normalization)).toList();
        for(Object value:lattice.getNodeArray()) {
            Node node=(Node)value;node.setPosition(new Vector3d(node.getEquilibriumPosition()));node.updatePosition();
            node.getVelocity().zero();node.getAcceleration().zero();node.getForce().zero();node.resetAuxiliaryFieldMotion();
        }
        activeSweep=request.sphericalSweep();sweepAmplitude=request.amplitude();steps=0;
        prescribeSweep(0,true);
        receivers=null;outerReceivers=null;pairedMeasurements=List.of();troughMeasurements=List.of();
        twoRadiusSummary="Single-axis research receivers are not used for spherical excitation";
        diagnosticInner=new double[0][];diagnosticOuter=new double[0][];
        project.scene.updateProbeMeasurements(remframework.opengl.controller.TransportSystem.getInstance());
        if(activeOrbit==null) {
        project.scene.startDiamondProbeMeasurements(probes,request.probeShell(),request.positionThreshold(),request.gradientThreshold());
        project.scene.getProbeShellArrivalReport().enableSweep(activeSweep.updatesPerCycle(),activeSweep.measurementCycles(),center);
        project.scene.getProbeShellArrivalReport().configureSweepSource(activeSweep.turns(),request.amplitude(),request.geometryWavelength());
        }
        controlledProbeRun=true;retainedResults=false;running=true;
        report(GeometryConfigurationStatusCode.GENERATED,sweepStatus("Recording"));
    }
    private void prescribeSweep(int update,boolean publish) {
        var offset=(activeOrbit==null?activeSweep.direction(update):activeOrbit.direction(update)).mul(sweepAmplitude);
        var previous=(activeOrbit==null?activeSweep.direction(update-1L):activeOrbit.direction(update-1L)).mul(sweepAmplitude);
        var velocity=new Vector3d(offset).sub(previous);
        for(var binding:drivenNodes) {
            var node=binding.node;
            node.setPosition(new Vector3d(node.getEquilibriumPosition()).fma(binding.weight,offset));
            if(publish)node.updatePosition();
            node.getVelocity().set(velocity).mul(binding.weight);node.getAcceleration().zero();node.getForce().zero();
        }
    }
    private String sweepStatus(String prefix) {
        if(activeOrbit!=null) return "Continuous rotating orbit; update "+steps+"; X/Y/Z periods "
            +activeOrbit.fastPeriod()+" / "+(activeOrbit.fastPeriod()*activeOrbit.ratio())+" / "+activeOrbit.period()
            +". Repeats until paused. Reflections included; no arrival measurement.";

        var rows=Project.getInstance().scene.getProbeShellArrivalReport().sweepRows();
        long complete=rows.stream().filter(r->r.result().measurement().complete()).count();
        return prefix+": spherical sweep; "+steps+" / "+safeSteps+" updates; period "+activeSweep.updatesPerCycle()
            +"; turns "+activeSweep.turns()+"; "+activeSweep.measurementCycles()+" measurement cycles; probes "+complete+" / "+rows.size()
            +" complete. Earliest possible whole-window completion: "+((long)activeSweep.updatesPerCycle()*(activeSweep.measurementCycles()+1))+" probe updates. Boundary cutoff remains active. Coverage is a finite spherical spiral, not exact equality of all rotation axes.";
    }

    /** Called once after all Node workers finish, independent of rendered FPS. */
    public void completedStep() {
        if(!running) return;var project=Project.getInstance();
        if(k!=project.getConstantFrequency() || gamma!=project.getConstantElectricFactor()) {
            reject(GeometryConfigurationStatusCode.INVALID_PARAMETERS,"Mechanics parameters changed; repeat experiment from equilibrium.");return;
        }
        steps++;
        if(activeSweep!=null) {
            prescribeSweep(steps,false);
            boolean complete=activeOrbit==null && project.scene.getProbeShellArrivalReport().sweepsComplete();
            if(complete || steps>=safeSteps) {
                running=false;project.getRemModel().stopSimulation();project.scene.finishProbeArrivalMeasurements();
                report(GeometryConfigurationStatusCode.GEOMETRIC_SYMMETRY_UNKNOWN,
                    sweepStatus(complete?"Completed configured sweep measurements":"Stopped at recording/boundary limit; incomplete windows retained"));
            } else report(GeometryConfigurationStatusCode.GENERATED,sweepStatus("Recording"));
            return;
        }
        if(receivers!=null) receivers.sample(steps);
        if(outerReceivers!=null && outerReceivers.failure()==null && steps<=outerSafeSteps)outerReceivers.sample(steps);
        if(steps>=safeSteps) {running=false;project.getRemModel().stopSimulation();
            if(controlledProbeRun)project.scene.finishProbeArrivalMeasurements();
            if(slabProbeRun) {
                report(GeometryConfigurationStatusCode.GEOMETRIC_SYMMETRY_UNKNOWN,"Paused XY slab with "+activeAxis+" excitation at "+safeSteps+" updates. Fixed surface effects INCLUDED; XY propagation diagnostic only, bulk isotropy not assessed.");return;
            }
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
