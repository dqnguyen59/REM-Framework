package remframework.geometry.discovery;

import java.util.List;

public final class CanonicalGeometryConstraints {
    private CanonicalGeometryConstraints() { }

    public static List<DynamicElasticGeometryConstraint> faceDiagonals(
            FixedDegreeGraph graph, DynamicElasticSettings settings) {
        if (graph.canonicalTopology() != CanonicalDynamicElasticTopology.CUBE
                || !settings.canonicalGeometry().faceDiagonalsEnabled())
            return List.of();
        double target = settings.targetDistance() * Math.sqrt(2);
        return List.of(
                diagonal(0, 2, target), diagonal(1, 3, target),
                diagonal(4, 6, target), diagonal(5, 7, target),
                diagonal(0, 5, target), diagonal(1, 4, target),
                diagonal(1, 6, target), diagonal(2, 5, target),
                diagonal(2, 7, target), diagonal(3, 6, target),
                diagonal(3, 4, target), diagonal(0, 7, target));
    }

    private static DynamicElasticGeometryConstraint diagonal(
            int first, int second, double target) {
        return new DynamicElasticGeometryConstraint(first, second, target,
                "Geometry Constraint: Cube face diagonal");
    }
}
