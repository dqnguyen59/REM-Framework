package remframework.geometry.discovery;

import java.nio.file.Path;

public final class GeometryDiscoveryWorkspaceState {
    private volatile boolean open;
    private volatile boolean active;
    private volatile boolean remWasRunning;
    private volatile Path currentSource;
    private volatile Path outputDirectory = Path.of("lattice/candidates");
    private volatile TopologyScoringProfile scoringProfile =
            TopologyScoringProfile.GENERAL_GEOMETRY;
    private volatile TopologyScoreWeights scoreWeights = TopologyScoreWeights.defaults();
    private volatile boolean weightsOverridden;
    private volatile boolean showOnlyNonZeroScoreComponents;
    private volatile boolean guidedMode = true;
    private volatile boolean relaxationAdvancedExpanded;
    private volatile boolean searchAdvancedExpanded;
    private volatile boolean detailedDiagnosticsExpanded;
    private volatile boolean scoreWeightsExpanded;
    private volatile boolean archiveExpanded;
    private volatile boolean introductionDismissed;
    private volatile GeometryDiscoveryWorkflowStage selectedStage =
            GeometryDiscoveryWorkflowStage.CANDIDATE;
    private volatile GeometryDiscoveryDetailTab selectedDetailTab =
            GeometryDiscoveryDetailTab.OVERVIEW;

    public boolean open() { return open; }
    void setOpen(boolean value) { open = value; }
    public boolean active() { return active; }
    void setActive(boolean value) { active = value; }
    public boolean remWasRunning() { return remWasRunning; }
    void setRemWasRunning(boolean value) { remWasRunning = value; }
    public Path currentSource() { return currentSource; }
    void setCurrentSource(Path value) { currentSource = value; }
    public Path outputDirectory() { return outputDirectory; }
    public void setOutputDirectory(Path value) { outputDirectory = value; }
    public TopologyScoringProfile scoringProfile() { return scoringProfile; }
    void setScoringProfile(TopologyScoringProfile value) { scoringProfile = value; }
    public TopologyScoreWeights scoreWeights() { return scoreWeights; }
    void setScoreWeights(TopologyScoreWeights value) { scoreWeights = value; }
    public boolean weightsOverridden() { return weightsOverridden; }
    void setWeightsOverridden(boolean value) { weightsOverridden = value; }
    public boolean showOnlyNonZeroScoreComponents() { return showOnlyNonZeroScoreComponents; }
    public void setShowOnlyNonZeroScoreComponents(boolean value) {
        showOnlyNonZeroScoreComponents = value;
    }
    public boolean guidedMode() { return guidedMode; }
    public void setGuidedMode(boolean value) { guidedMode = value; }
    public boolean relaxationAdvancedExpanded() { return relaxationAdvancedExpanded; }
    public void setRelaxationAdvancedExpanded(boolean value) {
        relaxationAdvancedExpanded = value;
    }
    public boolean searchAdvancedExpanded() { return searchAdvancedExpanded; }
    public void setSearchAdvancedExpanded(boolean value) { searchAdvancedExpanded = value; }
    public boolean detailedDiagnosticsExpanded() { return detailedDiagnosticsExpanded; }
    public void setDetailedDiagnosticsExpanded(boolean value) {
        detailedDiagnosticsExpanded = value;
    }
    public boolean scoreWeightsExpanded() { return scoreWeightsExpanded; }
    public void setScoreWeightsExpanded(boolean value) { scoreWeightsExpanded = value; }
    public boolean archiveExpanded() { return archiveExpanded; }
    public void setArchiveExpanded(boolean value) { archiveExpanded = value; }
    public boolean introductionDismissed() { return introductionDismissed; }
    public void setIntroductionDismissed(boolean value) { introductionDismissed = value; }
    public GeometryDiscoveryWorkflowStage selectedStage() { return selectedStage; }
    public void setSelectedStage(GeometryDiscoveryWorkflowStage value) { selectedStage = value; }
    public GeometryDiscoveryDetailTab selectedDetailTab() { return selectedDetailTab; }
    public void setSelectedDetailTab(GeometryDiscoveryDetailTab value) {
        selectedDetailTab = value;
    }
}
