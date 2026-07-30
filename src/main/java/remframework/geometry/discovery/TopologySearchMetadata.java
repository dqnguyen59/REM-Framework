package remframework.geometry.discovery;

public record TopologySearchMetadata(
        String metadataVersion,
        boolean redesignedDiagnosticsAvailable,
        double totalScore,
        String searchMode,
        String scoringProfile,
        long searchSeed,
        String parentMutation,
        double edgeLengthComponent,
        double directionalComponent,
        double tensorComponent,
        double degreeVarianceComponent,
        int minimumDegree,
        int maximumDegree,
        double meanDegree,
        int graphDiameter,
        int bridgeCount,
        int articulationPointCount,
        double meanDirectionalBias,
        double tensorIsotropyError,
        boolean angularReferenceAvailable,
        double angularReferenceError,
        double maximumUncoveredAngle,
        double oppositeDirectionMismatch,
        int globalDimensionality,
        int dimensionalityMismatchCount) {

    // Version 0.1 aliases.
    public double meanDirectionalImbalance() { return meanDirectionalBias; }
    public double tensorAnisotropy() { return tensorIsotropyError; }
}
