package remframework.geometry.discovery.realization.sequence;

import java.util.*;

/** Editable starting hypotheses; none is treated as a scientific default. */
public final class ConstraintSequencePresets {
    private ConstraintSequencePresets() { }
    public static ConstraintSequence edgeFirst(){return preset("EDGE_FIRST",ConstraintId.EDGE_LENGTH,ConstraintId.FACE_ORIENTATION,ConstraintId.FACE_PLANARITY,ConstraintId.FACE_ANGLE,ConstraintId.FACE_DISTANCE,ConstraintId.FINAL_VALIDATION);}
    public static ConstraintSequence orientationFirst(){return preset("ORIENTATION_FIRST",ConstraintId.FACE_ORIENTATION,ConstraintId.FACE_DISTANCE,ConstraintId.FACE_PLANARITY,ConstraintId.FACE_ANGLE,ConstraintId.EDGE_LENGTH,ConstraintId.FINAL_VALIDATION);}
    public static ConstraintSequence convexityFirst(){return preset("CONVEXITY_FIRST",ConstraintId.FACE_ORIENTATION,ConstraintId.CONVEXITY,ConstraintId.FACE_PLANARITY,ConstraintId.FACE_ANGLE,ConstraintId.FACE_DISTANCE,ConstraintId.EDGE_LENGTH,ConstraintId.FINAL_VALIDATION);}
    public static ConstraintSequence faceFirst(){return preset("FACE_FIRST",ConstraintId.FACE_ORIENTATION,ConstraintId.FACE_PLANARITY,ConstraintId.FACE_ANGLE,ConstraintId.FACE_DISTANCE,ConstraintId.EDGE_LENGTH,ConstraintId.FINAL_VALIDATION);}
    public static List<ConstraintSequence> hypotheses(){return List.of(edgeFirst(),orientationFirst(),convexityFirst(),faceFirst());}
    private static ConstraintSequence preset(String name,ConstraintId... ids){List<ConstraintSequenceStage> stages=new ArrayList<>();for(int i=0;i<ids.length;i++){ConstraintSequenceStage s=ConstraintSequenceStage.required(ids[i]);stages.add(new ConstraintSequenceStage(name.toLowerCase(Locale.ROOT)+"-"+(i+1),s.constraintId(),s.displayName(),s.enabled(),s.mode(),s.activationBehavior(),s.finalRestoringStrength(),s.rampMode(),s.rampIterations(),s.transitionTolerance(),s.stableIterations(),s.maximumIterations(),s.stagnationPolicy(),s.severeRegressionThreshold(),s.rollbackPolicy(),"Starting hypothesis; user editable."));}return new ConstraintSequence(name,1,name,ConstraintSequenceMode.CUSTOM_SEQUENCE,stages,"Starting hypothesis only; not an accepted scientific default.");}
}
