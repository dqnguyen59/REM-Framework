package remframework.geometry.discovery;

/**
 * A geometric relation that is deliberately not a topology connection and
 * therefore never contributes to graph degree or lattice export.
 */
public record DynamicElasticGeometryConstraint(
        int firstNodeId, int secondNodeId, double targetDistance, String label) {
}
