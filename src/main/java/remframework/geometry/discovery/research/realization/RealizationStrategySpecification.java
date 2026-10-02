package remframework.geometry.discovery.research.realization;

import remframework.geometry.discovery.realization.session.ContinuationConstraint;

import java.util.List;
import java.util.Map;

/** Immutable, topology-independent research policy. It contains no benchmark identity. */
public record RealizationStrategySpecification(String id,int strategyVersion,
        Initialization initialization,Activation activation,
        List<ContinuationConstraint> constraintActivationOrder,RampType rampType,
        int rampDuration,Map<String,Double> finalRestoringStrengths,
        Map<String,Double> transitionErrorRatioBoundaries,int stableIterations,
        int maximumIterationsPerStage,Integration integration,
        AdaptiveStepSettings adaptiveStep,double displacementLimit,double forceLimit,
        int stagnationWindow,double stagnationRelativeImprovement,
        RestartPolicy restartPolicy,int multistartCount,RollbackPolicy rollbackPolicy,
        Requirement convexity,Requirement normalAlignment,boolean maximumVolumeFinishing,
        SeedPolicy seedPolicy,String experimentalStatus) {
    public RealizationStrategySpecification {
        constraintActivationOrder=List.copyOf(constraintActivationOrder);
        finalRestoringStrengths=Map.copyOf(finalRestoringStrengths);
        transitionErrorRatioBoundaries=Map.copyOf(transitionErrorRatioBoundaries);
        if(id==null||id.isBlank()||strategyVersion<1||rampDuration<0||stableIterations<1
                ||maximumIterationsPerStage<1||multistartCount<1||stagnationWindow<1)
            throw new IllegalArgumentException("Invalid realization strategy specification.");
        if(experimentalStatus==null||experimentalStatus.isBlank())experimentalStatus="EXPERIMENTAL_STRATEGY_CANDIDATE";
    }
    public enum Initialization { RANDOM_SPHERICAL,SPECTRAL_EMBEDDING,GRAPH_DISTANCE_SPHERICAL }
    public enum Activation { SIMULTANEOUS,SEQUENTIAL,SEQUENTIAL_WITH_RAMP,SEQUENTIAL_WITH_ROLLBACK }
    public enum RampType { NONE,LINEAR_BY_ITERATION,ERROR_GATED }
    public enum Integration { DYNAMIC_ELASTIC,ADAPTIVE_GLOBAL_STEP,BOUNDED_DISPLACEMENT,BACKTRACKING_ACCEPTANCE }
    public enum Requirement { OFF,MEASURE_ONLY,REQUIRED }
    public enum RestartPolicy { NONE,DETERMINISTIC_MULTISTART }
    public enum RollbackPolicy { DISABLED,SEVERE_REGRESSION }
    public enum SeedPolicy { MASTER_SEED_SEQUENCE }
    public record AdaptiveStepSettings(boolean enabled,double initialStep,double minimumStep,
            double maximumStep,double reductionFactor) { }

    public boolean executableByCurrentSolver(){return (initialization==Initialization.RANDOM_SPHERICAL||initialization==Initialization.SPECTRAL_EMBEDDING)
            &&(activation==Activation.SIMULTANEOUS||activation==Activation.SEQUENTIAL_WITH_RAMP)
            &&integration==Integration.DYNAMIC_ELASTIC&&convexity!=Requirement.REQUIRED
            &&normalAlignment!=Requirement.REQUIRED&&!maximumVolumeFinishing;}
}
