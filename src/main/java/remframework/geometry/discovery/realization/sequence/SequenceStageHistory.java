package remframework.geometry.discovery.realization.sequence;

import java.util.Map;

public record SequenceStageHistory(String stageId,int stageNumber,int iterations,
        Map<ConstraintId,Double> startErrors,Map<ConstraintId,Double> endErrors,
        Map<ConstraintId,Double> errorRatios,int stableCount,String stagnationClassification,
        double latestRegression,SequenceConfigurationStatus status,String explanation) {
    public SequenceStageHistory {startErrors=Map.copyOf(startErrors);endErrors=Map.copyOf(endErrors);errorRatios=Map.copyOf(errorRatios);explanation=explanation==null?"":explanation;}
}
