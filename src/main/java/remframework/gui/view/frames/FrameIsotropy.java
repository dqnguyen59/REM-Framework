package remframework.gui.view.frames;

import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.NkCommandBuffer;
import org.lwjgl.nuklear.NkRect;
import org.lwjgl.nuklear.NkVec2;
import org.lwjgl.nuklear.Nuklear;
import org.lwjgl.system.MemoryStack;
import remframework.gui.model.NuklearModel;
import remframework.gui.model.Theme;
import remframework.opengl.model.PassiveObserver;
import remframework.opengl.model.InterpolatedProbe;
import remframework.opengl.model.Probe;
import remframework.opengl.model.ProbeArrivalMeasurement;
import remframework.opengl.model.ProbeDefinition;
import remframework.opengl.model.ProbeDetectorType;
import remframework.opengl.model.ProbeComparison;
import remframework.opengl.model.Scene;
import remframework.opengl.model.entity.Occupancy;
import remframework.opengl.model.entity.Node;
import remframework.opengl.helpers.Neighbor;
import remframework.settings.Project;

import java.util.List;
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
    private int selectedArrivalShell = -1;
    private double positionThresholdExponent = -12.0;
    private double gradientThresholdExponent = -12.0;
    private double speedSpreadTolerance = ProbeArrivalMeasurement.DEFAULT_SPEED_SPREAD_TOLERANCE_PERCENT;
    private final StringBuffer toleranceBuffer = new StringBuffer();
    private double controlledWavelength=8;
    private final StringBuffer wavelengthBuffer=new StringBuffer();
    private boolean inspectorPositionedForSession;
    private boolean diamondViewInitialized;

    public FrameIsotropy(NuklearModel model) {
        super(model);
        resizeCapability = ResizeCapability.enabled(MIN_WIDTH, MIN_HEIGHT, 12);
        windowOptions &= ~Nuklear.NK_WINDOW_NO_SCROLLBAR;
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
        if(project.scene.getLattice().isMechanicsOnly()) {
            if(!diamondViewInitialized) {
                selectedTab=4;diamondViewInitialized=true;
                Nuklear.nk_window_set_scroll(ctx,0,0);
            }
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),2);
            if(nk_button_label(ctx,"Diamond transport")) {selectedTab=4;Nuklear.nk_window_set_scroll(ctx,0,0);}
            if(nk_button_label(ctx,"Probe measurements")) {selectedTab=0;Nuklear.nk_window_set_scroll(ctx,0,0);}
            if(selectedTab==4) diamondTransportSection(ctx);
            else diamondProbeSection(ctx);
            return;
        } else if(diamondViewInitialized) {diamondViewInitialized=false;selectedTab=0;}
        Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 2);
        boolean hasGeneratedProbes = project.getProbeDefinitions().stream()
                .anyMatch(ProbeDefinition::isGenerated);
        if (nk_button_label(ctx, hasGeneratedProbes ? "Edit Probes..." : "Add Probes...")) {
            nuklearModel.showFrame(NuklearModel.Frame.addProbes);
        }
        Nuklear.nk_label(ctx, "Probe center is the lattice geometric center", Nuklear.NK_TEXT_LEFT);

        Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 4);
        if (Nuklear.nk_button_label(ctx, "Gradient visualization")) selectedTab = 0;
        if (Nuklear.nk_button_label(ctx, "Calibration")) selectedTab = 1;
        if (Nuklear.nk_button_label(ctx, "Gradient Imbalance Comparison")) selectedTab = 2;
        if (Nuklear.nk_button_label(ctx, "Propagation Arrival")) selectedTab = 3;

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
            case 3 -> "Propagation Arrival content";
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
        if (selectedTab == 3) {
            arrivalSection(ctx);
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

    private void arrivalSection(NkContext ctx) {
        section(ctx, "Propagation arrival by probe shell");
        if(project.scene.getLattice().isMechanicsOnly()) {
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*3,1);
            Nuklear.nk_label_wrap(ctx,"Bulk runs stop before reflections. Thin XY slabs include fixed surface effects and record 200 updates; only in-plane response is assessed. Arrival thresholds/interpolation need convergence checks; this is not an isotropy proof.");
            if(project.scene.getLattice().isDiamondTransportEnabled())return;
        }
        List<Integer> shells = ProbeArrivalMeasurement.availableShells(project.scene.getProbes());
        if (shells.isEmpty()) {
            Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 1);
            Nuklear.nk_label_wrap(ctx,
                    "No generated probe shell is available. Add passive probes first.");
            return;
        }
        if (!shells.contains(selectedArrivalShell)) selectedArrivalShell = shells.getFirst();
        if(project.scene.getLattice().isMechanicsOnly()) {
            final int shellIndex=selectedArrivalShell;
            boolean planar=project.scene.getProbes().stream().anyMatch(p->p.getDefinition().getPlacement()!=null
                && p.getDefinition().getPlacement().measurementSpace().equals("Diamond physical XY")
                && p.getDefinition().getPlacement().targets().stream().anyMatch(t->t.shell()==shellIndex));
            scalar(ctx,"Direction coverage",planar?"XY only: 8 axes/diagonal directions":"XYZ: 26 directions");
        }

        Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 2);
        Nuklear.nk_label(ctx, "Shell", Nuklear.NK_TEXT_LEFT);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            String[] options = shells.stream()
                    .map(shell -> "Shell " + (shell + 1))
                    .toArray(String[]::new);
            String current = "Shell " + (selectedArrivalShell + 1);
            String selected = nk_combo_options(ctx, stack, options, current);
            int option = java.util.Arrays.asList(options).indexOf(selected);
            if (option >= 0 && selectedArrivalShell != shells.get(option)) {
                selectedArrivalShell = shells.get(option);
                project.scene.getProbeArrivalMeasurement().clear();
            }
        }

        scalar(ctx, "Position change threshold", "10^" + (int) positionThresholdExponent);
        positionThresholdExponent = Math.rint(nk_slider(
                ctx, -18.0, positionThresholdExponent, 0.0, 1.0));
        scalar(ctx, "Gradient change threshold", "10^" + (int) gradientThresholdExponent);
        gradientThresholdExponent = Math.rint(nk_slider(
                ctx, -18.0, gradientThresholdExponent, 0.0, 1.0));

        ProbeArrivalMeasurement measurement = project.scene.getProbeArrivalMeasurement();
        if(project.scene.getLattice().isMechanicsOnly()) {
            try(MemoryStack stack=MemoryStack.stackPush()) {
                double entered=nk_label_edit(ctx,stack,"Speed spread tolerance (%)",toleranceBuffer,speedSpreadTolerance);
                if(Double.isFinite(entered) && entered>0) speedSpreadTolerance=entered;
                double wavelength=nk_label_edit(ctx,stack,"Packet wavelength (bond lengths)",wavelengthBuffer,controlledWavelength);
                if(Double.isFinite(wavelength) && wavelength>=2)controlledWavelength=wavelength;
            }
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*2,1);
            Nuklear.nk_label_wrap(ctx,"Tolerance defaults to 1%. Detection thresholds above set arrival sensitivity; they are not the acceptance tolerance.");
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
            if(Nuklear.nk_button_label(ctx,"Run controlled centered packet (amplitude 0.001)")) {
                project.scene.getLattice().getDisturbance().requestProbeRun(controlledWavelength,selectedArrivalShell,
                    Math.pow(10,positionThresholdExponent),Math.pow(10,gradientThresholdExponent));
                project.getRemModel().startSimulation();
            }
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*3,1);
            Nuklear.nk_label_wrap(ctx,String.format(Locale.ROOT,"Source support: %.1f bonds. Suggested shell radii: %.1f / %.1f. Regenerate probes after changing wavelength. Full 3D tests need space in X, Y and Z; a thin slab tests different boundary conditions.",1.5*controlledWavelength,1.5*controlledWavelength+4,1.5*controlledWavelength+8));
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
            if(Nuklear.nk_button_label(ctx,"Change lattice dimensions..."))nuklearModel.showFrame(NuklearModel.Frame.dimension);
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*3,1);
            Nuklear.nk_label_wrap(ctx,project.scene.getLattice().getDisturbance().summary());
        }
        Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 2);
        if (Nuklear.nk_button_label(ctx, "Start / Reset Measurement")) {
            if(project.scene.getLattice().isMechanicsOnly())
                project.scene.requestDiamondProbeArrival(selectedArrivalShell,Math.pow(10.0,positionThresholdExponent),Math.pow(10.0,gradientThresholdExponent));
            else measurement.start(List.copyOf(project.scene.getProbes()), selectedArrivalShell,
                    Math.pow(10.0, positionThresholdExponent),
                    Math.pow(10.0, gradientThresholdExponent));
        }
        if (Nuklear.nk_button_label(ctx, "Clear")) measurement.clear();

        ProbeArrivalMeasurement.Snapshot snapshot = measurement.snapshot();
        scalar(ctx, "Time unit", "simulation updates since Start");
        scalar(ctx, "Status", arrivalStatus(snapshot));
        scalar(ctx, "Elapsed updates", Long.toString(snapshot.elapsedUpdates()));
        if(project.scene.getLattice().isMechanicsOnly()) {
            double positionSpread=ProbeArrivalMeasurement.equalRadiusApparentSpread(snapshot.results(),false);
            double gradientSpread=ProbeArrivalMeasurement.equalRadiusApparentSpread(snapshot.results(),true);
            scalar(ctx,"Apparent position-speed spread",Double.isFinite(positionSpread)?String.format(Locale.ROOT,"%.3f%%",positionSpread):"Incomplete");
            scalar(ctx,"Apparent gradient-speed spread",Double.isFinite(gradientSpread)?String.format(Locale.ROOT,"%.3f%%",gradientSpread):"Incomplete");
            scalar(ctx,"Position tolerance check",ProbeArrivalMeasurement.toleranceResult(positionSpread,speedSpreadTolerance));
            scalar(ctx,"Gradient tolerance check",ProbeArrivalMeasurement.toleranceResult(gradientSpread,speedSpreadTolerance));
            scalar(ctx,"Isotropy verdict","Inconclusive — convergence checks pending");
        }
        Nuklear.nk_layout_row_dynamic(ctx, 42, 1);
        Nuklear.nk_label_wrap(ctx,
                "Position: first n with |x(n) - x(0)| >= threshold. Gradient: first n with "
                + "|g(n) - g(0)| >= threshold. Combined arrival is max(position, gradient).");

        if (!snapshot.hasMeasurement()) {
            Nuklear.nk_layout_row_dynamic(ctx, rowHeightFor(ctx), 1);
            Nuklear.nk_label(ctx,
                    "Choose a shell and press Start / Reset Measurement.", Nuklear.NK_TEXT_LEFT);
            return;
        }

        section(ctx, "Arrival chart — Y: arrival update | X: probe P1 ... PN");
        scalar(ctx, "Legend", "Position = cyan | Gradient = yellow | Combined = red");
        drawArrivalChart(ctx, snapshot);

        section(ctx, "Exact arrival updates");
        Nuklear.nk_layout_row_begin(ctx, Nuklear.NK_DYNAMIC, rowHeightFor(ctx), 6);
        Nuklear.nk_layout_row_push(ctx, 0.07f);
        Nuklear.nk_label(ctx, "Probe", Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_layout_row_push(ctx, 0.20f);
        Nuklear.nk_label(ctx, "ID", Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_layout_row_push(ctx, 0.31f);
        Nuklear.nk_label(ctx, "Represented ray", Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_layout_row_push(ctx, 0.14f);
        Nuklear.nk_label(ctx, "Position", Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_layout_row_push(ctx, 0.14f);
        Nuklear.nk_label(ctx, "Gradient", Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_layout_row_push(ctx, 0.14f);
        Nuklear.nk_label(ctx, "Combined", Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_layout_row_end(ctx);
        for (ProbeArrivalMeasurement.Result result : snapshot.results()) {
            Nuklear.nk_layout_row_begin(ctx, Nuklear.NK_DYNAMIC, rowHeightFor(ctx), 6);
            Nuklear.nk_layout_row_push(ctx, 0.07f);
            Nuklear.nk_label(ctx, "P" + result.ordinal(), Nuklear.NK_TEXT_LEFT);
            Nuklear.nk_layout_row_push(ctx, 0.20f);
            Nuklear.nk_label(ctx, result.probeId(), Nuklear.NK_TEXT_LEFT);
            Nuklear.nk_layout_row_push(ctx, 0.31f);
            Nuklear.nk_label(ctx, result.representedRays(), Nuklear.NK_TEXT_LEFT);
            Nuklear.nk_layout_row_push(ctx, 0.14f);
            Nuklear.nk_label(ctx, arrivalValue(result.positionArrivalUpdate()), Nuklear.NK_TEXT_RIGHT);
            Nuklear.nk_layout_row_push(ctx, 0.14f);
            Nuklear.nk_label(ctx, arrivalValue(result.gradientArrivalUpdate()), Nuklear.NK_TEXT_RIGHT);
            Nuklear.nk_layout_row_push(ctx, 0.14f);
            Nuklear.nk_label(ctx, arrivalValue(result.combinedArrivalUpdate()), Nuklear.NK_TEXT_RIGHT);
            Nuklear.nk_layout_row_end(ctx);
        }
    }

    /** Draws three first-change line series with numeric probe and update axes. */
    private void drawArrivalChart(NkContext ctx, ProbeArrivalMeasurement.Snapshot snapshot) {
        Nuklear.nk_layout_row_dynamic(ctx, 280, 1);
        NkRect bounds = NkRect.create();
        if (Nuklear.nk_widget(bounds, ctx) == Nuklear.NK_WIDGET_INVALID) return;
        NkCommandBuffer canvas = Nuklear.nk_window_get_canvas(ctx);
        NkRect plot = NkRect.create().set(bounds.x() + 52, bounds.y() + 10,
                Math.max(1, bounds.w() - 64), Math.max(1, bounds.h() - 42));
        NkRect priorClip = NkRect.create().set(canvas.clip());
        NkRect chartClip = intersect(bounds, priorClip, NkRect.create());
        Nuklear.nk_push_scissor(canvas, chartClip);
        Nuklear.nk_fill_rect(canvas, bounds, 0, Theme.colorChart);

        ArrivalAxisRange yRange = arrivalAxisRange(snapshot);
        int yTicks = yRange.tickCount();
        NkRect labelBounds = NkRect.create();
        for (int tick = 0; tick <= yTicks; tick++) {
            long value = yRange.minimum() + yRange.tickStep() * tick;
            float y = arrivalY(plot, value, yRange);
            Nuklear.nk_stroke_line(canvas, plot.x(), y, plot.x() + plot.w(), y,
                    tick == 0 ? 2.0f : 1.0f, tick == 0 ? Theme.grey12 : Theme.grey05);
            labelBounds.set(bounds.x() + 2, y - 8, 46, 16);
            Nuklear.nk_draw_text(canvas, labelBounds, Long.toString(value),
                    nuklearModel.getDefaultFont(), Theme.colorChart, Theme.grey12);
        }

        int count = snapshot.results().size();
        int xStride = arrivalProbeTickStride(count);
        for (int index = 0; index < count; index++) {
            if (index % xStride != 0 && index != count - 1) continue;
            float x = arrivalX(plot, index, count);
            Nuklear.nk_stroke_line(canvas, x, plot.y() + plot.h(), x,
                    plot.y() + plot.h() + 4, 1.0f, Theme.grey12);
            labelBounds.set(x - 12, plot.y() + plot.h() + 7, 24, 16);
            Nuklear.nk_draw_text(canvas, labelBounds,
                    Integer.toString(snapshot.results().get(index).ordinal()),
                    nuklearModel.getDefaultFont(), Theme.colorChart, Theme.grey12);
        }

        drawArrivalSeries(canvas, plot, snapshot, yRange, 0, Theme.blue4);
        drawArrivalSeries(canvas, plot, snapshot, yRange, 1, Theme.yellow);
        drawArrivalSeries(canvas, plot, snapshot, yRange, 2, Theme.red);
        Nuklear.nk_push_scissor(canvas, priorClip);
    }

    private static void drawArrivalSeries(
            NkCommandBuffer canvas, NkRect plot, ProbeArrivalMeasurement.Snapshot snapshot,
            ArrivalAxisRange yRange, int series, org.lwjgl.nuklear.NkColor color) {
        boolean hasPrevious = false;
        float previousX = 0.0f;
        float previousY = 0.0f;
        NkRect marker = NkRect.create();
        int count = snapshot.results().size();
        for (int index = 0; index < count; index++) {
            long arrival = arrivalValue(snapshot.results().get(index), series);
            if (arrival < 0) {
                hasPrevious = false;
                continue;
            }
            float x = arrivalX(plot, index, count);
            float y = arrivalY(plot, arrival, yRange);
            if (hasPrevious) {
                Nuklear.nk_stroke_line(canvas, previousX, previousY, x, y, 2.0f, color);
            }
            marker.set(x - 3, y - 3, 6, 6);
            Nuklear.nk_fill_circle(canvas, marker, color);
            previousX = x;
            previousY = y;
            hasPrevious = true;
        }
    }

    private static long arrivalValue(ProbeArrivalMeasurement.Result result, int series) {
        return switch (series) {
            case 0 -> result.positionArrivalUpdate();
            case 1 -> result.gradientArrivalUpdate();
            default -> result.combinedArrivalUpdate();
        };
    }

    record ArrivalAxisRange(long minimum, long maximum, long tickStep) {
        ArrivalAxisRange {
            if (minimum < 0 || maximum <= minimum || tickStep <= 0) {
                throw new IllegalArgumentException("Invalid arrival-axis range.");
            }
        }

        int tickCount() {
            return Math.toIntExact((maximum - minimum) / tickStep);
        }
    }

    static ArrivalAxisRange arrivalAxisRange(ProbeArrivalMeasurement.Snapshot snapshot) {
        long firstArrival = Long.MAX_VALUE;
        long lastArrival = Long.MIN_VALUE;
        for (ProbeArrivalMeasurement.Result result : snapshot.results()) {
            for (long arrival : new long[]{result.positionArrivalUpdate(),
                    result.gradientArrivalUpdate(), result.combinedArrivalUpdate()}) {
                if (arrival < 0) continue;
                firstArrival = Math.min(firstArrival, arrival);
                lastArrival = Math.max(lastArrival, arrival);
            }
        }
        if (firstArrival == Long.MAX_VALUE) return new ArrivalAxisRange(0, 4, 1);

        long spread = lastArrival - firstArrival;
        long padding = Math.max(1, (long) Math.ceil(Math.max(1, spread) * 0.1));
        long paddedMinimum = Math.max(0, firstArrival - padding);
        long paddedMaximum = lastArrival + padding;
        long tickStep = Math.max(1, (paddedMaximum - paddedMinimum + 3) / 4);
        long minimum = paddedMinimum / tickStep * tickStep;
        long maximum = (paddedMaximum + tickStep - 1) / tickStep * tickStep;
        return new ArrivalAxisRange(minimum, maximum, tickStep);
    }

    static int arrivalProbeTickStride(int probeCount) {
        return Math.max(1, (probeCount + 19) / 20);
    }

    private static float arrivalX(NkRect plot, int probeIndex, int probeCount) {
        if (probeCount <= 1) return plot.x() + plot.w() * 0.5f;
        return plot.x() + plot.w() * probeIndex / (probeCount - 1.0f);
    }

    private static float arrivalY(NkRect plot, long arrival, ArrivalAxisRange range) {
        double normalized = (arrival - range.minimum())
                / (double) (range.maximum() - range.minimum());
        return (float) (plot.y() + plot.h() * (1.0 - normalized));
    }

    private static NkRect intersect(NkRect first, NkRect second, NkRect destination) {
        float left = Math.max(first.x(), second.x());
        float top = Math.max(first.y(), second.y());
        float right = Math.min(first.x() + first.w(), second.x() + second.w());
        float bottom = Math.min(first.y() + first.h(), second.y() + second.h());
        return destination.set(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    private static String arrivalStatus(ProbeArrivalMeasurement.Snapshot snapshot) {
        if (!snapshot.hasMeasurement()) return "Not started";
        if (snapshot.complete()) return "Complete";
        return snapshot.running() ? "Measuring" : "Stopped";
    }

    private static String arrivalValue(long update) {
        return update < 0 ? "Waiting" : Long.toString(update);
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

    private int diamondProbeTab;
    private void diamondProbeSection(NkContext ctx) {
        var scene=project.scene;
        section(ctx,"Diamond physical probe sampling");
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
        if(nk_button_label(ctx,"Add / replace diamond probe shells..."))nuklearModel.showFrame(NuklearModel.Frame.addProbes);
        scalar(ctx,"Probe installation",scene.getDiamondProbeStatus());
        var probes=scene.getProbes().stream().filter(p->p.getDetectorType()==remframework.opengl.model.ProbeDetectorType.DIAMOND_INTERPOLATED).toList();
        scalar(ctx,"Passive probes",Integer.toString(probes.size()));
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*2,1);
        Nuklear.nk_label_wrap(ctx,"Physical coordinates and raw bond imbalance; probes exert no force or pressure. Histories contain per-update samples, not cubic phase averages.");
        if(scene.getLattice().isDiamondTransportEnabled()) {
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*2,1);
            Nuklear.nk_label_wrap(ctx,"Disable transport for mechanical isotropy measurements.");
            if(nk_button_label(ctx,"Pause and disable Identity transport"))scene.getLattice().setDiamondTransportEnabled(false);
        }
        if(probes.isEmpty())return;
        var selected=scene.getSelectedProbe();
        if(selected==null || !probes.contains(selected)) {scene.selectProbe(probes.getFirst());selected=probes.getFirst();}
        try(MemoryStack stack=MemoryStack.stackPush()) {
            String[] ids=probes.stream().map(PassiveObserver::getId).toArray(String[]::new);
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
            String id=nk_combo_options(ctx,stack,ids,selected.getId());
            for(var probe:probes)if(probe.getId().equals(id)){scene.selectProbe(probe);selected=probe;break;}
        }
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),3);
        if(nk_button_label(ctx,"Live / history"))diamondProbeTab=0;
        if(nk_button_label(ctx,"Compare"))diamondProbeTab=1;
        if(nk_button_label(ctx,"Propagation arrival"))diamondProbeTab=2;
        if(diamondProbeTab==2) {arrivalSection(ctx);return;}
        if(diamondProbeTab==1) {
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),2);
            if(nk_button_label(ctx,"Toggle selected in A / B"))scene.toggleComparisonProbe(selected);
            Nuklear.nk_label(ctx,"Assigns the next empty slot",Nuklear.NK_TEXT_LEFT);
            comparisonSection(ctx);return;
        }
        var target=selected.getDefinition().getPlacement().targets().getFirst();
        scalar(ctx,"Shell / radius",(target.shell()+1)+" / "+target.requestedRadius()+" bond lengths");
        diamondVector(ctx,"Requested physical direction",target.requestedDirection());
        diamondVector(ctx,"Equilibrium target",target.requestedCoordinate());
        var observation=selected.getObservation();
        diamondVector(ctx,"Displacement",new Vector3d(observation.position()).sub(target.requestedCoordinate()));
        diamondVector(ctx,"Velocity",observation.velocity());
        diamondVector(ctx,"Raw local imbalance",observation.gradientImbalance());
        if(nk_button_label(ctx,"Open selected probe history"))nuklearModel.showFrame(NuklearModel.Frame.occupancyHistory);
    }
    private void diamondTransportSection(NkContext ctx) {
        var lattice=project.scene.getLattice();
        var transport=remframework.opengl.controller.TransportSystem.getInstance();
        section(ctx,"Local Identity transport — live diagnostics");
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),2);
        if(nk_button_label(ctx,"Preferences / enable transport"))nuklearModel.showFrame(NuklearModel.Frame.preferences);
        if(nk_button_label(ctx,"Add / edit Identities"))nuklearModel.showFrame(NuklearModel.Frame.identity);
        scalar(ctx,"Occupancy pressure",lattice.isDiamondPressureEnabled()?"Enabled":"Disabled");
        scalar(ctx,"Diamond transport",lattice.isDiamondTransportEnabled()?"Enabled":"Disabled");
        scalar(ctx,"Transport System switch",transport.isEnableTransport()?"Enabled":"Disabled");
        scalar(ctx,"Transfer threshold",format(transport.getTransferThreshold()));
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*2,1);
        Nuklear.nk_label_wrap(ctx,"Enable local Identity transport in Preferences. Select an Identity below, then Run. Equal best directions wait; fixed nodes and walls are excluded.");
        for(Occupancy candidate:project.occupancies) {
            if(candidate.getIdentity()==null)continue;
            Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
            if(nk_button_label(ctx,"Select "+candidate.getIdentity().getName()))
                project.getRemModel().setSelectedOccupancy(candidate);
        }
        Occupancy selected=project.getRemModel().getSelectedOccupancy();
        if(selected==null || selected.getIdentity()==null) {
            scalar(ctx,"Selected Identity","None — add one in Identities");
        } else {
            synchronized(selected) {
                section(ctx,selected.getIdentity().getName());
                var m=selected.diamondTransportMeasurement();
                if(selected.getNode()!=null)diamondVector(ctx,"Current node index",selected.getNodeIndex());
                diamondVector(ctx,"Last source imbalance",selected.getGradientImbalance());
                diamondVector(ctx,"Pressure / residual after update",selected.getPressure());
                diamondVector(ctx,"Persistent momentum",selected.getTransportMomentum());
                scalar(ctx,"Transport updates / transfers",m.updates()+" / "+m.transfers());
                Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*2,1);
                Nuklear.nk_label_wrap(ctx,"Result: "+m.status());
                diamondVector(ctx,"Last hop",m.lastHop());
                diamondVector(ctx,"Pressure consumed on last update",m.consumedPressure());
                diamondVector(ctx,"Accumulated hop displacement",m.displacement());
                scalar(ctx,"Travelled path length",format(m.pathLength()));
                scalar(ctx,"Mean hop displacement / update",m.updates()==0?"Not measured":format(m.displacement().length()/m.updates()));
                Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),1);
                if(nk_button_label(ctx,"Reset selected measurements (preserves pressure)"))selected.resetDiamondTransportMeasurement();
            }
        }
        section(ctx,"Isotropy assessment");
        section(ctx,"Directional spread: not measured");
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx)*2,1);
        Nuklear.nk_label_wrap(ctx,"A moving Identity is a transport diagnostic. The 1% isotropy criterion requires equivalent directional experiments and boundary/sampling checks in later steps.");
    }

    private static void diamondVector(NkContext ctx, String label, Vector3d value) {
        section(ctx,label);
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),3);
        Nuklear.nk_label(ctx,format(value.x),Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx,format(value.y),Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx,format(value.z),Nuklear.NK_TEXT_RIGHT);
    }
    private static void diamondVector(NkContext ctx, String label, org.joml.Vector3i value) {
        section(ctx,label);
        Nuklear.nk_layout_row_dynamic(ctx,rowHeightFor(ctx),3);
        Nuklear.nk_label(ctx,Integer.toString(value.x),Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx,Integer.toString(value.y),Nuklear.NK_TEXT_RIGHT);
        Nuklear.nk_label(ctx,Integer.toString(value.z),Nuklear.NK_TEXT_RIGHT);
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
