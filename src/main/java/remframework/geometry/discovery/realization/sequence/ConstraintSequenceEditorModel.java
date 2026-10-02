package remframework.geometry.discovery.realization.sequence;

import java.util.*;

/** UI-independent sequence editing operations used by the workflow panel. */
public final class ConstraintSequenceEditorModel {
    private ConstraintSequence sequence;
    public ConstraintSequenceEditorModel(ConstraintSequence sequence){this.sequence=Objects.requireNonNull(sequence);}
    public ConstraintSequence sequence(){return sequence;}
    public void add(ConstraintSequenceStage stage){var copy=new ArrayList<>(sequence.stages());copy.add(stage);replace(copy);}
    public void remove(int index){var copy=new ArrayList<>(sequence.stages());copy.remove(index);replace(copy);}
    public void moveUp(int index){move(index,index-1);}
    public void moveDown(int index){move(index,index+1);}
    public void duplicate(int index){var copy=new ArrayList<>(sequence.stages());copy.add(index+1,copy.get(index).duplicate());replace(copy);}
    public void setEnabled(int index,boolean enabled){var copy=new ArrayList<>(sequence.stages());copy.set(index,copy.get(index).withEnabled(enabled));replace(copy);}
    public void update(int index,ConstraintSequenceStage stage){var copy=new ArrayList<>(sequence.stages());copy.set(index,stage);replace(copy);}
    public void resetToBuiltIn(ConstraintSequence builtIn){sequence=Objects.requireNonNull(builtIn);}
    private void move(int from,int to){if(to<0||to>=sequence.stages().size())return;var copy=new ArrayList<>(sequence.stages());Collections.swap(copy,from,to);replace(copy);}
    private void replace(List<ConstraintSequenceStage> stages){sequence=sequence.withStages(stages);}
}
