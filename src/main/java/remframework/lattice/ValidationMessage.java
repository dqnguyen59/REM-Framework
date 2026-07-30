package remframework.lattice;

public record ValidationMessage(Severity severity, String message) {
    public enum Severity { WARNING, ERROR }
}
