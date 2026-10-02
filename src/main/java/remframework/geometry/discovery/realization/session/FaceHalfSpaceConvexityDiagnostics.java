package remframework.geometry.discovery.realization.session;

/** Immutable diagnostics for the complete face-supporting-half-space test. */
public record FaceHalfSpaceConvexityDiagnostics(
        FaceHalfSpaceConvexityStatus status, int testedPairCount, int violatingPairCount,
        int invalidPairCount, double maximumViolation, double rmsViolation,
        int worstFaceId, int worstVertexId, double objective, double tolerance,
        double scale, boolean globallyReversed, String explanation) {
    public static FaceHalfSpaceConvexityDiagnostics unavailable(FaceHalfSpaceConvexityStatus status,
            String explanation, double tolerance, double scale) {
        return new FaceHalfSpaceConvexityDiagnostics(status, 0, 0, 0, Double.NaN,
                Double.NaN, -1, -1, Double.NaN, tolerance, scale, false, explanation);
    }
    public boolean available() { return status == FaceHalfSpaceConvexityStatus.SATISFIED
            || status == FaceHalfSpaceConvexityStatus.VIOLATING; }
}
