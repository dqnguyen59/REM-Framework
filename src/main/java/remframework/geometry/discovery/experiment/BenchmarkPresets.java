package remframework.geometry.discovery.experiment;

/** General-condition definitions for optional reference-topology benchmarks. */
public final class BenchmarkPresets {
    private BenchmarkPresets() { }

    public static ThreeDExperimentConditions conditions(BenchmarkPreset preset) {
        return switch (preset == null ? BenchmarkPreset.NONE : preset) {
            case NONE, CUBE_EMERGENT_BENCHMARK -> ThreeDExperimentConditions.defaults();
            case CUBE_CONSTRAINED_BENCHMARK -> new ThreeDExperimentConditions(
                    FacePlanarityMode.REQUIRE_PLANAR_FACES,
                    FaceAngleConditionMode.REQUIRE_EQUAL_ANGLES_WITHIN_EACH_FACE,
                    DihedralConditionMode.REQUIRE_EQUAL_DIHEDRAL_ANGLES,
                    RegularityMode.MEASURE_ONLY, RegularityMode.MEASURE_ONLY,
                    RegularityMode.MEASURE_ONLY,
                    FacePlaneRadiusMode.REQUIRE_EQUAL_FACE_PLANE_RADII,
                    RegularityMode.MEASURE_ONLY, GeometryCenterDefinition.VERTEX_CENTROID,
                    1e-7, 1e-6, 1e-6);
        };
    }
}
