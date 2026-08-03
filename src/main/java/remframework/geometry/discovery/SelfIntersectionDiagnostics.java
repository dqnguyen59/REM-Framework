package remframework.geometry.discovery;

/** Immutable diagnostics for the optional continuous non-adjacent-face avoidance objective. */
public record SelfIntersectionDiagnostics(int intersectingFacePairs,int intersectingTrianglePairs,
        int coplanarOverlapCount,int nearClearancePairCount,double minimumDistance,
        double maximumPenetration,double rmsAvoidanceError,double objective,boolean finite) {
    public static SelfIntersectionDiagnostics inactive(){return new SelfIntersectionDiagnostics(0,0,0,0,Double.POSITIVE_INFINITY,0,0,0,true);}
}
