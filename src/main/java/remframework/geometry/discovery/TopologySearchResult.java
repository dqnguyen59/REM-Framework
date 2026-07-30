package remframework.geometry.discovery;

import java.util.List;

public record TopologySearchResult(
        TopologySearchSettings settings, TopologyCandidate initialCandidate,
        TopologyCandidate currentCandidate, TopologyCandidate bestCandidate,
        List<TopologyCandidate> topCandidates, List<TopologySearchHistoryEntry> history,
        TopologySearchMetrics metrics) {
}
