package remframework.geometry.discovery;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;
public record GeometryOptimizationResult(boolean attempted, Realization baseline,
        Realization optimized, double baselineMeasure,double optimizedMeasure,
        double relativeImprovement,boolean constraintsPreserved,
        GeometrySymmetryPolicy symmetryPolicy, Boolean symmetricAfterOptimization,
        int iterationCount, String summary) {
    public static GeometryOptimizationResult notAttempted(){return new GeometryOptimizationResult(false,null,null,Double.NaN,Double.NaN,Double.NaN,true,GeometrySymmetryPolicy.MEASURE_ONLY,null,0,"Optimization is a separate post-validation stage.");}
}
