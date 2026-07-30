package remframework.geometry.discovery;

import java.util.List;

public record GeometryDiscoveryResult(
        GeometryDiscoveryModel model,
        GeometryDiscoverySettings settings,
        GeometryDiscoveryMetrics metrics,
        DiscoveryValidationResult validation,
        RuntimeCompatibility runtimeCompatibility,
        List<String> diagnostics) {
    public boolean converged() { return metrics.converged(); }
}
