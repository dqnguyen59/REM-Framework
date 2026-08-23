package remframework.gui.view.frames;

import org.junit.jupiter.api.Test;
import remframework.gui.model.NuklearModel;
import remframework.opengl.model.ProbeArrivalMeasurement;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameIsotropyLayoutTest {

    @Test
    void sourceSampleLabelNamesCompletedCycleMeasurement() {
        assertEquals("Source samples per completed cycle 1",
                FrameIsotropy.sourceSamplesLabel(0));
        assertEquals("Source samples per completed cycle 8",
                FrameIsotropy.sourceSamplesLabel(7));
    }

    @Test
    void historyButtonIsACompactStatisticsIcon() {
        assertEquals("▥", FrameIsotropy.HISTORY_BUTTON_LABEL);
    }

    @Test
    void historyFrameIsRegisteredAndResizable() {
        NuklearModel model = new NuklearModel();
        assertEquals(FrameOccupancyHistory.class,
                model.createInstance(NuklearModel.Frame.occupancyHistory).getClass());
        assertEquals("Occupancy History",
                model.createInstance(NuklearModel.Frame.occupancyHistory).getTitle());
        assertEquals(true,
                model.createInstance(NuklearModel.Frame.occupancyHistory).isResizeCapabilityEnabled());
    }

    @Test
    void inspectorScrollAndPositionMetricsKeepBottomRowsReachable() {
        int visibleHeight = 420;
        int contentHeight = FrameIsotropy.inspectorContentHeight(40);
        int scrollMaximum = FrameIsotropy.inspectorScrollMaximum(contentHeight, visibleHeight);

        assertTrue(contentHeight > visibleHeight);
        assertEquals(contentHeight, visibleHeight + scrollMaximum);
        assertTrue(FrameIsotropy.inspectorRowHeight() >= 22);

        int[] position = FrameIsotropy.clampInspectorPosition(
                900, 700, 720, 720, 1280, 900, 31);
        assertEquals(560, position[0]);
        assertEquals(180, position[1]);
    }

    @Test
    void arrivalChartRangeAutomaticallyTracksRecordedArrivalValues() {
        ProbeArrivalMeasurement.Snapshot snapshot = new ProbeArrivalMeasurement.Snapshot(
                0, 1.0e-12, 1.0e-12, true, 7,
                List.of(new ProbeArrivalMeasurement.Result(
                        1, "probe-0", "X axis (ray 1)", 3, 9, 0.1, 0.2)));

        assertEquals(new FrameIsotropy.ArrivalAxisRange(2, 10, 2),
                FrameIsotropy.arrivalAxisRange(snapshot));
        assertEquals(1, FrameIsotropy.arrivalProbeTickStride(20));
        assertEquals(2, FrameIsotropy.arrivalProbeTickStride(21));
    }

    @Test
    void arrivalChartUsesStableFallbackAndPaddingForSparseData() {
        ProbeArrivalMeasurement.Snapshot waiting = new ProbeArrivalMeasurement.Snapshot(
                0, 1.0e-12, 1.0e-12, true, 500, List.of(
                new ProbeArrivalMeasurement.Result(1, "probe-0", "X", -1, -1, 0, 0)));
        assertEquals(new FrameIsotropy.ArrivalAxisRange(0, 4, 1),
                FrameIsotropy.arrivalAxisRange(waiting));

        ProbeArrivalMeasurement.Snapshot oneArrival = new ProbeArrivalMeasurement.Snapshot(
                0, 1.0e-12, 1.0e-12, true, 500, List.of(
                new ProbeArrivalMeasurement.Result(1, "probe-0", "X", 100, -1, 0, 0)));
        assertEquals(new FrameIsotropy.ArrivalAxisRange(99, 101, 1),
                FrameIsotropy.arrivalAxisRange(oneArrival));
    }
}
