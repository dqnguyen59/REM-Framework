package remframework.gui.view.frames;

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import remframework.geometry.discovery.*;
import remframework.gui.model.NuklearModel;
import remframework.helpers.Constants;

/**
 * Context and guidance panel for the integrated discovery workspace.
 */
public final class GeometryDiscoverySearchFrame extends AbstractFrame {
    private final GeometryDiscoveryWorkspaceController controller =
            GeometryDiscoveryWorkspaceManager.getInstance();

    public GeometryDiscoverySearchFrame(NuklearModel model) {
        super(model);
        windowOptions = Nuklear.NK_WINDOW_TITLE | Nuklear.NK_WINDOW_BORDER
                | Nuklear.NK_WINDOW_NO_SCROLLBAR;
    }

    @Override public String getTitle() { return "Current Status"; }

    @Override public void render(long window, NkContext context) {
        super.render(window, context);
        GeometryDiscoveryWorkspaceLayout.Bounds bounds =
                GeometryDiscoveryWorkspaceLayout.calculate(getWindowWidth(), getWindowHeight(),
                        nuklearModel.getMenuBarHeight() + 4,
                        Constants.STATUS_BAR_HEIGHT).status();
        createFixedLayout(context, bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    @Override protected void layout(NkContext c, int x, int y, int width, int height) {
        GeometryDiscoveryWorkflowResolution workflow = controller.workflow();
        GeometryDiscoveryWorkspaceSnapshot snapshot = controller.workspace().snapshot();
        heading(c, "Stage " + workflow.stage().number() + ": " + workflow.stage().label());
        label(c, "Experiment state: " + display(workflow.state().name()));
        label(c, "Candidate: " + snapshot.geometry().model().name());
        DiscoveryExperimentConfiguration experiment =
                controller.state().experimentConfiguration();
        if (experiment != null) {
            label(c, "Experiment: " + experiment.name());
            label(c, "Workflow phase: " + display(
                    controller.state().experimentLifecycle().name()));
        }
        label(c, "Scoring profile: " + display(controller.state().scoringProfile().name()));
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 3);
        if (Nuklear.nk_button_label(c, "Current Candidate"))
            controller.state().setViewportSelection(
                    DiscoveryViewportSelection.CURRENT_CANDIDATE);
        if (Nuklear.nk_button_label(c, "Best Candidate"))
            controller.state().setViewportSelection(
                    DiscoveryViewportSelection.BEST_CANDIDATE);
        if (Nuklear.nk_button_label(c, "Preview Candidate"))
            controller.state().setViewportSelection(
                    DiscoveryViewportSelection.PREVIEW_CANDIDATE);
        if (controller.state().guidedMode()) {
            heading(c, "Recommended Next Action");
            label(c, workflow.recommendation().action());
            label(c, workflow.recommendation().explanation());
        }
        if (snapshot.error() != null && !snapshot.error().isBlank()) {
            heading(c, "Error");
            label(c, snapshot.error());
        }
        if (snapshot.search() != null) {
            TopologySearchResult search = snapshot.search();
            TopologySearchMetrics metrics = search.metrics();
            heading(c, "Search Progress");
            int maximum = search.settings().maximumSearchIterations();
            label(c, "Iteration: " + metrics.completedIterations() + " / " + maximum);
            label(c, String.format("Completion: %.1f%%", maximum == 0 ? 100
                    : 100.0 * metrics.completedIterations() / maximum));
            label(c, String.format("Current score: %.7g",
                    search.currentCandidate().score().total()));
            label(c, String.format("Best score: %.7g",
                    search.bestCandidate().score().total()));
            label(c, "Accepted: " + metrics.acceptedMutationCount()
                    + "    Rejected: " + metrics.rejectedMutationCount());
            label(c, "Invalid: " + metrics.invalidMutationCount()
                    + "    Duplicates: " + metrics.duplicateTopologyCount());
            label(c, "Relaxation failures: " + metrics.relaxationFailureCount());
            label(c, String.format("Temperature: %.6g",
                    search.history().isEmpty() ? search.settings().initialTemperature()
                            : search.history().getLast().temperature()));
            label(c, "Elapsed: " + metrics.elapsedMilliseconds() + " ms");
        } else if (snapshot.relaxationRunning()) {
            GeometryDiscoveryMetrics metrics = snapshot.geometry().metrics();
            int maximum = snapshot.geometry().settings().maximumIterations();
            label(c, "Relaxation iteration: " + metrics.iterationCount() + " / " + maximum);
            label(c, String.format("Relaxation completion: %.1f%%",
                    100.0 * metrics.iterationCount() / maximum));
            label(c, String.format("Current edge error: %.7g",
                    metrics.totalEdgeLengthError()));
        }
    }

    private void heading(NkContext c, String text) {
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit + 3, 1);
        Nuklear.nk_style_set_font(c, nuklearModel.getDefaultFontBold());
        Nuklear.nk_label_wrap(c, text);
        Nuklear.nk_style_set_font(c, nuklearModel.getDefaultFont());
    }

    private void label(NkContext c, String text) {
        Nuklear.nk_layout_row_dynamic(c, rowHeight + 3, 1);
        Nuklear.nk_label_wrap(c, text);
    }

    private String display(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
