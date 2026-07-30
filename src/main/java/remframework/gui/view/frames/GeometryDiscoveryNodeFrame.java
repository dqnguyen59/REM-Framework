package remframework.gui.view.frames;

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import remframework.geometry.discovery.*;
import remframework.gui.model.NuklearModel;

import java.util.Map;
import java.util.stream.Collectors;

public final class GeometryDiscoveryNodeFrame extends AbstractFrame {
    private final GeometryDiscoveryWorkspaceController controller =
            GeometryDiscoveryWorkspaceManager.getInstance();
    public GeometryDiscoveryNodeFrame(NuklearModel model) {
        super(model);
        windowOptions = Nuklear.NK_WINDOW_TITLE | Nuklear.NK_WINDOW_BORDER
                | Nuklear.NK_WINDOW_MOVABLE | Nuklear.NK_WINDOW_SCALABLE;
    }
    @Override public String getTitle() { return "Selected Discovery Node"; }
    @Override public void render(long window, NkContext context) {
        super.render(window, context);
        createLayout(context, Math.max(805, getWindowWidth() - 350),
                Math.max(300, getWindowHeight() - 330), 345, 320);
    }
    @Override protected void layout(NkContext c, int x, int y, int width, int height) {
        GeometryDiscoveryModel model = controller.workspace().snapshot().geometry().model();
        Integer selected = controller.viewState().selectedNodeId();
        if (selected == null) { label(c, "Click a node or press Tab to select."); return; }
        DiscoveryNode node = model.nodes().stream().filter(n -> n.id() == selected)
                .findFirst().orElse(null);
        if (node == null) { label(c, "Selected node is unavailable."); return; }
        LocalDirectionalDiagnostics d = new DirectionalBalanceCalculator().calculate(model)
                .localDiagnostics().get(selected);
        SelectedNodeMetrics local = new SelectedNodeMetricsCalculator().calculate(model,
                selected, controller.workspace().snapshot().geometry().settings().targetDistance());
        Map<Integer, java.util.Set<Integer>> adjacency = TopologyConstraintValidator.adjacency(model);
        label(c, "Node " + selected + " position=" + node.position());
        label(c, "Degree=" + d.degree() + " neighbors="
                + adjacency.get(selected).stream().map(String::valueOf)
                .collect(Collectors.joining(", ")));
        label(c, "Edge distances=" + local.neighborDistances());
        label(c, String.format("Bias %.6g tensor %.6g", d.normalizedDirectionalBias(),
                d.tensorIsotropyError()));
        label(c, "Pairwise dots=" + d.pairwiseDotProducts());
        label(c, "Angular reference=" + d.angularReferenceType()
                + " applicable=" + d.angularReferenceApplicable()
                + " error=" + d.knownReferenceAngularError());
        label(c, String.format("Coverage mean %.6g maxGap %.6g opposite %.6g",
                d.meanNearestDirectionAngle(), d.maximumUncoveredAngle(),
                d.meanOppositeDirectionMismatch()));
        label(c, "Local dimension=" + d.localDimensionality()
                + " eigenvalues=" + d.eigenvalues());
    }
    private void label(NkContext c, String value) {
        Nuklear.nk_layout_row_dynamic(c, rowHeight, 1);
        Nuklear.nk_label_wrap(c, value);
    }
}
