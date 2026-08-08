package remframework.geometry.discovery.realization.session;

/** Structured availability/result state for face-half-space convexity. */
public enum FaceHalfSpaceConvexityStatus {
    SATISFIED, VIOLATING, UNAVAILABLE_FACE_COMPLEX_INVALID,
    UNAVAILABLE_INCOHERENT_ORIENTATION, UNAVAILABLE_DEGENERATE_FACE,
    UNAVAILABLE_INVALID_CENTER, NUMERICALLY_INVALID
}
