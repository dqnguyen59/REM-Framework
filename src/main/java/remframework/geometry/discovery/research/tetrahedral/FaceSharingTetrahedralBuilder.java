package remframework.geometry.discovery.research.tetrahedral;

import java.util.*;
import org.joml.Vector3d;
import remframework.geometry.discovery.*;
import static remframework.geometry.discovery.research.tetrahedral.FaceSharingTetrahedralExperiment.*;

/**
 * Reversible, breadth-first display construction; face order is deterministic, not a symmetry claim.
 * <br><br>
 * The original mode preserves equal connected lengths; the opt-in sharing mode treats base length as a target.
 */
public final class FaceSharingTetrahedralBuilder {
    private record State(Snapshot snapshot,List<Face> frontier,int layer,int nextFace) {
        private State { frontier=List.copyOf(frontier); }
    }
    private final int maximumNodes;
    private record Saved(State state,GeometryDiscoveryModel model,double baseLength,List<GeometryConfigurationStatus> statuses,FaceSharingTetrahedralDeformation.Result deformation) { }
    private final Deque<Saved> history=new ArrayDeque<>();
    private final boolean reuseNodeEight;
    private boolean continuous;
    private GeometryDiscoverySession liveSession;
    private int stableUpdates,liveUpdates;
    private double movement;
    private int remainingNodes,batchAdded,batchAttachments;
    private boolean livePaused,batchFinishing;
    private GeometryDiscoveryModel deformedModel;
    private List<GeometryConfigurationStatus> layoutStatuses=List.of();
    private FaceSharingTetrahedralDeformation.Result deformation;
    private State state;
    private double baseLength;
    private GeometryConfigurationStatus actionStatus;
    private FaceSharingTetrahedralPreview.Preview preview;

    public FaceSharingTetrahedralBuilder(double baseLength,int maximumNodes) {
        this(baseLength,maximumNodes,false);
    }
    public FaceSharingTetrahedralBuilder(double baseLength,int maximumNodes,boolean reuseNodeEight) {
        this.reuseNodeEight=reuseNodeEight;
        if(maximumNodes<4 || maximumNodes>1000 || !validLength(baseLength))
            throw new IllegalArgumentException("Base length must be in [1e-6, 1e6]; node limit in [4,1000].");
        this.baseLength=baseLength;this.maximumNodes=maximumNodes;reset();
    }
    public void reset() {
        remainingNodes=0;batchFinishing=false;
        Snapshot seed=new FaceSharingTetrahedralExperiment().generate(0,4).generations().getFirst();
        state=new State(seed,seed.exposedFaces(),1,0);history.clear();
        actionStatus=info(reuseNodeEight
            ? "Seed ABCD. Base length is a target; existing nodes may close five-tetrahedron edge rings."
            : "Seed ABCD. Add one node per exposed face; complete each layer before the next.");
        if(reuseNodeEight) {
            deformedModel=FaceSharingTetrahedralRelaxation.solverInput(FaceSharingTetrahedralPreview.create(0,4,baseLength).model());
            deform();
        } else rebuild();
    }
    public boolean addNode() {
        Snapshot previous=state.snapshot();
        Face face=state.frontier().get(state.nextFace());
        Set<Integer> candidates=reuseNodeEight?closureCandidates(previous,face):Set.of();
        if(candidates.size()>1) {
            actionStatus=stop(GeometryConfigurationStatusCode.AMBIGUOUS_COMBINATORIAL_FACE_SET,
                "Multiple five-ring closure candidates: "+candidates+". Previous topology retained.");return false;
        }
        boolean reuse=!candidates.isEmpty();
        if(!reuse && previous.nodeCount()>=maximumNodes) {
            actionStatus=stop(GeometryConfigurationStatusCode.RESOURCE_LIMIT_REACHED,
                "Node limit "+maximumNodes+" reached. Current partial layer retained; removal remains available.");
            return false;
        }
        int id=reuse?candidates.iterator().next():previous.nodeCount();
        List<Integer> births=new ArrayList<>(previous.nodeBirthGenerations());
        if(!reuse) births.add(state.layer());
        Map<Long,DiscoveryConnection> edgeMap=new LinkedHashMap<>();
        previous.edges().forEach(edge -> edgeMap.put(edge.undirectedKey(),edge));
        for(int vertex:face.nodes()) {
            var edge=new DiscoveryConnection(vertex,id);edgeMap.putIfAbsent(edge.undirectedKey(),edge);
        }
        var tetrahedron=new Tetrahedron(face.a(),face.b(),face.c(),id,state.layer());
        List<Tetrahedron> tetrahedra=new ArrayList<>(previous.tetrahedra());tetrahedra.add(tetrahedron);
        Map<Face,Integer> incidence=new LinkedHashMap<>(previous.faceIncidence());
        tetrahedron.faces().forEach(f -> incidence.merge(f,1,Integer::sum));
        if(incidence.values().stream().anyMatch(count -> count>2)
            || previous.faceIncidence().getOrDefault(face,0)!=1) {
            actionStatus=stop(GeometryConfigurationStatusCode.NON_MANIFOLD_COMBINATORIAL_BOUNDARY,
                "Attachment rejected: exposed face required and no face may have more than two tetrahedra.");
            return false;
        }
        Snapshot next=new Snapshot(state.layer(),births,List.copyOf(edgeMap.values()),tetrahedra,incidence);
        if(reuseNodeEight) {
            var boundaryFailures=FaceSharingTetrahedralClosure.validateBoundary(next);
            if(!boundaryFailures.isEmpty()) {
                actionStatus=stop(GeometryConfigurationStatusCode.NON_MANIFOLD_COMBINATORIAL_BOUNDARY,
                    "Attachment rejected; topology retained. "+boundaryFailures);
                return false;
            }
        }
        GeometryDiscoveryModel nextModel=null;
        if(reuseNodeEight) {
            nextModel=attachmentModel(next,face,id,reuse);
            if(nextModel==null) return false;
        }
        history.push(new Saved(state,deformedModel==null?null:deformedModel.copy(),baseLength,layoutStatuses,deformation));
        int cursor=state.nextFace()+1;
        while(cursor<state.frontier().size() && next.faceIncidence().getOrDefault(state.frontier().get(cursor),0)!=1) cursor++;
        if(cursor==state.frontier().size()) state=new State(next,next.exposedFaces(),state.layer()+1,0);
        else state=new State(next,state.frontier(),state.layer(),cursor);
        actionStatus=info(reuse
            ? "Reused existing node "+id+" for face "+face+". Added "+(next.edges().size()-previous.edges().size())+" missing connections; no new node allocated. "+progress()+"."
            : "Added node "+id+" to face "+face+". "+progress()+".");
        if(reuseNodeEight) { deformedModel=nextModel;deform(); } else rebuild();
        return true;
    }
    public record LengthCheck(int firstNode,int secondNode,double length,double absoluteError,
            double tolerance,boolean matches) { }
    public record BatchResult(int requestedNodes,int addedNodes,int attachments,boolean completed,LengthCheck lengths) { }

    /** Measures connections only, using the existing solver's absolute distance tolerance. */
    public LengthCheck checkTargetLengths() {
        var model=model();
        double tolerance=GeometryDiscoverySettings.defaults(baseLength).convergenceTolerance();
        double worst=-1,length=Double.NaN;int first=-1,second=-1;
        for(var edge:model.connections()) {
            double measured=model.nodes().get(edge.firstNodeId()).position()
                .distance(model.nodes().get(edge.secondNodeId()).position());
            double error=Math.abs(measured-baseLength);
            if(!Double.isFinite(error)) return new LengthCheck(edge.firstNodeId(),edge.secondNodeId(),measured,
                Double.POSITIVE_INFINITY,tolerance,false);
            if(error>worst) { worst=error;length=measured;first=edge.firstNodeId();second=edge.secondNodeId(); }
        }
        return new LengthCheck(first,second,length,worst,tolerance,worst<=tolerance);
    }

    /** Stops at the first failed connection check, retaining the failing attachment for inspection and undo. */
    public BatchResult addNodes(int requestedNodes) {
        int initial=snapshot().nodeCount(),attachments=0;
        if(requestedNodes<1 || requestedNodes>1000) {
            actionStatus=stop(GeometryConfigurationStatusCode.INVALID_PARAMETERS,"Additional nodes must be an integer from 1 to 1000.");
            return new BatchResult(requestedNodes,0,0,false,checkTargetLengths());
        }
        while(snapshot().nodeCount()-initial<requestedNodes) {
            var check=checkTargetLengths();
            if(!check.matches()) return failedBatch(requestedNodes,initial,attachments,check);
            if(attachments>=1000) {
                actionStatus=stop(GeometryConfigurationStatusCode.RESOURCE_LIMIT_REACHED,
                    "Batch stopped after 1000 attachments; requested additional nodes not reached. Current topology retained.");
                return new BatchResult(requestedNodes,snapshot().nodeCount()-initial,attachments,false,check);
            }
            if(!addNode()) return new BatchResult(requestedNodes,snapshot().nodeCount()-initial,attachments,false,checkTargetLengths());
            attachments++;
            check=checkTargetLengths();
            if(!check.matches()) return failedBatch(requestedNodes,initial,attachments,check);
        }
        var check=checkTargetLengths();
        actionStatus=info("Batch completed: added "+requestedNodes+" nodes in "+attachments
            +" attachments. Every connection matches target "+baseLength+" within absolute tolerance "+check.tolerance()+".");
        return new BatchResult(requestedNodes,requestedNodes,attachments,true,check);
    }
    private BatchResult failedBatch(int requested,int initial,int attachments,LengthCheck check) {
        int added=snapshot().nodeCount()-initial;
        actionStatus=stop(GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS,
            String.format(Locale.ROOT,"Batch stopped: added %d of %d nodes in %d attachments. Connection %d-%d: "
                +"length %.12g, target %.12g, absolute error %.12g, tolerance %.12g. "
                +"Cannot set all connections to target base length within current layout solver limits. "
                +"Settled=%s; updates=%d. Failing model retained; use minus to undo. "
                +"This is not proof of geometric impossibility. Latest attachment: %s",
                added,requested,attachments,check.firstNode(),check.secondNode(),check.length(),baseLength,
                check.absoluteError(),check.tolerance(),deformation!=null && deformation.stationary(),
                deformation==null?0:deformation.iterations(),actionStatus.explanation()));
        return new BatchResult(requested,added,attachments,false,check);
    }

    private GeometryDiscoveryModel attachmentModel(Snapshot next,Face face,int id,boolean reuse) {
        List<DiscoveryNode> nodes=new ArrayList<>(deformedModel.sortedNodes().stream().map(DiscoveryNode::copy).toList());
        if(!reuse) {
            Vector3d a=nodes.get(face.a()).position();
            Vector3d center=new Vector3d(a).add(nodes.get(face.b()).position()).add(nodes.get(face.c()).position()).div(3);
            Vector3d normal=new Vector3d(nodes.get(face.b()).position()).sub(a)
                .cross(new Vector3d(nodes.get(face.c()).position()).sub(a));
            if(normal.lengthSquared()<Math.pow(baseLength,4)*1e-20) {
                actionStatus=stop(GeometryConfigurationStatusCode.DEGENERATE,"Attachment face is degenerate; previous model retained.");
                return null;
            }
            normal.normalize();
            var parent=state.snapshot().tetrahedra().stream().filter(t -> t.faces().contains(face)).findFirst().orElseThrow();
            int opposite=List.of(parent.a(),parent.b(),parent.c(),parent.d()).stream()
                .filter(vertex -> !face.nodes().contains(vertex)).findFirst().orElseThrow();
            if(new Vector3d(nodes.get(opposite).position()).sub(center).dot(normal)>0) normal.negate();
            nodes.add(new DiscoveryNode(id,center.add(normal.mul(Math.sqrt(2.0/3)*baseLength)),false));
        }
        // The reused node stays in place as the initial guess; no midpoint or alias node is created.
        return new GeometryDiscoveryModel("tetrahedral-sharing-builder",
            "Incremental shared-node construction; base length is a soft target.",nodes,next.edges());
    }
    public void enableContinuousLayout() { continuous=true; remainingNodes=0;batchFinishing=false;beginLiveLayout(); }
    public boolean continuousLayout() { return continuous; }
    public boolean layoutPaused() { return livePaused; }
    public void toggleLayoutPause() { livePaused=!livePaused; }
    public double liveMovement() { return movement; }
    public int liveUpdates() { return liveUpdates; }
    public int remainingBatchNodes() { return remainingNodes; }
    public void queueNodes(int count) {
        if(count<1 || count>1000) {
            actionStatus=stop(GeometryConfigurationStatusCode.INVALID_PARAMETERS,"Additional nodes must be 1..1000.");return;
        }
        remainingNodes=count;batchFinishing=false;batchAdded=0;batchAttachments=0;livePaused=false;liveUpdates=0;stableUpdates=0;
        actionStatus=info("Batch queued: "+count+" additional nodes; waiting for whole-network settling.");
    }
    public void queueToTotal(int total) {
        if(total<snapshot().nodeCount() || total>maximumNodes) {
            actionStatus=stop(GeometryConfigurationStatusCode.INVALID_PARAMETERS,
                "Desired total must be between current count "+snapshot().nodeCount()+" and node limit "+maximumNodes+".");return;
        }
        if(total==snapshot().nodeCount()) { cancelBatch();actionStatus=info("Desired total already reached: "+total+" nodes.");return; }
        queueNodes(total-snapshot().nodeCount());
    }
    public void cancelBatch() { remainingNodes=0;batchFinishing=false;actionStatus=info("Batch cancelled; continuous layout remains active."); }
    private void beginLiveLayout() {
        liveSession=new GeometryDiscoverySession(deformedModel,GeometryDiscoverySettings.defaults(baseLength).withMaximumIterations(2000));
        liveSession.start();stableUpdates=0;liveUpdates=0;movement=Double.POSITIVE_INFINITY;livePaused=false;
    }
    /** Bounded per-frame work. Stationarity permits deformed lengths; exact solver criteria are unchanged. */
    public void updateLayout(int steps) {
        if(!continuous || livePaused) return;
        for(int i=0;i<steps;i++) {
            if(liveSession.isFinished()) {
                liveSession=new GeometryDiscoverySession(deformedModel,GeometryDiscoverySettings.defaults(baseLength).withMaximumIterations(2000));
                liveSession.start();
            }
            var before=deformedModel.sortedNodes().stream().map(n -> new Vector3d(n.position())).toList();
            liveSession.step();deformedModel=liveSession.model().copy();liveUpdates++;
            movement=0;
            for(int id=0;id<before.size();id++) movement=Math.max(movement,before.get(id).distance(deformedModel.nodes().get(id).position()));
            if(!Double.isFinite(movement) || deformedModel.nodes().stream().anyMatch(n -> !n.position().isFinite())) {
                remainingNodes=0;livePaused=true;actionStatus=stop(GeometryConfigurationStatusCode.NUMERICAL_FAILURE,"Continuous layout became nonfinite; batch stopped.");return;
            }
            stableUpdates=movement<=baseLength*1e-7?stableUpdates+1:0;
        }
        var assessment=FaceSharingTetrahedralRelaxation.assess(deformedModel,state.snapshot(),baseLength*.35,baseLength*1e-9);
        var check=checkTargetLengths();
        deformation=new FaceSharingTetrahedralDeformation.Result(deformedModel.copy(),List.of(),stableUpdates>=20,
            liveUpdates,check.absoluteError()/baseLength,assessment);
        layoutStatuses=List.of(info("Continuous experimental layout: updates="+liveUpdates+", movement="+movement
            +", settled="+(stableUpdates>=20)+". Length deviation="+check.absoluteError()+". Not REM equilibrium."));
        if(remainingNodes==0 && !batchFinishing) return;
        if(stableUpdates>=20 && (assessment.degenerateTetrahedra()>0 || assessment.overlappingTetrahedronPairs()>0)) {
            remainingNodes=0;batchFinishing=false;actionStatus=stop(assessment.degenerateTetrahedra()>0?GeometryConfigurationStatusCode.DEGENERATE:GeometryConfigurationStatusCode.SELF_INTERSECTING,
                "Batch stopped: current layout has overlaps or degenerate tetrahedra; continuous layout remains active.");return;
        }
        if(stableUpdates>=20) {
            if(batchFinishing) {
                batchFinishing=false;actionStatus=info("Batch completed and settled: "+batchAdded+" additional nodes in "+batchAttachments
                    +" attachments. Deformed connection lengths retained; no REM equilibrium claim.");return;
            }
            if(batchAttachments>=1000) { remainingNodes=0;batchFinishing=false;actionStatus=stop(GeometryConfigurationStatusCode.RESOURCE_LIMIT_REACHED,"Batch attachment limit 1000 reached.");return; }
            int before=state.snapshot().nodeCount();
            if(!addNode()) { remainingNodes=0;return; }
            batchAttachments++;int added=state.snapshot().nodeCount()-before;batchAdded+=added;remainingNodes-=added;batchFinishing=remainingNodes==0;
            actionStatus=info("Batch: "+batchAdded+" new nodes, "+batchAttachments+" attachments; "+remainingNodes
                +" remaining. Waiting for settling; deformed lengths allowed.");
        } else if(liveUpdates>=2000) {
            actionStatus=stop(GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS,
                "Waiting for settling beyond 2000 updates. Added "+batchAdded
                +" nodes; "+remainingNodes+" remain queued. Growth resumes automatically when settled. Continuous layout remains active.");
        }
    }
    private void deform() {
        if(continuous) { beginLiveLayout();return; }
        deformation=FaceSharingTetrahedralDeformation.run(deformedModel,state.snapshot(),baseLength,2000);
        deformedModel=deformation.model();layoutStatuses=deformation.statuses();
    }
    public boolean removeNode() {
        remainingNodes=0;batchFinishing=false;
        if(history.isEmpty()) {
            actionStatus=stop(GeometryConfigurationStatusCode.INVALID_PARAMETERS,
                "ABCD is the minimum seed; no attachment is available to undo.");
            return false;
        }
        Saved saved=history.pop();state=saved.state();
        actionStatus=info("Undid the latest attachment (new node or shared-node connection). "+progress()+".");
        if(reuseNodeEight) {
            deformedModel=saved.model().copy();layoutStatuses=saved.statuses();deformation=saved.deformation();
            if(Double.compare(baseLength,saved.baseLength())!=0) {
                scaleModel(baseLength/saved.baseLength());deform();
            }
            if(continuous) beginLiveLayout();
        } else rebuild();
        return true;
    }
    public boolean setBaseLength(double value) {
        remainingNodes=0;batchFinishing=false;
        if(!validLength(value)) {
            actionStatus=stop(GeometryConfigurationStatusCode.INVALID_PARAMETERS,
                "Base length must be finite and between 1e-6 and 1e6. Previous length retained.");
            return false;
        }
        double factor=value/baseLength;baseLength=value;
        actionStatus=info(reuseNodeEight
            ? "Base length target changed to "+baseLength+"; whole network recalculated."
            : "Base length changed to "+baseLength+"; all connected distances rescaled.");
        if(reuseNodeEight) { scaleModel(factor);deform(); } else rebuild();
        return true;
    }
    private void scaleModel(double factor) {
        for(var node:deformedModel.nodes()) node.position().mul(factor);
    }
    public boolean sharingMode() { return reuseNodeEight; }
    public FaceSharingTetrahedralDeformation.Result deformation() { return deformation; }
    private static boolean validLength(double value) { return Double.isFinite(value) && value>=1e-6 && value<=1e6; }
    private void rebuild() {
        var run=new Run(state.snapshot().generation(),List.of(state.snapshot()),info(progress()
            +". This snapshot may contain a partial layer; no enumeration or isotropy claim."));
        preview=FaceSharingTetrahedralPreview.fromSnapshot(state.snapshot(),run,baseLength);
    }
    public GeometryDiscoveryModel model() {
        if(reuseNodeEight) return deformedModel.copy();
        // Rigidly align the existing viewer's anchor convention without changing any distance.
        return FaceSharingTetrahedralRelaxation.solverInput(preview.model());
    }
    public List<GeometryConfigurationStatus> statuses() {
        List<GeometryConfigurationStatus> result=new ArrayList<>(reuseNodeEight?layoutStatuses:preview.statuses());result.add(actionStatus);
        return List.copyOf(result);
    }
    public Snapshot snapshot() { return state.snapshot(); }
    public double baseLength() { return baseLength; }
    public int currentLayer() { return state.layer(); }
    public int additionsInLayer() { return state.nextFace(); }
    public int layerCapacity() { return state.frontier().size(); }
    public int completedGeneration() { return state.layer()-1; }
    public boolean canRemove() { return !history.isEmpty(); }
    public boolean canAdd() {
        return state.snapshot().nodeCount()<maximumNodes || (reuseNodeEight
            && !closureCandidates(state.snapshot(),state.frontier().get(state.nextFace())).isEmpty());
    }
    /** Coordinate-free five-ring policy; an independent experiment, not REM mechanics. */
    static Set<Integer> closureCandidates(Snapshot snapshot,Face face) {
        Set<Integer> candidates=new TreeSet<>();
        List<Integer> vertices=face.nodes();
        for(int i=0;i<3;i++) {
            int u=vertices.get(i),v=vertices.get((i+1)%3),end=vertices.get((i+2)%3),count=0;
            Map<Integer,Set<Integer>> link=new HashMap<>();
            for(var tetra:snapshot.tetrahedra()) {
                List<Integer> nodes=List.of(tetra.a(),tetra.b(),tetra.c(),tetra.d());
                if(!nodes.contains(u)||!nodes.contains(v)) continue;
                var pair=nodes.stream().filter(n -> n!=u && n!=v).toList();
                link.computeIfAbsent(pair.get(0),k -> new HashSet<>()).add(pair.get(1));
                link.computeIfAbsent(pair.get(1),k -> new HashSet<>()).add(pair.get(0));count++;
            }
            if(count!=4 || link.size()!=5 || link.getOrDefault(end,Set.of()).size()!=1) continue;
            var endpoints=link.entrySet().stream().filter(e -> e.getValue().size()==1).map(Map.Entry::getKey).toList();
            if(endpoints.size()!=2 || link.values().stream().anyMatch(n -> n.size()>2)) continue;
            Set<Integer> visited=new HashSet<>();Deque<Integer> queue=new ArrayDeque<>();queue.add(end);
            while(!queue.isEmpty()) { int n=queue.remove();if(visited.add(n)) queue.addAll(link.get(n)); }
            if(visited.size()!=5) continue;
            int apex=endpoints.get(0)==end?endpoints.get(1):endpoints.get(0);
            if(face.nodes().contains(apex)) continue;
            candidates.add(apex);
        }
        return Collections.unmodifiableSet(candidates);
    }
    public String progress() { return "Completed layer "+completedGeneration()
        +"; next/current layer "+currentLayer()+": "+additionsInLayer()+"/"+layerCapacity()+" added"; }
    private static GeometryConfigurationStatus info(String explanation) {
        return GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.info(
            GeometryConfigurationStage.GRAPH_GENERATION,GeometryConfigurationStatusCode.GENERATED,
            "Incremental tetrahedral construction",explanation));
    }
    private static GeometryConfigurationStatus stop(GeometryConfigurationStatusCode code,String explanation) {
        return GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.stop(
            GeometryConfigurationStage.GRAPH_GENERATION,code,"Incremental construction action stopped",explanation));
    }
}
