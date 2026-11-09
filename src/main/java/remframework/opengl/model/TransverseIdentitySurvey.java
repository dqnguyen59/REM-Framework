package remframework.opengl.model;

import java.nio.file.*;
import java.util.*;
import java.io.*;
import org.joml.Vector3d;
import remframework.geometry.discovery.research.diamond.DiamondDirectionalComparison;
import remframework.opengl.model.entity.Identity;
import remframework.opengl.model.entity.Node;
import remframework.settings.Project;

/** Sequential rotated Identity pulses, sampled at completed physics frames. */
public final class TransverseIdentitySurvey {
    private volatile boolean requested,running,next,cancelRequested;
    private volatile String status="Not started";
    private Identity source;
    private Object[] nodes;
    private Vector3d origin;
    private Vector3d latticeCenter;
    private List<Vector3d> orientations;
    private List<Receiver> receivers;
    private remframework.lattice.DiamondTopology topology;
    private DiamondProbePaths bondPaths;
    private int orientation,step,limit,rays,first,spacing,shells,period,cycles,maxUpdates,quietUpdates;
    private double amplitude,floor,k,gamma,boundaryDistance;
    private boolean sponge;
    private int spongeLayers;
    private Path directory;
    private BufferedWriter detail,summary,traces,windows;
    private final List<Row> rows=new ArrayList<>();
    private int completedOrientations;
    private List<ShellSummary> displayShells=List.of();
    private List<ReceiverDistance> displayReceiverDistances=List.of();
    private volatile ViewState view=ViewState.empty();
    /** Immutable data published to the UI; rendering never reads the physics arrays. */
    public record ShellSummary(int shell,double radius,int arrived,int total,int complete,
                               double minimum,double maximum,double mean,double arrivalSpread,
                               int peaks,double peakSpread,double targetDistanceMinimum,double targetDistanceMaximum,
                               int distances,double distanceMinimum,double distanceMaximum,
                               int paths,double minimumHops,double maximumHops,double weightedHopMinimum,double weightedHopMaximum) {
        public double range(){return maximum-minimum;}
        public boolean provisional(){return complete<total;}
        public double targetDistanceRange(){return targetDistanceMaximum-targetDistanceMinimum;}
        public double distanceRange(){return distanceMaximum-distanceMinimum;}
        public double hopRange(){return maximumHops-minimumHops;}
        public double weightedHopRange(){return weightedHopMaximum-weightedHopMinimum;}
    }
    public record Trace(int shell,int ray,double radius,List<Double> samples) { }
    public record ReceiverDistance(int shell,int orientation,int ray,double nx,double ny,double nz,
                                   double targetRadius,double actualDistance,double latticeCenterDistance,boolean reconstructed) {
        public double difference(){return actualDistance-targetRadius;}
    }
    public record ViewState(boolean running,int completed,int orientation,int step,int limit,
                            double px,double py,double pz,boolean absorber,int layers,
                            int dimensionX,int dimensionY,int dimensionZ,int shells,int rays,
                            int period,int cycles,double amplitude,String location,
                            List<ShellSummary> results,List<Trace> traces,List<ReceiverDistance> receiverDistances) {
        public static ViewState empty(){return new ViewState(false,0,0,0,0,0,0,0,false,0,
                0,0,0,0,0,0,0,0,"",List.of(),List.of(),List.of());}
        public double progress(){return Math.min(1,(completed+(running&&limit>0?(double)step/limit:0))/26);}
    }
    public ViewState view(){return view;}

    /** Load the newest fully saved orientation sweep, without rerunning the mechanics. */
    public void loadLatestResults(Path root) throws IOException {
        if(running())throw new IOException("Stop the survey before loading saved results");
        if(!Files.isDirectory(root))throw new IOException("No saved survey results yet");
        List<Path> candidates;
        try(var paths=Files.list(root)) {
            candidates=paths.filter(Files::isDirectory).filter(p->p.getFileName().toString().matches("transverse-\\d+"))
                    .sorted(Comparator.comparing((Path p)->p.getFileName().toString()).reversed()).toList();
        }
        for(var candidate:candidates) {
            var summaryPath=candidate.resolve("summary.csv");
            var configPath=candidate.resolve("configuration.properties");
            if(!Files.exists(summaryPath)||!Files.exists(configPath))continue;
            var props=new Properties();try(var reader=Files.newBufferedReader(configPath)){props.load(reader);}
            var lines=Files.readAllLines(summaryPath);
            var auditPath=candidate.resolve("bond-path-summary.csv");
            if(Files.exists(auditPath))lines.addAll(Files.readAllLines(auditPath).stream().skip(1).toList());
            var pooled=new HashMap<Integer,Map<String,String[]>>();
            var planes=new HashSet<Integer>();
            try {
                for(int i=1;i<lines.size();i++) {
                    var c=lines.get(i).split(",",-1);if(c.length!=12)continue;
                    if(c[0].equals("plane"))planes.add(Integer.parseInt(c[1]));
                    if(c[0].equals("all"))pooled.computeIfAbsent(Integer.parseInt(c[2]),s->new HashMap<>()).put(c[4],c);
                }
                int count=Integer.parseInt(props.getProperty("shells"));
                if(planes.size()!=26||pooled.size()!=count)continue;
                var loaded=new ArrayList<ShellSummary>();
                for(int shell=0;shell<count;shell++) {
                    var metrics=pooled.get(shell);var a=metrics.get("arrival");var peak=metrics.get("peak");var rms=metrics.get("rms");
                    if(a==null||peak==null||rms==null)throw new IllegalArgumentException("Missing shell measurements");
                    var target=metrics.get("target_distance");var distance=metrics.get("probe_distance");
                    var minHops=metrics.get("stencil_min_hops");var maxHops=metrics.get("stencil_max_hops");var weighted=metrics.get("weighted_hops");
                    // Historical surveys stored the exact target radius, but not the weighted rest coordinate.
                    double targetRadius=Double.parseDouble(a[3]);
                    loaded.add(new ShellSummary(shell,targetRadius,Integer.parseInt(a[5]),Integer.parseInt(a[6]),
                            Integer.parseInt(rms[5]),Double.parseDouble(a[7]),Double.parseDouble(a[8]),Double.parseDouble(a[9]),
                            Double.parseDouble(a[11]),Integer.parseInt(peak[5]),Double.parseDouble(peak[11]),
                            target==null?targetRadius:Double.parseDouble(target[7]),target==null?targetRadius:Double.parseDouble(target[8]),
                            distance==null?0:Integer.parseInt(distance[5]),distance==null?Double.NaN:Double.parseDouble(distance[7]),
                            distance==null?Double.NaN:Double.parseDouble(distance[8]),weighted==null?0:Integer.parseInt(weighted[5]),
                            minHops==null?Double.NaN:Double.parseDouble(minHops[7]),maxHops==null?Double.NaN:Double.parseDouble(maxHops[8]),
                            weighted==null?Double.NaN:Double.parseDouble(weighted[7]),weighted==null?Double.NaN:Double.parseDouble(weighted[8])));
                }
                view=new ViewState(false,26,25,Integer.parseInt(props.getProperty("recordingUpdates")),
                        Integer.parseInt(props.getProperty("recordingUpdates")),0,0,0,Boolean.parseBoolean(props.getProperty("absorberEnabled")),
                        Integer.parseInt(props.getProperty("absorberLayers")),Integer.parseInt(props.getProperty("dimensionX")),
                        Integer.parseInt(props.getProperty("dimensionY")),Integer.parseInt(props.getProperty("dimensionZ")),count,
                        Integer.parseInt(props.getProperty("rays")),Integer.parseInt(props.getProperty("periodUpdates")),
                        Integer.parseInt(props.getProperty("pulseCycles")),Double.parseDouble(props.getProperty("amplitude")),
                        candidate.toString(),List.copyOf(loaded),List.of(),loadReceiverDistances(candidate,Integer.parseInt(props.getProperty("rays"))));
                status="Loaded completed survey: 26 / 26 orientations";return;
            } catch(IllegalArgumentException|NullPointerException malformed) {
                throw new IOException("Cannot read survey "+candidate.getFileName()+": "+malformed.getMessage(),malformed);
            }
        }
        throw new IOException("No completed 26-orientation survey found");
    }

    private static List<ReceiverDistance> loadReceiverDistances(Path directory,int rays) throws IOException {
        var path=directory.resolve("probes.csv");boolean reconstructed=false;
        List<String> lines=Files.exists(path)?Files.readAllLines(path):List.of();
        if(lines.isEmpty()||!List.of(lines.getFirst().split(",",-1)).contains("probe_distance")) {
            path=directory.resolve("probe-path-audit.csv");reconstructed=true;
            lines=Files.exists(path)?Files.readAllLines(path):List.of();
        }
        if(lines.isEmpty())return List.of();
        var header=List.of(lines.getFirst().split(",",-1));
        if(!header.containsAll(List.of("orientation","shell","ray","radius","probe_distance")))return List.of();
        var planes=DiamondDirectionalComparison.directions().stream().map(p->TransversePulseAnalysis.plane(p,rays)).toList();
        var result=new ArrayList<ReceiverDistance>();
        for(int line=1;line<lines.size();line++) {
            var c=lines.get(line).split(",",-1);
            if(c.length!=header.size())throw new IOException("Incomplete probe geometry row in "+path.getFileName());
            int orientation=Integer.parseInt(c[header.indexOf("orientation")]),ray=Integer.parseInt(c[header.indexOf("ray")]);
            if(orientation<0||orientation>=26||ray<0||ray>=rays)throw new IOException("Invalid probe geometry direction");
            var direction=planes.get(orientation).get(ray);
            double nx=header.contains("nx")?Double.parseDouble(c[header.indexOf("nx")]):direction.x;
            double ny=header.contains("ny")?Double.parseDouble(c[header.indexOf("ny")]):direction.y;
            double nz=header.contains("nz")?Double.parseDouble(c[header.indexOf("nz")]):direction.z;
            result.add(new ReceiverDistance(Integer.parseInt(c[header.indexOf("shell")]),orientation,ray,nx,ny,nz,
                    Double.parseDouble(c[header.indexOf("radius")]),Double.parseDouble(c[header.indexOf("probe_distance")]),
                    header.contains("lattice_center_distance")?Double.parseDouble(c[header.indexOf("lattice_center_distance")]):Double.NaN,reconstructed));
        }
        return List.copyOf(result);
    }

    private void publishView() {
        if(orientations==null||directory==null)return;
        var p=Project.getInstance();var direction=orientations.get(Math.min(orientation,orientations.size()-1));
        var live=new ArrayList<Trace>();
        if(running&&!next&&receivers!=null)for(var r:receivers)if(r.ray%Math.max(1,rays/4)==0) {
            var samples=new ArrayList<Double>(step+1);for(int t=0;t<=step;t++)samples.add(r.signal[t]);
            live.add(new Trace(r.shell,r.ray,r.radius,List.copyOf(samples)));
        }
        view=new ViewState(running,completedOrientations,Math.min(orientation,25),next?0:step,limit,
                direction.x,direction.y,direction.z,sponge,spongeLayers,p.getDimensionX(),p.getDimensionY(),p.getDimensionZ(),
                shells,rays,period,cycles,amplitude,directory.toString(),displayShells,List.copyOf(live),displayReceiverDistances);
    }

    private void updateDisplayShells() {
        var summaries=new ArrayList<ShellSummary>();
        for(int shell=0;shell<shells;shell++) {
            int index=shell;var selected=rows.stream().filter(r->r.receiver.shell==index).toList();
            var arrivals=selected.stream().mapToDouble(r->r.m.arrival()).filter(Double::isFinite).summaryStatistics();
            var peaks=selected.stream().filter(r->r.m.peakTime()<limit-4).mapToDouble(r->r.m.peak()).filter(Double::isFinite).summaryStatistics();
            var targets=selected.stream().mapToDouble(r->r.receiver.radius).summaryStatistics();
            var distances=selected.stream().mapToDouble(r->r.receiver.distance).filter(Double::isFinite).summaryStatistics();
            var weighted=selected.stream().mapToDouble(r->r.receiver.paths.weightedMean()).summaryStatistics();
            int complete=(int)selected.stream().filter(r->r.m.status().equals("complete")).count();
            double min=arrivals.getCount()==0?Double.NaN:arrivals.getMin(),max=arrivals.getCount()==0?Double.NaN:arrivals.getMax();
            summaries.add(new ShellSummary(shell,first+shell*spacing,(int)arrivals.getCount(),selected.size(),complete,min,max,
                    arrivals.getCount()==0?Double.NaN:arrivals.getAverage(),spread(arrivals), (int)peaks.getCount(),spread(peaks),
                    targets.getMin(),targets.getMax(),(int)distances.getCount(),distances.getMin(),distances.getMax(),
                    selected.size(),selected.stream().mapToInt(r->r.receiver.paths.minimum()).min().orElse(0),
                    selected.stream().mapToInt(r->r.receiver.paths.maximum()).max().orElse(0),weighted.getMin(),weighted.getMax()));
        }
        displayShells=List.copyOf(summaries);
        displayReceiverDistances=rows.stream().map(r->new ReceiverDistance(r.receiver.shell,r.orientation,r.receiver.ray,
                r.receiver.direction.x,r.receiver.direction.y,r.receiver.direction.z,r.receiver.radius,r.receiver.distance,r.receiver.latticeDistance,false)).toList();
    }
    private static double spread(DoubleSummaryStatistics s) {
        return s.getCount()==0||s.getAverage()==0?Double.NaN:100*(s.getMax()-s.getMin())/Math.abs(s.getAverage());
    }
    private record Receiver(int shell,int ray,double radius,double distance,double latticeDistance,DiamondProbePaths.Sample paths,Vector3d direction,List<Node> nodes,
                            List<ProbeDefinition.InterpolationSource> weights,double[] signal,double[] other) { }

    /** Straight-line rest distance to the interpolated coordinate, not mean stencil-node distance. */
    static double interpolatedDistance(Vector3d origin,List<Vector3d> coordinates,List<ProbeDefinition.InterpolationSource> weights) {
        var relative=new Vector3d();
        for(int i=0;i<coordinates.size();i++)relative.fma(weights.get(i).weight(),new Vector3d(coordinates.get(i)).sub(origin));
        return relative.length();
    }
    private record Row(int orientation,Receiver receiver,TransversePulseAnalysis.Metrics m,TransversePulseAnalysis.Transit transit,double attenuation) { }
    public String status(){return status;}
    public boolean running(){return running||requested;}
    public void request(){if(!running){Project.getInstance().getRemModel().stopSimulation();requested=true;}}
    public void requestCancel(){cancelRequested=true;Project.getInstance().getRemModel().stopSimulation();}
    private void cancel(){requested=false;running=false;next=false;if(source!=null)source.endDiagnosticPulse();close();status="Cancelled; partial results retained";publishView();Project.getInstance().getRemModel().stopSimulation();}

    /** Invoked only when no frame calculation is pending. */
    public void advance(Scene scene) {
        try {
            if(cancelRequested){cancelRequested=false;cancel();return;}
            if(requested){requested=false;start(scene);}
            if(running&&next){next=false;beginOrientation(scene);}
        } catch(Exception e){cancel();status="Survey stopped: "+e.getMessage();}
    }
    private void start(Scene scene) throws Exception {
        var p=Project.getInstance();var lattice=scene.getLattice();
        if(!lattice.isMechanicsOnly()||lattice.isDiamondTransportEnabled()||lattice.isDiamondPressureEnabled())
            throw new IllegalArgumentException("Use diamond mechanics with transport and pressure disabled");
        var active=p.occupancies.stream().filter(o->o.isOccupied()&&o.getIdentity().isActive()).toList();
        if(active.size()!=1)throw new IllegalArgumentException("Exactly one active occupied Identity required");
        var occupancy=active.getFirst();source=occupancy.getIdentity();
        var node=lattice.getNodeByIndex(occupancy.getNodeIndex());
        if(node==null||node.isFixed())throw new IllegalArgumentException("Source must occupy a movable node");
        occupancy.setNode(node);origin=new Vector3d(node.getEquilibriumPosition());nodes=lattice.getNodeArray();
        topology=new remframework.lattice.DiamondTopology(p.getDimensionX(),p.getDimensionY(),p.getDimensionZ());
        var blocked=new boolean[topology.size()];
        var lower=new Vector3d(Double.POSITIVE_INFINITY);var upper=new Vector3d(Double.NEGATIVE_INFINITY);
        for(Object value:nodes){var n=(Node)value;blocked[topology.idAtIndex(n.getIndex())]=n.isFixed()||n.isWall();lower.min(n.getEquilibriumPosition());upper.max(n.getEquilibriumPosition());}
        latticeCenter=new Vector3d(lower).add(upper).mul(.5);
        bondPaths=new DiamondProbePaths(topology,topology.idAtIndex(node.getIndex()),id->!blocked[id]);
        lattice.applyPendingAbsorptionLayerChange();
        sponge=lattice.isDiamondSpongeEnabled();spongeLayers=lattice.getNumEdgeAbsorptionLayers();
        k=p.getConstantFrequency();gamma=p.getConstantElectricFactor();
        Path config=Path.of("measurements/diamond-shells/presets/transverse-identity.properties");
        var props=new Properties();if(Files.exists(config))try(var reader=Files.newBufferedReader(config)){props.load(reader);}
        first=integer(props,"firstRadius",8);spacing=integer(props,"spacing",4);shells=integer(props,"shells",4);
        rays=integer(props,"rays",16);period=integer(props,"periodUpdates",8);cycles=integer(props,"pulseCycles",2);
        quietUpdates=integer(props,"quietUpdates",Math.max(8,period*cycles));
        maxUpdates=integer(props,"maxUpdates",160);amplitude=number(props,"amplitude",.01);floor=number(props,"signalFloor",1e-10);
        if(first<1||spacing<1||shells<2||shells>16||rays<8||rays>64||period<4||cycles<1||cycles>8||maxUpdates<8||maxUpdates>4096||quietUpdates<8||quietUpdates>4096||amplitude<=0||floor<=0)
            throw new IllegalArgumentException("Invalid transverse survey configuration");
        boundaryDistance=Double.POSITIVE_INFINITY;
        for(Object value:nodes){var n=(Node)value;if(n.isFixed()||n.isWall()||(sponge&&n.getAbsorptionFactor()<1f))boundaryDistance=Math.min(boundaryDistance,origin.distance(n.getEquilibriumPosition()));}
        // Boundary influence begins at movable nodes touching a fixed/wall node, or in the sponge.
        // Graph distances obey d(source,b)+d(b,receiver) >= 2*d(source,b)-d(source,receiver).
        // Each update reads only direct neighbors; reserve two further updates for ordering.
        int boundaryHops=Integer.MAX_VALUE,stencilHops=0;var adjacent=new int[4];
        double stencilRadius=first+(shells-1)*spacing+5.;
        for(Object value:nodes) {
            var n=(Node)value;int id=topology.idAtIndex(n.getIndex()),h=bondPaths.hops(id);if(h<0)continue;
            if(origin.distance(n.getEquilibriumPosition())<=stencilRadius)stencilHops=Math.max(stencilHops,h);
            boolean affected=sponge&&n.getAbsorptionFactor()<1f;
            int count=topology.neighbors(id,adjacent);
            for(int i=0;i<count;i++)if(blocked[adjacent[i]])affected=true;
            if(affected)boundaryHops=Math.min(boundaryHops,h);
        }
        limit=reflectionFreeLimit(boundaryHops,stencilHops,maxUpdates);
        if(limit<=period*cycles+4)throw new IllegalArgumentException("Boundary-free window too short: "+limit+" updates; reduce radii/pulse length or absorber width");
        scene.clearProbeArrivalMeasurements();lattice.getDisturbance().cancel();lattice.setDiamondIdentityExcitationEnabled(true);
        orientations=DiamondDirectionalComparison.directions();orientation=0;completedOrientations=0;rows.clear();displayShells=List.of();displayReceiverDistances=List.of();
        directory=Path.of("measurements/diamond-shells/transverse-"+System.currentTimeMillis());Files.createDirectories(directory);
        props.setProperty("dimensionX",""+p.getDimensionX());props.setProperty("dimensionY",""+p.getDimensionY());props.setProperty("dimensionZ",""+p.getDimensionZ());
        props.setProperty("k",""+k);props.setProperty("gamma",""+gamma);props.setProperty("sourcePosition",origin.toString());
        props.setProperty("sourceIndexX",""+node.getIndex().x);props.setProperty("sourceIndexY",""+node.getIndex().y);props.setProperty("sourceIndexZ",""+node.getIndex().z);
        props.setProperty("latticeCenterPosition",latticeCenter.toString());props.setProperty("sourceCenterOffset",Double.toString(origin.distance(latticeCenter)));
        props.setProperty("absorberEnabled",""+sponge);props.setProperty("absorberLayers",""+spongeLayers);
        props.setProperty("boundaryDistanceBonds",""+boundaryDistance);props.setProperty("recordingUpdates",""+limit);
        props.setProperty("nearestBoundaryAffectedHops",""+boundaryHops);props.setProperty("maximumStencilHopsBound",""+stencilHops);
        props.setProperty("recordingStopRule","All receivers above signalFloor and last quietUpdates="+quietUpdates+" samples below 1% of each receiver peak; otherwise graph boundary cutoff or maxUpdates. Actual per-orientation lengths are in windows.csv.");
        props.setProperty("orientations","26");props.setProperty("waveform","sin(2*pi*t/period)*sin(pi*t/(period*cycles))^2; finite pulse");
        props.setProperty("probeDistanceDefinition","length(sum(weight * (node_equilibrium_position - source_equilibrium_position))); straight-line bond units");
        props.setProperty("bondPathDefinition","Breadth-first shortest connected-bond counts through movable nodes. Min/max span each probe's positive-weight stencil; weighted mean is a sampling diagnostic, not a wave trajectory.");
        try(var writer=Files.newBufferedWriter(directory.resolve("configuration.properties"))){props.store(writer,"Transverse Identity survey: actual run configuration");}
        detail=Files.newBufferedWriter(directory.resolve("probes.csv"));summary=Files.newBufferedWriter(directory.resolve("summary.csv"));traces=Files.newBufferedWriter(directory.resolve("traces.csv"));
        detail.write("orientation,px,py,pz,shell,radius,ray,nx,ny,nz,arrival_half_peak,peak,peak_update,rms,duration_10pct,period,leakage_rms_ratio,transit_lag,correlation,speed,peak_ratio_to_previous,status,probe_distance,stencil_min_hops,stencil_max_hops,weighted_hops,lattice_center_distance\n");
        summary.write("scope,orientation,shell,radius,metric,valid,total,min,max,mean,absolute_range,spread_percent\n");
        traces.write("orientation,shell,ray,update,parallel_displacement,other_displacement_squared\n");
        windows=Files.newBufferedWriter(directory.resolve("windows.csv"));
        windows.write("orientation,recorded_updates,safe_update_limit,complete_receivers,total_receivers,stop_reason\n");
        running=true;next=true;
    }
    /** Conservative direct-neighbor dependency bound; this is not a wave-speed estimate. */
    static int reflectionFreeLimit(int boundaryHops,int receiverHops,int maximumUpdates) {
        return (int)Math.min(maximumUpdates,2L*boundaryHops-receiverHops-2);
    }
    private static int integer(Properties p,String key,int fallback){int value=Integer.parseInt(p.getProperty(key,""+fallback));p.setProperty(key,""+value);return value;}
    private static double number(Properties p,String key,double fallback){double value=Double.parseDouble(p.getProperty(key,""+fallback));if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite "+key);p.setProperty(key,""+value);return value;}

    private void beginOrientation(Scene scene) {
        var lattice=scene.getLattice();
        for(Object value:nodes){Node n=(Node)value;n.setPosition(new Vector3d(n.getEquilibriumPosition()));n.updatePosition();n.getVelocity().zero();n.getAcceleration().zero();n.getForce().zero();n.resetAuxiliaryFieldMotion();}
        var polarization=orientations.get(orientation);
        var definitions=DiamondProbePlacement.create(nodes,first,spacing,shells,false,TransversePulseAnalysis.plane(polarization,rays),origin);
        receivers=new ArrayList<>();
        for(var d:definitions){var t=d.getPlacement().targets().getFirst();var stencil=d.getInterpolationSources().stream().map(s->lattice.getNodeByIndex(s.nodeIndex())).toList();
            for(int i=0;i<stencil.size();i++)if(d.getInterpolationSources().get(i).weight()>0){var n=stencil.get(i);if(n.isFixed()||(sponge&&n.getAbsorptionFactor()<1f)||n.getEquilibriumPosition().distance(t.requestedCoordinate())>5)
                throw new IllegalArgumentException("Receiver outside validated interior/stencil bound");}
            double distance=interpolatedDistance(origin,stencil.stream().map(Node::getEquilibriumPosition).toList(),d.getInterpolationSources());
            double latticeDistance=interpolatedDistance(latticeCenter,stencil.stream().map(Node::getEquilibriumPosition).toList(),d.getInterpolationSources());
            var paths=bondPaths.sample(stencil.stream().map(n->topology.idAtIndex(n.getIndex())).toList(),d.getInterpolationSources().stream().map(ProbeDefinition.InterpolationSource::weight).toList());
            receivers.add(new Receiver(t.shell(),t.ray(),t.realizedRadius(),distance,latticeDistance,paths,t.requestedDirection(),stencil,d.getInterpolationSources(),new double[limit+1],new double[limit+1]));
        }
        source.startDiagnosticPulse(polarization,amplitude,period,cycles);step=0;Project.getInstance().getRemModel().startSimulation();
        status="Orientation "+(orientation+1)+" / 26; 0 / "+limit+" updates";
        publishView();
    }
    public void sampled(Scene scene) {
        if(!running||next)return;
        try {
            var p=Project.getInstance();var lattice=scene.getLattice();
            if(nodes!=lattice.getNodeArray()||k!=p.getConstantFrequency()||gamma!=p.getConstantElectricFactor()
                    ||sponge!=lattice.isDiamondSpongeEnabled()||spongeLayers!=lattice.getNumEdgeAbsorptionLayers()
                    ||!lattice.isDiamondIdentityExcitationEnabled()||lattice.isDiamondTransportEnabled()||lattice.isDiamondPressureEnabled())
                throw new IllegalStateException("Experiment settings changed");
            step++;var polarization=orientations.get(orientation);
            for(var r:receivers){var displacement=new Vector3d();for(int i=0;i<r.nodes.size();i++){var n=r.nodes.get(i);displacement.fma(r.weights.get(i).weight(),new Vector3d(n.getPosition()).sub(n.getEquilibriumPosition()));}
                double parallel=displacement.dot(polarization);r.signal[step]=parallel;r.other[step]=Math.max(0,displacement.lengthSquared()-parallel*parallel);}
            status="Orientation "+(orientation+1)+" / 26; "+step+" / "+limit+" updates";
            boolean complete=step>=2*period*cycles&&step%4==0&&receivers.stream().allMatch(r->pulseMetrics(r).status().equals("complete"));
            if(complete||step>=limit){p.getRemModel().stopSimulation();finishOrientation();source.endDiagnosticPulse();completedOrientations=orientation+1;orientation++;
                if(orientation<orientations.size())next=true;
                else {for(int s=0;s<shells;s++){int shell=s;summarize("all",-1,s,rows.stream().filter(r->r.receiver.shell==shell).toList());}running=false;close();status="Complete: 26 orientations; results in "+directory;}}
            publishView();
        } catch(Exception e){cancel();status="Survey stopped: "+e.getMessage();}
    }
    private void finishOrientation() throws IOException {
        var polarization=orientations.get(orientation);var current=new ArrayList<Row>();
        for(var r:receivers){var m=pulseMetrics(r);var previous=current.stream().filter(x->x.receiver.shell==r.shell-1&&x.receiver.ray==r.ray).findFirst().orElse(null);
            var transit=previous==null||!previous.m.status().equals("complete")||!m.status().equals("complete")?new TransversePulseAnalysis.Transit(Double.NaN,Double.NaN,Double.NaN):TransversePulseAnalysis.transit(Arrays.copyOf(previous.receiver.signal,step+1),Arrays.copyOf(r.signal,step+1),spacing);
            double ratio=previous==null||m.peakTime()>=limit-4||previous.m.peakTime()>=limit-4?Double.NaN:m.peak()/previous.m.peak();var row=new Row(orientation,r,m,transit,ratio);current.add(row);rows.add(row);
            detail.write(String.format(Locale.ROOT,"%d,%.9g,%.9g,%.9g,%d,%.9g,%d,%.9g,%.9g,%.9g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%.12g,%s,%.17g,%d,%d,%.17g,%.17g%n",orientation,polarization.x,polarization.y,polarization.z,r.shell,r.radius,r.ray,r.direction.x,r.direction.y,r.direction.z,m.arrival(),m.peak(),m.peakTime(),m.rms(),m.duration(),m.period(),m.leakage(),transit.lag(),transit.correlation(),transit.speed(),ratio,m.status(),r.distance,r.paths.minimum(),r.paths.maximum(),r.paths.weightedMean(),r.latticeDistance));
            for(int t=0;t<=step;t++)traces.write(String.format(Locale.ROOT,"%d,%d,%d,%d,%.12g,%.12g%n",orientation,r.shell,r.ray,t,r.signal[t],r.other[t]));
        }
        long complete=current.stream().filter(r->r.m.status().equals("complete")).count();
        windows.write(orientation+","+step+","+limit+","+complete+","+receivers.size()+","+(complete==receivers.size()?"complete_pulses":"safe_or_configured_limit")+"\n");windows.flush();
        for(int s=0;s<shells;s++){int shell=s;summarize("plane",orientation,s,current.stream().filter(r->r.receiver.shell==shell).toList());}
        detail.flush();summary.flush();traces.flush();
        updateDisplayShells();
    }
    private TransversePulseAnalysis.Metrics pulseMetrics(Receiver r) {
        return TransversePulseAnalysis.analyze(Arrays.copyOf(r.signal,step+1),Arrays.copyOf(r.other,step+1),floor,quietUpdates);
    }
    private void summarize(String scope,int orientation,int shell,List<Row> selected) throws IOException {
        for(String metric:List.of("arrival","peak","rms","duration","period","leakage","speed","attenuation","target_distance","probe_distance","stencil_min_hops","stencil_max_hops","weighted_hops")) {
            double[] values=selected.stream().filter(r->switch(metric){case "target_distance","probe_distance","stencil_min_hops","stencil_max_hops","weighted_hops"->true;case "arrival"->Double.isFinite(r.m.arrival());case "peak"->Double.isFinite(r.m.peak())&&r.m.peakTime()<limit-4;case "attenuation"->Double.isFinite(r.attenuation)&&r.m.peakTime()<limit-4;default->r.m.status().equals("complete");})
                    .mapToDouble(r->switch(metric){case "target_distance"->r.receiver.radius;case "probe_distance"->r.receiver.distance;case "stencil_min_hops"->r.receiver.paths.minimum();case "stencil_max_hops"->r.receiver.paths.maximum();case "weighted_hops"->r.receiver.paths.weightedMean();case "arrival"->r.m.arrival();case "peak"->r.m.peak();case "rms"->r.m.rms();case "duration"->r.m.duration();case "period"->r.m.period();case "leakage"->r.m.leakage();case "speed"->r.transit.speed();default->r.attenuation;}).filter(Double::isFinite).toArray();
            var stats=Arrays.stream(values).summaryStatistics();double min=values.length==0?Double.NaN:stats.getMin(),max=values.length==0?Double.NaN:stats.getMax(),mean=values.length==0?Double.NaN:stats.getAverage();
            summary.write(String.format(Locale.ROOT,"%s,%d,%d,%d,%s,%d,%d,%.17g,%.17g,%.17g,%.17g,%.17g%n",scope,orientation,shell,first+shell*spacing,metric,values.length,selected.size(),min,max,mean,max-min,mean==0?Double.NaN:100*(max-min)/Math.abs(mean)));
        }
    }
    private void close(){for(var w:new BufferedWriter[]{detail,summary,traces,windows})if(w!=null)try{w.close();}catch(IOException ignored){}detail=null;summary=null;traces=null;windows=null;}
}
