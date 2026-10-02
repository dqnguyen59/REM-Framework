package remframework.geometry.discovery.realization.session;

import java.time.Instant;
import java.util.*;

public record ManualAction(long sequence,Instant wallClock,int scientificIteration,ManualConstraint constraint,
        boolean oldRelaxationState,boolean newRelaxationState,Set<ManualConstraint> activeForceSet,
        ManualVelocityPolicy velocityPolicy,long seed,int coordinateSnapshotRevision,
        Map<ManualConstraint,Double> rawErrors,Map<ManualConstraint,Double> ratios,
        double previousFactor,double newFactor,Double baseStrength,Double previousEffectiveStrength,
        Double newEffectiveStrength,boolean strengthFactorChange) {
    public ManualAction {activeForceSet=Set.copyOf(activeForceSet);rawErrors=Map.copyOf(rawErrors);ratios=Map.copyOf(ratios);}
    public ManualAction(long sequence,Instant wallClock,int scientificIteration,ManualConstraint constraint,
            boolean oldRelaxationState,boolean newRelaxationState,Set<ManualConstraint> activeForceSet,
            ManualVelocityPolicy velocityPolicy,long seed,int coordinateSnapshotRevision,
            Map<ManualConstraint,Double> rawErrors,Map<ManualConstraint,Double> ratios) {
        this(sequence,wallClock,scientificIteration,constraint,oldRelaxationState,newRelaxationState,activeForceSet,
                velocityPolicy,seed,coordinateSnapshotRevision,rawErrors,ratios,1,1,null,null,null,false);
    }
    public boolean factorChange(){return strengthFactorChange;}
}
