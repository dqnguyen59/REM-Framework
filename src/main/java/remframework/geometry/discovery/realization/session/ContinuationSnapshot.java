package remframework.geometry.discovery.realization.session;

import java.util.List;

public record ContinuationSnapshot(RealizationStrategy strategy,String scheduleId,int scheduleVersion,
        remframework.geometry.discovery.TopologyInitialization initialization,
        int stageIndex,int stageCount,String stageId,String stageName,ContinuationStageState state,
        int stageIteration,int totalIteration,double rampFactor,int stableIterations,int requiredStableIterations,
        String worstConstraint,double worstRatio,List<ContinuationConstraint> active,
        List<ContinuationConstraint> retained,List<ContinuationStageOutcome> history,String failureReason,
        SpectralInitializationSettings spectralInitialization,InitializationSnapshot initializationState) {
    public ContinuationSnapshot { active=List.copyOf(active);retained=List.copyOf(retained);history=List.copyOf(history); }
    public static ContinuationSnapshot simultaneous(){return simultaneous(remframework.geometry.discovery.TopologyInitialization.RANDOM_SPHERICAL);}
    public static ContinuationSnapshot manual(remframework.geometry.discovery.TopologyInitialization initialization,SpectralInitializationSettings settings,InitializationSnapshot state,List<ContinuationConstraint> active,int iteration){return new ContinuationSnapshot(RealizationStrategy.MANUAL_RELAXATION,"MANUAL",1,initialization,0,0,"MANUAL","Manual Relaxation",ContinuationStageState.RELAXING,iteration,iteration,1,0,0,"unavailable",Double.NaN,active,List.of(),List.of(),"",settings,state);}
    public static ContinuationSnapshot simultaneous(remframework.geometry.discovery.TopologyInitialization initialization){return simultaneous(initialization,SpectralInitializationSettings.instant(),InitializationSnapshot.instant());}
    public static ContinuationSnapshot simultaneous(remframework.geometry.discovery.TopologyInitialization initialization,SpectralInitializationSettings settings,InitializationSnapshot state){return new ContinuationSnapshot(RealizationStrategy.SIMULTANEOUS,"NONE",0,initialization,0,0,"SIMULTANEOUS","Simultaneous",ContinuationStageState.RELAXING,0,0,1,0,0,"unavailable",Double.NaN,List.of(),List.of(),List.of(),"",settings,state);}
}
