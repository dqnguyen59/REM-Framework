package remframework.geometry.discovery;

import java.nio.file.Path;

public record LoadedDiscoveryCandidate(GeometryDiscoveryModel model,
                                       GeometryDiscoverySettings settings,
                                       GeometryDiscoveryResult storedResult,
                                       Path sourcePath) {
}
