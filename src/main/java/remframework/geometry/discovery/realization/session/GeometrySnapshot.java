package remframework.geometry.discovery.realization.session;

import java.util.*;
import org.joml.Vector3d;

public record GeometrySnapshot(String snapshotId,String label,List<Vector3d> coordinates,List<Vector3d> velocities,
        Set<ManualConstraint> activeForceSet,List<ManualConstraintState> measurements,int iteration,long seed,
        String centerDefinition,boolean qualified,int revision,Map<ManualConstraint,ConstraintStrengthFactor> strengthFactors) {
    public GeometrySnapshot {coordinates=copy(coordinates);velocities=copy(velocities);activeForceSet=Set.copyOf(activeForceSet);measurements=List.copyOf(measurements);strengthFactors=strengthFactors==null?Map.of():Map.copyOf(strengthFactors);}
    public GeometrySnapshot(String snapshotId,String label,List<Vector3d> coordinates,List<Vector3d> velocities,
            Set<ManualConstraint> activeForceSet,List<ManualConstraintState> measurements,int iteration,long seed,
            String centerDefinition,boolean qualified,int revision) {
        this(snapshotId,label,coordinates,velocities,activeForceSet,measurements,iteration,seed,centerDefinition,qualified,revision,Map.of());
    }
    private static List<Vector3d> copy(List<Vector3d> values){return values.stream().map(Vector3d::new).toList();}
}
