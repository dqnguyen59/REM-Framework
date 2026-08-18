package remframework.gui.view.frames;

import org.junit.jupiter.api.Test;
import remframework.gui.model.NuklearModel;

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
}
