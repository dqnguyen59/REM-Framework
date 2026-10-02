package remframework.geometry.discovery.experiment;

import remframework.geometry.discovery.GeometryEmbeddingDimension;

import java.util.Set;

/** Declares profile-specific gates without adding constraints to realization forces. */
public enum ExperimentProfile {
    CLOSED_2D_AREA(GeometryEmbeddingDimension.TWO_D, 2, false,
            Set.of(MeasurementType.AREA, MeasurementType.REGULARITY),
            Set.of(OptimizationObjective.NONE, OptimizationObjective.MAXIMIZE_ENCLOSED_AREA)),
    CLOSED_3D_VOLUME(GeometryEmbeddingDimension.THREE_D, 3, false,
            Set.of(MeasurementType.SURFACE_AREA, MeasurementType.VOLUME, MeasurementType.REGULARITY),
            Set.of(OptimizationObjective.NONE, OptimizationObjective.MAXIMIZE_ENCLOSED_VOLUME)),
    CONVEX_POLYHEDRAL_VOLUME(GeometryEmbeddingDimension.THREE_D, 3, true,
            Set.of(MeasurementType.SURFACE_AREA, MeasurementType.VOLUME, MeasurementType.REGULARITY),
            Set.of(OptimizationObjective.NONE, OptimizationObjective.MAXIMIZE_ENCLOSED_VOLUME));

    private final GeometryEmbeddingDimension dimension;
    private final int minimumDegree;
    private final boolean planarTopologyRequired;
    private final Set<MeasurementType> measurements;
    private final Set<OptimizationObjective> objectives;

    ExperimentProfile(GeometryEmbeddingDimension dimension, int minimumDegree,
            boolean planarTopologyRequired, Set<MeasurementType> measurements,
            Set<OptimizationObjective> objectives) {
        this.dimension = dimension;
        this.minimumDegree = minimumDegree;
        this.planarTopologyRequired = planarTopologyRequired;
        this.measurements = Set.copyOf(measurements);
        this.objectives = Set.copyOf(objectives);
    }

    public GeometryEmbeddingDimension intendedDimension() { return dimension; }
    public int minimumDegree() { return minimumDegree; }
    public boolean planarTopologyRequired() { return planarTopologyRequired; }
    public Set<MeasurementType> permittedMeasurements() { return measurements; }
    public Set<OptimizationObjective> permittedOptimizationObjectives() { return objectives; }
    public AngleConditionMode defaultAngleMode() { return AngleConditionMode.MEASURE_ONLY; }
    public RegularityMode defaultRegularityMode() { return RegularityMode.MEASURE_ONLY; }
}
