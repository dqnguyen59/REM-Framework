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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class FixedDegreeGraphGenerator {
    public FixedDegreeGraph generate(int nodeCount, int degree, long seed, int attempt) {
        FixedDegreeValidation validation = new FixedDegreeValidator().validate(nodeCount, degree);
        if (!validation.valid())
            throw new IllegalArgumentException(String.join(" ", validation.messages()));
        List<DiscoveryConnection> edges = cycle(nodeCount);
        if (degree == 3) addPerfectMatching(edges, nodeCount, seed, attempt);
        GeometryDiscoveryModel signatureModel = new GeometryDiscoveryModel("signature", "",
                java.util.stream.IntStream.range(0, nodeCount)
                        .mapToObj(id -> new DiscoveryNode(id,
                                new org.joml.Vector3d(), id == 0)).toList(), edges);
        return new FixedDegreeGraph(nodeCount, degree, edges,
                TopologySignature.of(signatureModel));
    }

    private List<DiscoveryConnection> cycle(int n) {
        List<DiscoveryConnection> result = new ArrayList<>();
        for (int i = 0; i < n; i++)
            result.add(new DiscoveryConnection(i, (i + 1) % n));
        return result;
    }

    private void addPerfectMatching(List<DiscoveryConnection> edges, int n,
                                    long seed, int attempt) {
        if (n == 4) {
            edges.add(new DiscoveryConnection(0, 2));
            edges.add(new DiscoveryConnection(1, 3));
            return;
        }
        for (int retry = 0; retry < 10_000; retry++) {
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < n; i++) ids.add(i);
            Collections.shuffle(ids, new Random(seed ^ ((long) attempt << 32) ^ retry));
            List<DiscoveryConnection> matching = new ArrayList<>();
            Set<Long> keys = new HashSet<>();
            boolean valid = true;
            for (int i = 0; i < n; i += 2) {
                int a = ids.get(i), b = ids.get(i + 1);
                int difference = Math.abs(a - b);
                if (difference == 1 || difference == n - 1) { valid = false; break; }
                DiscoveryConnection edge = new DiscoveryConnection(a, b);
                if (!keys.add(edge.undirectedKey())) { valid = false; break; }
                matching.add(edge);
            }
            if (valid) {
                edges.addAll(matching);
                return;
            }
        }
        throw new IllegalStateException("No deterministic 3-regular topology was generated.");
    }
}
