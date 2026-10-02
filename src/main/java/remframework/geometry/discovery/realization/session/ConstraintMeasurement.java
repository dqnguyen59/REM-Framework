package remframework.geometry.discovery.realization.session;

import java.util.List;

/**
 * Immutable scientific measurement record. A missing numeric value is represented by
 * {@code null}; availability is determined by {@link #state()}, never by a sentinel.
 */
public record ConstraintMeasurement(String constraintId, MeasurementState state,
        Double rawValue, Double tolerance, Double ratio, String unit,
        String reasonCode, String reasonText, List<String> affectedElementIds,
        int sampleCount, int invalidSampleCount, long coordinateRevision,
        String qualitativeState) {
    public ConstraintMeasurement {
        if (constraintId == null || constraintId.isBlank()) throw new IllegalArgumentException("constraintId is required");
        state = state == null ? MeasurementState.NUMERICALLY_INVALID : state;
        unit = unit == null ? "" : unit;
        reasonCode = reasonCode == null ? "" : reasonCode;
        reasonText = reasonText == null ? "" : reasonText;
        affectedElementIds = affectedElementIds == null ? List.of() : List.copyOf(affectedElementIds);
        qualitativeState = qualitativeState == null ? "" : qualitativeState;
        if (sampleCount < 0 || invalidSampleCount < 0) throw new IllegalArgumentException("sample counts cannot be negative");
        if (state == MeasurementState.MEASURED || state == MeasurementState.PARTIAL_MEASUREMENT) {
            if ((rawValue == null || !Double.isFinite(rawValue)) && qualitativeState.isBlank())
                throw new IllegalArgumentException("Measured values must be finite unless the measurement is qualitative");
        }
        if (ratio != null && !Double.isFinite(ratio) && state != MeasurementState.MEASURED) ratio = null;
    }

    public boolean measured() { return state == MeasurementState.MEASURED || state == MeasurementState.PARTIAL_MEASUREMENT; }
    public boolean ratioAvailable() { return measured() && ratio != null && Double.isFinite(ratio); }

    public static ConstraintMeasurement measured(String id, double raw, Double tolerance,
            String unit, String formula, int samples, int invalidSamples, long revision) {
        Double ratio = tolerance != null && Double.isFinite(tolerance) && tolerance > 0 ? raw / tolerance : null;
        return new ConstraintMeasurement(id, MeasurementState.MEASURED, raw, tolerance, ratio,
                unit, "", formula, List.of(), samples, invalidSamples, revision, "");
    }

    public static ConstraintMeasurement unavailable(String id, MeasurementState state,
            String reasonCode, String reason, String unit, int samples, int invalidSamples,
            List<String> affected, long revision) {
        return new ConstraintMeasurement(id, state, null, null, null, unit, reasonCode,
                reason, affected, samples, invalidSamples, revision, "");
    }

    public static ConstraintMeasurement legacy(String id, Double rawValue, Double tolerance,
            String unit, long revision) {
        if (rawValue == null || !Double.isFinite(rawValue))
            return unavailable(id, MeasurementState.NUMERICALLY_INVALID_LEGACY_VALUE,
                    "LEGACY_NON_FINITE_VALUE", "Legacy persisted result contained NaN or Infinity; it was not converted to zero.",
                    unit, 0, 1, List.of(), revision);
        return measured(id, rawValue, tolerance, unit, "Legacy persisted measurement", 1, 0, revision);
    }

    public ConstraintMeasurement withQualitativeState(String value) {
        return new ConstraintMeasurement(constraintId, state, rawValue, tolerance, ratio, unit,
                reasonCode, reasonText, affectedElementIds, sampleCount, invalidSampleCount,
                coordinateRevision, value);
    }

    public ConstraintMeasurement withTolerance(Double value) {
        Double nextRatio = measured() && rawValue != null && value != null && Double.isFinite(value) && value > 0
                ? rawValue / value : null;
        return new ConstraintMeasurement(constraintId, state, rawValue, value, nextRatio, unit,
                reasonCode, reasonText, affectedElementIds, sampleCount, invalidSampleCount,
                coordinateRevision, qualitativeState);
    }
}
