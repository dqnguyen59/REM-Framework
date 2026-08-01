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

public record TopologySearchSettings(
        TopologySearchMode searchMode,
        long randomSeed,
        int maximumSearchIterations,
        int mutationsAttemptedPerIteration,
        int minimumDegree,
        int maximumDegree,
        int requiredEdgeCount,
        int archiveSize,
        double initialTemperature,
        double coolingRate,
        double minimumTemperature,
        double scoreImprovementTolerance,
        GeometryDiscoverySettings geometrySettings,
        TopologyScoreWeights scoreWeights,
        TopologyScoringProfile scoringProfile,
        TopologyDiagnosticSettings diagnosticSettings,
        int checkpointInterval) {
    public TopologySearchSettings(
            TopologySearchMode searchMode, long randomSeed, int maximumSearchIterations,
            int mutationsAttemptedPerIteration, int minimumDegree, int maximumDegree,
            int requiredEdgeCount, int archiveSize, double initialTemperature,
            double coolingRate, double minimumTemperature, double scoreImprovementTolerance,
            GeometryDiscoverySettings geometrySettings, TopologyScoreWeights scoreWeights,
            int checkpointInterval) {
        this(searchMode, randomSeed, maximumSearchIterations, mutationsAttemptedPerIteration,
                minimumDegree, maximumDegree, requiredEdgeCount, archiveSize, initialTemperature,
                coolingRate, minimumTemperature, scoreImprovementTolerance, geometrySettings,
                scoreWeights, TopologyScoringProfile.GENERAL_GEOMETRY,
                TopologyDiagnosticSettings.defaults(), checkpointInterval);
    }

    public static TopologySearchSettings defaults(long seed,
                                                  GeometryDiscoverySettings geometry) {
        return new TopologySearchSettings(TopologySearchMode.HILL_CLIMBING, seed,
                1000, 1, 1, 6, -1, 20, 1, 0.997, 1e-6,
                1e-9, geometry, TopologyScoreWeights.defaults(),
                TopologyScoringProfile.GENERAL_GEOMETRY,
                TopologyDiagnosticSettings.defaults(), 100);
    }
}
