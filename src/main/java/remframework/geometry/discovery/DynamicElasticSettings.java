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

public record DynamicElasticSettings(
        double targetDistance,
        double requiredAccuracy,
        double springStrength,
        double updateStrength,
        double velocityRetention,
        double linearVelocityDamping,
        double angularVelocityDamping,
        double maximumVelocity,
        double maximumForce,
        double maximumEquilibriumSpeed,
        int requiredStableIterations,
        int maximumIterations,
        int workerUpdatesPerPublication,
        long randomSeed,
        DynamicElasticSpatialSettings spatial,
        DynamicElasticCanonicalGeometrySettings canonicalGeometry) {

    public DynamicElasticSettings(double targetDistance, double requiredAccuracy,
            double springStrength, double updateStrength, double velocityRetention,
            double linearVelocityDamping, double angularVelocityDamping,
            double maximumVelocity, double maximumForce, double maximumEquilibriumSpeed,
            int requiredStableIterations, int maximumIterations,
            int workerUpdatesPerPublication, long randomSeed) {
        this(targetDistance, requiredAccuracy, springStrength, updateStrength,
                velocityRetention, linearVelocityDamping, angularVelocityDamping,
                maximumVelocity, maximumForce, maximumEquilibriumSpeed,
                requiredStableIterations, maximumIterations, workerUpdatesPerPublication,
                randomSeed, DynamicElasticSpatialSettings.defaults(targetDistance),
                DynamicElasticCanonicalGeometrySettings.disabled());
    }
    public DynamicElasticSettings(double targetDistance, double requiredAccuracy,
            double springStrength, double updateStrength, double velocityRetention,
            double linearVelocityDamping, double angularVelocityDamping,
            double maximumVelocity, double maximumForce, double maximumEquilibriumSpeed,
            int requiredStableIterations, int maximumIterations,
            int workerUpdatesPerPublication, long randomSeed,
            DynamicElasticSpatialSettings spatial) {
        this(targetDistance, requiredAccuracy, springStrength, updateStrength,
                velocityRetention, linearVelocityDamping, angularVelocityDamping,
                maximumVelocity, maximumForce, maximumEquilibriumSpeed,
                requiredStableIterations, maximumIterations, workerUpdatesPerPublication,
                randomSeed, spatial, DynamicElasticCanonicalGeometrySettings.disabled());
    }

    public DynamicElasticSettings {
        if (spatial == null) throw new IllegalArgumentException("Spatial settings are required.");
        if (canonicalGeometry == null)
            throw new IllegalArgumentException("Canonical geometry settings are required.");
        if (!positiveFinite(targetDistance)) throw new IllegalArgumentException(
                "Target distance must be finite and positive.");
        if (!Double.isFinite(requiredAccuracy) || requiredAccuracy <= 0
                || requiredAccuracy > 1) throw new IllegalArgumentException(
                "Required accuracy must be in (0, 1].");
        if (!nonNegativeFinite(springStrength) || !nonNegativeFinite(updateStrength)
                || !nonNegativeFinite(linearVelocityDamping)
                || !nonNegativeFinite(angularVelocityDamping)
                || !positiveFinite(maximumVelocity) || !positiveFinite(maximumForce)
                || !nonNegativeFinite(maximumEquilibriumSpeed))
            throw new IllegalArgumentException("Dynamic elastic numeric settings are invalid.");
        if (!Double.isFinite(velocityRetention) || velocityRetention < 0
                || velocityRetention > 1 || linearVelocityDamping > 1)
            throw new IllegalArgumentException(
                    "Velocity retention and linear damping must be between 0 and 1.");
        if (requiredStableIterations < 1 || maximumIterations < 1
                || workerUpdatesPerPublication < 1)
            throw new IllegalArgumentException("Iteration settings must be positive.");
    }

    public static DynamicElasticSettings defaults(double targetDistance, long seed) {
        return new DynamicElasticSettings(targetDistance, .98, .08, .08,
                .96, .01, .01, targetDistance * .2, targetDistance,
                .0001, 100, 100_000, 25, seed,
                DynamicElasticSpatialSettings.defaults(targetDistance),
                DynamicElasticCanonicalGeometrySettings.disabled());
    }

    public DynamicElasticSettings withTargetDistance(double value) {
        return new DynamicElasticSettings(value, requiredAccuracy, springStrength,
                updateStrength, velocityRetention, linearVelocityDamping,
                angularVelocityDamping, maximumVelocity, maximumForce,
                maximumEquilibriumSpeed, requiredStableIterations, maximumIterations,
                workerUpdatesPerPublication, randomSeed,
                scaleSpatial(spatial, targetDistance, value), canonicalGeometry);
    }
    public DynamicElasticSettings withRequiredAccuracy(double value) {
        return new DynamicElasticSettings(targetDistance, value, springStrength,
                updateStrength, velocityRetention, linearVelocityDamping,
                angularVelocityDamping, maximumVelocity, maximumForce,
                maximumEquilibriumSpeed, requiredStableIterations, maximumIterations,
                workerUpdatesPerPublication, randomSeed, spatial, canonicalGeometry);
    }
    public DynamicElasticSettings withSpringStrength(double value) {
        return new DynamicElasticSettings(targetDistance, requiredAccuracy, value,
                updateStrength, velocityRetention, linearVelocityDamping,
                angularVelocityDamping, maximumVelocity, maximumForce,
                maximumEquilibriumSpeed, requiredStableIterations, maximumIterations,
                workerUpdatesPerPublication, randomSeed, spatial, canonicalGeometry);
    }
    public DynamicElasticSettings withUpdateStrength(double value) {
        return new DynamicElasticSettings(targetDistance, requiredAccuracy, springStrength,
                value, velocityRetention, linearVelocityDamping, angularVelocityDamping,
                maximumVelocity, maximumForce, maximumEquilibriumSpeed,
                requiredStableIterations, maximumIterations, workerUpdatesPerPublication,
                randomSeed, spatial, canonicalGeometry);
    }
    public DynamicElasticSettings withRequiredStableIterations(int value) {
        return new DynamicElasticSettings(targetDistance, requiredAccuracy, springStrength,
                updateStrength, velocityRetention, linearVelocityDamping,
                angularVelocityDamping, maximumVelocity, maximumForce,
                maximumEquilibriumSpeed, value, maximumIterations,
                workerUpdatesPerPublication, randomSeed, spatial, canonicalGeometry);
    }
    public DynamicElasticSettings withMaximumIterations(int value) {
        return new DynamicElasticSettings(targetDistance, requiredAccuracy, springStrength,
                updateStrength, velocityRetention, linearVelocityDamping,
                angularVelocityDamping, maximumVelocity, maximumForce,
                maximumEquilibriumSpeed, requiredStableIterations, value,
                workerUpdatesPerPublication, randomSeed, spatial, canonicalGeometry);
    }
    public DynamicElasticSettings withSpatial(DynamicElasticSpatialSettings value) {
        return new DynamicElasticSettings(targetDistance, requiredAccuracy, springStrength,
                updateStrength, velocityRetention, linearVelocityDamping,
                angularVelocityDamping, maximumVelocity, maximumForce,
                maximumEquilibriumSpeed, requiredStableIterations, maximumIterations,
                workerUpdatesPerPublication, randomSeed, value, canonicalGeometry);
    }
    public DynamicElasticSettings withCanonicalGeometry(
            DynamicElasticCanonicalGeometrySettings value) {
        return new DynamicElasticSettings(targetDistance, requiredAccuracy, springStrength,
                updateStrength, velocityRetention, linearVelocityDamping,
                angularVelocityDamping, maximumVelocity, maximumForce,
                maximumEquilibriumSpeed, requiredStableIterations, maximumIterations,
                workerUpdatesPerPublication, randomSeed, spatial, value);
    }
    private static DynamicElasticSpatialSettings scaleSpatial(
            DynamicElasticSpatialSettings value, double oldTarget, double newTarget) {
        double scale = newTarget / oldTarget;
        return new DynamicElasticSpatialSettings(value.nonNeighborExclusionEnabled(),
                value.minimumNonNeighborDistance() * scale,
                value.edgeCrossingPreventionEnabled(),
                value.edgeClearanceDistance() * scale,
                value.localSpreadEnabled(), value.strength());
    }
    private static boolean positiveFinite(double value) {
        return Double.isFinite(value) && value > 0;
    }
    private static boolean nonNegativeFinite(double value) {
        return Double.isFinite(value) && value >= 0;
    }
}
