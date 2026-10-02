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
import remframework.geometry.discovery.workflow.WorkspaceController;
import remframework.geometry.discovery.GeometryDiscoveryWorkspaceManager;
import remframework.gui.model.NuklearModel;

public final class GeometryDiscoveryIntroductionFrame extends AbstractFrame {
    private final WorkspaceController controller =
            GeometryDiscoveryWorkspaceManager.getInstance();
    public GeometryDiscoveryIntroductionFrame(NuklearModel model) {
        super(model);
        isDialog = true;
        windowOptions = Nuklear.NK_WINDOW_TITLE | Nuklear.NK_WINDOW_BORDER
                | Nuklear.NK_WINDOW_MOVABLE;
    }
    @Override public String getTitle() { return "Geometry Discovery Introduction"; }
    @Override public void render(long window, NkContext context) {
        super.render(window, context);
        createLayoutCentered(context, 520, 300);
    }
    @Override protected void layout(NkContext c, int x, int y, int width, int height) {
        label(c, "Geometry Discovery works in five stages:");
        label(c, "1. Load a candidate.");
        label(c, "2. Relax its geometry.");
        label(c, "3. Evaluate it.");
        label(c, "4. Search alternative topologies.");
        label(c, "5. Review and export results.");
        label(c, "Discovery remains independent from the active REM lattice.");
        Nuklear.nk_layout_row_dynamic(c, rowHeightEdit, 2);
        if (Nuklear.nk_button_label(c, "Do Not Show Again")) {
            controller.state().setIntroductionDismissed(true);
            close();
        }
        if (Nuklear.nk_button_label(c, "Start Guided Mode")) {
            controller.state().setGuidedMode(true);
            close();
        }
    }
    private void label(NkContext c, String text) {
        Nuklear.nk_layout_row_dynamic(c, rowHeight, 1);
        Nuklear.nk_label_wrap(c, text);
    }
}
