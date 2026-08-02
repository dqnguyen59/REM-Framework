package remframework.geometry.discovery.realization.session;

import java.util.*;
import remframework.geometry.discovery.experiment.*;

public record ContinuationSchedule(String id,int version,List<ContinuationStageDefinition> stages) {
    public ContinuationSchedule { stages=List.copyOf(stages); }
    public static ContinuationSchedule closedUniformFaceSequential(ThreeDExperimentConditions c,
            int stable,int maximum,int ramp){
        List<ContinuationStageDefinition> stages=new ArrayList<>();Set<ContinuationConstraint> retained=new LinkedHashSet<>();
        add(stages,retained,"EDGE_EQUALITY","Connected edge equality",ContinuationConstraint.EDGE_EQUALITY,stable,maximum,ramp);
        add(stages,retained,"OUTWARD_ORIENTATION","Outward face orientation",ContinuationConstraint.OUTWARD_ORIENTATION,stable,maximum,ramp);
        if(c.facePlanarity()==FacePlanarityMode.REQUIRE_PLANAR_FACES)add(stages,retained,"FACE_PLANARITY","Face planarity",ContinuationConstraint.FACE_PLANARITY,stable,maximum,ramp);
        if(c.faceAngles()==FaceAngleConditionMode.REQUIRE_EQUAL_ANGLES_WITHIN_EACH_FACE)add(stages,retained,"FACE_ANGLE_EQUALITY","Face-angle equality",ContinuationConstraint.FACE_ANGLE_EQUALITY,stable,maximum,ramp);
        if(c.facePlaneRadii()==FacePlaneRadiusMode.REQUIRE_EQUAL_FACE_PLANE_RADII)add(stages,retained,"FACE_PLANE_RADII","Equal face-plane radii",ContinuationConstraint.FACE_PLANE_RADII,stable,maximum,ramp);
        add(stages,retained,"JOINT_QUALIFICATION","Joint qualification",ContinuationConstraint.JOINT_QUALIFICATION,stable,maximum,ramp);
        return new ContinuationSchedule("CLOSED_UNIFORM_FACE_SEQUENTIAL",1,stages);
    }
    private static void add(List<ContinuationStageDefinition> stages,Set<ContinuationConstraint> retained,String id,String name,ContinuationConstraint introduced,int stable,int maximum,int ramp){stages.add(new ContinuationStageDefinition(id,name,Set.of(introduced),Set.copyOf(retained),stable,maximum,ContinuationRampMode.LINEAR_BY_ITERATION,ramp,0,1,false,5));retained.add(introduced);}
}
