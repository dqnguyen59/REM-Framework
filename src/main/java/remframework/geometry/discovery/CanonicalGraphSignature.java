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

import java.util.Arrays;
import java.util.List;

/**
 * Label-independent graph signature. Exhaustive canonical labeling is used for
 * the initially supported bounded enumeration size (N <= 8).
 */
public final class CanonicalGraphSignature {
    private CanonicalGraphSignature() { }

    public static String of(int nodeCount, List<DiscoveryConnection> edges) {
        boolean[][] adjacency = new boolean[nodeCount][nodeCount];
        for (DiscoveryConnection edge : edges) {
            adjacency[edge.firstNodeId()][edge.secondNodeId()] = true;
            adjacency[edge.secondNodeId()][edge.firstNodeId()] = true;
        }
        if (nodeCount <= 8) {
            int[] permutation = new int[nodeCount];
            boolean[] used = new boolean[nodeCount];
            String[] best = {null};
            enumerate(0, permutation, used, adjacency, best);
            return nodeCount + ":" + best[0];
        }
        // Deterministic isomorphism invariant for larger bounded samples.
        String[] profiles = new String[nodeCount];
        for (int i = 0; i < nodeCount; i++) profiles[i] = distanceProfile(i, adjacency);
        Arrays.sort(profiles);
        return nodeCount + ":" + String.join("|", profiles);
    }

    private static void enumerate(int depth, int[] permutation, boolean[] used,
                                  boolean[][] adjacency, String[] best) {
        if (depth == permutation.length) {
            String encoded = encode(permutation, adjacency);
            if (best[0] == null || encoded.compareTo(best[0]) < 0) best[0] = encoded;
            return;
        }
        for (int node = 0; node < permutation.length; node++) if (!used[node]) {
            used[node] = true;
            permutation[depth] = node;
            enumerate(depth + 1, permutation, used, adjacency, best);
            used[node] = false;
        }
    }

    private static String encode(int[] permutation, boolean[][] adjacency) {
        StringBuilder result = new StringBuilder(permutation.length * permutation.length / 2);
        for (int i = 0; i < permutation.length; i++)
            for (int j = i + 1; j < permutation.length; j++)
                result.append(adjacency[permutation[i]][permutation[j]] ? '1' : '0');
        return result.toString();
    }

    private static String distanceProfile(int start, boolean[][] adjacency) {
        int[] distance = new int[adjacency.length];
        Arrays.fill(distance, -1);
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        distance[start] = 0;
        queue.add(start);
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            for (int next = 0; next < adjacency.length; next++)
                if (adjacency[current][next] && distance[next] < 0) {
                    distance[next] = distance[current] + 1;
                    queue.addLast(next);
                }
        }
        Arrays.sort(distance);
        return Arrays.toString(distance);
    }
}
