package remframework.geometry.discovery.realization.session;

public record ContinuationStageOutcome(String stageId,ContinuationStageState state,int iterations,
        int stableIterations,double rampFactor,String worstConstraint,double worstRatio,String reason) { }
