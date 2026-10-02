package remframework.geometry.discovery.realization.session;

/** Immutable audit record for one experimental runtime corrective-force factor. */
public record ConstraintStrengthFactor(String constraintId, Double baseStrength,
        double runtimeFactor, Double effectiveStrength, boolean correctiveForceImplemented,
        boolean relaxationActive, long latestChangeRevision) {
    public static final double MIN_FACTOR = 0.0;
    public static final double MAX_FACTOR = 100.0;

    public ConstraintStrengthFactor {
        if (constraintId == null || constraintId.isBlank()) throw new IllegalArgumentException("Constraint ID is required.");
        validateFactor(runtimeFactor);
        if (baseStrength != null && (!Double.isFinite(baseStrength) || baseStrength < 0))
            throw new IllegalArgumentException("Base strength must be finite and non-negative.");
        if (effectiveStrength != null) {
            double expected = baseStrength == null ? 0 : baseStrength * runtimeFactor;
            if (!Double.isFinite(expected) || Math.abs(effectiveStrength - expected) > Math.max(1e-12, Math.abs(expected) * 1e-12))
                throw new IllegalArgumentException("Effective strength must equal base strength multiplied by runtime factor.");
        }
    }

    public ConstraintStrengthFactor(String constraintId, Double baseStrength, double runtimeFactor,
            boolean correctiveForceImplemented, boolean relaxationActive, long revision) {
        this(constraintId, baseStrength, runtimeFactor,
                correctiveForceImplemented && baseStrength != null ? baseStrength * runtimeFactor : null,
                correctiveForceImplemented, relaxationActive, revision);
    }

    public static void validateFactor(double value) {
        if (!Double.isFinite(value) || value < MIN_FACTOR || value > MAX_FACTOR)
            throw new IllegalArgumentException("Runtime strength factor must be finite and within [0, 100].");
    }

    public ConstraintStrengthFactor withFactor(double value, boolean active, long revision) {
        validateFactor(value);
        return new ConstraintStrengthFactor(constraintId, baseStrength, value,
                correctiveForceImplemented && baseStrength != null ? baseStrength * value : null,
                correctiveForceImplemented, active, revision);
    }
}
