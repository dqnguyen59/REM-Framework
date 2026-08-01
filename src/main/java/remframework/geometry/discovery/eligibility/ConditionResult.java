package remframework.geometry.discovery.eligibility;

public record ConditionResult(String condition, ConditionOutcome outcome, String explanation,
        Double measuredValue, Double expectedValue, Double tolerance) {
    public static ConditionResult pass(String name, String explanation) {
        return new ConditionResult(name, ConditionOutcome.PASS, explanation, null, null, null);
    }
    public static ConditionResult fail(String name, String explanation) {
        return new ConditionResult(name, ConditionOutcome.FAIL, explanation, null, null, null);
    }
    public static ConditionResult measured(String name, boolean pass, double measured,
            double expected, double tolerance, String explanation) {
        return new ConditionResult(name, pass ? ConditionOutcome.PASS : ConditionOutcome.FAIL,
                explanation, measured, expected, tolerance);
    }
    public static ConditionResult unknown(String name, String explanation) {
        return new ConditionResult(name, ConditionOutcome.NOT_DETERMINABLE_FROM_TOPOLOGY,
                explanation, null, null, null);
    }
}
