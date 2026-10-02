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
        boolean spatiallyValid, boolean canonicalAngleConstraintsEnabled,
        double targetAngleDegrees, double angleToleranceDegrees,
        int anglesSatisfied, int angleCount, double meanAngleErrorDegrees,
        double maximumAngleErrorDegrees, boolean canonicalGeometryValid,
        int topologyIndex, String canonicalGraphSignature,
        int duplicateGraphsSkipped, int generationAttempts) {
}
