package remframework.geometry.discovery.realization.sequence;

import java.util.*;
import remframework.geometry.discovery.realization.session.*;

/** Compatibility adapter that executes custom stages through the existing continuation solver. */
public final class CustomSequenceAdapter {
    private CustomSequenceAdapter() { }
    public static ContinuationSchedule toContinuation(ConstraintSequence sequence){
        new ConstraintSequenceValidator().requireExecutable(sequence);List<ContinuationStageDefinition> result=new ArrayList<>();
        for(int i=0;i<sequence.stages().size();i++){ConstraintSequenceStage stage=sequence.stages().get(i);if(!stage.enabled())continue;ConstraintActivationPlan.Snapshot plan=ConstraintActivationPlan.at(sequence,i);Set<ContinuationConstraint> retained=map(plan.retained());Set<ContinuationConstraint> introduced=stage.mode()==ConstraintStageMode.REQUIRED?map(Set.of(stage.constraintId())):Set.of();
            if(stage.mode()==ConstraintStageMode.REQUIRED&&introduced.isEmpty())throw new IllegalArgumentException(stage.constraintId().displayName()+" is not defined as an active restoring constraint by the current solver; use MEASURE_ONLY.");
            result.add(new ContinuationStageDefinition(stage.stageId(),stage.displayName(),introduced,retained,stage.stableIterations(),stage.maximumIterations(),stage.rampMode(),stage.rampIterations(),0,stage.finalRestoringStrength(),stage.rollbackPolicy()==ConstraintRollbackPolicy.ROLLBACK_STAGE,stage.severeRegressionThreshold()));}
        if(result.isEmpty())throw new IllegalArgumentException("A custom sequence needs at least one enabled executable stage.");return new ContinuationSchedule(sequence.sequenceId(),sequence.version(),result);
    }
    private static Set<ContinuationConstraint> map(Collection<ConstraintId> ids){LinkedHashSet<ContinuationConstraint> result=new LinkedHashSet<>();for(ConstraintId id:ids){ContinuationConstraint mapped=switch(id){case EDGE_LENGTH->ContinuationConstraint.EDGE_EQUALITY;case FACE_ORIENTATION->ContinuationConstraint.OUTWARD_ORIENTATION;case FACE_PLANARITY->ContinuationConstraint.FACE_PLANARITY;case FACE_ANGLE->ContinuationConstraint.FACE_ANGLE_EQUALITY;case FACE_DISTANCE->ContinuationConstraint.FACE_PLANE_RADII;case FINAL_VALIDATION->ContinuationConstraint.JOINT_QUALIFICATION;default->null;};if(mapped!=null)result.add(mapped);}return Set.copyOf(result);}
}
