package remframework.geometry.discovery;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;

public final class DiscoveryVisualizationPublicationPolicy {
    public boolean shouldPublish(DiscoveryVisualizationUpdateTrigger trigger,
                                 boolean acceptedMutation, boolean newBest,
                                 boolean timedSnapshotDue) {
        return switch (trigger) {
            case EVERY_ITERATION -> true;
            case ACCEPTED_MUTATION -> acceptedMutation;
            case NEW_BEST_CANDIDATE -> newBest;
            case TIMED_SNAPSHOT -> timedSnapshotDue;
        };
    }

    public boolean shouldPauseOnNewBest(boolean enabled, boolean newBest) {
        return enabled && newBest;
    }
}
