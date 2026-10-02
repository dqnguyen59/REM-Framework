package remframework.geometry.discovery.realization.session;

public record ExperimentConvergenceDiagnostics(int iteration, double maximumConnectedEdgeError,
        double maximumPlaneDeviation, double meanPlaneDeviation, double rmsPlaneDeviation,
        double planarityTolerance, double angleVariance, double maximumAngleDeviation,
        String edgeConstraint, String planarity, String angleCondition, String simpleBoundary,
        String areaQualification, boolean fullExperimentConverged) { }
