package remframework.geometry.discovery;

import java.nio.file.Path;
import java.time.Instant;

public record DynamicElasticResultRow(
        Instant timestamp, String runId, int neighborsPerNode, int nodeCount,
        String topologySignature, long seed, double targetDistance,
        double requiredAccuracy, boolean stable, DynamicElasticResultStatus status,
        int connectionsSatisfied, int connectionCount, double meanDistanceAccuracy,
        double maximumNodeSpeed, int iterations, long elapsedMilliseconds,
        Path resultFile, DynamicElasticTopologySource topologySource,
        CanonicalDynamicElasticTopology canonicalTopology,
        double minimumNonNeighborDistance,
        double minimumObservedNonNeighborDistance,
        int edgeClearanceViolations, double meanLocalSpread,
        double minimumLocalSpread, boolean elasticEquilibrium,
        boolean spatiallyValid) {
}
