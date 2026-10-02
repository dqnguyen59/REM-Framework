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

import java.util.LinkedHashMap;
import java.util.Map;

public final class GeometryDiscoveryWorkflowResolver {
    public static final String START_RELAXATION = "startRelaxation";
    public static final String PAUSE = "pause";
    public static final String SINGLE_STEP = "singleStep";
    public static final String RUN_TO_CONVERGENCE = "runToConvergence";
    public static final String START_SEARCH = "startSearch";
    public static final String STOP_SEARCH = "stopSearch";
    public static final String SINGLE_SEARCH_STEP = "singleSearchStep";
    public static final String LOAD_BEST = "loadBest";
    public static final String PREVIEW_BEST = "previewBest";
    public static final String COMPARE = "compare";
    public static final String EXPORT = "export";

    public GeometryDiscoveryWorkflowResolution resolve(
            GeometryDiscoveryWorkspaceSnapshot snapshot) {
        if (snapshot == null || snapshot.geometry() == null)
            return resolution(GeometryDiscoveryWorkflowStage.CANDIDATE,
                    GeometryDiscoveryWorkflowState.NO_CANDIDATE,
                    "Load a candidate",
                    "Choose a built-in example or open an explicit candidate file.", snapshot);
        if (!snapshot.error().isBlank() || !snapshot.geometry().validation().isValid())
            return resolution(GeometryDiscoveryWorkflowStage.CANDIDATE,
                    GeometryDiscoveryWorkflowState.ERROR,
                    "Resolve the candidate error",
                    snapshot.error().isBlank()
                            ? "The candidate failed validation and cannot be processed."
                            : snapshot.error(), snapshot);
        if (snapshot.relaxationRunning())
            return resolution(GeometryDiscoveryWorkflowStage.GEOMETRY_RELAXATION,
                    GeometryDiscoveryWorkflowState.RELAXING,
                    "Allow relaxation to continue or pause it",
                    "The worker is updating coordinates toward the configured geometry constraints.",
                    snapshot);
        if (snapshot.searchRunning())
            return resolution(GeometryDiscoveryWorkflowStage.TOPOLOGY_SEARCH,
                    GeometryDiscoveryWorkflowState.SEARCHING,
                    "Monitor search progress",
                    "The worker is evaluating topology mutations; pause if settings need review.",
                    snapshot);
        if (hasSearchResults(snapshot)) {
            boolean complete = snapshot.search().metrics().completedIterations()
                    >= snapshot.search().settings().maximumSearchIterations();
            return resolution(complete ? GeometryDiscoveryWorkflowStage.RESULTS
                            : GeometryDiscoveryWorkflowStage.TOPOLOGY_SEARCH,
                    complete ? GeometryDiscoveryWorkflowState.BEST_CANDIDATE_AVAILABLE
                            : GeometryDiscoveryWorkflowState.SEARCH_PAUSED,
                    complete ? "Review or export the best candidate" : "Resume the topology search",
                    complete
                            ? "The search archive contains evaluated alternatives."
                            : "Search progress is preserved and can continue from the current candidate.",
                    snapshot);
        }
        if (geometrySatisfied(snapshot.geometry()))
            return resolution(GeometryDiscoveryWorkflowStage.EVALUATION,
                    GeometryDiscoveryWorkflowState.READY_FOR_SEARCH,
                    "Choose an evaluation profile or start topology search",
                    "Geometry is already converged, so relaxation is not required.", snapshot);
        if (snapshot.geometry().metrics().iterationCount() > 0)
            return resolution(GeometryDiscoveryWorkflowStage.GEOMETRY_RELAXATION,
                    GeometryDiscoveryWorkflowState.CANDIDATE_LOADED,
                    "Continue relaxation or reset the candidate",
                    "The geometry has changed but has not reached convergence.", snapshot);
        return resolution(GeometryDiscoveryWorkflowStage.GEOMETRY_RELAXATION,
                GeometryDiscoveryWorkflowState.RELAXATION_REQUIRED,
                "Run geometry relaxation",
                "Connected edge lengths or separation constraints are not yet satisfied.", snapshot);
    }

    public boolean geometrySatisfied(GeometryDiscoveryResult result) {
        GeometryDiscoveryMetrics metrics = result.metrics();
        double target = Math.max(1e-12, result.settings().targetDistance());
        double normalizedError = metrics.totalEdgeLengthError()
                / Math.max(1, result.model().connections().size()) / (target * target);
        return metrics.converged() || normalizedError <= result.settings().convergenceTolerance()
                && metrics.collisionViolationCount() == 0;
    }

    public boolean hasSearchResults(GeometryDiscoveryWorkspaceSnapshot snapshot) {
        return snapshot != null && snapshot.search() != null
                && !snapshot.search().history().isEmpty()
                && snapshot.search().bestCandidate() != null;
    }

    private GeometryDiscoveryWorkflowResolution resolution(
            GeometryDiscoveryWorkflowStage stage, GeometryDiscoveryWorkflowState state,
            String action, String explanation, GeometryDiscoveryWorkspaceSnapshot snapshot) {
        boolean candidate = snapshot != null && snapshot.geometry() != null
                && snapshot.geometry().validation().isValid() && snapshot.error().isBlank();
        boolean relaxation = snapshot != null && snapshot.relaxationRunning();
        boolean searching = snapshot != null && snapshot.searchRunning();
        boolean results = hasSearchResults(snapshot);
        boolean converged = candidate && geometrySatisfied(snapshot.geometry());
        Map<String, GeometryDiscoveryActionAvailability> actions = new LinkedHashMap<>();
        actions.put(START_RELAXATION, available(candidate && !relaxation && !searching && !converged,
                candidate ? searching ? "Pause topology search before relaxing geometry."
                        : converged ? "Geometry is already converged."
                        : "Relaxation is already running." : "Load a valid candidate first."));
        actions.put(PAUSE, available(relaxation || searching, "Nothing is currently running."));
        actions.put(SINGLE_STEP, available(candidate && !relaxation && !searching && !converged,
                converged ? "Geometry is already converged."
                        : "Pause active work before taking a single step."));
        actions.put(RUN_TO_CONVERGENCE,
                available(candidate && !searching && !relaxation && !converged,
                        searching ? "Pause topology search before relaxing geometry."
                                : converged ? "Geometry is already converged."
                                : "Relaxation is already running."));
        actions.put(START_SEARCH, available(candidate && !searching && !relaxation,
                candidate ? relaxation ? "Pause relaxation before starting topology search."
                        : "Topology search is already running." : "Load a valid candidate first."));
        actions.put(STOP_SEARCH, available(searching, "No topology search is running."));
        actions.put(SINGLE_SEARCH_STEP, available(candidate && !searching && !relaxation,
                "Pause active work before taking a search iteration."));
        actions.put(LOAD_BEST, available(results, "Run at least one search iteration first."));
        actions.put(PREVIEW_BEST, available(results, "No best candidate is available yet."));
        actions.put(COMPARE, available(results, "Search results are required for comparison."));
        actions.put(EXPORT, available(candidate, "Load a valid candidate before exporting."));
        return new GeometryDiscoveryWorkflowResolution(stage, state,
                new GeometryDiscoveryRecommendation(action, explanation), Map.copyOf(actions));
    }

    private GeometryDiscoveryActionAvailability available(boolean enabled, String reason) {
        return enabled ? GeometryDiscoveryActionAvailability.available()
                : GeometryDiscoveryActionAvailability.disabled(reason);
    }
}
