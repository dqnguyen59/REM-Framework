package remframework.geometry.discovery.realization.sequence;

import java.util.List;
import java.util.Objects;

public record ConstraintSequence(String sequenceId,int version,String name,ConstraintSequenceMode mode,
        List<ConstraintSequenceStage> stages,String notes) {
    public ConstraintSequence {
        if(sequenceId==null||sequenceId.isBlank()||version<1)throw new IllegalArgumentException("Sequence ID and positive version are required.");
        name=name==null||name.isBlank()?sequenceId:name;mode=Objects.requireNonNullElse(mode,ConstraintSequenceMode.CUSTOM_SEQUENCE);
        stages=List.copyOf(stages==null?List.of():stages);notes=notes==null?"":notes;
        if(stages.stream().map(ConstraintSequenceStage::stageId).distinct().count()!=stages.size())
            throw new IllegalArgumentException("Stage IDs must be unique within a sequence.");
        if(mode==ConstraintSequenceMode.CUSTOM_SEQUENCE&&stages.isEmpty())throw new IllegalArgumentException("A custom sequence requires an explicit ordered stage list.");
    }
    public ConstraintSequence withStages(List<ConstraintSequenceStage> value){return new ConstraintSequence(sequenceId,version,name,mode,value,notes);}
}
