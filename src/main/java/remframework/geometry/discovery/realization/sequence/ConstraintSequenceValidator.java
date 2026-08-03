package remframework.geometry.discovery.realization.sequence;

import java.util.*;

/** Reports ordering concerns without mutating or reordering the experiment. */
public final class ConstraintSequenceValidator {
    public List<ConstraintSequenceIssue> validate(ConstraintSequence sequence){
        List<ConstraintSequenceIssue> issues=new ArrayList<>();List<ConstraintSequenceStage> stages=sequence.stages();
        int lastEnabled=-1;for(int i=0;i<stages.size();i++)if(stages.get(i).enabled())lastEnabled=i;
        Set<ConstraintId> earlier=new LinkedHashSet<>();
        for(int i=0;i<stages.size();i++){
            ConstraintSequenceStage stage=stages.get(i);if(!stage.enabled())continue;
            for(ConstraintId.Prerequisite p:stage.constraintId().prerequisites()){
                if(p==ConstraintId.Prerequisite.MUST_BE_LAST&&i!=lastEnabled)
                    issues.add(new ConstraintSequenceIssue(i,stage.constraintId(),ConstraintSequenceIssue.Severity.BLOCKING,"Final Validation "+p.explanation()+"; execution would otherwise be undefined."));
                else if(p!=ConstraintId.Prerequisite.MUST_BE_LAST&&!satisfied(p,earlier))
                    issues.add(new ConstraintSequenceIssue(i,stage.constraintId(),ConstraintSequenceIssue.Severity.WARNING,stage.constraintId().displayName()+" "+p.explanation()+". The order is retained as an experimental hypothesis."));
            }
            earlier.add(stage.constraintId());
        }
        return List.copyOf(issues);
    }
    public void requireExecutable(ConstraintSequence sequence){List<ConstraintSequenceIssue> blocking=validate(sequence).stream().filter(i->i.severity()==ConstraintSequenceIssue.Severity.BLOCKING).toList();if(!blocking.isEmpty())throw new IllegalArgumentException(blocking.getFirst().explanation());}
    private boolean satisfied(ConstraintId.Prerequisite p,Set<ConstraintId> prior){return switch(p){
        case VALID_FACE_PLANES->prior.contains(ConstraintId.FACE_PLANARITY);
        case VALID_FACE_NORMALS->prior.contains(ConstraintId.FACE_ORIENTATION)||prior.contains(ConstraintId.FACE_PLANARITY);
        case ADJACENT_VALID_FACE_NORMALS->prior.contains(ConstraintId.FACE_ORIENTATION)&&prior.contains(ConstraintId.FACE_PLANARITY);
        case NONDEGENERATE_FACE_EDGES->prior.contains(ConstraintId.EDGE_LENGTH);
        case CLOSED_COHERENT_BOUNDARY->prior.contains(ConstraintId.CLOSED_BOUNDARY)&&prior.contains(ConstraintId.SURFACE_ORIENTATION);
        case MUST_BE_LAST->true;};}
}
