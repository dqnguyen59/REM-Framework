package remframework.opengl.model;

import org.joml.Vector3d;
import org.joml.Matrix4d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GradientArrowGeometryTest {
    @Test
    void canonicalArrowAxisFollowsKnownGradientDirections() {
        assertDirection(new Vector3d(1, 0, 0));
        assertDirection(new Vector3d(0, 1, 0));
        assertDirection(new Vector3d(0, 0, 1));
        assertDirection(new Vector3d(-1, 1, 0));
        assertDirection(new Vector3d(-1.936094e-6, 4.605262e-6, 0));
    }

    @Test
    void nearZeroGradientHasNoStaleDirection() {
        assertNull(GradientArrowGeometry.rotationForDirection(new Vector3d(1e-13, 0, 0)));
    }

    private static void assertDirection(Vector3d expected) {
        Vector3d actual = GradientArrowGeometry.rotatedCanonicalAxis(expected);
        Vector3d eulerDegrees = GradientArrowGeometry.rotationForDirection(expected);
        Vector3d entityAxis = new Matrix4d()
                .setRotationXYZ(Math.toRadians(eulerDegrees.x), Math.toRadians(eulerDegrees.y),
                                Math.toRadians(eulerDegrees.z))
                .transformDirection(new Vector3d(0, 0, 1));
        Vector3d normalized = expected.normalize();
        assertEquals(normalized.x, actual.x, 1e-12);
        assertEquals(normalized.y, actual.y, 1e-12);
        assertEquals(normalized.z, actual.z, 1e-12);
        assertEquals(normalized.x, entityAxis.x, 1e-12);
        assertEquals(normalized.y, entityAxis.y, 1e-12);
        assertEquals(normalized.z, entityAxis.z, 1e-12);
    }
}
