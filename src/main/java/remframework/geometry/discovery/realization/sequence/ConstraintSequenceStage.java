package remframework.geometry.discovery.realization.sequence;

import java.util.Objects;
import java.util.UUID;
import remframework.geometry.discovery.realization.session.ContinuationRampMode;

/** Immutable, persistence-ready definition of one ordered Stage 2 experiment stage. */
public record ConstraintSequenceStage(String stageId, ConstraintId constraintId, String displayName,
        boolean enabled, ConstraintStageMode mode, ConstraintActivationBehavior activationBehavior,
        double finalRestoringStrength, ContinuationRampMode rampMode, int rampIterations,
        double transitionTolerance, int stableIterations, int maximumIterations,
        ConstraintStagnationPolicy stagnationPolicy, double severeRegressionThreshold,
        ConstraintRollbackPolicy rollbackPolicy, String notes) {
    public ConstraintSequenceStage {
        if(stageId==null||stageId.isBlank())throw new IllegalArgumentException("A stable stage ID is required.");
        Objects.requireNonNull(constraintId,"constraintId");
        displayName=displayName==null||displayName.isBlank()?constraintId.displayName():displayName;
        mode=mode==null?ConstraintStageMode.REQUIRED:mode;
        activationBehavior=activationBehavior==null?ConstraintActivationBehavior.CUMULATIVE:activationBehavior;
        rampMode=rampMode==null?ContinuationRampMode.LINEAR_BY_ITERATION:rampMode;
        stagnationPolicy=stagnationPolicy==null?ConstraintStagnationPolicy.STOP_SEQUENCE:stagnationPolicy;
        rollbackPolicy=rollbackPolicy==null?ConstraintRollbackPolicy.NONE:rollbackPolicy;
        notes=notes==null?"":notes;
        if(!Double.isFinite(finalRestoringStrength)||finalRestoringStrength<0||rampIterations<0
                ||!Double.isFinite(transitionTolerance)||transitionTolerance<=0||stableIterations<1
                ||maximumIterations<1||!Double.isFinite(severeRegressionThreshold)||severeRegressionThreshold<=1)
            throw new IllegalArgumentException("Stage numeric settings are outside their valid ranges.");
    }
    public static ConstraintSequenceStage required(ConstraintId id){return new ConstraintSequenceStage(
            UUID.randomUUID().toString(),id,id.displayName(),true,ConstraintStageMode.REQUIRED,
            ConstraintActivationBehavior.CUMULATIVE,1,ContinuationRampMode.LINEAR_BY_ITERATION,
            100,1,20,5_000,ConstraintStagnationPolicy.STOP_SEQUENCE,5,
            ConstraintRollbackPolicy.NONE,"");}
    public ConstraintSequenceStage duplicate(){return new ConstraintSequenceStage(UUID.randomUUID().toString(),constraintId,
            displayName,enabled,mode,activationBehavior,finalRestoringStrength,rampMode,rampIterations,
            transitionTolerance,stableIterations,maximumIterations,stagnationPolicy,
            severeRegressionThreshold,rollbackPolicy,notes);}
    public ConstraintSequenceStage withEnabled(boolean value){return new ConstraintSequenceStage(stageId,constraintId,
            displayName,value,mode,activationBehavior,finalRestoringStrength,rampMode,rampIterations,
            transitionTolerance,stableIterations,maximumIterations,stagnationPolicy,
            severeRegressionThreshold,rollbackPolicy,notes);}
}
