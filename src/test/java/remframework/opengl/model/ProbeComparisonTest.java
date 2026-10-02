package remframework.opengl.model;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import remframework.opengl.model.entity.Entity;
import remframework.settings.Project;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProbeComparisonTest {
    private static final ProbeDefinition DEFINITION = new ProbeDefinition(
            "probe-test", new Vector3i(1, 2, 3), new org.joml.Vector4f(1.0f),
            new ProbeDefinition.Placement(List.of(new ProbeDefinition.Target(
                    0, 0, "X axis", 7, new Vector3d(), new Vector3d(3, 4, 0),
                    7.0, 0.0, new Vector3i(7, 0, 0), 0.0, 7)), 0, "XYZ"));

    @Test
    void ctrlStyleAssignmentUsesAThenBAndClickingSlotRemovesIt() {
        ProbeComparison comparison = new ProbeComparison();
        PassiveObserver first = observer(new Vector3d(1, 0, 0));
        PassiveObserver second = observer(new Vector3d(0, 1, 0));

        assertTrue(comparison.toggle(first));
        assertSame(first, comparison.get(ProbeComparison.Slot.A));
        assertTrue(comparison.toggle(second));
        assertSame(second, comparison.get(ProbeComparison.Slot.B));
        assertTrue(comparison.toggle(first));
        assertNull(comparison.get(ProbeComparison.Slot.A));
        assertTrue(comparison.toggle(second));
        assertNull(comparison.get(ProbeComparison.Slot.B));
    }

    @Test
    void calculationUsesLiveGradientAndRequestedDirection() {
        PassiveObserver observer = observer(new Vector3d(3, 4, 0));
        ProbeComparison.Measurement measurement = ProbeComparison.calculate(observer);

        assertEquals(new Vector3d(3, 4, 0), measurement.gradient());
        assertEquals(0.6, measurement.requestedDirection().x, 1.0e-12);
        assertEquals(0.8, measurement.requestedDirection().y, 1.0e-12);
        assertEquals(0.0, measurement.requestedDirection().z, 1.0e-12);
        assertEquals(7, measurement.requestedRadius());
        assertEquals(5.0, measurement.radial(), 1.0e-12);
        assertEquals(0.0, measurement.sideways(), 1.0e-12);
    }

    @Test
    void clearRemovesBothSlots() {
        ProbeComparison comparison = new ProbeComparison();
        comparison.toggle(observer(new Vector3d(1, 0, 0)));
        comparison.toggle(observer(new Vector3d(0, 1, 0)));
        comparison.clear();
        assertNull(comparison.get(ProbeComparison.Slot.A));
        assertNull(comparison.get(ProbeComparison.Slot.B));
    }

    @Test
    void sceneResetClearsComparisonSlotsAndProbeMarkers() {
        Scene scene = Project.getInstance().scene;
        scene.clearProbes();
        PassiveObserver first = observer(new Vector3d(1, 0, 0));
        PassiveObserver second = observer(new Vector3d(0, 1, 0));
        scene.addProbe(first);
        scene.addProbe(second);
        assertTrue(scene.toggleComparisonProbe(first));
        assertTrue(scene.toggleComparisonProbe(second));

        scene.clearProbes();

        assertNull(scene.getComparison().get(ProbeComparison.Slot.A));
        assertNull(scene.getComparison().get(ProbeComparison.Slot.B));
        assertTrue(scene.getProbes().isEmpty());
    }

    private static PassiveObserver observer(Vector3d gradient) {
        return new PassiveObserver() {
            @Override public String getId() { return DEFINITION.getId(); }
            @Override public ProbeDetectorType getDetectorType() { return DEFINITION.getDetectorType(); }
            @Override public ProbeDefinition getDefinition() { return DEFINITION; }
            @Override public Entity getMarker() { return null; }
            @Override public Observation getObservation() {
                return new Observation(new Vector3d(), new Vector3d(), new Vector3d(), gradient);
            }
            @Override public void setSelected(boolean selected) { }
            @Override public void setHovered(boolean hovered) { }
            @Override public void setComparisonSlot(int slot) { }
            @Override public void updateMarker() { }
        };
    }
}
