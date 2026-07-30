package remframework.geometry.discovery;

import java.util.Map;

public final class GeometryDiscoveryWorkspaceHelp {
    public static final Map<String, String> PRIMARY_TOOLTIPS = Map.ofEntries(
            Map.entry("Load Example", "Load the selected built-in candidate without changing REM."),
            Map.entry("Open Candidate File", "Open an explicit discovery candidate TOML file."),
            Map.entry("Reload Candidate", "Reload the current candidate from its source file."),
            Map.entry("Recent Candidates", "Open the most recently selected candidate file."),
            Map.entry("Start Relaxation", "Begin coordinate relaxation on the discovery worker."),
            Map.entry("Pause", "Pause the active relaxation or topology search."),
            Map.entry("Single Step", "Perform exactly one geometry relaxation iteration."),
            Map.entry("Reset", "Restore the candidate coordinates loaded for this experiment."),
            Map.entry("Run to Convergence", "Relax until convergence or the iteration limit."),
            Map.entry("View Detailed Diagnostics", "Open detailed metrics in the bottom tabs."),
            Map.entry("Edit Score Weights", "Show configurable score-component weights."),
            Map.entry("Reset Profile", "Restore all weights from the selected scoring profile."),
            Map.entry("Start Search", "Begin incremental topology search on the discovery worker."),
            Map.entry("Stop Search", "Stop topology mutation while retaining available results."),
            Map.entry("Single Search Iteration", "Evaluate one topology-search iteration."),
            Map.entry("Preview Best", "Preview the best result without replacing current state."),
            Map.entry("Load Best", "Load the best topology candidate into geometry controls."),
            Map.entry("Compare", "Compare the current and best candidates."),
            Map.entry("Export Best", "Export the best candidate and associated search reports."),
            Map.entry("Browse Archive", "Show ranked topology-search candidates."));

    private GeometryDiscoveryWorkspaceHelp() { }

    public static String stageHelp(GeometryDiscoveryWorkflowStage stage) {
        return switch (stage) {
            case CANDIDATE -> "Purpose: choose the finite graph to inspect. Use this first or "
                    + "when starting over. Loading changes only the discovery candidate.";
            case GEOMETRY_RELAXATION -> "Purpose: optimize candidate coordinates while preserving "
                    + "topology. Use when geometry constraints are not converged.";
            case EVALUATION -> "Purpose: score and inspect the current geometry. Profiles and "
                    + "weight edits change evaluation only, not geometry or topology.";
            case TOPOLOGY_SEARCH -> "Purpose: mutate graph connections and relax each proposal. "
                    + "Use after loading a valid candidate; the active REM lattice is unchanged.";
            case RESULTS -> "Purpose: preview, compare, load, or export evaluated search results. "
                    + "Preview does not replace the current candidate.";
        };
    }
}
