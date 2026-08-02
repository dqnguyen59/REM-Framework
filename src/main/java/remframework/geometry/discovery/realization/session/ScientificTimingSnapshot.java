package remframework.geometry.discovery.realization.session;

/** Immutable pacing measurements for the latest completed scientific update. */
public record ScientificTimingSnapshot(long initializationNanos,long completedIterations,
        double averageIterationMillis,double achievedUpdatesPerSecond,long snapshotPublicationNanos) {
    public static ScientificTimingSnapshot initial(long initializationNanos){return new ScientificTimingSnapshot(initializationNanos,0,0,0,0);}
}
