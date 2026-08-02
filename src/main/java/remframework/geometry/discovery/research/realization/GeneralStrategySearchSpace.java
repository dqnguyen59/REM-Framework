package remframework.geometry.discovery.research.realization;

import remframework.geometry.discovery.realization.session.ContinuationConstraint;

import java.util.List;
import java.util.Map;

/** Declared finite alternatives. Unsupported general algorithms remain visible and are not run. */
public final class GeneralStrategySearchSpace {
    private GeneralStrategySearchSpace(){ }
    public static ResearchSearchManifest initial(long masterSeed){
        List<ContinuationConstraint> order=List.of(ContinuationConstraint.EDGE_EQUALITY,
                ContinuationConstraint.OUTWARD_ORIENTATION,ContinuationConstraint.FACE_PLANARITY,
                ContinuationConstraint.FACE_ANGLE_EQUALITY,ContinuationConstraint.FACE_PLANE_RADII,
                ContinuationConstraint.JOINT_QUALIFICATION);
        List<RealizationStrategySpecification> strategies=List.of(
                spec("simultaneous-v1",RealizationStrategySpecification.Activation.SIMULTANEOUS,0,2_000,
                        RealizationStrategySpecification.Requirement.MEASURE_ONLY,RealizationStrategySpecification.Requirement.MEASURE_ONLY,RealizationStrategySpecification.Integration.DYNAMIC_ELASTIC,order),
                spec("sequential-ramp-100-v1",RealizationStrategySpecification.Activation.SEQUENTIAL_WITH_RAMP,100,2_000,
                        RealizationStrategySpecification.Requirement.MEASURE_ONLY,RealizationStrategySpecification.Requirement.MEASURE_ONLY,RealizationStrategySpecification.Integration.DYNAMIC_ELASTIC,order),
                spec("sequential-ramp-300-v1",RealizationStrategySpecification.Activation.SEQUENTIAL_WITH_RAMP,300,5_000,
                        RealizationStrategySpecification.Requirement.MEASURE_ONLY,RealizationStrategySpecification.Requirement.MEASURE_ONLY,RealizationStrategySpecification.Integration.DYNAMIC_ELASTIC,order),
                spec("sequential-convexity-v1",RealizationStrategySpecification.Activation.SEQUENTIAL_WITH_RAMP,200,5_000,
                        RealizationStrategySpecification.Requirement.REQUIRED,RealizationStrategySpecification.Requirement.MEASURE_ONLY,RealizationStrategySpecification.Integration.DYNAMIC_ELASTIC,order),
                spec("sequential-normal-alignment-v1",RealizationStrategySpecification.Activation.SEQUENTIAL_WITH_RAMP,200,5_000,
                        RealizationStrategySpecification.Requirement.MEASURE_ONLY,RealizationStrategySpecification.Requirement.REQUIRED,RealizationStrategySpecification.Integration.DYNAMIC_ELASTIC,order),
                spec("sequential-convexity-normal-v1",RealizationStrategySpecification.Activation.SEQUENTIAL_WITH_RAMP,200,5_000,
                        RealizationStrategySpecification.Requirement.REQUIRED,RealizationStrategySpecification.Requirement.REQUIRED,RealizationStrategySpecification.Integration.DYNAMIC_ELASTIC,order),
                spec("adaptive-sequential-v1",RealizationStrategySpecification.Activation.SEQUENTIAL_WITH_RAMP,200,5_000,
                        RealizationStrategySpecification.Requirement.MEASURE_ONLY,RealizationStrategySpecification.Requirement.MEASURE_ONLY,RealizationStrategySpecification.Integration.ADAPTIVE_GLOBAL_STEP,order));
        return new ResearchSearchManifest("SUCCESSIVE_HALVING",masterSeed,
                "SPHERE_LIKE: connected closed orientable surface, Euler characteristic 2, one intended interior region",
                List.of(500,2_000,10_000),List.of(42L,43L,44L,45L,46L),List.of(142L,143L,144L),strategies,
                List.of("SPECTRAL_EMBEDDING initializer","GRAPH_DISTANCE_SPHERICAL initializer",
                        "required convexity force","required normal-alignment force","adaptive/backtracking integration",
                        "maximum-volume finishing phase","custom constraint-order execution"));
    }
    private static RealizationStrategySpecification spec(String id,RealizationStrategySpecification.Activation activation,
            int ramp,int maximum,RealizationStrategySpecification.Requirement convexity,
            RealizationStrategySpecification.Requirement normal,RealizationStrategySpecification.Integration integration,
            List<ContinuationConstraint> order){return new RealizationStrategySpecification(id,1,
            RealizationStrategySpecification.Initialization.RANDOM_SPHERICAL,activation,order,
            ramp==0?RealizationStrategySpecification.RampType.NONE:RealizationStrategySpecification.RampType.LINEAR_BY_ITERATION,
            ramp,Map.of("facePlanarity",1.0,"faceAngle",.6,"faceDistance",.6,"orientation",1.0),
            Map.of("required",1.0),20,maximum,integration,
            new RealizationStrategySpecification.AdaptiveStepSettings(false,1,1e-6,1,.5),
            Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,100,1e-6,
            RealizationStrategySpecification.RestartPolicy.DETERMINISTIC_MULTISTART,5,
            activation==RealizationStrategySpecification.Activation.SEQUENTIAL_WITH_ROLLBACK?RealizationStrategySpecification.RollbackPolicy.SEVERE_REGRESSION:RealizationStrategySpecification.RollbackPolicy.DISABLED,
            convexity,normal,false,RealizationStrategySpecification.SeedPolicy.MASTER_SEED_SEQUENCE,
            "EXPERIMENTAL_STRATEGY_CANDIDATE");}
}
