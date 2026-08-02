package remframework.geometry.discovery.realization.session;

/** Immutable UI projection; it does not participate in solver or qualification decisions. */
public record ConstraintPresentation(String constraintId,String displayName,
        ConstraintPresentationMode mode,boolean applicable,boolean requiredForQualification,
        boolean activeRealizationForce,double rawError,String errorUnits,String metricName,
        double tolerance,String toleranceUnits,NormalizedErrorRatio errorRatio,
        Double restoringStrength,ConstraintPresentationStatus status,String explanation) {
    public boolean continuous(){return errorRatio!=null;}
}
