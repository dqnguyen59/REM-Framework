package remframework.geometry.discovery.ui;

import remframework.geometry.discovery.GeometryConfigurationStatusCode;
import remframework.geometry.discovery.GeometryConfigurationStatus;
import remframework.geometry.discovery.realization.session.*;

/** Human-readable, stage-separated status mapping for the discovery workspace. */
public final class StatusPresentation {
    private StatusPresentation() { }

    public static String realizationState(IncrementalSession session) {
        if (session == null) return "NOT_TESTED";
        return switch (session.scientificState()) {
            case INITIALIZED -> "INITIALIZED";
            case RELAXING -> "RELAXING";
            case CONVERGED -> "REALIZED";
            case QUALIFICATION_FAILED -> "EDGE_REALIZATION_FOUND; EXPERIMENT_QUALIFICATION_FAILED";
            case SEARCH_LIMIT_REACHED -> "NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS";
            case FAILED -> "FAILED";
        };
    }

    public static String equalEdgeConclusion(IncrementalSession session, TestResult result) {
        if (session == null && result == null) return "Not tested";
        if (result != null && result.realization() != null && result.realization().converged())
            return "Found";
        if (session != null && (session.scientificState() == ScientificState.CONVERGED
                || session.scientificState() == ScientificState.QUALIFICATION_FAILED))
            return "Found";
        if ((session != null && session.scientificState() == ScientificState.SEARCH_LIMIT_REACHED)
                || (result != null && result.realization() != null
                && result.realization().status() == RealizationStatus.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS))
            return "Not found within limits";
        return "Not tested";
    }

    /** Backward-compatible wording API; callers should prefer equalEdgeConclusion. */
    public static String solvabilityConclusion(IncrementalSession session, TestResult result) {
        return equalEdgeConclusion(session, result);
    }

    public static String configurationSummary(GeometryConfigurationStatus status) {
        return status.code() + " - " + status.summary();
    }
}
