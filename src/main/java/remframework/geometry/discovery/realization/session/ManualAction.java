package remframework.geometry.discovery.realization.session;

import java.time.Instant;
import java.util.*;

public record ManualAction(long sequence,Instant wallClock,int scientificIteration,ManualConstraint constraint,
        boolean oldRelaxationState,boolean newRelaxationState,Set<ManualConstraint> activeForceSet,
        ManualVelocityPolicy velocityPolicy,long seed,int coordinateSnapshotRevision,
        Map<ManualConstraint,Double> rawErrors,Map<ManualConstraint,Double> ratios) {
    public ManualAction {activeForceSet=Set.copyOf(activeForceSet);rawErrors=Map.copyOf(rawErrors);ratios=Map.copyOf(ratios);}
}
