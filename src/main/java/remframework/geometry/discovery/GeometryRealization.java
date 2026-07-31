package remframework.geometry.discovery;
import org.joml.Vector3d;
import java.util.List;
public record GeometryRealization(long seed, GeometryEmbeddingDimension dimension,
        List<Vector3d> initialCoordinates, List<Vector3d> finalCoordinates,
        double meanEdgeAccuracy, double maximumSpeed, boolean converged,
        boolean collisionFree, boolean edgeIntersectionFree, int effectiveDimension,
        GeometryRealizationStatus status, List<GeometryConfigurationDiagnostic> diagnostics) {
    public GeometryRealization {
        initialCoordinates=initialCoordinates.stream().map(Vector3d::new).toList();
        finalCoordinates=finalCoordinates.stream().map(Vector3d::new).toList();
        diagnostics=List.copyOf(diagnostics);
    }
}
