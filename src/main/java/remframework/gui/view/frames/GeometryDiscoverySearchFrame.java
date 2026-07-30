package remframework.gui.view.frames;

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import remframework.geometry.discovery.GeometryDiscoveryWorkflowResolution;
import remframework.geometry.discovery.GeometryDiscoveryWorkspaceController;
import remframework.geometry.discovery.GeometryDiscoveryWorkspaceManager;
import remframework.geometry.discovery.GeometryDiscoveryWorkspaceSnapshot;
import remframework.geometry.discovery.TopologySearchMetrics;
import remframework.geometry.discovery.TopologySearchResult;
import remframework.gui.model.NuklearModel;

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
        createLayout(context, Math.max(680, getWindowWidth() - 365),
                nuklearModel.getMenuBarHeight() + 5, 360,
                getWindowHeight() - nuklearModel.getMenuBarHeight() - 300);
    }

    @Override protected void layout(NkContext c, int x, int y, int width, int height) {
        GeometryDiscoveryWorkflowResolution workflow = controller.workflow();
        GeometryDiscoveryWorkspaceSnapshot snapshot = controller.workspace().snapshot();
        heading(c, "Stage " + workflow.stage().number() + ": " + workflow.stage().label());
        label(c, "Experiment state: " + display(workflow.state().name()));
        label(c, "Candidate: " + snapshot.geometry().model().name());
        label(c, "Scoring profile: " + display(controller.state().scoringProfile().name()));
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
                    + "    Invalid: " + metrics.invalidMutationCount());
            label(c, "Elapsed: " + metrics.elapsedMilliseconds() + " ms");
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
