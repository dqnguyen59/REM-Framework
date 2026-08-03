package remframework.geometry.discovery.realization.session;

public record ManualConstraintState(ManualConstraint constraint,boolean relaxationActive,
        boolean measurementAvailable,boolean requiredForFinalQualification,double rawError,
        double tolerance,double normalizedRatio,ManualConstraintStatus status,Double restoringStrength) {
    public enum ManualConstraintStatus { ACTIVE, INACTIVE_PASS, INACTIVE_FAIL, MEASURED, UNAVAILABLE, NUMERICALLY_INVALID }
}
