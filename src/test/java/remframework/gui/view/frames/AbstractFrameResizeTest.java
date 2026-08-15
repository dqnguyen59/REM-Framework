package remframework.gui.view.frames;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.lwjgl.nuklear.NkContext;
import remframework.gui.model.NuklearModel;

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

    @Test
    void resizeCapabilityIsDisabledByDefaultAndCanBeEnabledByAFrame() {
        assertFalse(new TestFrame(false).isResizeCapabilityEnabled());
        assertTrue(new TestFrame(true).isResizeCapabilityEnabled());
    }

    private static final class TestFrame extends AbstractFrame {
        private TestFrame(boolean resizable) {
            super(new NuklearModel());
            if (resizable) resizeCapability = ResizeCapability.enabled(120, 100, 12);
        }

        @Override
        public String getTitle() {
            return "Test frame";
        }

        @Override
        protected void layout(NkContext ctx, int x, int y, int width, int height) {
        }
    }
}
