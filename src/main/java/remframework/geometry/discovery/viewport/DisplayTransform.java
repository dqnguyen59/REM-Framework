package remframework.geometry.discovery.viewport;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;

import org.joml.Vector3d;

import java.util.List;

/** Display-only centering and scaling; scientific coordinates are never mutated. */
public record DisplayTransform(Vector3d centroid, double boundingRadius,
        double displayScale, double displayRadius) {
    public DisplayTransform {
        centroid = new Vector3d(centroid);
    }

    public static DisplayTransform calculate(List<Vector3d> coordinates,
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
        return new DisplayTransform(centroid, radius,
                displayRadius / radius, displayRadius);
    }

    public Vector3d display(Vector3d scientificCoordinate) {
        return new Vector3d(scientificCoordinate).sub(centroid).mul(displayScale);
    }
}
