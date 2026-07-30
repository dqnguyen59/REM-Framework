package remframework.geometry.discovery;

public record DiscoveryValidationMessage(Severity severity, String code, String message) {
    public enum Severity { WARNING, ERROR }
}
