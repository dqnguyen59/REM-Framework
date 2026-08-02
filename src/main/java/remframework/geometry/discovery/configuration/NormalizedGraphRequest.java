package remframework.geometry.discovery.configuration;

import remframework.geometry.discovery.experiment.FaceComposition;

/** Mode-independent graph-generator input produced by parameter preflight. */
public record NormalizedGraphRequest(int nodeCount,int exactDegree,long expectedEdgeCount,
        Long expectedFaceCount,FaceComposition faceComposition,
        SurfaceTopologyAssumption surfaceTopologyAssumption,
        PrimaryParameterMode originalPrimaryParameterMode,
        ParameterDerivationReport derivationReport,GenerationStrategy selectedStrategy,
        UniformSphericalSearchLimits specializedSearchLimits) { }
