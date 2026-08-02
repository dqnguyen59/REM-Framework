package remframework.geometry.discovery.realization.session;

import java.util.List;

public record ContinuationSnapshot(RealizationStrategy strategy,String scheduleId,int scheduleVersion,
        int stageIndex,int stageCount,String stageId,String stageName,ContinuationStageState state,
        int stageIteration,int totalIteration,double rampFactor,int stableIterations,int requiredStableIterations,
        String worstConstraint,double worstRatio,List<ContinuationConstraint> active,
        List<ContinuationConstraint> retained,List<ContinuationStageOutcome> history,String failureReason) {
    public ContinuationSnapshot { active=List.copyOf(active);retained=List.copyOf(retained);history=List.copyOf(history); }
    public static ContinuationSnapshot simultaneous(){return new ContinuationSnapshot(RealizationStrategy.SIMULTANEOUS,"NONE",0,0,0,"SIMULTANEOUS","Simultaneous",ContinuationStageState.RELAXING,0,0,1,0,0,"unavailable",Double.NaN,List.of(),List.of(),List.of(),"");}
}
