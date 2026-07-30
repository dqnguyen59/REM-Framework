package remframework.geometry.discovery;

import java.nio.file.Path;

public record GeometryBatchRun(GeometryBatchCombination combination,
                               GeometryDiscoveryResult result,
                               long elapsedNanoseconds, Path exportedCandidate) {
}
