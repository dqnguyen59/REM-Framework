package remframework.geometry.discovery;

public record TopologySearchMetrics(
        int completedIterations, int acceptedMutationCount, int rejectedMutationCount,
        int invalidMutationCount, int duplicateTopologyCount, int relaxationFailureCount,
        long elapsedMilliseconds) {
}
