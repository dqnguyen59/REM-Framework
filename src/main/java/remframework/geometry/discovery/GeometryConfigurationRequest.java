package remframework.geometry.discovery;

import java.nio.file.Path;
import java.time.Duration;

public record GeometryConfigurationRequest(int nodeCount, int exactDegree,
        GeometryEmbeddingDimension intendedDimension, GraphSymmetryFilter symmetryFilter,
        int maximumConfigurations, Duration enumerationTimeLimit,
        String generationStrategy, Path outputDirectory) {
    public GeometryConfigurationRequest {
        intendedDimension = intendedDimension == null ? GeometryEmbeddingDimension.THREE_D : intendedDimension;
        symmetryFilter = symmetryFilter == null ? GraphSymmetryFilter.BOTH : symmetryFilter;
        generationStrategy = generationStrategy == null || generationStrategy.isBlank()
                ? "EXHAUSTIVE_BACKTRACKING" : generationStrategy;
    }
    public static GeometryConfigurationRequest exhaustive(int n, int k,
            GeometryEmbeddingDimension dimension) {
        return new GeometryConfigurationRequest(n, k, dimension, GraphSymmetryFilter.BOTH,
                10_000, Duration.ofSeconds(30), "EXHAUSTIVE_BACKTRACKING", null);
    }
}
