package remframework.measurement.gravitypressure;

/**
 * Immutable, fully scalar Gravity Test 2 CSV row.
 */
public record GravityPressureMeasurementSnapshot(String[] values) {
    public GravityPressureMeasurementSnapshot {
        values = values.clone();
    }

    @Override
    public String[] values() {
        return values.clone();
    }
}
