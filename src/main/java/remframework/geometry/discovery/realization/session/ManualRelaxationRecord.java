package remframework.geometry.discovery.realization.session;

import java.util.List;

public record ManualRelaxationRecord(RealizationStrategy strategy,ManualVelocityPolicy velocityPolicy,
        List<ManualConstraint> activeForceSet,List<ManualAction> actionHistory,List<GeometrySnapshot> snapshots,
        boolean finalQualification,String configurationStatus,String explanation) {
    public ManualRelaxationRecord {activeForceSet=List.copyOf(activeForceSet);actionHistory=List.copyOf(actionHistory);snapshots=List.copyOf(snapshots);}
}
