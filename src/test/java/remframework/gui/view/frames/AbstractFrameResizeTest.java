package remframework.gui.view.frames;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AbstractFrameResizeTest {

    @Test
    void largeCursorDeltaIsAppliedAndClampedToViewport() {
        AbstractFrame.ResizeBounds bounds = AbstractFrame.boundsFromResizeDelta(
                100, 80, 500, 400, 2000, 1500, 520, 420, 1600, 1200);

        assertEquals(1500, bounds.width());
        assertEquals(1120, bounds.height());
    }

    @Test
    void negativeCursorDeltaStopsAtMinimumSize() {
        AbstractFrame.ResizeBounds bounds = AbstractFrame.boundsFromResizeDelta(
                100, 80, 500, 400, -900, -900, 520, 420, 1600, 1200);

        assertEquals(520, bounds.width());
        assertEquals(420, bounds.height());
    }
}
