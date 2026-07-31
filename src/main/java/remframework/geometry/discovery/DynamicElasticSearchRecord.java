package remframework.geometry.discovery;

public record DynamicElasticSearchRecord(
        int nodeCount, int topologyAttempt, long seed,
        DynamicElasticResult result, String skippedReason) {
}
