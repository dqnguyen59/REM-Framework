package remframework.gui.view.frames;

import org.joml.Vector3d;
import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import remframework.gui.model.NuklearModel;
import remframework.opengl.model.Probe;
import remframework.opengl.model.ProbeDefinition;
import remframework.opengl.model.entity.Occupancy;
import remframework.opengl.model.entity.Node;
import remframework.settings.Project;

import java.util.Locale;

/** Read-only live view of the detectGradient8 receiver state. */
public final class FrameIsotropy extends AbstractFrame {

    private final Project project = Project.getInstance();

    public FrameIsotropy(NuklearModel model) {
        super(model);
        windowOptions |= Nuklear.NK_WINDOW_SCALABLE;
    }

    @Override
    public String getTitle() {
        return "Isotropy";
    }

    @Override
    public void render(long windowHandle, NkContext ctx) {
        super.render(windowHandle, ctx);
        createLayout(ctx, 40, nuklearModel.getMenuBarHeight() + 50, 720, 540);
    }

    @Override
    protected void layout(NkContext ctx, int x, int y, int width, int height) {
        Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 2);
        boolean hasGeneratedProbes = project.getProbeDefinitions().stream()
                .anyMatch(ProbeDefinition::isGenerated);
        if (nk_button_label(ctx, hasGeneratedProbes ? "Edit Probes..." : "Add Probes...")) {
            nuklearModel.showFrame(NuklearModel.Frame.addProbes);
        }
        Nuklear.nk_label(ctx, "Probe center is the lattice geometric center", Nuklear.NK_TEXT_LEFT);

        Probe probe = project.scene.getSelectedProbe();
        section(ctx, "Selected passive probe");
        if (probe == null) {
            scalar(ctx, "Probe", "None");
        } else {
            Node node = probe.getNode();
            scalar(ctx, "Probe", probe.getId());
            ProbeDefinition.Placement placement = probe.getPlacement();
            if (placement != null) {
                scalar(ctx, "Ray / plane", placement.rayPlane());
                scalar(ctx, "Requested radius", Integer.toString(placement.requestedRadius()));
                scalar(ctx, "Realized radius", String.format(Locale.ROOT, "%.6f",
                                                              placement.realizedRadius()));
                scalar(ctx, "Graph distance (diagnostic)",
                       Integer.toString(placement.graphDistance()));
            }
            vector(ctx, "Node coordinate", probe.getNodeCoordinate());
            vector(ctx, "Node position", node.getPosition());
            vector(ctx, "Gradient Imbalance", node.getGradientImbalance());
            vector(ctx, "Acceleration", node.getAcceleration());
            vector(ctx, "Node Velocity", node.getVelocity());
            naVector(ctx, "Transport Momentum");
            naVector(ctx, "Pressure");
            scalar(ctx, "Cycle state", "N/A (Occupancy only)");
        }

        section(ctx, "Receiver Occupancy reference");
        Occupancy receiver = project.occupancies.size() > 1 ? project.occupancies.get(1) : null;
        if (receiver == null) {
            Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
            Nuklear.nk_label(ctx, "Receiver unavailable", Nuklear.NK_TEXT_LEFT);
            return;
        }

        Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 4);
        Nuklear.nk_label(ctx, "Value", Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_label(ctx, "X", Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx, "Y", Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx, "Z", Nuklear.NK_TEXT_RIGHT);

        scalar(ctx, "Identity", receiver.getIdentity() == null ? "" : receiver.getIdentity().getName());
        vector(ctx, "Current node coordinate", receiver.getNodeIndex());
        vector(ctx, "Gradient Imbalance", receiver.getGradientImbalance());
        vector(ctx, "Cycle gradients: G−", receiver.getLastMaxNegativeGradient());
        vector(ctx, "Cycle gradients: G+", receiver.getLastMaxPositiveGradient());
        vector(ctx, "Cycle gradients: G", receiver.getGradientImbalance());
        vector(ctx, "Acceleration", receiver.getAcceleration());
        vector(ctx, "Transport Momentum", receiver.getTransportMomentum());
        vector(ctx, "Pressure", receiver.getPressure());
        vector(ctx, "Node Velocity", receiver.getNode() == null ? new Vector3d() : receiver.getNode().getVelocity());
        scalar(ctx, "Samples in current cycle", Integer.toString(receiver.getCycleSampleCountForAudit()));
        scalar(ctx, "Samples in last completed cycle", Integer.toString(receiver.getSamplesPerCycleForAudit()));
        scalar(ctx, "Cycle completed", Boolean.toString(receiver.wasCycleCompletedThisUpdate()));
    }

    private static void section(NkContext ctx, String label) {
        Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 1);
        Nuklear.nk_label(ctx, label, Nuklear.NK_TEXT_LEFT);
    }

    private static void naVector(NkContext ctx, String label) {
        Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 4);
        Nuklear.nk_label(ctx, label, Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_label(ctx, "N/A", Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx, "N/A", Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx, "N/A", Nuklear.NK_TEXT_RIGHT);
    }

    private static void scalar(NkContext ctx, String label, String value) {
        Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 2);
        Nuklear.nk_label(ctx, label, Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_label(ctx, value, Nuklear.NK_TEXT_RIGHT);
    }

    private static int rowHeightFor(NkContext ctx) {
        return 22;
    }

    private static void vector(NkContext ctx, String label, Vector3d value) {
        Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 4);
        Nuklear.nk_label(ctx, label, Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_label(ctx, format(value.x), Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx, format(value.y), Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx, format(value.z), Nuklear.NK_TEXT_RIGHT);
    }

    private static void vector(NkContext ctx, String label, org.joml.Vector3i value) {
        Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 4);
        Nuklear.nk_label(ctx, label, Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_label(ctx, Integer.toString(value.x), Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx, Integer.toString(value.y), Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx, Integer.toString(value.z), Nuklear.NK_TEXT_RIGHT);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "% .6e", value);
    }
}
