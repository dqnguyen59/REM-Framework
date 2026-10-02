package remframework.geometry.discovery.reference;

import java.util.List;
import remframework.geometry.discovery.DiscoveryConnection;
import remframework.geometry.discovery.GraphIsomorphismAnalyzer;
import remframework.geometry.discovery.configuration.Configuration;

/** Optional exact descriptive recognition of a small, versioned reference catalog. */
public final class ReferenceTopologyRecognizer {
    public static final String METHOD = "EXACT_GRAPH_ISOMORPHISM";
    public static final String VERSION = "reference-topologies-v2";
    private static final String DODECAHEDRON_CANONICAL_SURFACE="20:1110000000000000000001100000000000000000110000000000000000110000000000000011000000000100001000000000000010000000100001000000000001000000000010000100001000000001001000100000010100101000001011";
    private static final List<DiscoveryConnection> CUBE = List.of(
            edge(0,1), edge(1,2), edge(2,3), edge(3,0),
            edge(4,5), edge(5,6), edge(6,7), edge(7,4),
            edge(0,4), edge(1,5), edge(2,6), edge(3,7));
    private static final List<DiscoveryConnection> TETRAHEDRON = List.of(
            edge(0,1), edge(0,2), edge(0,3), edge(1,2), edge(1,3), edge(2,3));
    private static final List<DiscoveryConnection> DODECAHEDRON=generalizedPetersen(10,2);
    private final GraphIsomorphismAnalyzer isomorphism = new GraphIsomorphismAnalyzer();

    public ReferenceTopologyMatch recognize(Configuration configuration) {
        if(configuration.nodeCount()==20&&configuration.edges().size()==30
                &&DODECAHEDRON_CANONICAL_SURFACE.equals(configuration.canonicalGraphLabel()))
            return new ReferenceTopologyMatch(ReferenceTopology.DODECAHEDRON_GRAPH,true,
                    METHOD,VERSION,ReferenceTopologyMatch.GEOMETRY_DISCLAIMER);
        return recognize(configuration.nodeCount(), configuration.edges());
    }

    public ReferenceTopologyMatch recognize(int nodeCount, List<DiscoveryConnection> edges) {
        ReferenceTopology topology = ReferenceTopology.UNKNOWN;
        if (nodeCount == 8 && edges.size() == CUBE.size()
                && isomorphism.areIsomorphic(nodeCount, edges, CUBE))
            topology = ReferenceTopology.CUBE_GRAPH;
        else if (nodeCount == 4 && edges.size() == TETRAHEDRON.size()
                && isomorphism.areIsomorphic(nodeCount, edges, TETRAHEDRON))
            topology = ReferenceTopology.TETRAHEDRON_GRAPH;
        boolean matched = topology != ReferenceTopology.UNKNOWN;
        return new ReferenceTopologyMatch(topology, matched, METHOD, VERSION,
                matched ? ReferenceTopologyMatch.GEOMETRY_DISCLAIMER
                        : "No exact match exists in the optional reference catalog. "
                        + "UNKNOWN topologies remain independently testable in Stage 2.");
    }

    public static List<DiscoveryConnection> cubeGraphEdges() { return CUBE; }
    public static List<DiscoveryConnection> tetrahedronGraphEdges() { return TETRAHEDRON; }
    public static List<DiscoveryConnection> dodecahedronGraphEdges(){return DODECAHEDRON;}
    private static List<DiscoveryConnection> generalizedPetersen(int n,int step){java.util.ArrayList<DiscoveryConnection> edges=new java.util.ArrayList<>();for(int i=0;i<n;i++){edges.add(edge(i,(i+1)%n));edges.add(edge(i,n+i));edges.add(edge(n+i,n+(i+step)%n));}return List.copyOf(edges);}
    private static DiscoveryConnection edge(int a, int b) { return new DiscoveryConnection(a, b); }
}
