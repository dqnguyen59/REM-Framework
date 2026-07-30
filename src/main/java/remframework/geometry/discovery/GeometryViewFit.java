package remframework.geometry.discovery;

import org.joml.Vector3d;

public record GeometryViewFit(Vector3d center, double radius, double cameraDistance,
                              double nearPlane, double farPlane) {
}
