package remframework.geometry.discovery.research.benchmark;

/** Descriptive output produced only after a solver attempt has ended. */
public record PostRunBenchmarkEvaluation(String configurationId,String topologyReference,
        boolean exactTopologyMatch,int faceCount,int uniformFaceSize,
        double coordinateIndependentRegularitySimilarity,String explanation) { }
