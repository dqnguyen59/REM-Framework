package remframework.geometry.discovery.configuration;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record Configuration(ConfigurationId id, int nodeCount, int exactDegree,
        List<DiscoveryConnection> edges, List<List<Integer>> adjacency,
        String canonicalGraphLabel, String isomorphismClassId, long automorphismGroupSize,
        GraphSymmetryClassification graphSymmetry, boolean connected, int generationIndex,
        String generationMethod, boolean enumerationComplete, GeometryConfigurationStatus status,
        List<GeometryConfigurationDiagnostic> diagnostics, Instant creationTimestamp,
        Map<String, String> provenance) {
    public Configuration {
        edges = edges.stream().sorted((a,b) -> Long.compare(a.undirectedKey(), b.undirectedKey())).toList();
        adjacency = adjacency == null ? adjacency(nodeCount, edges) : adjacency.stream().map(List::copyOf).toList();
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        creationTimestamp = creationTimestamp == null ? Instant.EPOCH : creationTimestamp;
        provenance = provenance == null ? Map.of() : Map.copyOf(new TreeMap<>(provenance));
        if (adjacency.size() != nodeCount || adjacency.stream().anyMatch(v -> v.size() != exactDegree))
            throw new IllegalArgumentException("Configuration adjacency must have exact degree K.");
        if (!connected) throw new IllegalArgumentException("A configuration must be connected.");
    }
    public static List<List<Integer>> adjacency(int n, List<DiscoveryConnection> edges) {
        List<List<Integer>> result = new ArrayList<>();
        for (int i=0;i<n;i++) result.add(new ArrayList<>());
        for (DiscoveryConnection e:edges) { result.get(e.firstNodeId()).add(e.secondNodeId()); result.get(e.secondNodeId()).add(e.firstNodeId()); }
        return result.stream().map(v -> v.stream().sorted().toList()).toList();
    }
}
