package remframework.geometry.discovery;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;

import java.util.List;

public record FixedDegreeGraph(int nodeCount, int degree,
                               List<DiscoveryConnection> connections,
                               String topologySignature,
                               DynamicElasticTopologySource topologySource,
                               CanonicalDynamicElasticTopology canonicalTopology,
                               String canonicalGraphSignature,
                               int topologyIndex,
                               int duplicateGraphsSkipped,
                               int generationAttempts) {
    public FixedDegreeGraph(int nodeCount, int degree,
                            List<DiscoveryConnection> connections,
                            String topologySignature) {
        this(nodeCount, degree, connections, topologySignature,
                DynamicElasticTopologySource.GENERATED_FIXED_DEGREE_TOPOLOGY, null,
                CanonicalGraphSignature.of(nodeCount, connections), 0, 0, 1);
    }
    public FixedDegreeGraph(int nodeCount, int degree,
                            List<DiscoveryConnection> connections, String topologySignature,
                            DynamicElasticTopologySource topologySource,
                            CanonicalDynamicElasticTopology canonicalTopology) {
        this(nodeCount, degree, connections, topologySignature, topologySource,
                canonicalTopology, CanonicalGraphSignature.of(nodeCount, connections),
                0, 0, 1);
    }
    public FixedDegreeGraph {
        connections = List.copyOf(connections);
        if (canonicalGraphSignature == null || canonicalGraphSignature.isBlank())
            canonicalGraphSignature = CanonicalGraphSignature.of(nodeCount, connections);
        if (topologyIndex < 0 || duplicateGraphsSkipped < 0 || generationAttempts < 1)
            throw new IllegalArgumentException("Topology enumeration metadata is invalid.");
        if (topologySource == null)
            throw new IllegalArgumentException("Topology source is required.");
        if (topologySource == DynamicElasticTopologySource.CANONICAL_TOPOLOGY
                && canonicalTopology == null)
            throw new IllegalArgumentException("Canonical topology selection is required.");
        if (topologySource == DynamicElasticTopologySource.GENERATED_FIXED_DEGREE_TOPOLOGY)
            canonicalTopology = null;
        FixedDegreeValidation validation =
                new FixedDegreeValidator().validate(nodeCount, degree);
        if (!validation.valid())
            throw new IllegalArgumentException(String.join(" ", validation.messages()));
        java.util.Set<Long> edges = new java.util.HashSet<>();
        int[] degrees = new int[nodeCount];
        for (DiscoveryConnection connection : connections) {
            int first = connection.firstNodeId(), second = connection.secondNodeId();
            if (first < 0 || first >= nodeCount || second < 0 || second >= nodeCount)
                throw new IllegalArgumentException("Connection endpoint is outside node range.");
            if (!edges.add(connection.undirectedKey()))
                throw new IllegalArgumentException("Duplicate undirected connection.");
            degrees[first]++;
            degrees[second]++;
        }
        if (java.util.Arrays.stream(degrees).anyMatch(value -> value != degree))
            throw new IllegalArgumentException("Every node must have degree exactly " + degree + ".");
        if (!connected(nodeCount, connections))
            throw new IllegalArgumentException("Fixed-degree graph must be connected.");
    }
    private static boolean connected(int count, List<DiscoveryConnection> edges) {
        java.util.List<java.util.List<Integer>> adjacency = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) adjacency.add(new java.util.ArrayList<>());
        for (DiscoveryConnection edge : edges) {
            adjacency.get(edge.firstNodeId()).add(edge.secondNodeId());
            adjacency.get(edge.secondNodeId()).add(edge.firstNodeId());
        }
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        seen.add(0);
        queue.add(0);
        while (!queue.isEmpty())
            for (int next : adjacency.get(queue.removeFirst()))
                if (seen.add(next)) queue.addLast(next);
        return seen.size() == count;
    }
}
