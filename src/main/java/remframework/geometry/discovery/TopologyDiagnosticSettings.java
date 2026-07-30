package remframework.geometry.discovery;

public record TopologyDiagnosticSettings(
        double eigenvalueRelativeTolerance,
        double oppositeDirectionToleranceRadians,
        int sphericalSampleCount) {
    public static TopologyDiagnosticSettings defaults() {
        return new TopologyDiagnosticSettings(1e-4, Math.toRadians(15), 256);
    }
}
