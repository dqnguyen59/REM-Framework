package remframework.geometry.discovery;

import org.joml.Vector3d;

import java.util.List;
import java.util.stream.IntStream;

public enum CanonicalDynamicElasticTopology {
    CYCLE("Cycle", -1, 2),
    TETRAHEDRON("Tetrahedron", 4, 3),
    CUBE("Cube", 8, 3);

    private final String displayName;
    private final int fixedNodeCount;
    private final int exactDegree;

    CanonicalDynamicElasticTopology(String displayName, int fixedNodeCount, int exactDegree) {
        this.displayName = displayName;
        this.fixedNodeCount = fixedNodeCount;
        this.exactDegree = exactDegree;
    }

    public String displayName() { return displayName; }
    public int fixedNodeCount() { return fixedNodeCount; }
    public int exactDegree() { return exactDegree; }

    public FixedDegreeGraph create(int requestedNodeCount) {
        int count = this == CYCLE ? requestedNodeCount : fixedNodeCount;
        if (this == CYCLE && count < 3)
            throw new IllegalArgumentException("Canonical Cycle requires N >= 3.");
        List<DiscoveryConnection> edges = switch (this) {
            case CYCLE -> IntStream.range(0, count)
                    .mapToObj(i -> new DiscoveryConnection(i, (i + 1) % count)).toList();
            case TETRAHEDRON -> List.of(
                    edge(0, 1), edge(0, 2), edge(0, 3),
                    edge(1, 2), edge(1, 3), edge(2, 3));
            case CUBE -> List.of(
                    edge(0, 1), edge(1, 2), edge(2, 3), edge(3, 0),
                    edge(4, 5), edge(5, 6), edge(6, 7), edge(7, 4),
                    edge(0, 4), edge(1, 5), edge(2, 6), edge(3, 7));
        };
        GeometryDiscoveryModel model = new GeometryDiscoveryModel("canonical", "",
                IntStream.range(0, count)
                        .mapToObj(id -> new DiscoveryNode(id, new Vector3d(), id < 2)).toList(),
                edges);
        return new FixedDegreeGraph(count, exactDegree, edges, TopologySignature.of(model),
                DynamicElasticTopologySource.CANONICAL_TOPOLOGY, this);
    }

    private static DiscoveryConnection edge(int first, int second) {
        return new DiscoveryConnection(first, second);
    }
}
