package remframework.opengl.model;
import org.joml.Vector3d;
import remframework.settings.Settings.SliceType;
/** Physical slab in lattice coordinates, independent of storage indices and camera rotation. */
public final class PhysicalCrossSection {
    private PhysicalCrossSection() { }
    public static boolean contains(Vector3d referencePosition, Vector3d center, SliceType plane, double thickness) {
        if (!Double.isFinite(thickness) || thickness <= 0) throw new IllegalArgumentException("Thickness must be positive and finite");
        double distance = switch (plane) {
            case physicalXY -> referencePosition.z-center.z;
            case physicalXZ -> referencePosition.y-center.y;
            case physicalYZ -> referencePosition.x-center.x;
            default -> throw new IllegalArgumentException("A physical cross-section plane is required");
        };
        return Math.abs(distance) <= thickness/2 + 1e-12;
    }
}
