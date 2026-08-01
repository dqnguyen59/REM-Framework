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
import remframework.geometry.discovery.experiment.ExperimentProfile;

public record ConfigurationRequest(int nodeCount, int exactDegree,
        GeometryEmbeddingDimension intendedDimension, GraphSymmetryFilter symmetryFilter,
        int maximumConfigurations, Duration enumerationTimeLimit,
        String generationStrategy, Path outputDirectory, ExperimentProfile experimentProfile) {
    public ConfigurationRequest {
        intendedDimension = intendedDimension == null ? GeometryEmbeddingDimension.THREE_D : intendedDimension;
        symmetryFilter = symmetryFilter == null ? GraphSymmetryFilter.BOTH : symmetryFilter;
        generationStrategy = generationStrategy == null || generationStrategy.isBlank()
                ? "EXHAUSTIVE_BACKTRACKING" : generationStrategy;
        experimentProfile = experimentProfile == null
                ? (intendedDimension == GeometryEmbeddingDimension.TWO_D
                    ? ExperimentProfile.CLOSED_2D_AREA : ExperimentProfile.CLOSED_3D_VOLUME)
                : experimentProfile;
    }
    public ConfigurationRequest(int nodeCount, int exactDegree,
            GeometryEmbeddingDimension intendedDimension, GraphSymmetryFilter symmetryFilter,
            int maximumConfigurations, Duration enumerationTimeLimit,
            String generationStrategy, Path outputDirectory) {
        this(nodeCount, exactDegree, intendedDimension, symmetryFilter, maximumConfigurations,
                enumerationTimeLimit, generationStrategy, outputDirectory, null);
    }
    public static ConfigurationRequest exhaustive(int n, int k,
            GeometryEmbeddingDimension dimension) {
        return new ConfigurationRequest(n, k, dimension, GraphSymmetryFilter.BOTH,
                10_000, Duration.ofSeconds(30), "EXHAUSTIVE_BACKTRACKING", null, null);
    }
}
