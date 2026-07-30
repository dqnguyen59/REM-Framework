package remframework.gui.view.frames;

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import remframework.geometry.discovery.*;
import remframework.gui.model.NuklearModel;

public final class GeometryDiscoveryStatusFrame extends AbstractFrame {
    private final GeometryDiscoveryWorkspaceController controller =
            GeometryDiscoveryWorkspaceManager.getInstance();

    public GeometryDiscoveryStatusFrame(NuklearModel model) {
        super(model);
        windowOptions = Nuklear.NK_WINDOW_BORDER | Nuklear.NK_WINDOW_NO_SCROLLBAR;
    }
    @Override public String getTitle() { return "Discovery Viewport Status"; }
    @Override public void render(long window, NkContext context) {
        super.render(window, context);
        int rightWidth = 370;
        createLayout(context, 345, nuklearModel.getMenuBarHeight() + 6,
                Math.max(300, getWindowWidth() - 345 - rightWidth), 72);
    }
    @Override protected void layout(NkContext c, int x, int y, int width, int height) {
        GeometryDiscoveryWorkspaceSnapshot snapshot = controller.workspace().snapshot();
        GeometryDiscoveryWorkflowResolution workflow = controller.workflow();
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 1);
        Nuklear.nk_style_set_font(c, nuklearModel.getDefaultFontBold());
        Nuklear.nk_label(c, statusLabel(workflow.state()), Nuklear.NK_TEXT_CENTERED);
        Nuklear.nk_style_set_font(c, nuklearModel.getDefaultFont());
        Nuklear.nk_layout_row_dynamic(c, rowHeight, 1);
        Nuklear.nk_label(c, "Candidate: " + snapshot.geometry().model().name()
                + "    Profile: " + profileLabel(controller.state().scoringProfile()),
                Nuklear.NK_TEXT_CENTERED);
    }
    private String statusLabel(GeometryDiscoveryWorkflowState state) {
        return switch (state) {
            case NO_CANDIDATE -> "No Candidate";
            case CANDIDATE_LOADED -> "Candidate Loaded";
            case RELAXATION_REQUIRED -> "Relaxation Recommended";
            case RELAXING -> "Geometry Relaxing";
            case GEOMETRY_CONVERGED, READY_FOR_EVALUATION, READY_FOR_SEARCH ->
                    "Geometry Converged";
            case SEARCHING -> "Search Running";
            case SEARCH_PAUSED -> "Search Paused";
            case BEST_CANDIDATE_AVAILABLE -> "Best Candidate Available";
            case ERROR -> "Error";
        };
    }
    private String profileLabel(TopologyScoringProfile profile) {
        return profile.name().replace('_', ' ');
    }
}
