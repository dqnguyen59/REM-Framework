package remframework.geometry.discovery;

import java.util.Map;

public record TopologySearchHistoryEntry(
        int iteration, double currentScore, double bestScore, boolean accepted,
        String mutationType, double temperature, long elapsedMilliseconds,
        Map<String, TopologyScoreComponent> currentScoreComponents) {
}
