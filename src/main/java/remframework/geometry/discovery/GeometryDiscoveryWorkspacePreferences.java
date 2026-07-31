package remframework.geometry.discovery;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class GeometryDiscoveryWorkspacePreferences {
    private final Path path;

    public GeometryDiscoveryWorkspacePreferences(Path path) { this.path = path; }

    public void load(GeometryDiscoveryWorkspaceState state, GeometryDiscoveryViewState view) {
        if (Files.notExists(path)) return;
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
            state.setScoringProfile(TopologyScoringProfile.valueOf(values.getProperty(
                    "scoringProfile", TopologyScoringProfile.GENERAL_GEOMETRY.name())));
            state.setScoreWeights(TopologyScoreWeights.forProfile(state.scoringProfile()));
            state.setOutputDirectory(Path.of(values.getProperty(
                    "outputDirectory", "lattice/candidates")));
            String source = values.getProperty("lastCandidate", "");
            if (!source.isBlank()) state.setCurrentSource(Path.of(source));
            view.setOrthographic(Boolean.parseBoolean(values.getProperty("orthographic", "false")));
            view.setShowConnections(Boolean.parseBoolean(
                    values.getProperty("showConnections", "true")));
            view.setShowAxes(Boolean.parseBoolean(values.getProperty("showAxes", "true")));
            view.setShowBoundingBox(Boolean.parseBoolean(
                    values.getProperty("showBoundingBox", "true")));
            state.setGuidedMode(Boolean.parseBoolean(values.getProperty("guidedMode", "true")));
            state.setIntroductionDismissed(Boolean.parseBoolean(
                    values.getProperty("introductionDismissed", "false")));
            state.setSelectedDetailTab(GeometryDiscoveryDetailTab.valueOf(values.getProperty(
                    "selectedDetailTab", GeometryDiscoveryDetailTab.OVERVIEW.name())));
            state.setVisualizationUpdateTrigger(DiscoveryVisualizationUpdateTrigger.valueOf(
                    values.getProperty("visualizationUpdateTrigger",
                            DiscoveryVisualizationUpdateTrigger.ACCEPTED_MUTATION.name())));
            state.setSnapshotUpdatesPerSecond(Integer.parseInt(
                    values.getProperty("visualizationSpeed", "8")));
            state.setMaximumVisualizationSpeed(Boolean.parseBoolean(
                    values.getProperty("maximumVisualizationSpeed", "false")));
            state.setPauseOnNewBest(Boolean.parseBoolean(
                    values.getProperty("pauseOnNewBest", "false")));
            state.setDynamicElasticUpdatesPerSecond(Integer.parseInt(values.getProperty(
                    "dynamicElasticVisualizationSpeed", "30")));
            state.setDynamicElasticMaximumSpeed(Boolean.parseBoolean(values.getProperty(
                    "dynamicElasticMaximumSpeed", "false")));
            state.setDynamicElasticPublicationInterval(Integer.parseInt(values.getProperty(
                    "dynamicElasticPublicationInterval", "1")));
            state.setDynamicElasticMaximumIterations(Integer.parseInt(values.getProperty(
                    "dynamicElasticMaximumIterations", "100000")));
        } catch (Exception exception) {
            System.err.println("Could not load Geometry Discovery preferences: "
                    + exception.getMessage());
        }
    }

    public void save(GeometryDiscoveryWorkspaceState state, GeometryDiscoveryViewState view) {
        Properties values = new Properties();
        values.setProperty("scoringProfile", state.scoringProfile().name());
        values.setProperty("outputDirectory", state.outputDirectory().toString());
        values.setProperty("lastCandidate",
                state.currentSource() == null ? "" : state.currentSource().toString());
        values.setProperty("orthographic", Boolean.toString(view.orthographic()));
        values.setProperty("showConnections", Boolean.toString(view.showConnections()));
        values.setProperty("showAxes", Boolean.toString(view.showAxes()));
        values.setProperty("showBoundingBox", Boolean.toString(view.showBoundingBox()));
        values.setProperty("guidedMode", Boolean.toString(state.guidedMode()));
        values.setProperty("introductionDismissed",
                Boolean.toString(state.introductionDismissed()));
        values.setProperty("selectedDetailTab", state.selectedDetailTab().name());
        values.setProperty("visualizationUpdateTrigger",
                state.visualizationUpdateTrigger().name());
        values.setProperty("visualizationSpeed",
                Integer.toString(state.snapshotUpdatesPerSecond()));
        values.setProperty("maximumVisualizationSpeed",
                Boolean.toString(state.maximumVisualizationSpeed()));
        values.setProperty("pauseOnNewBest", Boolean.toString(state.pauseOnNewBest()));
        values.setProperty("dynamicElasticVisualizationSpeed",
                Integer.toString(state.dynamicElasticUpdatesPerSecond()));
        values.setProperty("dynamicElasticMaximumSpeed",
                Boolean.toString(state.dynamicElasticMaximumSpeed()));
        values.setProperty("dynamicElasticPublicationInterval",
                Integer.toString(state.dynamicElasticPublicationInterval()));
        values.setProperty("dynamicElasticMaximumIterations",
                Integer.toString(state.dynamicElasticMaximumIterations()));
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                values.store(output, "REM Framework Geometry Discovery workspace UI preferences");
            }
        } catch (IOException exception) {
            System.err.println("Could not save Geometry Discovery preferences: "
                    + exception.getMessage());
        }
    }
}
