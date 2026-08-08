package remframework.geometry.discovery.realization.session;

/** Explicit Stage 2 convexity formulations. */
public enum ConvexityMode {
    LEGACY_CONVEXITY("Existing Convexity"),
    FACE_HALF_SPACE_CONVEXITY("Face Half-Space Convexity"),
    COMBINED_CONVEXITY("Combined Convexity");

    private final String displayName;

    ConvexityMode(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
