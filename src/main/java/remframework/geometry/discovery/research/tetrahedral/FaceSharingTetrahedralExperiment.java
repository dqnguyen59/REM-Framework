package remframework.geometry.discovery.research.tetrahedral;

import java.util.*;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.Configuration;

/**
 * Independent coordinate-free Geometry Discovery experiment.
 * <br><br>
 * Node IDs 0,1,2,3 denote A,B,C,D. No mechanical or embedding claims are made.
 */
public final class FaceSharingTetrahedralExperiment {
    /** Canonical, unoriented triangle; incidence is combinatorial only. */
    public record Face(int a, int b, int c) {
        public Face {
            int[] ids = {a,b,c};
            Arrays.sort(ids);
            a = ids[0]; b = ids[1]; c = ids[2];
            if (a < 0 || a == b || b == c) throw new IllegalArgumentException("Distinct nonnegative face nodes required.");
        }
        public List<Integer> nodes() { return List.of(a,b,c); }
    }
    public record Tetrahedron(int a, int b, int c, int d, int generation) {
        public List<Face> faces() {
            return List.of(new Face(a,b,c), new Face(a,b,d), new Face(a,c,d), new Face(b,c,d));
        }
    }
    public record Snapshot(int generation, List<Integer> nodeBirthGenerations,
            List<DiscoveryConnection> edges, List<Tetrahedron> tetrahedra,
            Map<Face,Integer> faceIncidence) {
        public Snapshot {
            nodeBirthGenerations = List.copyOf(nodeBirthGenerations);
            edges = List.copyOf(edges);
            tetrahedra = List.copyOf(tetrahedra);
            faceIncidence = Collections.unmodifiableMap(new LinkedHashMap<>(faceIncidence));
        }
        public int nodeCount() { return nodeBirthGenerations.size(); }
        public List<List<Integer>> adjacency() { return Configuration.adjacency(nodeCount(), edges); }
        public List<Integer> degrees() { return adjacency().stream().map(List::size).toList(); }
        public List<Face> exposedFaces() {
            return faceIncidence.entrySet().stream().filter(e -> e.getValue() == 1).map(Map.Entry::getKey).toList();
        }
        /** Independent graph cycles E-V+1; not the count of all simple cycles. */
        public long cycleRank() { return (long) edges.size() - nodeCount() + 1; }
        /** Every fresh node reconnects three existing vertices, adding two independent cycles. */
        public long attachmentReconnections() { return 2L * (tetrahedra.size() - 1); }
    }
    public record Run(int requestedGeneration, List<Snapshot> generations, GeometryConfigurationStatus status) {
        public Run { generations = List.copyOf(generations); }
    }
    /**
     * Explicit guard for later candidate rules. K is a maximum, not exact regularity.
     * <br><br>
     * This does not select faces, resolve competing attachments, reuse nodes, or run a bounded experiment.
     */
    public record DegreeCapacity(int k) {
        public DegreeCapacity {
            if (k < 3) throw new IllegalArgumentException("The seed requires K >= 3.");
        }
        public GeometryConfigurationStatus checkFreshAttachment(Snapshot snapshot, Face face) {
            if (!snapshot.exposedFaces().contains(face))
                return status(GeometryConfigurationStatusCode.INVALID_PARAMETERS, "Attachment requires an exposed face.", false);
            List<Integer> degrees = snapshot.degrees();
            List<Integer> blocked = face.nodes().stream().filter(id -> degrees.get(id) >= k).toList();
            if (!blocked.isEmpty())
                return GeometryConfigurationStatus.of(new GeometryConfigurationDiagnostic(
                    GeometryConfigurationStage.GRAPH_GENERATION, GeometryConfigurationStatusCode.INVALID_DEGREE,
                    GeometryConfigurationSeverity.ERROR, "Degree capacity exhausted.",
                    "K=" + k + "; fresh attachment would exceed capacity at " + blocked,
                    blocked, List.of(), List.of(face.toString()), false));
            return status(GeometryConfigurationStatusCode.GENERATED, "Fresh attachment fits K=" + k + "; no selection rule applied.", true);
        }
    }
    /** Generates whole synchronous generations, retaining the last complete snapshot at a declared node limit. */
    public Run generate(int generation, int maximumNodes) {
        if (generation < 0 || maximumNodes < 4)
            return new Run(generation, List.of(), status(GeometryConfigurationStatusCode.INVALID_PARAMETERS,
                "Generation must be nonnegative and maximumNodes must accommodate ABCD (4).", false));
        List<Integer> births = new ArrayList<>(List.of(0,0,0,0));
        List<DiscoveryConnection> edges = new ArrayList<>();
        for (int a=0; a<4; a++) for (int b=a+1; b<4; b++) edges.add(new DiscoveryConnection(a,b));
        List<Tetrahedron> tetrahedra = new ArrayList<>();
        Tetrahedron seed = new Tetrahedron(0,1,2,3,0);
        tetrahedra.add(seed);
        Map<Face,Integer> incidence = new LinkedHashMap<>();
        seed.faces().forEach(f -> incidence.put(f,1));
        List<Snapshot> snapshots = new ArrayList<>();
        snapshots.add(new Snapshot(0,births,edges,tetrahedra,incidence));
        for (int g=1; g<=generation; g++) {
            List<Face> frontier = snapshots.getLast().exposedFaces();
            long nextNodes = (long) births.size() + frontier.size();
            if (nextNodes > maximumNodes)
                return new Run(generation, snapshots, status(GeometryConfigurationStatusCode.RESOURCE_LIMIT_REACHED,
                    "Generation " + g + " requires " + nextNodes + " nodes; maximumNodes=" + maximumNodes
                    + ". Retained generation " + (g-1) + "; no partial symmetric generation was emitted.", false));
            for (Face f : frontier) {
                int id = births.size();
                births.add(g);
                for (int vertex : f.nodes()) edges.add(new DiscoveryConnection(vertex,id));
                Tetrahedron t = new Tetrahedron(f.a(),f.b(),f.c(),id,g);
                tetrahedra.add(t);
                t.faces().forEach(face -> incidence.merge(face,1,Integer::sum));
            }
            snapshots.add(new Snapshot(g,births,edges,tetrahedra,incidence));
        }
        return new Run(generation,snapshots,status(GeometryConfigurationStatusCode.GENERATED,
            "Completed naive symmetric construction through generation " + generation
            + ". Graph topology only; realization, isotropy and exact degree are unqualified.",true));
    }
    private static GeometryConfigurationStatus status(GeometryConfigurationStatusCode code, String explanation, boolean proceed) {
        return GeometryConfigurationStatus.of(proceed
            ? GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.GRAPH_GENERATION,code,"Face-sharing tetrahedral research",explanation)
            : GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GRAPH_GENERATION,code,"Face-sharing tetrahedral research stopped",explanation));
    }
    public static void main(String[] args) {
        int generation = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        int limit = args.length > 1 ? Integer.parseInt(args[1]) : 10000;
        Run run = new FaceSharingTetrahedralExperiment().generate(generation,limit);
        System.out.println("generation,nodes,tetrahedra,exposedFaces,edges,cycleRank,attachmentReconnections,originalDegrees");
        for (Snapshot s : run.generations())
            System.out.printf("%d,%d,%d,%d,%d,%d,%d,%s%n",s.generation(),s.nodeCount(),s.tetrahedra().size(),
                s.exposedFaces().size(),s.edges().size(),s.cycleRank(),s.attachmentReconnections(),s.degrees().subList(0,4));
        System.out.println(run.status().code() + ": " + run.status().explanation());
    }
}
