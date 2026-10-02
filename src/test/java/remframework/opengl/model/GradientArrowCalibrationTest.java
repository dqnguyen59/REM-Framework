package remframework.opengl.model;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.opengl.model.entity.Entity;
import remframework.settings.Project;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GradientArrowCalibrationTest {
    @Test
    void calibrationToggleAndDirectionStateHaveThreeChoices() {
        Scene scene = new Scene();
        assertEquals(false, scene.isShowCalibrationArrow());
        scene.setShowCalibrationArrow(true);
        assertEquals(true, scene.isShowCalibrationArrow());
        scene.setCalibrationDirection(Scene.CalibrationDirection.POSITIVE_Y);
        assertEquals(Scene.CalibrationDirection.POSITIVE_Y, scene.getCalibrationDirection());
        scene.setShowCalibrationArrow(false);
        assertEquals(false, scene.isShowCalibrationArrow());
        assertEquals(3, Scene.CalibrationDirection.values().length);
    }

    @Test
    void negativeXCalibrationTipUsesParentedRendererTransform() {
        assertCalibrationEndpoint(new Vector3d(-1, 0, 0));
    }

    @Test
    void positiveYCalibrationTipUsesParentedRendererTransform() {
        assertCalibrationEndpoint(new Vector3d(0, 1, 0));
    }

    @Test
    void positiveZCalibrationTipUsesParentedRendererTransform() {
        assertCalibrationEndpoint(new Vector3d(0, 0, 1));
    }

    private static void assertCalibrationEndpoint(Vector3d direction) {
        Lattice lattice = new Lattice();
        Entity base = lattice.createEntity(null, null, new Vector3d(), new Vector3d(), 1.0);
        base.setRotation(17, 23, 11);
        base.setScale(1.7);
        base.updateMatrix();
        Vector3d anchor = new Vector3d(2, 3, 4);
        Vector3d expectedBase = new Vector3d(anchor).mulPosition(base.getTransformMatrix());
        Vector3d expectedTip = new Vector3d(anchor)
                .add(new Vector3d(direction).mul(GradientArrowGeometry.CALIBRATION_LENGTH))
                .mulPosition(base.getTransformMatrix());
        Entity arrow = lattice.createEntity(base, null, anchor,
                GradientArrowGeometry.rotationForDirection(direction),
                GradientArrowGeometry.CALIBRATION_LENGTH
                        / (GradientArrowGeometry.MESH_LENGTH * Project.getInstance().settings.scale));
        arrow.updateMatrix();
        Vector3d actualBase = new Vector3d(0, 0, 0).mulPosition(arrow.getTransformMatrix());
        Vector3d actualTip = new Vector3d(0, 0, GradientArrowGeometry.MESH_LENGTH)
                .mulPosition(arrow.getTransformMatrix());
        assertEquals(expectedBase.x, actualBase.x, 1e-12);
        assertEquals(expectedBase.y, actualBase.y, 1e-12);
        assertEquals(expectedBase.z, actualBase.z, 1e-12);
        assertEquals(expectedTip.x, actualTip.x, 1e-12);
        assertEquals(expectedTip.y, actualTip.y, 1e-12);
        assertEquals(expectedTip.z, actualTip.z, 1e-12);
    }
}
