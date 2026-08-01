package remframework.gui.view.frames;
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

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import remframework.gui.model.NuklearModel;
import remframework.helpers.Constants;

public final class GeometryDiscoveryStatusFrame extends AbstractFrame {
    private final WorkspaceController controller =
            GeometryDiscoveryWorkspaceManager.getInstance();

    public GeometryDiscoveryStatusFrame(NuklearModel model) {
        super(model);
        windowOptions = Nuklear.NK_WINDOW_BORDER | Nuklear.NK_WINDOW_NO_SCROLLBAR;
    }
    @Override public String getTitle() { return "Discovery Viewport Status"; }
    @Override public void render(long window, NkContext context) {
        super.render(window, context);
        GeometryDiscoveryWorkspaceLayout.Bounds viewport =
                GeometryDiscoveryWorkspaceLayout.calculate(getWindowWidth(), getWindowHeight(),
                        nuklearModel.getMenuBarHeight() + 4,
                        Constants.STATUS_BAR_HEIGHT).viewport();
        createFixedLayout(context, viewport.x(), viewport.y(), viewport.width(),
                Math.min(72, viewport.height()));
    }
    @Override protected void layout(NkContext c, int x, int y, int width, int height) {
        GeometryDiscoveryWorkspaceSnapshot snapshot = controller.workspace().snapshot();
        GeometryDiscoveryWorkflowResolution workflow = controller.workflow();
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 1);
        Nuklear.nk_style_set_font(c, nuklearModel.getDefaultFontBold());
        Nuklear.nk_label(c, controller.workspace().dynamicElasticActive()
                ? dynamicElasticStatus() : statusLabel(workflow.state()),
                Nuklear.NK_TEXT_CENTERED);
        Nuklear.nk_style_set_font(c, nuklearModel.getDefaultFont());
        Nuklear.nk_layout_row_dynamic(c, rowHeight, 1);
        Nuklear.nk_label(c, "Candidate: " + snapshot.geometry().model().name()
                + "    Profile: " + profileLabel(controller.state().scoringProfile()),
                Nuklear.NK_TEXT_CENTERED);
    }
    private String dynamicElasticStatus() {
        return DynamicElasticStatusReporter.label(
                controller.workspace().dynamicElasticMetrics());
    }
    private String statusLabel(GeometryDiscoveryWorkflowState state) {
        return switch (state) {
            case NO_CANDIDATE -> "No Candidate";
            case CANDIDATE_LOADED -> "Candidate Loaded";
            case RELAXATION_REQUIRED -> "Relaxation Required";
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
