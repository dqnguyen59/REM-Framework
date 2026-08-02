package remframework.geometry.discovery.viewport;

import org.joml.Vector3d;

import java.util.List;

/** Immutable, snapshot-revision-bound measurements for one selected element. */
public record SelectedElementMeasurements(SelectedElement selection, long revision,
        Vector3d position, List<Integer> vertexIds, List<String> edgeIds,
        List<String> faceIds, double length, double targetLength, double error,
        double tolerance, double ratio, Vector3d midpoint, double centerDistance,
        double area, double perimeter, double planarityError, List<Double> angles,
        Vector3d centroid, Vector3d normal, double outwardAlignment,
        double facePlaneDistance, double faceCentroidDistance, String state) {
    public SelectedElementMeasurements {
        position=copy(position); midpoint=copy(midpoint); centroid=copy(centroid); normal=copy(normal);
        vertexIds=List.copyOf(vertexIds); edgeIds=List.copyOf(edgeIds);
        faceIds=List.copyOf(faceIds); angles=List.copyOf(angles);
    }
    private static Vector3d copy(Vector3d value){return value==null?null:new Vector3d(value);}
}
