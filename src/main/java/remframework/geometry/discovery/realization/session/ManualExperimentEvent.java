package remframework.geometry.discovery.realization.session;

import java.time.Instant;

public record ManualExperimentEvent(long index,Instant wallClock,int scientificIteration,
        Type type,String detail,int coordinateSnapshotRevision) {
    public enum Type { RECORDING_STARTED, RECORDING_STOPPED, FORCE_CHANGED, SNAPSHOT_SAVED, SNAPSHOT_RESTORED, SNAPSHOT_DELETED, QUALIFICATION_EVALUATED }
}
