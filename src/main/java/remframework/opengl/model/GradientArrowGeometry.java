package remframework.opengl.model;

import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Geometry-only transforms for the arrow mesh used by passive gradient overlays. */
public final class GradientArrowGeometry {
    private static final Vector3d CANONICAL_ARROW_AXIS = new Vector3d(0, 0, 1);
    private static final double MIN_DIRECTION_LENGTH_SQUARED = 1.0e-24;

    private GradientArrowGeometry() {
    }

    /**
     * Returns XYZ Euler angles that rotate the arrow mesh's verified local +Z axis
     * onto {@code direction}; returns {@code null} for a near-zero direction.
     */
    public static Vector3d rotationForDirection(Vector3d direction) {
        if (direction.lengthSquared() <= MIN_DIRECTION_LENGTH_SQUARED) return null;
        Quaterniond rotation = new Quaterniond().rotationTo(
                CANONICAL_ARROW_AXIS, new Vector3d(direction).normalize());
        Vector3d euler = rotation.getEulerAnglesXYZ(new Vector3d());
        return euler.mul(Math.toDegrees(1.0));
    }

    /** Applies the same canonical-axis rotation for geometry-focused verification. */
    public static Vector3d rotatedCanonicalAxis(Vector3d direction) {
        if (direction.lengthSquared() <= MIN_DIRECTION_LENGTH_SQUARED) return new Vector3d();
        return new Quaterniond().rotationTo(
                CANONICAL_ARROW_AXIS, new Vector3d(direction).normalize())
                .transform(new Vector3d(CANONICAL_ARROW_AXIS));
    }
}
