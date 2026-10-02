package remframework.geometry.discovery.realization.session;

import java.util.*;

public record ManualRecording(String recordingId,String name,String configurationId,long seed,
        List<ManualAction> actions,List<ManualExperimentEvent> events,List<GeometrySnapshot> snapshots,
        boolean qualified,String configurationStatus,String explanation) {
    public ManualRecording {actions=List.copyOf(actions);events=List.copyOf(events);snapshots=List.copyOf(snapshots);}
}
