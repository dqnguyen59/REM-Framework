package remframework.measurement.detector;

import org.joml.Vector3i;

/** Symmetric geometric predicate for the canonical Extended Identity family. */
public final class ExtendedIdentityShape {
    private ExtendedIdentityShape() { }

    public static double score(int dx, int dy, int dz, double i2, double i3) {
        int a = Math.abs(dx), b = Math.abs(dy), c = Math.abs(dz);
        int m1 = Math.min(a, Math.min(b, c));
        int m3 = Math.max(a, Math.max(b, c));
        int m2 = a + b + c - m1 - m3;
        return (double) a + b + c + i2 * (m2 - m1) + i3 * m1;
    }

    public static boolean contains(Vector3i offset, int radius, double i2, double i3) {
        return score(offset.x, offset.y, offset.z, i2, i3) < radius;
    }
}
