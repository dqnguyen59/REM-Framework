package remframework.gui.view.frames;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrameIsotropyLayoutTest {

    @Test
    void sourceSampleLabelNamesCompletedCycleMeasurement() {
        assertEquals("Source samples per completed cycle 1",
                FrameIsotropy.sourceSamplesLabel(0));
        assertEquals("Source samples per completed cycle 8",
                FrameIsotropy.sourceSamplesLabel(7));
    }
}
