package remframework.opengl.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RemModelStepTest {
    @Test
    void runningLabelIsPauseAndPausedLabelIsRun() {
        RemModel model = new RemModel();
        assertEquals("Run", model.getBtnSimulatingLabel());
        model.startSimulation();
        assertEquals("Pause", model.getBtnSimulatingLabel());
    }

    @Test
    void pausedStepIsOneShotAndLeavesSimulationPaused() {
        RemModel model = new RemModel();
        model.requestSingleStep();

        assertTrue(model.consumeSingleStepRequest());
        assertFalse(model.isSimulating());
        assertTrue(model.isSimulationUpdateActive());
        assertFalse(model.consumeSingleStepRequest());

        model.finishSingleStep();
        assertFalse(model.isSimulationUpdateActive());
        assertFalse(model.isSimulating());
    }

    @Test
    void fivePausedStepRequestsRemainFiveIndependentOneShotRequests() {
        RemModel model = new RemModel();
        for (int i = 0; i < 5; i++) {
            model.requestSingleStep();
            assertTrue(model.consumeSingleStepRequest());
            model.finishSingleStep();
        }
        assertFalse(model.isSimulating());
        assertFalse(model.isSimulationUpdateActive());
    }
}
