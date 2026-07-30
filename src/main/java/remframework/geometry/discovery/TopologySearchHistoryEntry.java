package remframework.geometry.discovery;

public record TopologySearchHistoryEntry(
        int iteration, double currentScore, double bestScore, boolean accepted,
        String mutationType, double temperature, long elapsedMilliseconds) {
}
