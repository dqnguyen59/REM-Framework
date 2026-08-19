package remframework.gui.view.frames;

import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.NkRect;
import org.lwjgl.nuklear.NkVec2;
import org.lwjgl.nuklear.Nuklear;
import org.lwjgl.system.MemoryStack;
import remframework.gui.model.NuklearModel;
import remframework.opengl.model.PassiveObserver;
import remframework.opengl.model.InterpolatedProbe;
import remframework.opengl.model.Probe;
import remframework.opengl.model.ProbeDefinition;
import remframework.opengl.model.ProbeDetectorType;
import remframework.opengl.model.ProbeComparison;
import remframework.opengl.model.Scene;
import remframework.opengl.model.entity.Occupancy;
import remframework.opengl.model.entity.Node;
import remframework.opengl.helpers.Neighbor;
import remframework.settings.Project;

import java.util.Locale;

/** Read-only live view of the detectGradient8 receiver state. */
public final class FrameIsotropy extends AbstractFrame {

    static final String HISTORY_BUTTON_LABEL = "▥";

    private static final int DEFAULT_WIDTH = 720;
    private static final int DEFAULT_HEIGHT = 720;
    private static final int MIN_WIDTH = 520;
    private static final int MIN_HEIGHT = 420;
    private static final int INSPECTOR_ROW_HEIGHT = 22;

    private final Project project = Project.getInstance();
    private boolean showSource = true;
    private boolean showNeighbors = true;
    private boolean showVectors = true;
    private boolean showGradient = true;
    private int selectedInterpolationSource;
    private int selectedTab;
    private boolean inspectorPositionedForSession;

    public FrameIsotropy(NuklearModel model) {
        super(model);
        resizeCapability = ResizeCapability.enabled(MIN_WIDTH, MIN_HEIGHT, 12);
    }

    @Override
    public String getTitle() {
        return "Isotropy";
    }

    @Override
    public void close() {
        project.scene.exitNeighborhoodInspection();
        super.close();
    }

    @Override
    public void render(long windowHandle, NkContext ctx) {
        super.render(windowHandle, ctx);
        var savedRect = appSettings.getFrameRectMap().get(getTitle());
        int width = savedRect == null
                ? DEFAULT_WIDTH
                : Math.max(MIN_WIDTH, Math.round(savedRect.w()));
        int height = savedRect == null
                ? DEFAULT_HEIGHT
                : Math.max(MIN_HEIGHT, Math.round(savedRect.h()));
        if (savedRect != null) {
            savedRect.w(width);
            savedRect.h(height);
        }
        boolean inspectionActive = project.scene.isNeighborhoodInspectionActive()
                || project.scene.isNeighborhoodInspectionTransitionActive();
        if (!inspectionActive) {
            inspectorPositionedForSession = false;
        } else if (!inspectorPositionedForSession) {
            if (savedRect == null) {
                savedRect = NkRect.create();
                appSettings.getFrameRectMap().put(getTitle(), savedRect);
            }
            positionNearInspectionCluster(savedRect, width, height);
            inspectorPositionedForSession = true;
        }
        createLayout(ctx, 40, nuklearModel.getMenuBarHeight() + 50, width, height);
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

        Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 3);
        if (Nuklear.nk_button_label(ctx, "Gradient visualization")) selectedTab = 0;
        if (Nuklear.nk_button_label(ctx, "Calibration")) selectedTab = 1;
        if (Nuklear.nk_button_label(ctx, "Gradient Imbalance Comparison")) selectedTab = 2;

        boolean inspectionActive = project.scene.isNeighborhoodInspectionActive()
                || project.scene.isNeighborhoodInspectionTransitionActive();
        if (inspectionActive && selectedTab == 0) {
            Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 2);
            Nuklear.nk_label(ctx, "Neighborhood Inspector", Nuklear.NK_TEXT_LEFT);
            if (Nuklear.nk_button_label(ctx, "Exit Inspector (Esc)")) {
                project.scene.exitNeighborhoodInspection();
            }
        }

        NkVec2 contentSize = NkVec2.create();
        Nuklear.nk_window_get_content_region_size(ctx, contentSize);
        int fixedHeight = rowHeight + rowHeightFor(ctx)
                + (inspectionActive && selectedTab == 0 ? rowHeightFor(ctx) : 0);
        int tabHeight = Math.max(rowHeight,
                                Math.round(contentSize.y()) - fixedHeight);
        Nuklear.nk_layout_row_dynamic(ctx, tabHeight, 1);
        String tabContentTitle = switch (selectedTab) {
            case 1 -> "Calibration content";
            case 2 -> "Gradient Imbalance Comparison content";
            default -> "Gradient visualization content";
        };
        if (!Nuklear.nk_group_begin(ctx, tabContentTitle,
                                    Nuklear.NK_WINDOW_SCROLL_AUTO_HIDE)) return;

        if (selectedTab == 1) {
            calibrationSection(ctx);
            Nuklear.nk_group_end(ctx);
            return;
        }
        if (selectedTab == 2) {
            comparisonSection(ctx);
            Nuklear.nk_group_end(ctx);
            return;
        }

        section(ctx, "Gradient visualization");
        project.scene.setShowGradientArrows(nk_check_label(ctx, "Show gradient imbalance arrows",
                                                            project.scene.isShowGradientArrows(), 0.7, 0.3));
        scalar(ctx, "Gradient arrow scale exponent",
               Integer.toString((int) project.scene.getGradientArrowScaleExponent()));
        project.scene.setGradientArrowScaleExponent(
                Math.rint(nk_slider(ctx, 0.0, project.scene.getGradientArrowScaleExponent(), 120.0, 1)));
        scalar(ctx, "Visual scale", "10^" + (int) project.scene.getGradientArrowScaleExponent()
                + " = " + format(project.scene.getGradientArrowScale()));

        PassiveObserver probe = project.scene.getSelectedProbe();
        section(ctx, "Selected passive probe");
        if (probe == null) {
            scalar(ctx, "Probe", "None");
        } else {
            Nuklear.nk_layout_row_begin(ctx, Nuklear.NK_DYNAMIC, rowHeightFor(ctx), 3);
            Nuklear.nk_layout_row_push(ctx, 0.62f);
            Nuklear.nk_label(ctx, "Probe", Nuklear.NK_TEXT_LEFT);
            Nuklear.nk_layout_row_push(ctx, 0.19f);
            if (Nuklear.nk_button_label(ctx, HISTORY_BUTTON_LABEL))
                nuklearModel.showFrame(NuklearModel.Frame.occupancyHistory);
            Nuklear.nk_layout_row_push(ctx, 0.19f);
            if (Nuklear.nk_button_label(ctx, "Inspect Neighborhood"))
                project.scene.enterNeighborhoodInspection();
            Nuklear.nk_layout_row_end(ctx);
            scalar(ctx, "Probe ID", probe.getId());
            scalar(ctx, "Detector type", probe.getDetectorType().name());
            ProbeDefinition.Placement placement = probe.getDefinition().getPlacement();
            if (placement != null) {
                scalar(ctx, "Represented targets", placement.targets().stream()
                        .map(target -> "Shell " + (target.shell() + 1) + " " + target.rayPlane())
                        .reduce((left, right) -> left + ", " + right).orElse(""));
                scalar(ctx, "Requested radii", placement.targets().stream()
                        .map(target -> Integer.toString(target.requestedRadius()))
                        .reduce((left, right) -> left + ", " + right).orElse(""));
                if (probe.getDetectorType() == ProbeDetectorType.RAW_NODE) {
                    ProbeDefinition.Target firstTarget = placement.targets().getFirst();
                    scalar(ctx, "Realized radius", String.format(Locale.ROOT, "%.6f",
                                                                  firstTarget.realizedRadius()));
                    scalar(ctx, "Graph distance (diagnostic)",
                           Integer.toString(firstTarget.graphDistance()));
                } else {
                    ProbeDefinition.Target target = placement.targets().getFirst();
                    vector(ctx, "Exact requested coordinate", target.requestedCoordinate());
                    vector(ctx, "Requested direction", target.requestedDirection());
                    for (int sourceIndex = 0;
                         sourceIndex < probe.getDefinition().getInterpolationSources().size();
                         sourceIndex++) {
                        ProbeDefinition.InterpolationSource source = probe.getDefinition()
                                .getInterpolationSources().get(sourceIndex);
                        scalar(ctx, "Interpolation source " + (sourceIndex + 1),
                               String.format(Locale.ROOT, "(%d, %d, %d), weight %.9f",
                                       source.nodeIndex().x, source.nodeIndex().y,
                                       source.nodeIndex().z, source.weight()));
                    }
                }
            }
            if (probe instanceof InterpolatedProbe interpolatedProbe) {
                interpolationSourcesSection(ctx, interpolatedProbe);
            }
            PassiveObserver.Observation observation = probe.getObservation();
            if (probe instanceof Probe rawProbe) {
                Node node = rawProbe.getNode();
                scalar(ctx, "Measurement", "Raw node measurement");
                vector(ctx, "Node coordinate", rawProbe.getNodeCoordinate());
                vector(ctx, "Node position", node.getPosition());
                vector(ctx, "Stable gradient imbalance", probe.getStableGradientImbalance());
                scalar(ctx, "Gradient strength |g|", format(probe.getStableGradientImbalance().length()));
                vector(ctx, "Acceleration", node.getAcceleration());
                vector(ctx, "Node Velocity", node.getVelocity());
            } else if (probe instanceof InterpolatedProbe interpolatedProbe) {
                scalar(ctx, "Measurement", "Interpolated estimate — not a node measurement");
                vector(ctx, "Interpolated live position", observation.position());
                vector(ctx, "Weighted stable gradient imbalance", probe.getStableGradientImbalance());
                scalar(ctx, "Gradient strength |g|", format(probe.getStableGradientImbalance().length()));
                vector(ctx, "Interpolated acceleration", observation.acceleration());
                vector(ctx, "Interpolated velocity", observation.velocity());
                interpolationSourceDetails(ctx, interpolatedProbe);
            }
            inspectorSection(ctx, probe);
            for (int index = 0; index < probe.getOccupancies().size(); index++) {
                Occupancy occupancy = probe.getOccupancies().get(index);
                double weight = probe.getDefinition().getDetectorType() == ProbeDetectorType.RAW_NODE
                        ? 1.0
                        : probe.getDefinition().getInterpolationSources().get(index).weight();
                scalar(ctx, "Source Occupancy " + (index + 1),
                       String.format(Locale.ROOT, "weight %.9f", weight));
                vector(ctx, "Source stable gradient " + (index + 1),
                       occupancy.getGradientImbalance());
                scalar(ctx, sourceSamplesLabel(index),
                       Integer.toString(occupancy.getSamplesPerCycle()));
                scalar(ctx, "Source cycle completed " + (index + 1),
                       Boolean.toString(occupancy.wasCycleCompletedThisUpdate()));
            }
            naVector(ctx, "Transport Momentum");
            naVector(ctx, "Pressure");
            scalar(ctx, "Cycle state", "N/A (Occupancy only)");
        }

        section(ctx, "Receiver Occupancy reference");
        Occupancy receiver = project.occupancies.size() > 1 ? project.occupancies.get(1) : null;
        if (receiver == null) {
            Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
            Nuklear.nk_label(ctx, "Receiver unavailable", Nuklear.NK_TEXT_LEFT);
            Nuklear.nk_group_end(ctx);
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
        Nuklear.nk_group_end(ctx);
    }

    private void calibrationSection(NkContext ctx) {
        section(ctx, "Arrow calibration");
        project.scene.setShowCalibrationArrow(nk_check_label(ctx, "Show calibration arrow",
                                                             project.scene.isShowCalibrationArrow(), 0.7, 0.3));
        Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 2);
        Nuklear.nk_label(ctx, "Direction:", Nuklear.NK_TEXT_LEFT);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String[] directions = {"-X", "+Y", "+Z"};
            Scene.CalibrationDirection currentDirection = project.scene.getCalibrationDirection();
            String currentLabel = switch (currentDirection) {
                case NEGATIVE_X -> "-X";
                case POSITIVE_Y -> "+Y";
                case POSITIVE_Z -> "+Z";
            };
            String selected = nk_combo_options(ctx, stack, directions, currentLabel);
            Scene.CalibrationDirection selectedDirection = switch (selected) {
                case "+Y" -> Scene.CalibrationDirection.POSITIVE_Y;
                case "+Z" -> Scene.CalibrationDirection.POSITIVE_Z;
                default -> Scene.CalibrationDirection.NEGATIVE_X;
            };
            project.scene.setCalibrationDirection(selectedDirection);
        }
        Vector3d calibrationVector = project.scene.getCalibrationDirection().vector();
        scalar(ctx, "Calibration", calibrationDirectionLabel(project.scene.getCalibrationDirection())
                + "  (" + (int) calibrationVector.x + ", "
                + (int) calibrationVector.y + ", " + (int) calibrationVector.z + ")");
    }

    private void inspectorSection(NkContext ctx, PassiveObserver probe) {
        section(ctx, "Neighborhood Inspector (read-only)");
        if (project.scene.isNeighborhoodInspectionActive()
                || project.scene.isNeighborhoodInspectionTransitionActive()) {
            scalar(ctx, "Inspected object", "Existing selected probe marker");
            scalar(ctx, "Camera target", probe instanceof InterpolatedProbe
                    ? "Selected interpolation source position" : "Live probe marker position");
            Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 2);
            Nuklear.nk_label(ctx, "Inspection alpha", Nuklear.NK_TEXT_LEFT);
            project.scene.setInspectionSourceAlpha(
                    nk_slider(ctx, 0.0, project.scene.getInspectionSourceAlpha(), 1.0, 0.05));
            project.scene.setShowInspectionProbe(
                    nk_check_label(ctx, "Show probe", project.scene.isShowInspectionProbe(), 0.7, 0.3));
            section(ctx, "Inspection legend");
            scalar(ctx, "Selected source", "Opaque green");
            scalar(ctx, "Direct neighbor", "Grey / configured alpha");
            scalar(ctx, "Other source", "Orange / configured alpha");
            scalar(ctx, "Probe marker", project.scene.isShowInspectionProbe()
                    ? "Shown / configured alpha" : "Hidden");
        } else {
            scalar(ctx, "Status", "Select a probe, then inspect (live or paused)");
        }
    }

    private void interpolationSourcesSection(NkContext ctx, InterpolatedProbe probe) {
        section(ctx, "Interpolation Sources");
        int selectedIndex = project.scene.getInspectedSourceIndex();
        for (int index = 0; index < probe.getSourceNodes().size(); index++) {
            Node source = probe.getSourceNodes().get(index);
            ProbeDefinition.InterpolationSource definitionSource = probe.getDefinition()
                    .getInterpolationSources().get(index);
            Nuklear.nk_layout_row_begin(ctx, Nuklear.NK_DYNAMIC, rowHeightFor(ctx), 3);
            Nuklear.nk_layout_row_push(ctx, 0.26f);
            if (Nuklear.nk_button_label(ctx,
                    (index == selectedIndex ? "> " : "") + "Source " + (index + 1))) {
                project.scene.selectInspectionSource(index);
            }
            Nuklear.nk_layout_row_push(ctx, 0.49f);
            Nuklear.nk_label(ctx, formatCoordinate(source.getIndex()), Nuklear.NK_TEXT_LEFT);
            Nuklear.nk_layout_row_push(ctx, 0.25f);
            Nuklear.nk_label(ctx, "weight " + format(definitionSource.weight()), Nuklear.NK_TEXT_RIGHT);
            Nuklear.nk_layout_row_end(ctx);
        }
    }

    private void interpolationSourceDetails(NkContext ctx, InterpolatedProbe probe) {
        int selectedIndex = Math.clamp(project.scene.getInspectedSourceIndex(),
                                       0, probe.getSourceNodes().size() - 1);
        Node source = probe.getSourceNodes().get(selectedIndex);
        ProbeDefinition.InterpolationSource definitionSource = probe.getDefinition()
                .getInterpolationSources().get(selectedIndex);
        section(ctx, "Selected interpolation source");
        scalar(ctx, "Source", "Source " + (selectedIndex + 1));
        vector(ctx, "Source coordinate", source.getIndex());
        scalar(ctx, "Interpolation weight", format(definitionSource.weight()));
        vector(ctx, "Source position", source.getPosition());
        vector(ctx, "Source velocity", source.getVelocity());
        vector(ctx, "Source acceleration", source.getAcceleration());
        vector(ctx, "Source stable gradient", probe.getOccupancies().get(selectedIndex)
                .getGradientImbalance());

        section(ctx, "Direct neighbors of selected source");
        for (Neighbor direction : Neighbor.values()) {
            Node neighbor = source.getNeighbor(direction);
            if (neighbor == null) {
                scalar(ctx, directionLabel(direction), "Unavailable");
                continue;
            }
            vector(ctx, directionLabel(direction) + " coordinate", neighbor.getIndex());
            vector(ctx, directionLabel(direction) + " position", neighbor.getPosition());
            vector(ctx, directionLabel(direction) + " velocity", neighbor.getVelocity());
            vector(ctx, directionLabel(direction) + " acceleration", neighbor.getAcceleration());
            vector(ctx, directionLabel(direction) + " stable gradient",
                   neighbor.getGradientImbalance());
        }
    }

    private static String formatCoordinate(org.joml.Vector3i coordinate) {
        return "(" + coordinate.x + ", " + coordinate.y + ", " + coordinate.z + ")";
    }

    private static String directionLabel(Neighbor direction) {
        return switch (direction) {
            case RIGHT -> "+X";
            case TOP -> "+Y";
            case FRONT -> "+Z";
            case LEFT -> "-X";
            case BOTTOM -> "-Y";
            case BACK -> "-Z";
        };
    }

    static String sourceSamplesLabel(int index) {
        return "Source samples per completed cycle " + (index + 1);
    }

    private void comparisonSection(NkContext ctx) {
        section(ctx, "Gradient Imbalance Comparison");
        if (Nuklear.nk_button_label(ctx, "Clear Comparison")) project.scene.clearComparison();
        ProbeComparison comparison = project.scene.getComparison();
        PassiveObserver a = comparison.get(ProbeComparison.Slot.A);
        PassiveObserver b = comparison.get(ProbeComparison.Slot.B);
        scalar(ctx, "Comparison A", a == null ? "Waiting for A" : a.getId());
        scalar(ctx, "Comparison B", b == null ? "Waiting for B" : b.getId());
        if (a == null || b == null) return;
        comparisonValues(ctx, "A", a);
        comparisonValues(ctx, "B", b);
        double delta = comparison.deltaRadial();
        scalar(ctx, "Δg_parallel", format(delta));
        scalar(ctx, "|Δg_parallel|", format(Math.abs(delta)));
    }

    private void comparisonValues(NkContext ctx, String label, PassiveObserver probe) {
        ProbeComparison.Measurement measurement = ProbeComparison.calculate(probe);
        scalar(ctx, label + " ID", probe.getId());
        scalar(ctx, label + " detector type", probe.getDetectorType().name());
        scalar(ctx, label + " requested radius", Integer.toString(measurement.requestedRadius()));
        vector(ctx, label + " requested direction", measurement.requestedDirection());
        vector(ctx, label + " g", measurement.gradient());
        scalar(ctx, label + " |g|", format(measurement.gradient().length()));
        scalar(ctx, label + " g_parallel", format(measurement.radial()));
        scalar(ctx, label + " g_perp", format(measurement.sideways()));
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

    private static String calibrationDirectionLabel(Scene.CalibrationDirection direction) {
        return switch (direction) {
            case NEGATIVE_X -> "−X";
            case POSITIVE_Y -> "+Y";
            case POSITIVE_Z -> "+Z";
        };
    }

    private static int rowHeightFor(NkContext ctx) {
        return INSPECTOR_ROW_HEIGHT;
    }

    static int inspectorContentHeight(int rowCount) {
        return Math.max(0, rowCount) * INSPECTOR_ROW_HEIGHT;
    }

    static int inspectorRowHeight() {
        return INSPECTOR_ROW_HEIGHT;
    }

    static int inspectorScrollMaximum(int contentHeight, int visibleHeight) {
        return Math.max(0, contentHeight - Math.max(0, visibleHeight));
    }

    static int[] clampInspectorPosition(int x, int y, int width, int height,
                                         int viewportWidth, int viewportHeight, int minimumY) {
        int maxX = Math.max(0, viewportWidth - width);
        int maxY = Math.max(minimumY, viewportHeight - height);
        return new int[]{Math.max(0, Math.min(x, maxX)),
                Math.max(minimumY, Math.min(y, maxY))};
    }

    private void positionNearInspectionCluster(NkRect rect, int width, int height) {
        PassiveObserver probe = project.scene.getSelectedProbe();
        if (probe == null) return;
        Vector3d anchor = project.scene.getInspectionFocusNode() == null
                ? probe.getObservation().position()
                : project.scene.getInspectionFocusNode().toWorldPosition();
        int[] viewport = {0, 0, appSettings.getWindowWidth(), appSettings.getWindowHeight()};
        Vector3f screen = new Matrix4f(project.camera.getProjectionMatrix())
                .mul(project.camera.getViewMatrix())
                .project(new Vector3f((float) anchor.x, (float) anchor.y, (float) anchor.z),
                         viewport, new Vector3f());
        int proposedX = Math.round(screen.x + 24.0f);
        int proposedY = Math.round(appSettings.getWindowHeight() - screen.y - height * 0.35f);
        int[] position = clampInspectorPosition(proposedX, proposedY, width, height,
                                                appSettings.getWindowWidth(),
                                                appSettings.getWindowHeight(),
                                                nuklearModel.getMenuBarHeight() + 5);
        rect.x(position[0]).y(position[1]).w(width).h(height);
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
