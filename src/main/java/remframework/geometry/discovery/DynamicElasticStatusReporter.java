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

public final class DynamicElasticStatusReporter {
    private DynamicElasticStatusReporter() { }

    public static String label(DynamicElasticMetrics metrics) {
        if (metrics == null) return "Relaxation Required";
        if (metrics.state() == DynamicElasticState.NUMERICAL_FAILURE)
            return "Numerical Failure";
        if (metrics.state() == DynamicElasticState.MAX_ITERATIONS_REACHED)
            return "Maximum Iterations Reached";
        if (metrics.state() == DynamicElasticState.RELAXING) return "Relaxing";
        if (metrics.state() == DynamicElasticState.NEAR_EQUILIBRIUM)
            return "Near Equilibrium";
        boolean allEdges = metrics.connectionsSatisfied() == metrics.connectionCount();
        boolean stable = metrics.stableIterations() >= metrics.requiredStableIterations();
        if (!allEdges || !stable || !metrics.elasticEquilibrium())
            return "Relaxation Required";
        return metrics.canonicalGeometryValid()
                ? "Canonical Geometry Validated" : "Elastic Equilibrium Reached";
    }
    public static String constraintState(boolean enabled) {
        return enabled ? "Enabled" : "Disabled";
    }
}
