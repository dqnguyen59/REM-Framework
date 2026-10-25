package remframework.gui.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NuklearModelFrameLifecycleTest {
    @Test
    void absentProgressCanBeClosedRepeatedly() {
        var model = new NuklearModel();
        assertDoesNotThrow(() -> {
            model.closeFrame(NuklearModel.Frame.progress);
            model.closeFrame(NuklearModel.Frame.progress);
        });
        assertEquals(0, model.dialogActiveCount);
        assertTrue(model.getFrames2Remove().isEmpty());
    }

    @Test
    void closingBeforeRenderCancelsQueuedAddition() {
        var model = new NuklearModel();
        var frame = model.showFrame(NuklearModel.Frame.progress);
        assertTrue(model.getFrames2Add().contains(frame));
        model.closeFrame(NuklearModel.Frame.progress);
        assertFalse(model.isFrameVisible(NuklearModel.Frame.progress));
        assertTrue(model.getFrames2Add().isEmpty());
        assertTrue(model.getFrames2Remove().isEmpty());
    }

    @Test
    void visibleFrameIsRemovedOnceAndLosesFocus() {
        var model = new NuklearModel();
        var frame = model.showFrame(NuklearModel.Frame.progress);
        model.getFrames2Add().clear();
        model.addToVisibleFrames(frame);
        model.closeFrame(NuklearModel.Frame.progress);
        model.closeFrame(NuklearModel.Frame.progress);
        assertEquals(1, model.getFrames2Remove().size());
        assertSame(frame, model.getFrames2Remove().get(0));
        assertFalse(model.isAnyFrameFocused());
    }
}
