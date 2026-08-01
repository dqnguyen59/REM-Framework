package remframework.geometry.discovery.configuration;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;

import java.nio.file.Path;
import java.time.Duration;

public record ConfigurationRequest(int nodeCount, int exactDegree,
        GeometryEmbeddingDimension intendedDimension, GraphSymmetryFilter symmetryFilter,
        int maximumConfigurations, Duration enumerationTimeLimit,
        String generationStrategy, Path outputDirectory) {
    public ConfigurationRequest {
        intendedDimension = intendedDimension == null ? GeometryEmbeddingDimension.THREE_D : intendedDimension;
        symmetryFilter = symmetryFilter == null ? GraphSymmetryFilter.BOTH : symmetryFilter;
        generationStrategy = generationStrategy == null || generationStrategy.isBlank()
                ? "EXHAUSTIVE_BACKTRACKING" : generationStrategy;
    }
    public static ConfigurationRequest exhaustive(int n, int k,
            GeometryEmbeddingDimension dimension) {
        return new ConfigurationRequest(n, k, dimension, GraphSymmetryFilter.BOTH,
                10_000, Duration.ofSeconds(30), "EXHAUSTIVE_BACKTRACKING", null);
    }
}
