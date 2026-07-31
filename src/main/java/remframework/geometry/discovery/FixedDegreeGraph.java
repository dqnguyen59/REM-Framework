package remframework.geometry.discovery;

import java.util.List;

public record FixedDegreeGraph(int nodeCount, int degree,
                               List<DiscoveryConnection> connections,
                               String topologySignature) {
    public FixedDegreeGraph {
        connections = List.copyOf(connections);
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
