package remframework.geometry.discovery.realization.session;

public record OrientationConvexityDiagnostics(OrientationState orientation,double outwardError,
        double maximumConvexityViolation,double rmsConvexityViolation,int violatingPairs,
        boolean convexWithinTolerance,String explanation) {
    public static OrientationConvexityDiagnostics undetermined(String reason){return new OrientationConvexityDiagnostics(OrientationState.UNDETERMINED,Double.NaN,Double.NaN,Double.NaN,0,false,reason);}
}
