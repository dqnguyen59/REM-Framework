package remframework.geometry.discovery;

import java.util.List;

public record GeometryConfigurationGenerationResult(String setId,
        GeometryConfigurationRequest request, List<GeometryConfiguration> configurations,
        GeometryConfigurationMetrics metrics, GeometryConfigurationStatus status) {
    public GeometryConfigurationGenerationResult { configurations = List.copyOf(configurations); }
}
