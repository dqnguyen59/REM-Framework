package remframework.geometry.discovery.reference;

import java.util.List;
import remframework.geometry.discovery.DiscoveryConnection;
import remframework.geometry.discovery.GraphIsomorphismAnalyzer;
import remframework.geometry.discovery.configuration.Configuration;

/** Optional exact descriptive recognition of a small, versioned reference catalog. */
public final class ReferenceTopologyRecognizer {
    public static final String METHOD = "EXACT_GRAPH_ISOMORPHISM";
    public static final String VERSION = "reference-topologies-v1";
    private static final List<DiscoveryConnection> CUBE = List.of(
            edge(0,1), edge(1,2), edge(2,3), edge(3,0),
            edge(4,5), edge(5,6), edge(6,7), edge(7,4),
            edge(0,4), edge(1,5), edge(2,6), edge(3,7));
    private static final List<DiscoveryConnection> TETRAHEDRON = List.of(
            edge(0,1), edge(0,2), edge(0,3), edge(1,2), edge(1,3), edge(2,3));
    private final GraphIsomorphismAnalyzer isomorphism = new GraphIsomorphismAnalyzer();

    public ReferenceTopologyMatch recognize(Configuration configuration) {
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
    private static DiscoveryConnection edge(int a, int b) { return new DiscoveryConnection(a, b); }
}
