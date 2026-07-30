package remframework.geometry.discovery;

import java.util.List;

public record GeometryDiscoveryWorkspaceSnapshot(
        GeometryDiscoveryResult geometry,
        TopologySearchResult search,
        List<TopologyCandidate> archive,
        TopologyCandidate preview,
        GeometryComparisonReport comparison,
        String status,
        String error,
        boolean relaxationRunning,
        boolean searchRunning,
        long version) {
}
