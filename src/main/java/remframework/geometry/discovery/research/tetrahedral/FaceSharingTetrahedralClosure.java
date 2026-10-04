package remframework.geometry.discovery.research.tetrahedral;

import java.util.*;
import org.joml.Vector3d;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.CombinatorialFaceComplex;
import static remframework.geometry.discovery.research.tetrahedral.FaceSharingTetrahedralExperiment.*;

/**
 * Controlled five-tetrahedron closure around edge 0-1, not a proximity-based merge policy.
 * <br><br>
 * Original nodes 8 and 11 become shared node 6. Base length is a soft geometric target.
 * This is independent Geometry Discovery research, not a crystal or REM qualification.
 */
public final class FaceSharingTetrahedralClosure {
    private FaceSharingTetrahedralClosure() { }
    public record Candidate(GeometryDiscoveryModel model,Snapshot topology,Map<Integer,Integer> originalNodeMapping,
            GeometryConfigurationStatus status) {
        public Candidate { originalNodeMapping=Map.copyOf(originalNodeMapping); }
    }
    public record Trial(Candidate candidate,GeometryDiscoveryResult result,
            FaceSharingTetrahedralRelaxation.Assessment assessment,List<GeometryConfigurationStatus> statuses,
            boolean stationary,double maximumRelativeEdgeError,int iterations) {
        public Trial { statuses=List.copyOf(statuses); }
    }

    public static Candidate candidate(double targetLength) {
        var source=FaceSharingTetrahedralPreview.create(2,20,targetLength);
        if(source.model()==null) return new Candidate(null,null,Map.of(),source.statuses().getFirst());
        var sourceSnapshot=source.topology().generations().getLast();
        var ring=sourceSnapshot.tetrahedra().stream()
            .filter(t -> vertices(t).contains(0) && vertices(t).contains(1)).toList();
        Map<Integer,Integer> mapping=new LinkedHashMap<>();
        for(int id:List.of(0,1,2,3,4,5,8)) mapping.put(id,mapping.size());
        mapping.put(11,mapping.get(8));
        List<Tetrahedron> tetrahedra=new ArrayList<>();
        Map<Face,Integer> incidence=new LinkedHashMap<>();
        Map<Long,DiscoveryConnection> edges=new LinkedHashMap<>();
        for(var t:ring) {
            var ids=vertices(t).stream().map(mapping::get).toList();
            if(ids.stream().distinct().count()!=4)
                return new Candidate(null,null,mapping,stop(GeometryConfigurationStatusCode.DEGENERATE,"Closure collapses a tetrahedron."));
            var closed=new Tetrahedron(ids.get(0),ids.get(1),ids.get(2),ids.get(3),t.generation());
            tetrahedra.add(closed);
            closed.faces().forEach(face -> incidence.merge(face,1,Integer::sum));
            for(int a=0;a<4;a++) for(int b=a+1;b<4;b++) {
                var edge=new DiscoveryConnection(Math.min(ids.get(a),ids.get(b)),Math.max(ids.get(a),ids.get(b)));
                edges.putIfAbsent(edge.undirectedKey(),edge);
            }
        }
        var snapshot=new Snapshot(2,List.of(0,0,0,0,1,1,2),List.copyOf(edges.values()),tetrahedra,incidence);
        var failures=validateClosedRing(snapshot);
        if(!failures.isEmpty())
            return new Candidate(null,snapshot,mapping,stop(GeometryConfigurationStatusCode.NON_MANIFOLD_COMBINATORIAL_BOUNDARY,
                "Controlled closure rejected: "+failures));
        List<DiscoveryNode> nodes=new ArrayList<>();
        for(int original:List.of(0,1,2,3,4,5,8)) {
            var position=new Vector3d(source.model().nodes().get(original).position());
            if(original==8) position.add(source.model().nodes().get(11).position()).mul(.5);
            nodes.add(new DiscoveryNode(mapping.get(original),position,false));
        }
        var model=new GeometryDiscoveryModel("tetrahedral-closure-ring",
            "Controlled five-tetrahedron closure around edge 0-1. Original 8 and 11 share node 6. "
            +"7 nodes, 16 edges, 5 tetrahedra; base length is a soft target.",nodes,snapshot.edges());
        return new Candidate(FaceSharingTetrahedralRelaxation.solverInput(model),snapshot,mapping,
            info(GeometryConfigurationStatusCode.GENERATED,"Local closure validated: five-cycle edge link; "
                +"10 boundary triangles form a connected manifold sphere. No crystal or isotropy claim."));
    }

    /** Reuses the existing solver, with a separate declared stationarity check for deformed targets. */
    public static Trial run(double targetLength,int maximumIterations) {
        var candidate=candidate(targetLength);
        if(candidate.model()==null) return new Trial(candidate,null,null,List.of(candidate.status()),false,Double.NaN,0);
        var settings=GeometryDiscoverySettings.defaults(targetLength).withMaximumIterations(maximumIterations);
        var session=new GeometryDiscoverySession(candidate.model(),settings);
        if(!session.result().validation().isValid())
            return new Trial(candidate,session.result(),null,List.of(stop(GeometryConfigurationStatusCode.INVALID_PARAMETERS,
                session.result().validation().messages().toString())),false,Double.NaN,0);
        int stableSteps=0,iterations=0;boolean stationary=false;
        double movementTolerance=targetLength*1e-7;
        session.start();
        while(!session.isFinished() && iterations<maximumIterations) {
            var before=session.model().sortedNodes().stream().map(n -> new Vector3d(n.position())).toList();
            session.step();iterations++;
            double movement=0;
            for(int id=0;id<before.size();id++)
                movement=Math.max(movement,before.get(id).distance(session.model().sortedNodes().get(id).position()));
            stableSteps=movement<=movementTolerance?stableSteps+1:0;
            if(stableSteps>=20) { stationary=true;session.pause();break; }
        }
        var result=session.result();
        var assessment=FaceSharingTetrahedralRelaxation.assess(result.model(),candidate.topology(),
            settings.minimumNodeSeparation(),targetLength*1e-9);
        double error=0;
        for(var edge:result.model().connections())
            error=Math.max(error,Math.abs(result.model().nodes().get(edge.firstNodeId()).position()
                .distance(result.model().nodes().get(edge.secondNodeId()).position())/targetLength-1));
        String report=String.format(Locale.ROOT,"Local closure: %d iterations; stationary=%s "
            +"(movement <= %.9g for 20 consecutive steps). Edge lengths %.6f..%.6f; "
            +"maximum target error %.4f%%; minimum separation %.6f; overlapping tetrahedron pairs %d; degenerate tetrahedra %d. "
            +"No crystal, periodicity, isotropy or REM equilibrium qualification.",
            iterations,stationary,movementTolerance,result.metrics().minimumConnectedNeighborDistance(),
            result.metrics().maximumConnectedNeighborDistance(),100*error,assessment.minimumSeparation(),
            assessment.overlappingTetrahedronPairs(),assessment.degenerateTetrahedra());
        List<GeometryConfigurationStatus> statuses=new ArrayList<>(List.of(candidate.status()));
        if(!result.metrics().allFinite()) statuses.add(stop(GeometryConfigurationStatusCode.NUMERICAL_FAILURE,report));
        else if(!stationary && !result.converged())
            statuses.add(stop(GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS,report));
        else statuses.add(info(GeometryConfigurationStatusCode.GEOMETRIC_SYMMETRY_UNKNOWN,report));
        if(assessment.overlappingTetrahedronPairs()>0)
            statuses.add(stop(GeometryConfigurationStatusCode.SELF_INTERSECTING,report));
        if(assessment.degenerateTetrahedra()>0) statuses.add(stop(GeometryConfigurationStatusCode.DEGENERATE,report));
        return new Trial(candidate,result,assessment,statuses,stationary,error,iterations);
    }

    /** Variable-degree boundary checks: the existing fixed-degree surface validator cannot represent this ring. */
    static List<String> validateClosedRing(Snapshot snapshot) {
        List<String> failures=new ArrayList<>();
        if(snapshot.tetrahedra().size()!=5) failures.add("EXPECTED_FIVE_TETRAHEDRA");
        if(snapshot.faceIncidence().values().stream().anyMatch(n -> n<1 || n>2)) failures.add("FACE_INCIDENCE");
        if(snapshot.tetrahedra().stream().map(t -> vertices(t).stream().sorted().toList()).distinct().count()!=5)
            failures.add("DUPLICATE_TETRAHEDRON");
        Map<Integer,Set<Integer>> edgeLink=new HashMap<>();
        for(var t:snapshot.tetrahedra()) {
            var ends=vertices(t).stream().filter(id -> id!=0 && id!=1).toList();
            if(ends.size()!=2) { failures.add("EDGE_RING");continue; }
            connect(edgeLink,ends.get(0),ends.get(1));
        }
        if(!cycle(edgeLink)) failures.add("EDGE_LINK_NOT_CLOSED_CYCLE");
        failures.addAll(validateBoundary(snapshot));
        return List.copyOf(failures);
    }
    static List<String> validateBoundary(Snapshot snapshot) {
        List<String> failures=new ArrayList<>();
        if(snapshot.faceIncidence().values().stream().anyMatch(n -> n<1 || n>2)) failures.add("FACE_INCIDENCE");
        if(snapshot.tetrahedra().stream().map(t -> vertices(t).stream().sorted().toList()).distinct().count()!=snapshot.tetrahedra().size())
            failures.add("DUPLICATE_TETRAHEDRON");
        var boundary=new CombinatorialFaceComplex(snapshot.nodeCount(),
            snapshot.exposedFaces().stream().map(Face::nodes).toList());
        Map<Long,Integer> boundaryIncidence=new HashMap<>();
        for(var f:boundary.faces()) for(int i=0;i<3;i++)
            boundaryIncidence.merge(new DiscoveryConnection(f.get(i),f.get((i+1)%3)).undirectedKey(),1,Integer::sum);
        if(boundaryIncidence.values().stream().anyMatch(n -> n!=2)) failures.add("BOUNDARY_EDGE_INCIDENCE");
        Set<Integer> boundaryVertices=new HashSet<>();
        boundary.faces().forEach(boundaryVertices::addAll);
        for(int vertex:boundaryVertices) {
            Map<Integer,Set<Integer>> link=new HashMap<>();
            for(var face:boundary.faces()) if(face.contains(vertex)) {
                int center=vertex;var others=face.stream().filter(id -> id!=center).toList();
                connect(link,others.get(0),others.get(1));
            }
            if(!cycle(link)) failures.add("BOUNDARY_VERTEX_LINK_"+vertex);
        }
        if(boundaryVertices.size()-boundary.edges().size()+boundary.faces().size()!=2)
            failures.add("BOUNDARY_EULER");
        return List.copyOf(failures);
    }
    private static void connect(Map<Integer,Set<Integer>> graph,int a,int b) {
        graph.computeIfAbsent(a,k -> new HashSet<>()).add(b);
        graph.computeIfAbsent(b,k -> new HashSet<>()).add(a);
    }
    private static boolean cycle(Map<Integer,Set<Integer>> graph) {
        if(graph.isEmpty() || graph.values().stream().anyMatch(n -> n.size()!=2)) return false;
        Set<Integer> reached=new HashSet<>();ArrayDeque<Integer> queue=new ArrayDeque<>();
        queue.add(graph.keySet().iterator().next());
        while(!queue.isEmpty()) { int id=queue.remove();if(reached.add(id)) queue.addAll(graph.get(id)); }
        return reached.size()==graph.size();
    }
    private static List<Integer> vertices(Tetrahedron t) { return List.of(t.a(),t.b(),t.c(),t.d()); }
    private static GeometryConfigurationStatus info(GeometryConfigurationStatusCode code,String explanation) {
        return GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.info(
            GeometryConfigurationStage.GEOMETRY_REALIZATION,code,"Controlled tetrahedral closure",explanation));
    }
    private static GeometryConfigurationStatus stop(GeometryConfigurationStatusCode code,String explanation) {
        return GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.stop(
            GeometryConfigurationStage.GEOMETRY_REALIZATION,code,"Controlled closure rejected or limited",explanation));
    }
    public static void main(String[] args) {
        var trial=run(args.length>0?Double.parseDouble(args[0]):10,args.length>1?Integer.parseInt(args[1]):2000);
        trial.statuses().forEach(s -> System.out.println(s.code()+": "+s.explanation()));
    }
}
