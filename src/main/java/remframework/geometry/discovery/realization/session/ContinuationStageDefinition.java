package remframework.geometry.discovery.realization.session;

import java.util.Set;

public record ContinuationStageDefinition(String id,String displayName,
        Set<ContinuationConstraint> introduced,Set<ContinuationConstraint> retained,
        int stableIterations,int maximumIterations,ContinuationRampMode rampMode,
        int rampIterations,double startingFactor,double finalFactor,boolean rollbackEnabled,
        double severeRegressionRatio) {
    public ContinuationStageDefinition { introduced=Set.copyOf(introduced);retained=Set.copyOf(retained); }
}
