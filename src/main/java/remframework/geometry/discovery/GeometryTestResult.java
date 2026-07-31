package remframework.geometry.discovery;
public record GeometryTestResult(GeometryConfigurationId configurationId,
        GraphSymmetryClassification graphSymmetry,GeometryRealization realization,
        FaceDerivationResult faceDerivation,ClosedBoundaryResult boundary,
        GeometricSymmetryResult geometricSymmetry,AreaResult area,VolumeResult volume,
        GeometryOptimizationResult optimization,GeometryConfigurationStatus status) { }
