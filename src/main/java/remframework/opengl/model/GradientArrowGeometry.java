package remframework.opengl.model;

import org.joml.Matrix4d;
import org.joml.Vector3d;

/** Euler calibration for the arrow mesh used by passive gradient overlays. */
public final class GradientArrowGeometry {
    /** The verified arrow mesh axis runs from local z=0 to local z=13. */
    public static final double MESH_LENGTH = 13.0;
    public static final double CALIBRATION_LENGTH = 10.0;
    /** Display-only limit; measured gradients are never clamped. */
    public static final double MAX_PROBE_ARROW_LENGTH = 6.0;

    public static double probeVisualLength(double magnitude, double gain) {
        if (!Double.isFinite(magnitude) || magnitude <= 0
                || !Double.isFinite(gain) || gain <= 0) return 0;
        // Compare before multiplying so extreme finite signals cannot overflow.
        if (magnitude >= MAX_PROBE_ARROW_LENGTH / gain) return MAX_PROBE_ARROW_LENGTH;
        return magnitude * gain;
    }

    private GradientArrowGeometry() {
    }

    /** Calibrated Euler rotation for the temporary cardinal-axis comparison arrow. */
    public static Vector3d rotationForDirection(Vector3d direction) {
        Vector3d unit = new Vector3d(direction).normalize();
        double pitchX = Math.toDegrees(Math.atan2(-unit.y, unit.z));
        double yawY = Math.toDegrees(Math.asin(unit.x));
        return new Vector3d(pitchX, yawY, 0.0);
    }

    /** Converts a world-space arrow vector into the local coordinates of its lattice parent. */
    public static Vector3d toParentLocalDirection(Vector3d worldDirection, Matrix4d parentWorld) {
        return new Matrix4d(parentWorld).invert().transformDirection(new Vector3d(worldDirection));
    }
}
