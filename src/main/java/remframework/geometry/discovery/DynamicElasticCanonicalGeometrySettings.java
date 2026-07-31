package remframework.geometry.discovery;

public record DynamicElasticCanonicalGeometrySettings(
        boolean angleConstraintsEnabled,
        double targetAngleDegrees,
        double angleToleranceDegrees,
        double angleStrength,
        boolean faceDiagonalsEnabled) {

    public DynamicElasticCanonicalGeometrySettings {
        if (!Double.isFinite(targetAngleDegrees) || targetAngleDegrees <= 0
                || targetAngleDegrees >= 180)
            throw new IllegalArgumentException("Target angle must be in (0, 180).");
        if (!Double.isFinite(angleToleranceDegrees) || angleToleranceDegrees < 0
                || !Double.isFinite(angleStrength) || angleStrength < 0)
            throw new IllegalArgumentException("Canonical angle settings must be finite and non-negative.");
    }

    public static DynamicElasticCanonicalGeometrySettings disabled() {
        return new DynamicElasticCanonicalGeometrySettings(false, 90, 2, .01, false);
    }

    public static DynamicElasticCanonicalGeometrySettings forTopology(
            CanonicalDynamicElasticTopology topology) {
        return switch (topology) {
            case CUBE -> new DynamicElasticCanonicalGeometrySettings(true, 90, 2, .01, false);
            case TETRAHEDRON -> new DynamicElasticCanonicalGeometrySettings(true,
                    Math.toDegrees(Math.acos(-1.0 / 3.0)), 2, .01, false);
            case CYCLE -> disabled();
        };
    }
}
