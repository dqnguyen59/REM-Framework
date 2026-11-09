package remframework.opengl.model;

import java.util.List;
import java.util.Locale;
import org.joml.Vector3d;
import remframework.opengl.model.entity.Identity;
import remframework.opengl.model.entity.Node;
import remframework.settings.Project;

/** Measurement clock around the existing Identity force mechanics; no source substitution. */
public final class IdentityOrbitSpreadRun {
    private Identity source;
    private Object[] nodes;
    private List<PassiveObserver> probes=List.of();
    private Vector3d increments,angles,amplitude;
    private int period,cycles,warmup;
    private long samples;
    private double k,gamma,sourceCenterOffset;
    private boolean running;
    private boolean spongeEnabled;
    private boolean equalAreaOrbit;
    private int spongeLayers;
    private String status="Identity orbit spread not started";
    public boolean running(){return running;}
    public String status(){return status;}
    public int period(){return period;}
    public double sourceCenterOffset(){return sourceCenterOffset;}
    public void invalidate(Scene scene) {
        cancel(scene,"Incomplete: probes or experiment changed");
        period=0;source=null;nodes=null;probes=List.of();status="Identity orbit spread cleared";
    }

    /** Integer source periods must share a finite, practical repeat interval. */
    public static int periodFor(Identity identity) {
        if(!identity.isXyzOrbit())throw new IllegalArgumentException("Select an Identity with XYZ orbit enabled");
        long result=1;
        for(double increment:new double[]{identity.getPhaseIncrement().x,identity.getPhaseIncrement().y,identity.getPhaseIncrement().z}) {
            double value=360/Math.abs(increment);long rounded=Math.round(value);
            if(!Double.isFinite(value)||rounded<1||rounded>1_048_576||Math.abs(value-rounded)>1e-8)
                throw new IllegalArgumentException("Each orbit axis needs an integer period from 1 to 1048576 updates");
            long a=result,b=rounded;while(b!=0){long remainder=a%b;a=b;b=remainder;}
            result=Math.multiplyExact(result/a,rounded);
            if(result>1_048_576)throw new IllegalArgumentException("Combined orbit period exceeds 1048576 updates");
        }
        return (int)result;
    }

    /** Called only at a completed-frame boundary. Restarts the saved source from equilibrium. */
    public void start(Scene scene,Identity identity,int warmupCycles,int measurementCycles,double positionThreshold,double gradientThreshold) {
        var project=Project.getInstance();var lattice=scene.getLattice();
        int requestedPeriod=periodFor(identity);
        if(warmupCycles<0||warmupCycles>100||measurementCycles<1||measurementCycles>100)
            throw new IllegalArgumentException("Warm-up cycles 0..100; measurement cycles 1..100");
        if(!lattice.isMechanicsOnly()||lattice.isDiamondTransportEnabled()||lattice.isDiamondPressureEnabled())
            throw new IllegalArgumentException("Use diamond mechanics with transport and pressure disabled");
        var occupied=project.occupancies.stream().filter(o->o.getIdentity()==identity&&o.isOccupied()).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Selected Identity must occupy a node"));
        // Loaded Occupancies retain an index but have no runtime Node until the first update.
        var sourceNode=lattice.getNodeByIndex(occupied.getNodeIndex());
        if(sourceNode==null)throw new IllegalArgumentException("Saved Identity node is outside the current lattice");
        if(sourceNode.isFixed()||project.occupancies.stream().anyMatch(o->o.getIdentity()!=identity&&o.getIdentity().isActive()))
            throw new IllegalArgumentException("Use one active Identity on a movable node");
        if(!identity.getAmplitude().isFinite()||identity.getAmplitude().lengthSquared()==0||!identity.getPhaseAngle().isFinite())
            throw new IllegalArgumentException("Finite nonzero source amplitude and finite start angles required");
        if(!Double.isFinite(positionThreshold)||positionThreshold<=0||!Double.isFinite(gradientThreshold)||gradientThreshold<=0)
            throw new IllegalArgumentException("Positive finite arrival thresholds required");
        var selected=List.copyOf(scene.getProbes());
        if(selected.isEmpty()||selected.stream().anyMatch(p->p.getDefinition().getDetectorType()!=ProbeDetectorType.DIAMOND_INTERPOLATED)
                ||ProbeArrivalMeasurement.availableShells(selected).isEmpty())
            throw new IllegalArgumentException("Generate and apply diamond shell probes first");
        lattice.applyPendingAbsorptionLayerChange();
        if (lattice.isDiamondSpongeEnabled()) {
            int width=lattice.getNumEdgeAbsorptionLayers();
            if(width<=0||2L*width>=Math.min(project.getDimensionX(),Math.min(project.getDimensionY(),project.getDimensionZ())))
                throw new IllegalArgumentException("Absorber needs a positive width smaller than half every lattice dimension");
            if(sourceNode.getAbsorptionFactor()<1f)
                throw new IllegalArgumentException("Identity source is inside the absorbing region; move it into the interior");
            for(var probe:selected)for(var stencil:probe.getDefinition().getInterpolationSources()) {
                var node=lattice.getNodeByIndex(stencil.nodeIndex());
                if(stencil.weight()>0 && (node==null||node.getAbsorptionFactor()<1f))
                    throw new IllegalArgumentException("Probe "+probe.getId()+" samples the absorbing region; reduce shell radius or layer width");
            }
        }
        project.getRemModel().stopSimulation();lattice.getDisturbance().cancel();scene.clearProbeArrivalMeasurements();
        occupied.setNode(sourceNode);
        nodes=lattice.getNodeArray();
        var geometricCenter=new Vector3d();
        for(Object value:nodes) {
            Node node=(Node)value;geometricCenter.add(node.getEquilibriumPosition());
            node.setPosition(new Vector3d(node.getEquilibriumPosition()));node.updatePosition();
            node.getVelocity().zero();node.getAcceleration().zero();node.getForce().zero();node.resetAuxiliaryFieldMotion();
        }
        sourceCenterOffset=geometricCenter.div(nodes.length).distance(occupied.getNode().getEquilibriumPosition());
        identity.reset();lattice.setDiamondIdentityExcitationEnabled(true);
        scene.updateProbeMeasurements(remframework.opengl.controller.TransportSystem.getInstance());
        scene.startDiamondProbeMeasurements(selected,ProbeArrivalMeasurement.availableShells(selected).getFirst(),positionThreshold,gradientThreshold);
        scene.getProbeShellArrivalReport().enableSharedSweep(requestedPeriod,measurementCycles,
                occupied.getNode().getEquilibriumPosition(),(long)warmupCycles*requestedPeriod);
        source=identity;probes=selected;period=requestedPeriod;cycles=measurementCycles;warmup=warmupCycles;samples=0;
        increments=new Vector3d(identity.getPhaseIncrement());angles=new Vector3d(identity.getPhaseAngle());amplitude=new Vector3d(identity.getAmplitude());
        k=project.getConstantFrequency();gamma=project.getConstantElectricFactor();running=true;
        spongeEnabled=lattice.isDiamondSpongeEnabled();spongeLayers=lattice.getNumEdgeAbsorptionLayers();
        equalAreaOrbit=identity.isEqualAreaOrbit();
        status="Recording Identity orbit: period "+period+"; warm-up "+warmup+"; measured cycles "+cycles+". Reflections included.";
        project.getRemModel().startSimulation();
    }
    public void cancel(Scene scene,String reason) {
        if(running){running=false;scene.finishProbeArrivalMeasurements();status=reason;}
    }
    /** Runs after the existing passive probe sample; it does not advance the source. */
    public void sampled(Scene scene) {
        if(!running)return;
        var project=Project.getInstance();var lattice=scene.getLattice();
        if(nodes!=lattice.getNodeArray()||!probes.equals(scene.getProbes())||!source.isXyzOrbit()
                ||equalAreaOrbit!=source.isEqualAreaOrbit()
                ||!increments.equals(source.getPhaseIncrement())||!angles.equals(source.getPhaseAngle())||!amplitude.equals(source.getAmplitude())
                ||k!=project.getConstantFrequency()||gamma!=project.getConstantElectricFactor()
                ||spongeEnabled!=lattice.isDiamondSpongeEnabled()||spongeLayers!=lattice.getNumEdgeAbsorptionLayers()
                ||!lattice.isDiamondIdentityExcitationEnabled()||lattice.isDiamondTransportEnabled()||lattice.isDiamondPressureEnabled()
                ||project.occupancies.stream().noneMatch(o->o.getIdentity()==source&&o.isOccupied())
                ||project.occupancies.stream().anyMatch(o->o.getIdentity()!=source&&o.getIdentity().isActive())
                ||source.getOrbitUpdates()!=samples+1) {
            cancel(scene,"Incomplete: source, mechanics, probes or source clock changed; restart measurement");return;
        }
        samples++;
        long target=(long)(warmup+cycles)*period;
        if(samples>=target) {
            running=false;scene.finishProbeArrivalMeasurements();project.getRemModel().stopSimulation();
            status="Complete: "+cycles+" shared Identity orbit cycles after "+warmup+" warm-up cycles; "+samples+" probe updates. Reflections included.";
        } else status="Recording Identity orbit: "+samples+" / "+target+" probe updates; period "+period+". Reflections included.";
    }
    public void writeSummary(Scene scene,java.io.Writer writer) throws java.io.IOException {
        writer.write("shell,radius_bonds,metric,complete_directions,total_directions,min,max,mean,spread_percent,source_period_updates,warmup_cycles,measurement_cycles,source_amplitude_x,source_amplitude_y,source_amplitude_z,source_offset_from_shell_center,reflections_included,edge_absorber_enabled,edge_absorber_layers,equal_area_orbit\n");
        for(var row:scene.getProbeShellArrivalReport().responseSpreads())writer.write(String.format(Locale.ROOT,
            "%d,%.17g,gradient_rms_response,%d,%d,%.17g,%.17g,%.17g,%.17g,%d,%d,%d,%.17g,%.17g,%.17g,%.17g,true,%s,%d,%s%n",
            row.shell()+1,row.radius(),row.complete(),row.total(),row.minimum(),row.maximum(),row.mean(),row.spreadPercent(),period,warmup,cycles,amplitude.x,amplitude.y,amplitude.z,sourceCenterOffset,spongeEnabled,spongeLayers,equalAreaOrbit));
        for(var row:scene.getProbeShellArrivalReport().cycleArrivals())writer.write(String.format(Locale.ROOT,
            "%d,%.17g,%s_first_arrival_timing,%d,%d,%.17g,%.17g,%.17g,%.17g,%d,%d,%d,%.17g,%.17g,%.17g,%.17g,true,%s,%d,%s%n",
            row.shell()+1,row.radius(),row.signal(),row.completeDirections(),row.totalDirections(),row.minimum(),row.maximum(),row.mean(),row.spreadPercent(),period,warmup,cycles,amplitude.x,amplitude.y,amplitude.z,sourceCenterOffset,spongeEnabled,spongeLayers,equalAreaOrbit));
    }
    public void writeProbes(Scene scene,java.io.Writer writer) throws java.io.IOException {
        writer.write("shell,radius_bonds,probe_id,window_start_update,window_end_update,samples,gradient_rms,mean_imbalance_x,mean_imbalance_y,mean_imbalance_z,position_first_arrival_update,gradient_first_arrival_update,status,source_increment_x,source_increment_y,source_increment_z,k,gamma\n");
        for(var row:scene.getProbeShellArrivalReport().sweepRows()) {
            var r=row.result().measurement();
            var arrival=scene.getProbeShellArrivalReport().rows().stream().filter(shell->shell.shell()==row.shell()).flatMap(shell->shell.snapshot().results().stream())
                    .filter(probe->probe.probeId().equals(row.result().probeId())).findFirst().orElse(null);
            writer.write(String.format(Locale.ROOT,"%d,%.17g,\"%s\",%d,%d,%d,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%s,%.17g,%.17g,%.17g,%.17g,%.17g%n",
                row.shell()+1,row.radius(),row.result().probeId().replace("\"","\"\""),r.startUpdate(),r.endUpdate(),r.samples(),r.rms(),r.meanX(),r.meanY(),r.meanZ(),
                arrival==null?Double.NaN:arrival.positionEstimatedArrival(),arrival==null?Double.NaN:arrival.gradientEstimatedArrival(),r.status(),increments.x,increments.y,increments.z,k,gamma));
        }
    }
}
