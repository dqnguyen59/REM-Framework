package remframework.geometry.discovery;

import java.nio.file.Path;

public record LoadedDynamicElasticResult(GeometryDiscoveryModel model,
                                         DynamicElasticSettings settings,
                                         Path source) {
}
