package remframework.geometry.discovery;

import org.joml.Vector3d;

import java.util.List;

/** Display-only centering and scaling; scientific coordinates are never mutated. */
public record GeometryDiscoveryDisplayTransform(Vector3d centroid, double boundingRadius,
        double displayScale, double displayRadius) {
    public GeometryDiscoveryDisplayTransform {
        centroid = new Vector3d(centroid);
    }

    public static GeometryDiscoveryDisplayTransform calculate(List<Vector3d> coordinates,
            double displayRadius) {
        Vector3d centroid = new Vector3d();
        if (!coordinates.isEmpty()) {
            for (Vector3d coordinate : coordinates) centroid.add(coordinate);
            centroid.div(coordinates.size());
        }
        double radius = 0;
        for (Vector3d coordinate : coordinates)
            radius = Math.max(radius, coordinate.distance(centroid));
        radius = Math.max(radius, 1e-12);
        return new GeometryDiscoveryDisplayTransform(centroid, radius,
                displayRadius / radius, displayRadius);
    }

    public Vector3d display(Vector3d scientificCoordinate) {
        return new Vector3d(scientificCoordinate).sub(centroid).mul(displayScale);
    }
}
