package remframework.gui.view.frames;

import org.joml.Vector3d;
import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.NkCommandBuffer;
import org.lwjgl.nuklear.NkRect;
import org.lwjgl.nuklear.NkVec2;
import org.lwjgl.nuklear.Nuklear;
import remframework.gui.model.NuklearModel;
import remframework.gui.model.Theme;
import remframework.opengl.model.OccupancyHistory;
import remframework.opengl.model.PassiveObserver;
import remframework.opengl.model.entity.OccupancyHistorySnapshot;
import remframework.settings.Project;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only diagnostic charts for the selected probe's retained Occupancy history. */
public final class FrameOccupancyHistory extends AbstractFrame {
    private static final int DEFAULT_WIDTH = 900;
    private static final int DEFAULT_HEIGHT = 650;
    private static final int MIN_WIDTH = 560;
    private static final int MIN_HEIGHT = 400;
    private static final String[] COMPONENTS = {"X", "Y", "Z"};
    private static final String[] COLOURS = {"red", "green", "blue", "orange", "purple"};
    private static final org.lwjgl.nuklear.NkColor[] CHART_COLOURS =
            {Theme.red, Theme.green, Theme.blue, Theme.yellow, Theme.blue4};

    private final Project project = Project.getInstance();
    private final boolean[] componentEnabled = {true, true, true};
    private final List<Boolean> sourceEnabled = new ArrayList<>();
    private double horizontalZoom = 1.0;

    public FrameOccupancyHistory(NuklearModel model) {
        super(model);
        resizeCapability = ResizeCapability.enabled(MIN_WIDTH, MIN_HEIGHT, 12);
    }

    @Override public String getTitle() { return "Occupancy History"; }

    @Override public void render(long windowHandle, NkContext ctx) {
        super.render(windowHandle, ctx);
        var savedRect = appSettings.getFrameRectMap().get(getTitle());
        int width = savedRect == null ? DEFAULT_WIDTH : Math.max(MIN_WIDTH, Math.round(savedRect.w()));
        int height = savedRect == null ? DEFAULT_HEIGHT : Math.max(MIN_HEIGHT, Math.round(savedRect.h()));
        if (savedRect != null) savedRect.w(width).h(height);
        createLayout(ctx, 60, nuklearModel.getMenuBarHeight() + 60, width, height);
    }

    @Override protected void layout(NkContext ctx, int x, int y, int width, int height) {
        PassiveObserver probe = project.scene.getSelectedProbe();
        if (probe == null) {
            Nuklear.nk_label(ctx, "No selected probe.", Nuklear.NK_TEXT_LEFT);
            return;
        }
        List<OccupancyHistory.Series> series = OccupancyHistory.forProbe(probe);
        int historyLength = historyLength(probe);
        scalar(ctx, "Probe", probe.getId());
        scalar(ctx, "Configured capacity", Integer.toString(historyCapacity(probe)));
        scalar(ctx, "Chart history length", Integer.toString(historyLength));
        scalar(ctx, "Populated slots", Integer.toString(populatedSlots(probe)));
        scalar(ctx, "Current phase index", Integer.toString(phaseIndex(probe)));
        scalar(ctx, "Completed cycles", Long.toString(completedCycles(probe)));
        scalar(ctx, "Cumulative samples", Long.toString(cumulativeSamples(probe)));
        scalar(ctx, "Samples per cycle", Integer.toString(samplesPerCycle(probe)));
        if (probe.getDetectorType() != remframework.opengl.model.ProbeDetectorType.RAW_NODE)
            Nuklear.nk_label_wrap(ctx, "Interpolated estimate — not a node history. Each sample is Σ(weight_i × source_i[s]).");

        Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 4);
        Nuklear.nk_label(ctx, "Components", Nuklear.NK_TEXT_LEFT);
        for (int component = 0; component < 3; component++)
            componentEnabled[component] = nk_check_label(ctx, COMPONENTS[component], componentEnabled[component], 0.7, 0.3);
        Nuklear.nk_layout_row_dynamic(ctx, 24, 1);
        Nuklear.nk_label(ctx, "Horizontal zoom", Nuklear.NK_TEXT_LEFT);
        horizontalZoom = nk_slider(ctx, 0.5, horizontalZoom, 4.0, 0.25);

        Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
        Nuklear.nk_label(ctx, "Legend (source coordinate, interpolation weight, colour)", Nuklear.NK_TEXT_LEFT);
        for (int index = 0; index < series.size(); index++) {
            OccupancyHistory.Series item = series.get(index);
            while (sourceEnabled.size() <= index) sourceEnabled.add(true);
            String weight = Double.isNaN(item.weight()) ? "estimate" : String.format(Locale.ROOT, "weight %.9f", item.weight());
            scalar(ctx, item.label(), "(" + item.coordinate().x + ", " + item.coordinate().y + ", "
                    + item.coordinate().z + "), " + weight + ", " + COLOURS[index % COLOURS.length]);
            sourceEnabled.set(index, nk_check_label(ctx, "Show " + item.label(), sourceEnabled.get(index), 0.7, 0.3));
        }

        NkVec2 content = NkVec2.create();
        Nuklear.nk_window_get_content_region_size(ctx, content);
        int detailsHeight = Math.max(rowHeight * 4, Math.round(content.y()) - rowHeight * 7);
        Nuklear.nk_layout_row_dynamic(ctx, detailsHeight, 1);
        if (!Nuklear.nk_group_begin(ctx, "Occupancy history charts", Nuklear.NK_WINDOW_SCROLL_AUTO_HIDE)) return;
        if (OccupancyHistory.showPhaseReferenceMarkers(historyLength))
            Nuklear.nk_label(ctx, "Phase reference lines: sample 120 | sample 240 (regions 0–119, 120–239, 240–359)", Nuklear.NK_TEXT_LEFT);
        else
            Nuklear.nk_label(ctx, "Phase reference lines appear only for a 360-sample history.", Nuklear.NK_TEXT_LEFT);
        for (int component = 0; component < 3; component++) {
            if (!componentEnabled[component]) continue;
            section(ctx, COMPONENTS[component] + " component — sample index / identity phase");
            for (int source = 0; source < series.size(); source++) {
                OccupancyHistory.Series item = series.get(source);
                if (!sourceEnabled(source, series.size())) continue;
                Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
                Nuklear.nk_label(ctx, item.label() + "  [" + COLOURS[source % COLOURS.length] + "]", Nuklear.NK_TEXT_LEFT);
                List<Vector3d> samples = item.samples();
                drawChart(ctx, samples, historyLength, component, source, chartWidth(width));
                Nuklear.nk_layout_row_dynamic(ctx, rowHeight, 1);
                Nuklear.nk_label_wrap(ctx, "Diagnostics: " + diagnosticText(samples, historyLength, component));
            }
        }
        Nuklear.nk_group_end(ctx);
    }

    /** Pixel width only; the logical sample domain is never derived from this value. */
    private int chartWidth(int frameWidth) {
        int normalWidth = Math.max(520, frameWidth - 40);
        return Math.max(520, (int) (normalWidth * horizontalZoom));
    }

    /** Draws in strict painter order: background/grid, scissored curves, then clip restoration. */
    private static void drawChart(NkContext ctx, List<Vector3d> samples, int length,
                                  int component, int source, int width) {
        Nuklear.nk_layout_row_static(ctx, 82, width, 1);
        NkRect bounds = NkRect.create();
        Nuklear.nk_layout_widget_bounds(ctx, bounds);
        NkCommandBuffer canvas = Nuklear.nk_window_get_canvas(ctx);
        double[] range = componentRange(samples, length, component);
        double[] rawRange = componentRawRange(samples, length, component);
        NkRect plot = NkRect.create().set(bounds.x() + 4, bounds.y() + 4,
                Math.max(1, bounds.w() - 8), Math.max(1, bounds.h() - 8));

        Nuklear.nk_fill_rect(canvas, bounds, 0, Theme.colorChart);
        for (int grid = 1; grid < 4; grid++) {
            float y = plot.y() + plot.h() * grid / 4.0f;
            Nuklear.nk_stroke_line(canvas, plot.x(), y, plot.x() + plot.w(), y,
                    1.0f, Theme.grey05);
        }
        if (OccupancyHistory.showPhaseReferenceMarkers(length)) {
            for (int sample : new int[]{120, 240}) {
                float x = xForSample(plot, sample, length);
                Nuklear.nk_stroke_line(canvas, x, plot.y(), x, plot.y() + plot.h(),
                        1.0f, Theme.grey12);
            }
        }

        NkRect priorClip = NkRect.create().set(canvas.clip());
        // The scissor is exactly the reserved chart rectangle. Plot padding remains
        // inside it, so no curve, marker, or grid stroke can escape this chart.
        Nuklear.nk_push_scissor(canvas, bounds);
        int segments = 0;
        int finite = 0;
        float previousX = 0;
        float previousY = 0;
        boolean havePrevious = false;
        for (int sample = 0; sample < length; sample++) {
            double value = componentValue(samples.get(sample), component);
            if (!Double.isFinite(value)) {
                havePrevious = false;
                continue;
            }
            finite++;
            float currentX = xForSample(plot, sample, length);
            float currentY = yForValue(plot, value, range);
            if (havePrevious) {
                Nuklear.nk_stroke_line(canvas, previousX, previousY, currentX, currentY,
                        2.0f, CHART_COLOURS[source % CHART_COLOURS.length]);
                segments++;
            }
            previousX = currentX;
            previousY = currentY;
            havePrevious = true;
        }
        // A constant series is deliberately rendered as a centred horizontal line.
        if (length > 0 && finite == length && rawRange[0] == rawRange[1]) {
            float centerY = plot.y() + plot.h() * 0.5f;
            Nuklear.nk_stroke_line(canvas, plot.x(), centerY, plot.x() + plot.w(), centerY,
                    2.0f, CHART_COLOURS[source % CHART_COLOURS.length]);
            segments = Math.max(0, length - 1);
        }
        Nuklear.nk_push_scissor(canvas, priorClip);
    }

    private static float xForSample(NkRect plot, int sample, int visibleSampleCount) {
        // Fixed logical domain: visibleStart = 0 and visibleSampleCount = historyLength.
        int visibleStart = 0;
        return plot.x() + ((sample - visibleStart) / (float) visibleSampleCount) * plot.w();
    }

    private static float yForValue(NkRect plot, double value, double[] range) {
        if (range[1] == range[0]) return plot.y() + plot.h() * 0.5f;
        double normalized = (value - range[0]) / (range[1] - range[0]);
        return (float) (plot.y() + plot.h() * (1.0 - normalized));
    }

    private static String diagnosticText(List<Vector3d> samples, int length, int component) {
        int finite = 0;
        int segments = 0;
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        boolean previousFinite = false;
        for (int sample = 0; sample < length; sample++) {
            double value = componentValue(samples.get(sample), component);
            boolean isFinite = Double.isFinite(value);
            if (isFinite) {
                finite++;
                minimum = Math.min(minimum, value);
                maximum = Math.max(maximum, value);
                if (previousFinite) segments++;
            }
            previousFinite = isFinite;
        }
        String first = length == 0 ? "n/a" : formatValue(componentValue(samples.getFirst(), component));
        String middle = length == 0 ? "n/a" : formatValue(componentValue(samples.get(length / 2), component));
        String last = length == 0 ? "n/a" : formatValue(componentValue(samples.get(length - 1), component));
        return "samples=" + length + ", finite=" + finite + ", min=" + formatValue(minimum)
                + ", max=" + formatValue(maximum) + ", first=" + first + ", middle=" + middle
                + ", last=" + last + ", segments drawn=" + segments;
    }

    private static String formatValue(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.6g", value) : "n/a";
    }

    private static double[] componentRange(List<Vector3d> samples, int length, int component) {
        double[] raw = componentRawRange(samples, length, component);
        double minimum = raw[0];
        double maximum = raw[1];
        if (!Double.isFinite(minimum)) return new double[]{-1.0, 1.0};
        if (minimum == maximum) {
            double padding = Math.max(1.0, Math.abs(minimum) * 0.1);
            return new double[]{minimum - padding, maximum + padding};
        }
        double padding = (maximum - minimum) * 0.05;
        return new double[]{minimum - padding, maximum + padding};
    }

    private static double[] componentRawRange(List<Vector3d> samples, int length, int component) {
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (int sample = 0; sample < length; sample++) {
            double value = componentValue(samples.get(sample), component);
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        return new double[]{minimum, maximum};
    }

    private boolean sourceEnabled(int index, int sourceCount) {
        return index < sourceEnabled.size() && sourceEnabled.get(index);
    }

    private static double componentValue(Vector3d value, int component) {
        return component == 0 ? value.x : component == 1 ? value.y : value.z;
    }

    static int historyLength(PassiveObserver probe) {
        return historyCapacity(probe);
    }

    static int samplesPerCycle(PassiveObserver probe) {
        return snapshots(probe).stream().mapToInt(OccupancyHistorySnapshot::configuredSamplesPerCycle)
                .min().orElse(0);
    }

    private static int historyCapacity(PassiveObserver probe) {
        return snapshots(probe).stream().mapToInt(OccupancyHistorySnapshot::capacity).min().orElse(0);
    }

    private static int populatedSlots(PassiveObserver probe) {
        return snapshots(probe).stream().mapToInt(OccupancyHistorySnapshot::populatedSlots).min().orElse(0);
    }

    private static int phaseIndex(PassiveObserver probe) {
        return snapshots(probe).stream().mapToInt(OccupancyHistorySnapshot::phaseIndex).min().orElse(0);
    }

    private static long completedCycles(PassiveObserver probe) {
        return snapshots(probe).stream().mapToLong(OccupancyHistorySnapshot::completedCycleCount).min().orElse(0);
    }

    private static long cumulativeSamples(PassiveObserver probe) {
        return snapshots(probe).stream().mapToLong(OccupancyHistorySnapshot::cumulativeSampleCount).min().orElse(0);
    }

    private static List<OccupancyHistorySnapshot> snapshots(PassiveObserver probe) {
        return probe.getOccupancies().stream().map(o -> o.getHistorySnapshotForAudit()).toList();
    }

    private static void section(NkContext ctx, String label) {
        Nuklear.nk_layout_row_dynamic(ctx, 24, 1);
        Nuklear.nk_label(ctx, label, Nuklear.NK_TEXT_LEFT);
    }

    private static void scalar(NkContext ctx, String label, String value) {
        Nuklear.nk_layout_row_dynamic(ctx, 24, 2);
        Nuklear.nk_label(ctx, label, Nuklear.NK_TEXT_LEFT);
        Nuklear.nk_label(ctx, value, Nuklear.NK_TEXT_RIGHT);
    }
}
