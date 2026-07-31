package remframework.geometry.discovery;
public record GeometryOptimizationResult(boolean attempted, GeometryRealization baseline,
        GeometryRealization optimized, double baselineMeasure,double optimizedMeasure,
        double relativeImprovement,boolean constraintsPreserved,
        GeometrySymmetryPolicy symmetryPolicy, Boolean symmetricAfterOptimization,
        String summary) {
    public static GeometryOptimizationResult notAttempted(){return new GeometryOptimizationResult(false,null,null,Double.NaN,Double.NaN,Double.NaN,true,GeometrySymmetryPolicy.MEASURE_ONLY,null,"Optimization is a separate post-validation stage.");}
}
