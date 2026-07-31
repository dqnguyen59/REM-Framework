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
    private volatile DiscoveryExperimentConfiguration experimentConfiguration;
    private volatile DiscoveryExperimentLifecycle experimentLifecycle =
            DiscoveryExperimentLifecycle.CREATED;
    private volatile DiscoveryViewportSelection viewportSelection =
            DiscoveryViewportSelection.CURRENT_CANDIDATE;
    private volatile boolean followBestCandidate;
    private volatile int snapshotUpdatesPerSecond = 8;
    private volatile DiscoveryVisualizationUpdateTrigger visualizationUpdateTrigger =
            DiscoveryVisualizationUpdateTrigger.ACCEPTED_MUTATION;
    private volatile boolean maximumVisualizationSpeed;
    private volatile boolean pauseOnNewBest;
    private volatile int dynamicElasticUpdatesPerSecond =
            DynamicElasticPlaybackPacing.DEFAULT_SPEED;
    private volatile boolean dynamicElasticMaximumSpeed;
    private volatile int dynamicElasticPublicationInterval =
            DynamicElasticPlaybackPacing.DEFAULT_PUBLICATION_INTERVAL;
    private volatile int dynamicElasticMaximumIterations = 100_000;

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
    public DiscoveryExperimentConfiguration experimentConfiguration() {
        return experimentConfiguration;
    }
    void setExperimentConfiguration(DiscoveryExperimentConfiguration value) {
        experimentConfiguration = value;
    }
    public DiscoveryExperimentLifecycle experimentLifecycle() { return experimentLifecycle; }
    void setExperimentLifecycle(DiscoveryExperimentLifecycle value) {
        experimentLifecycle = value;
    }
    public DiscoveryViewportSelection viewportSelection() { return viewportSelection; }
    public void setViewportSelection(DiscoveryViewportSelection value) {
        viewportSelection = value;
    }
    public boolean followBestCandidate() { return followBestCandidate; }
    public void setFollowBestCandidate(boolean value) { followBestCandidate = value; }
    public int snapshotUpdatesPerSecond() { return snapshotUpdatesPerSecond; }
    public void setSnapshotUpdatesPerSecond(int value) {
        if (!DiscoveryVisualizationPacing.validSpeed(value) && value != 8)
            throw new IllegalArgumentException("Visualization speed must be one of the supported values.");
        snapshotUpdatesPerSecond = value;
    }
    public DiscoveryVisualizationUpdateTrigger visualizationUpdateTrigger() {
        return visualizationUpdateTrigger;
    }
    public void setVisualizationUpdateTrigger(DiscoveryVisualizationUpdateTrigger value) {
        visualizationUpdateTrigger = java.util.Objects.requireNonNull(value);
    }
    public boolean maximumVisualizationSpeed() { return maximumVisualizationSpeed; }
    public void setMaximumVisualizationSpeed(boolean value) {
        maximumVisualizationSpeed = value;
    }
    public boolean pauseOnNewBest() { return pauseOnNewBest; }
    public void setPauseOnNewBest(boolean value) { pauseOnNewBest = value; }
    public int dynamicElasticUpdatesPerSecond() {
        return dynamicElasticUpdatesPerSecond;
    }
    public void setDynamicElasticUpdatesPerSecond(int value) {
        if (!DynamicElasticPlaybackPacing.validSpeed(value))
            throw new IllegalArgumentException(
                    "Unsupported Dynamic Elastic visualization speed.");
        dynamicElasticUpdatesPerSecond = value;
    }
    public boolean dynamicElasticMaximumSpeed() { return dynamicElasticMaximumSpeed; }
    public void setDynamicElasticMaximumSpeed(boolean value) {
        dynamicElasticMaximumSpeed = value;
    }
    public int dynamicElasticPublicationInterval() {
        return dynamicElasticPublicationInterval;
    }
    public void setDynamicElasticPublicationInterval(int value) {
        if (!DynamicElasticPlaybackPacing.validPublicationInterval(value))
            throw new IllegalArgumentException(
                    "Unsupported Dynamic Elastic publication interval.");
        dynamicElasticPublicationInterval = value;
    }
    public int dynamicElasticMaximumIterations() {
        return dynamicElasticMaximumIterations;
    }
    public void setDynamicElasticMaximumIterations(int value) {
        if (value < 1) throw new IllegalArgumentException(
                "Maximum iterations must be at least 1.");
        dynamicElasticMaximumIterations = value;
    }
}
