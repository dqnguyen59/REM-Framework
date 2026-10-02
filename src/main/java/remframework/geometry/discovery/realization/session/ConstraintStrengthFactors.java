package remframework.geometry.discovery.realization.session;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Immutable per-constraint runtime factor set. Base strengths remain owned by the request. */
public record ConstraintStrengthFactors(Map<ManualConstraint, ConstraintStrengthFactor> values,
        long revision) {
    public ConstraintStrengthFactors {
        EnumMap<ManualConstraint, ConstraintStrengthFactor> copy = new EnumMap<>(ManualConstraint.class);
        if (values != null) copy.putAll(values);
        values = Map.copyOf(copy);
        if (revision < 0) throw new IllegalArgumentException("Strength revision cannot be negative.");
    }

    public static ConstraintStrengthFactors defaults(Map<ManualConstraint, Double> baseStrengths,
            List<ManualConstraint> implemented) {
        EnumMap<ManualConstraint, ConstraintStrengthFactor> result = new EnumMap<>(ManualConstraint.class);
        for (ManualConstraint constraint : ManualConstraint.values()) {
            Double base = baseStrengths == null ? null : baseStrengths.get(constraint);
            boolean force = implemented != null && implemented.contains(constraint);
            result.put(constraint, new ConstraintStrengthFactor(constraint.name(), base, 1.0,
                    force, false, 0));
        }
        return new ConstraintStrengthFactors(result, 0);
    }

    public ConstraintStrengthFactor get(ManualConstraint constraint) { return values.get(constraint); }
    public double factor(ManualConstraint constraint) { return get(constraint).runtimeFactor(); }
    public double effective(ManualConstraint constraint) {
        ConstraintStrengthFactor value = get(constraint);
        return value.effectiveStrength() == null ? 0 : value.effectiveStrength();
    }

    public ConstraintStrengthFactors withFactor(ManualConstraint constraint, double factor,
            boolean active) {
        ConstraintStrengthFactor current = get(constraint);
        if (current == null) throw new IllegalArgumentException("Unknown constraint: " + constraint);
        if (!current.correctiveForceImplemented())
            throw new IllegalArgumentException("Strength unavailable: measurement-only constraint " + constraint.displayName());
        EnumMap<ManualConstraint, ConstraintStrengthFactor> next = new EnumMap<>(ManualConstraint.class);
        next.putAll(values);
        long nextRevision = revision + 1;
        next.put(constraint, current.withFactor(factor, active, nextRevision));
        return new ConstraintStrengthFactors(next, nextRevision);
    }

    public ConstraintStrengthFactors withActiveSet(java.util.Set<ManualConstraint> active) {
        EnumMap<ManualConstraint, ConstraintStrengthFactor> next = new EnumMap<>(ManualConstraint.class);
        for (var entry : values.entrySet()) {
            ConstraintStrengthFactor current = entry.getValue();
            next.put(entry.getKey(), new ConstraintStrengthFactor(current.constraintId(), current.baseStrength(),
                    current.runtimeFactor(), current.effectiveStrength(), current.correctiveForceImplemented(),
                    active.contains(entry.getKey()), current.latestChangeRevision()));
        }
        return new ConstraintStrengthFactors(next, revision);
    }
}
