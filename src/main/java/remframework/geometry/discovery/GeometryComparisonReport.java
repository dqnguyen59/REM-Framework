package remframework.geometry.discovery;

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
