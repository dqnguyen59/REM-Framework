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

public record GeometryComparisonReport(
        int nodeCountDifference,
        int connectionCountDifference,
        double meanEdgeDistanceDifference,
        double minimumEdgeDistanceDifference,
        double maximumEdgeDistanceDifference,
        String firstDimensionality,
        String secondDimensionality,
        double extentXDifference,
        double extentYDifference,
        double extentZDifference,
        boolean runtimeCompatibilityEqual,
        boolean convergenceEqual,
        int correspondingNodeCount,
        double normalizedGeometryDifference,
        String alignmentDescription) {

    public boolean geometricallyIdentical(double tolerance) {
        return nodeCountDifference == 0 && connectionCountDifference == 0
                && normalizedGeometryDifference <= tolerance;
    }
}
