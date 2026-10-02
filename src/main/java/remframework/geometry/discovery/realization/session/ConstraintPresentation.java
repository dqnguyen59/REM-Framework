package remframework.geometry.discovery.realization.session;

import java.util.List;
import remframework.geometry.discovery.ui.ScientificDisplayNames;

/** Immutable UI projection; it does not participate in solver or qualification decisions. */
public record ConstraintPresentation(String constraintId,String displayName,
        ConstraintPresentationMode mode,boolean applicable,boolean requiredForQualification,
        boolean activeRealizationForce,double rawError,String errorUnits,String metricName,
        double tolerance,String toleranceUnits,NormalizedErrorRatio errorRatio,
        Double restoringStrength,ConstraintPresentationStatus status,String explanation,
        ConstraintMeasurement measurement) {
    public ConstraintPresentation(String constraintId,String displayName,
            ConstraintPresentationMode mode,boolean applicable,boolean requiredForQualification,
            boolean activeRealizationForce,double rawError,String errorUnits,String metricName,
            double tolerance,String toleranceUnits,NormalizedErrorRatio errorRatio,
            Double restoringStrength,ConstraintPresentationStatus status,String explanation) {
        this(constraintId,displayName,mode,applicable,requiredForQualification,activeRealizationForce,
                rawError,errorUnits,metricName,tolerance,toleranceUnits,errorRatio,restoringStrength,
                status,explanation,legacyMeasurement(constraintId,status,rawError,tolerance,errorUnits,
                        explanation,errorRatio));
    }
    public ConstraintPresentation {
        displayName = ScientificDisplayNames.constraint(constraintId);
        measurement = measurement == null ? legacyMeasurement(constraintId,status,rawError,tolerance,
                errorUnits,explanation,errorRatio) : measurement;
    }
    public boolean continuous(){return errorRatio!=null;}
    public MeasurementState measurementState(){return measurement.state();}
    public String reasonCode(){return measurement.reasonCode();}
    public String reasonText(){return measurement.reasonText();}

    private static ConstraintMeasurement legacyMeasurement(String id,ConstraintPresentationStatus status,
            double raw,double tolerance,String unit,String reason,NormalizedErrorRatio ratio) {
        MeasurementState state = switch (status) {
            case MEASURED,SATISFIED,OUTSIDE_TOLERANCE,EXACT_TOLERANCE_EXCEEDED ->
                    Double.isFinite(raw) ? MeasurementState.MEASURED : MeasurementState.NUMERICALLY_INVALID;
            case NOT_APPLICABLE -> MeasurementState.NOT_APPLICABLE;
            case NUMERICALLY_INVALID -> MeasurementState.NUMERICALLY_INVALID;
            default -> MeasurementState.NOT_IMPLEMENTED;
        };
        Double value = Double.isFinite(raw) ? raw : null;
        Double acceptedTolerance = Double.isFinite(tolerance) ? tolerance : null;
        Double acceptedRatio = ratio != null && ratio.available() && Double.isFinite(ratio.value()) ? ratio.value() : null;
        return new ConstraintMeasurement(id,state,value,acceptedTolerance,acceptedRatio,unit,"LEGACY",
                reason,List.of(),value == null ? 0 : 1, value == null ? 1 : 0,0,"");
    }
}
