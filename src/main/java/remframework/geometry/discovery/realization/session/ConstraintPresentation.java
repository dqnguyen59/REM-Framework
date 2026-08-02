package remframework.geometry.discovery.realization.session;

import remframework.geometry.discovery.ui.ScientificDisplayNames;

/** Immutable UI projection; it does not participate in solver or qualification decisions. */
public record ConstraintPresentation(String constraintId,String displayName,
        ConstraintPresentationMode mode,boolean applicable,boolean requiredForQualification,
        boolean activeRealizationForce,double rawError,String errorUnits,String metricName,
        double tolerance,String toleranceUnits,NormalizedErrorRatio errorRatio,
        Double restoringStrength,ConstraintPresentationStatus status,String explanation) {
    public ConstraintPresentation {
        displayName = ScientificDisplayNames.constraint(constraintId);
    }
    public boolean continuous(){return errorRatio!=null;}
}
