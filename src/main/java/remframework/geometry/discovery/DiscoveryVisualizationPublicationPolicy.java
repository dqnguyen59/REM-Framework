package remframework.geometry.discovery;

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
