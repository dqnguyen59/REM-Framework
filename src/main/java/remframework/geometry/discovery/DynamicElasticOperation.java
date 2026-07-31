package remframework.geometry.discovery;

public enum DynamicElasticOperation {
    READY("Ready"),
    RUNNING_CONTINUOUSLY("Running continuously"),
    RUNNING_UNTIL_EQUILIBRIUM("Running until equilibrium"),
    PAUSED("Paused"),
    EQUILIBRIUM_REACHED("Equilibrium reached"),
    MAXIMUM_ITERATIONS_REACHED("Maximum iterations reached"),
    NUMERICAL_FAILURE("Numerical failure");

    private final String label;
    DynamicElasticOperation(String label) { this.label = label; }
    public String label() { return label; }
}
