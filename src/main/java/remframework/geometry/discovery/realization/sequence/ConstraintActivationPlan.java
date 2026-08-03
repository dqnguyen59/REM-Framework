package remframework.geometry.discovery.realization.sequence;

import java.util.*;

/** Exact active/retained sets for a stage, independent of topology. */
public final class ConstraintActivationPlan {
    private ConstraintActivationPlan() { }
    public static Snapshot at(ConstraintSequence sequence,int stageIndex){
        if(stageIndex<0||stageIndex>=sequence.stages().size())throw new IndexOutOfBoundsException(stageIndex);
        ConstraintSequenceStage current=sequence.stages().get(stageIndex);LinkedHashSet<ConstraintId> retained=new LinkedHashSet<>();
        if(current.activationBehavior()==ConstraintActivationBehavior.CUMULATIVE)for(int i=0;i<stageIndex;i++){ConstraintSequenceStage s=sequence.stages().get(i);if(s.enabled()&&s.mode()==ConstraintStageMode.REQUIRED)retained.add(s.constraintId());}
        LinkedHashSet<ConstraintId> active=new LinkedHashSet<>(retained);if(current.enabled()&&current.mode()==ConstraintStageMode.REQUIRED)active.add(current.constraintId());
        return new Snapshot(List.copyOf(active),List.copyOf(retained),current.activationBehavior()==ConstraintActivationBehavior.STAGE_ONLY);
    }
    public record Snapshot(List<ConstraintId> active,List<ConstraintId> retained,boolean experimentalStageOnly){public Snapshot{active=List.copyOf(active);retained=List.copyOf(retained);}}
}
