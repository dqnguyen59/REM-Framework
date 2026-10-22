package remframework.gui.view.frames.dialogs;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.Nuklear;
import org.lwjgl.system.MemoryStack;
import remframework.gui.model.NuklearModel;
import remframework.gui.view.frames.AbstractFrame;
import remframework.opengl.model.PassiveObserver;
import remframework.opengl.model.Probe;
import remframework.opengl.model.ProbeDefinition;
import remframework.opengl.model.ProbeDetectorType;
import remframework.opengl.model.ProbePlacement;
import remframework.opengl.model.Scene;
import remframework.opengl.model.entity.Node;
import remframework.settings.Project;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Non-modal workspace for creating passive, geometrically positioned probe definitions. */
public final class FrameAddProbes extends AbstractFrame {
    private static int retainedFirstShellRadius = 50;
    private static int retainedShellDistance = 10;
    private static int retainedNumberOfShells = 10;
    private static int retained2DProbesPerShell = 9;
    private static int retained3DProbesPerPlanePerShell = 9;
    private static int retainedRegion;
    private static boolean retainedInterpolatedBinding = true;
    private static Vector4f retainedDisplayColor = new Vector4f(0.2f, 0.8f, 1.0f, 1.0f);

    private final Project project = Project.getInstance();
    private final Scene scene = project.scene;
    private Node center;
    private boolean threeDimensional;
    private Vector3i centerIndex;
    private final boolean editing;
    private final List<ProbeDefinition> existingGeneratedDefinitions;

    private int firstShellRadius = retainedFirstShellRadius;
    private int shellDistance = retainedShellDistance;
    private int numberOfShells = retainedNumberOfShells;
    private int probesPerPlane;
    private int region = retainedRegion;
    private boolean interpolatedBinding = retainedInterpolatedBinding;
    private Vector4f displayColor = new Vector4f(retainedDisplayColor);

    private final StringBuffer firstShellRadiusBuffer = new StringBuffer();
    private final StringBuffer shellDistanceBuffer = new StringBuffer();
    private final StringBuffer numberOfShellsBuffer = new StringBuffer();
    private final StringBuffer probesPerPlaneBuffer = new StringBuffer();

    private List<ProbeDefinition> previewDefinitions = List.of();
    private List<ProbePlacement.ProbeReport> probeReports = List.of();
    private String previewStatus = "Preview not generated.";
    private String activeProbeSpace = "";
    private boolean previewValid;
    private boolean replacementPreviewActive;

    /** Opens Edit mode from the committed generated definitions, not from retained controls alone. */
    public FrameAddProbes(NuklearModel model) {
        super(model);
        if(scene.getLattice().isMechanicsOnly()) {
            centerIndex=new Vector3i();center=null;threeDimensional=false;
            probesPerPlane=retained2DProbesPerShell;
            editing=false;existingGeneratedDefinitions=List.of();
            return;
        }
        centerIndex = geometricCenterIndex();
        center = scene.getLattice().getNodeByIndex(centerIndex);
        threeDimensional = project.getDimensionZ() > 1;
        probesPerPlane = threeDimensional ? retained3DProbesPerPlanePerShell
                                          : retained2DProbesPerShell;
        activeProbeSpace = calculateActiveProbeSpace();
        editing = project.getProbeDefinitions().stream().anyMatch(ProbeDefinition::isGenerated);
        existingGeneratedDefinitions = project.getProbeDefinitions().stream()
                .filter(ProbeDefinition::isGenerated).map(definition -> definition.withId(definition.getId()))
                .toList();
        if (editing) {
            interpolatedBinding = existingGeneratedDefinitions.getFirst().getDetectorType()
                    != ProbeDetectorType.RAW_NODE;
            previewDefinitions = existingGeneratedDefinitions;
            probeReports = ProbePlacement.describe(center, existingGeneratedDefinitions);
            int representedTargetCount = existingGeneratedDefinitions.stream()
                    .filter(definition -> definition.getPlacement() != null)
                    .mapToInt(definition -> definition.getPlacement().targets().size()).sum();
            previewStatus = "Existing: requested targets " + representedTargetCount
                    + " | unavailable targets 0 | merged collisions "
                    + (representedTargetCount - existingGeneratedDefinitions.size())
                    + " | unique passive probes " + existingGeneratedDefinitions.size() + ".";
            previewValid = !existingGeneratedDefinitions.isEmpty()
                    && probeReports.size() == representedTargetCount;
            if (!previewValid) previewStatus = "Existing generated probes contain an invalid node reference.";
        }
    }

    @Override
    public String getTitle() {
        return editing ? "Edit Passive Probes" : "Add Passive Probes";
    }

    @Override
    public void render(long windowHandle, NkContext ctx) {
        super.render(windowHandle, ctx);
        createLayoutCentered(ctx, 820, 700);
    }

    @Override
    protected void layout(NkContext ctx, int x, int y, int width, int height) {
        if(scene.getLattice().isMechanicsOnly()) {
            Nuklear.nk_layout_row_dynamic(ctx,rowHeight*3,1);
            Nuklear.nk_label_wrap(ctx,"Cubic probe placement is unavailable for diamond. Open Isotropy and choose Diamond transport for live Identity measurements.");
            Nuklear.nk_layout_row_dynamic(ctx,rowHeight,1);
            if(nk_button_label(ctx,"Open Isotropy")) {nuklearModel.showFrame(NuklearModel.Frame.isotropy);close();}
            if(nk_button_label(ctx,"Close"))close();
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            boolean latticeContextChanged = refreshLatticeContext();
            Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
            if (center == null) {
                Nuklear.nk_label(ctx, "Lattice center is unavailable for this project.",
                                 Nuklear.NK_TEXT_LEFT);
            } else {
                Nuklear.nk_label(ctx, String.format("Center Node: (%d, %d, %d)",
                        centerIndex.x, centerIndex.y, centerIndex.z), Nuklear.NK_TEXT_LEFT);
            }
            if (editing) {
                Nuklear.nk_label(ctx, "Existing generated probes are the edit baseline; the receiver probe is preserved.",
                                 Nuklear.NK_TEXT_LEFT);
            }

            int previousFirstShellRadius = firstShellRadius;
            int previousShellDistance = shellDistance;
            int previousNumberOfShells = numberOfShells;
            int previousProbesPerPlane = probesPerPlane;
            int previousRegion = region;
            boolean previousInterpolatedBinding = interpolatedBinding;
            Vector4f previousColor = new Vector4f(displayColor);

            firstShellRadius = nk_label_edit(ctx, stack, "First Shell Radius:",
                    firstShellRadiusBuffer, firstShellRadius, 0.55, 0.45);
            shellDistance = nk_label_edit(ctx, stack, "Shell Distance:", shellDistanceBuffer,
                    shellDistance, 0.55, 0.45);
            numberOfShells = nk_label_edit(ctx, stack, "Number Of Shells:",
                    numberOfShellsBuffer, numberOfShells, 0.55, 0.45);
            probesPerPlane = nk_label_edit(ctx, stack, threeDimensional
                    ? "Probes Per Plane Per Shell (odd):" : "Probes Per Shell (odd):",
                    probesPerPlaneBuffer, probesPerPlane, 0.55, 0.45);
            String[] bindingModes = {"Raw aligned lattice nodes", "Exact-position interpolated"};
            String selectedBinding = nk_label_Options(ctx, "Binding:", bindingModes,
                    interpolatedBinding ? bindingModes[1] : bindingModes[0], 0.25, 0.75, 2);
            interpolatedBinding = bindingModes[1].equals(selectedBinding);
            String[] regions = regionLabels();
            String selectedRegion = nk_label_Options(ctx, "Region:", regions,
                    regions[Math.max(0, Math.min(region, regions.length - 1))], 0.25, 0.75,
                    Math.min(regions.length, 4));
            region = indexOf(regions, selectedRegion);
            displayColor = nk_combo_color_picker(ctx, stack, "Committed color:", displayColor,
                                                 0.55, 0.45);

            boolean changed = previousFirstShellRadius != firstShellRadius
                    || previousShellDistance != shellDistance
                    || previousNumberOfShells != numberOfShells
                    || previousProbesPerPlane != probesPerPlane
                    || previousRegion != region
                    || previousInterpolatedBinding != interpolatedBinding
                    || !previousColor.equals(displayColor);
            String calculatedActiveSpace = calculateActiveProbeSpace();
            boolean effectiveSpaceChanged = !calculatedActiveSpace.equals(activeProbeSpace);
            if (latticeContextChanged) removeStaleGeneratedProbes(effectiveSpaceChanged);
            if (changed || latticeContextChanged || effectiveSpaceChanged || previewDefinitions.isEmpty()) {
                regeneratePreview();
            }
            retainSettings();

            Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
            Nuklear.nk_label(ctx, previewStatus, Nuklear.NK_TEXT_LEFT);
            Nuklear.nk_label(ctx, activeProbeSpace, Nuklear.NK_TEXT_LEFT);
            Nuklear.nk_layout_row_dynamic(ctx, 260, 1);
            if (Nuklear.nk_group_begin(ctx, "Probe placement diagnostics", 0)) {
                renderProbeTable(ctx);
                Nuklear.nk_group_end(ctx);
            }
            Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
            Nuklear.nk_label(ctx, editing && !replacementPreviewActive
                    ? "Committed generated probes are shown as the current edit baseline."
                    : "Temporary replacement markers use a lighter version of the selected color.",
                    Nuklear.NK_TEXT_LEFT);

            Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 3);
            if (nk_button_label(ctx, "Cancel")) {
                close();
                return;
            }
            if (nk_button_label(ctx, previewValid ? "Apply" : "Apply (unavailable)") && previewValid) {
                apply();
            }
            if (nk_button_label(ctx, "Close")) close();
        }
    }

    private void regeneratePreview() {
        Set<Node> blocked = Collections.newSetFromMap(new IdentityHashMap<>());
        for (PassiveObserver observer : scene.getProbes()) {
            if (observer instanceof Probe probe
                    && (!editing || !probe.getId().startsWith(ProbeDefinition.GENERATED_ID_PREFIX))) {
                blocked.add(probe.getNode());
            }
        }
        for (ProbeDefinition definition : project.getProbeDefinitions()) {
            if (editing && definition.isGenerated()) continue;
            if (definition.getDetectorType() != ProbeDetectorType.RAW_NODE) continue;
            Node node = scene.getLattice().getNodeByIndex(definition.getNodeIndex());
            if (node != null) blocked.add(node);
        }

        ProbePlacement.Result result = ProbePlacement.place(
                center, firstShellRadius, shellDistance, numberOfShells, probesPerPlane,
                region, threeDimensional, previewColor(displayColor),
                blocked, "preview", selectedDetectorType());
        previewDefinitions = result.definitions();
        previewStatus = result.status();
        activeProbeSpace = result.activeProbeSpace().isEmpty()
                ? calculateActiveProbeSpace() : result.activeProbeSpace();
        previewValid = result.valid() && !previewDefinitions.isEmpty();
        probeReports = result.probeReports();
        replacementPreviewActive = previewValid;
        scene.setPreviewDefinitions(previewDefinitions, editing && replacementPreviewActive);
    }

    /** Applies validated generated definitions in memory and closes without project persistence. */
    private void apply() {
        List<ProbeDefinition> committed = new ArrayList<>();
        int nextId = 0;
        for (ProbeDefinition definition : previewDefinitions) {
            committed.add(definition.withId(ProbeDefinition.GENERATED_ID_PREFIX + nextId++)
                    .withColor(displayColor));
        }
        if (!scene.replaceGeneratedProbeDefinitions(committed)) {
            previewStatus = "Unable to apply probes because a referenced lattice node is unavailable.";
            return;
        }
        project.replaceGeneratedProbeDefinitions(committed);
        close();
    }

    /** Renders immutable requested geometry and either raw snapping or interpolation diagnostics. */
    private void renderProbeTable(NkContext ctx) {
        Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
        Nuklear.nk_label(ctx, "Shell | Ray / Plane | Detector | Requested radius | Exact requested coordinate | Requested direction | Realized radius | Angular deviation from ideal ray | Realized direction | Angular deviation from first-shell direction | Graph distance (diagnostic) | Selected coordinate / sources",
                Nuklear.NK_TEXT_LEFT);
        for (ProbePlacement.ProbeReport report : probeReports) {
            Vector3i coordinate = report.coordinate();
            String coordinateText = coordinate == null ? "-"
                    : String.format("(%d,%d,%d)", coordinate.x, coordinate.y, coordinate.z);
            String realizedText = Double.isFinite(report.realizedRadius())
                    ? String.format(java.util.Locale.ROOT, "%.6f", report.realizedRadius())
                    : "N/A";
            String angularText = Double.isFinite(report.angularDeviationDegrees())
                    ? String.format(java.util.Locale.ROOT, "%.3f°", report.angularDeviationDegrees())
                    : "N/A";
            Vector3i realizedDirection = report.realizedDirection();
            String realizedDirectionText = realizedDirection == null ? "-"
                    : String.format("(%d,%d,%d)", realizedDirection.x,
                                    realizedDirection.y, realizedDirection.z);
            String alignmentText = Double.isFinite(report.angularDeviationFromFirstShellDegrees())
                    ? String.format(java.util.Locale.ROOT, "%.3f°",
                                    report.angularDeviationFromFirstShellDegrees())
                    : "N/A";
            String graphDistanceText = report.graphDistance() >= 0
                    ? Integer.toString(report.graphDistance()) : "N/A";
            Vector3d requestedCoordinate = report.requestedCoordinate();
            String requestedCoordinateText = String.format(java.util.Locale.ROOT,
                    "(%.6f,%.6f,%.6f)", requestedCoordinate.x,
                    requestedCoordinate.y, requestedCoordinate.z);
            Vector3d requestedDirection = report.requestedDirection();
            String requestedDirectionText = String.format(java.util.Locale.ROOT,
                    "(%.6f,%.6f,%.6f)", requestedDirection.x,
                    requestedDirection.y, requestedDirection.z);
            String bindingText = report.detectorType() == ProbeDetectorType.RAW_NODE
                    ? coordinateText : report.interpolationSources().stream()
                    .map(source -> String.format(java.util.Locale.ROOT, "(%d,%d,%d) w=%.6f",
                            source.nodeIndex().x, source.nodeIndex().y, source.nodeIndex().z,
                            source.weight()))
                    .reduce((left, right) -> left + "; " + right).orElse("-");
            String resultText;
            if (!report.available()) {
                resultText = " UNAVAILABLE: " + report.unavailableReason();
            } else if (report.merged()) {
                resultText = " MERGED: " + String.join(", ", report.representedTargets());
            } else {
                resultText = "";
            }
            Nuklear.nk_label(ctx, String.format(java.util.Locale.ROOT,
                    "%d | %s | %s | %d | %s | %s | %s | %s | %s | %s | %s | %s%s",
                    report.shell() + 1, report.rayPlane(), report.detectorType(),
                    report.requestedRadius(), requestedCoordinateText, requestedDirectionText,
                    realizedText,
                    angularText, realizedDirectionText, alignmentText,
                    graphDistanceText, bindingText, resultText),
                    Nuklear.NK_TEXT_LEFT);
        }
    }

    /** Removes only transient markers; retained controls remain available on the next open. */
    @Override
    public void close() {
        scene.clearPreviewProbes();
        super.close();
    }

    /** Retains this dialog's controls for the current application session without project persistence. */
    private void retainSettings() {
        retainedFirstShellRadius = firstShellRadius;
        retainedShellDistance = shellDistance;
        retainedNumberOfShells = numberOfShells;
        if (threeDimensional) retained3DProbesPerPlanePerShell = probesPerPlane;
        else retained2DProbesPerShell = probesPerPlane;
        retainedRegion = region;
        retainedInterpolatedBinding = interpolatedBinding;
        retainedDisplayColor = new Vector4f(displayColor);
    }

    private ProbeDetectorType selectedDetectorType() {
        if (!interpolatedBinding) return ProbeDetectorType.RAW_NODE;
        return calculateActiveProbeSpace().contains("XYZ octant")
                ? ProbeDetectorType.INTERPOLATED_3D
                : ProbeDetectorType.INTERPOLATED_2D;
    }

    /** Returns the deterministic geometric center, independent of the mouse-selected node. */
    private Vector3i geometricCenterIndex() {
        return new Vector3i(project.getDimensionX() / 2,
                            project.getDimensionY() / 2,
                            project.getDimensionZ() / 2);
    }

    private String calculateActiveProbeSpace() {
        long largestRadius = firstShellRadius
                + (long) Math.max(0, numberOfShells - 1) * shellDistance;
        if (largestRadius < 1 || largestRadius > Integer.MAX_VALUE) return "";
        return ProbePlacement.activeProbeSpace(center, (int) largestRadius, region,
                threeDimensional, ProbePlacement.DEFAULT_TOLERANCES);
    }

    private boolean refreshLatticeContext() {
        Vector3i currentCenterIndex = geometricCenterIndex();
        Node currentCenter = scene.getLattice().getNodeByIndex(currentCenterIndex);
        boolean currentThreeDimensional = project.getDimensionZ() > 1;
        boolean changed = currentCenter != center || !currentCenterIndex.equals(centerIndex)
                || currentThreeDimensional != threeDimensional;
        if (changed) {
            centerIndex = currentCenterIndex;
            center = currentCenter;
            threeDimensional = currentThreeDimensional;
        }
        return changed;
    }

    private void removeStaleGeneratedProbes(boolean effectiveSpaceChanged) {
        List<ProbeDefinition> generated = project.getProbeDefinitions().stream()
                .filter(ProbeDefinition::isGenerated).toList();
        if (generated.isEmpty()) return;
        List<ProbePlacement.ProbeReport> reports = ProbePlacement.describe(center, generated);
        int targetCount = generated.stream().filter(definition -> definition.getPlacement() != null)
                .mapToInt(definition -> definition.getPlacement().targets().size()).sum();
        boolean staleGeometry = reports.size() != targetCount || reports.stream().anyMatch(report ->
                Math.abs(report.realizedRadius() - report.requestedRadius())
                        > ProbePlacement.DEFAULT_TOLERANCES.maximumRadialError());
        if (effectiveSpaceChanged || staleGeometry) {
            scene.replaceGeneratedProbeDefinitions(List.of());
            project.replaceGeneratedProbeDefinitions(List.of());
            previewDefinitions = List.of();
        }
    }

    private static Vector4f previewColor(Vector4f color) {
        return new Vector4f((color.x + 1.0f) * 0.5f,
                            (color.y + 1.0f) * 0.5f,
                            (color.z + 1.0f) * 0.5f,
                            Math.min(color.w, 0.55f));
    }

    private String[] regionLabels() {
        if (!threeDimensional) {
            return new String[]{"X+ Y+", "X- Y+", "X+ Y-", "X- Y-"};
        }
        return new String[]{"X+ Y+ Z+", "X- Y+ Z+", "X+ Y- Z+", "X- Y- Z+",
                            "X+ Y+ Z-", "X- Y+ Z-", "X+ Y- Z-", "X- Y- Z-"};
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) return i;
        }
        return 0;
    }
}
