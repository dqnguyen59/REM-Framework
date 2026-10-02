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

public record DynamicElasticSearchSettings(
        int exactNeighborsPerNode,
        int startingNodeCount,
        int maximumNodeCount,
        int topologyAttemptsPerNodeCount,
        int seedsPerTopology,
        DynamicElasticSearchPolicy policy,
        DynamicElasticSettings elasticSettings,
        int maximumUniqueTopologiesPerNodeCount) {
    public DynamicElasticSearchSettings(int exactNeighborsPerNode, int startingNodeCount,
            int maximumNodeCount, int topologyAttemptsPerNodeCount, int seedsPerTopology,
            DynamicElasticSearchPolicy policy, DynamicElasticSettings elasticSettings) {
        this(exactNeighborsPerNode, startingNodeCount, maximumNodeCount,
                topologyAttemptsPerNodeCount, seedsPerTopology, policy, elasticSettings, 100);
    }
    public DynamicElasticSearchSettings {
        if (exactNeighborsPerNode != 2 && exactNeighborsPerNode != 3)
            throw new IllegalArgumentException("Exact neighbors must be 2 or 3.");
        if (startingNodeCount < 1 || maximumNodeCount < startingNodeCount)
            throw new IllegalArgumentException("Invalid bounded node-count range.");
        if (topologyAttemptsPerNodeCount < 1 || seedsPerTopology < 1)
            throw new IllegalArgumentException("Topology attempts and seeds must be positive.");
        if (maximumUniqueTopologiesPerNodeCount < 1)
            throw new IllegalArgumentException("Maximum unique topologies must be positive.");
        if (policy == null || elasticSettings == null)
            throw new IllegalArgumentException("Search policy and elastic settings are required.");
    }
}
