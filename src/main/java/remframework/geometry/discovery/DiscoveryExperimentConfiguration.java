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

import java.nio.file.Path;

public record DiscoveryExperimentConfiguration(
        String name,
        String description,
        int embeddingDimension,
        int nodeCount,
        long randomSeed,
        TopologyScoringProfile scoringProfile,
        TopologyScoreWeights scoreWeights,
        boolean customWeights,
        DiscoveryInitialTopology initialTopology,
        DiscoveryNeighborCountPolicy neighborCountPolicy,
        int minimumDegree,
        int maximumDegree,
        double targetInitialDegree,
        double edgeProbability,
        Path sourceCandidate,
        DiscoveryGeometryInitialization geometryInitialization,
        double initialExtent,
        double perturbationMagnitude,
        GeometryDiscoverySettings geometrySettings,
        int workerIterationsPerUpdate,
        TopologySearchSettings topologySearchSettings) {

    public static DiscoveryExperimentConfiguration defaults() {
        GeometryDiscoverySettings geometry = GeometryDiscoverySettings.defaults(10)
                .withRandomSeed(42);
        TopologySearchSettings search = TopologySearchSettings.defaults(42, geometry);
        return new DiscoveryExperimentConfiguration(
                "New discovery", "", 3, 12, 42,
                TopologyScoringProfile.GENERAL_GEOMETRY,
                TopologyScoreWeights.forProfile(TopologyScoringProfile.GENERAL_GEOMETRY),
                false, DiscoveryInitialTopology.RANDOM_CONNECTED_GRAPH,
                DiscoveryNeighborCountPolicy.BOUNDED,
                3, 6, 4, .3, null,
                DiscoveryGeometryInitialization.RANDOM_SPHERE, 15, 0,
                geometry, 25, search);
    }
}
