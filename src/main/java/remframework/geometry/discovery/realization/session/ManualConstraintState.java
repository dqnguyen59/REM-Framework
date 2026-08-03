package remframework.geometry.discovery.realization.session;

public record ManualConstraintState(ManualConstraint constraint,boolean relaxationActive,
        boolean measurementAvailable,boolean requiredForFinalQualification,double rawError,
        double tolerance,double normalizedRatio,ManualConstraintStatus status,Double restoringStrength,
        ConstraintMeasurement measurement) {
    public ManualConstraintState(ManualConstraint constraint,boolean relaxationActive,
            boolean measurementAvailable,boolean requiredForFinalQualification,double rawError,
            double tolerance,double normalizedRatio,ManualConstraintStatus status,Double restoringStrength) {
        this(constraint,relaxationActive,measurementAvailable,requiredForFinalQualification,rawError,
                tolerance,normalizedRatio,status,restoringStrength,null);
    }
    public enum ManualConstraintStatus { ACTIVE, INACTIVE_PASS, INACTIVE_FAIL, MEASURED, UNAVAILABLE, NUMERICALLY_INVALID }
}
