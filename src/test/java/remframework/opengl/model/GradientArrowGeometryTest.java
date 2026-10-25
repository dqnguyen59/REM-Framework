package remframework.opengl.model;

import org.joml.Vector3d;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import remframework.opengl.model.entity.Entity;
import remframework.settings.Project;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Cardinal calibration against the native +Z arrow mesh and Scene.createArrows axes. */
class GradientArrowGeometryTest {
    @Test void bothRotationSensesAndNearReversalsFollowTheInputVectorWithoutAnimation() {
        var scene=new Scene();var lattice=scene.getLattice();
        var base=lattice.createEntity(null,null,new Vector3d(),new Vector3d(),1);
        var arrow=lattice.createEntity(base,null,new Vector3d(),new Vector3d(),1);
        // Exercise every quadrant and near-pole vectors in both turning directions.
        for(int sign:new int[]{-1,1})for(int step=0;step<=360;step++) {
            double angle=Math.toRadians(sign*step);
            var direction=new Vector3d(.001*Math.cos(3*angle),Math.sin(angle),Math.cos(angle)).normalize();
            scene.updateArrowFromLocalVector(arrow,new Vector3d(),direction,1,new Vector4f(1));
            var tip=new Vector3d(0,0,GradientArrowGeometry.MESH_LENGTH).mulPosition(arrow.getTransformMatrix());
            assertEquals(direction.x,tip.x,1e-10);assertEquals(direction.y,tip.y,1e-10);assertEquals(direction.z,tip.z,1e-10);
        }
    }
    @Test
    void probeArrowLengthIsBoundedWithoutChangingSmallSignalGain() {
        assertEquals(.01, GradientArrowGeometry.probeVisualLength(1e-9, 1e7), 1e-12);
        assertEquals(6, GradientArrowGeometry.probeVisualLength(50, 1e7));
        assertEquals(6, GradientArrowGeometry.probeVisualLength(Double.MAX_VALUE, Double.MAX_VALUE));
        assertEquals(0, GradientArrowGeometry.probeVisualLength(0, 1e7));
        assertEquals(0, GradientArrowGeometry.probeVisualLength(Double.NaN, 1e7));
        assertEquals(0, GradientArrowGeometry.probeVisualLength(Double.POSITIVE_INFINITY, 1e7));
    }
    @Test
    void minusXCalibrationArrowMatchesParentedMeshEndpoint() {
        assertCardinalEndpoint("-X", new Vector3d(-1, 0, 0));
    }

    @Test
    void plusYCalibrationArrowMatchesParentedMeshEndpoint() {
        assertCardinalEndpoint("+Y", new Vector3d(0, 1, 0));
    }

    @Test
    void plusZCalibrationArrowMatchesParentedMeshEndpoint() {
        assertCardinalEndpoint("+Z", new Vector3d(0, 0, 1));
    }

    @Test
    void normalArrowLocalXyVectorDoesNotAcquireZDisplacement() {
        Scene scene = new Scene();
        Lattice lattice = scene.getLattice();
        Entity base = lattice.createEntity(null, null, new Vector3d(), new Vector3d(), 1.0);
        Entity arrow = lattice.createEntity(base, null, new Vector3d(), new Vector3d(), 1.0);
        Vector3d localVector = new Vector3d(-1, 1, 0);

        scene.updateArrowFromLocalVector(arrow, new Vector3d(), localVector,
                localVector.length(), new Vector4f(1, 1, 1, 1));

        Vector3d actualBase = new Vector3d(0, 0, 0).mulPosition(arrow.getTransformMatrix());
        Vector3d actualTip = new Vector3d(0, 0, GradientArrowGeometry.MESH_LENGTH)
                .mulPosition(arrow.getTransformMatrix());
        Vector3d displacement = actualTip.sub(actualBase);
        assertEquals(0.0, displacement.z, 1e-12);
        assertEquals(localVector.x, displacement.x, 1e-12);
        assertEquals(localVector.y, displacement.y, 1e-12);
    }

    private static void assertCardinalEndpoint(String label, Vector3d localVector) {
        Lattice lattice = new Lattice();
        Entity baseEntity = lattice.createEntity(null, null, new Vector3d(), new Vector3d(), 1.0);
        baseEntity.setRotation(17, 23, 11);
        baseEntity.setScale(1.7);
        baseEntity.updateMatrix();

        Vector3d anchor = new Vector3d(2.0, 3.0, 4.0);
        Vector3d expectedBase = new Vector3d(anchor).mulPosition(baseEntity.getTransformMatrix());
        Vector3d expectedTip = new Vector3d(anchor).add(localVector)
                .mulPosition(baseEntity.getTransformMatrix());
        GradientArrowEntity arrow = lattice.createGradientArrowEntity(
                baseEntity, null, anchor,
                localVector.length() / (GradientArrowGeometry.MESH_LENGTH
                                        * Project.getInstance().settings.scale));
        // Calibration vectors are already parent-local; pass them unchanged.
        arrow.setDirection(localVector);
        arrow.updateMatrix();

        Vector3d actualBase = new Vector3d(0, 0, 0).mulPosition(arrow.getTransformMatrix());
        Vector3d actualTip = new Vector3d(0, 0, GradientArrowGeometry.MESH_LENGTH)
                .mulPosition(arrow.getTransformMatrix());
        Vector3d actualDirection = new Vector3d(actualTip).sub(actualBase).normalize();
        Vector3d expectedDirection = new Vector3d(expectedTip).sub(expectedBase).normalize();
        double directionError = Math.toDegrees(Math.acos(Math.clamp(actualDirection.dot(
                expectedDirection), -1.0, 1.0)));

        assertEquals(expectedBase.x, actualBase.x, 1e-12, label);
        assertEquals(expectedBase.y, actualBase.y, 1e-12, label);
        assertEquals(expectedBase.z, actualBase.z, 1e-12, label);
        assertEquals(expectedTip.x, actualTip.x, 1e-12, label);
        assertEquals(expectedTip.y, actualTip.y, 1e-12, label);
        assertEquals(expectedTip.z, actualTip.z, 1e-12, label);
        assertEquals(0.0, directionError, 1e-5, label);
    }

}
