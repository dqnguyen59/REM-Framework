package remframework.geometry.discovery.realization.session;

import java.util.*;
import org.joml.Vector3d;

public record GeometrySnapshot(String snapshotId,String label,List<Vector3d> coordinates,List<Vector3d> velocities,
        Set<ManualConstraint> activeForceSet,List<ManualConstraintState> measurements,int iteration,long seed,
        String centerDefinition,boolean qualified,int revision) {
    public GeometrySnapshot {coordinates=copy(coordinates);velocities=copy(velocities);activeForceSet=Set.copyOf(activeForceSet);measurements=List.copyOf(measurements);}
    private static List<Vector3d> copy(List<Vector3d> values){return values.stream().map(Vector3d::new).toList();}
}
