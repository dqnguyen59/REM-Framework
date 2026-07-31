package remframework.geometry.discovery;
import java.util.List;
public record ClosedBoundaryResult(boolean closed, boolean orientable, boolean manifold,
        boolean connected, boolean selfIntersecting, int eulerCharacteristic,
        List<DerivedFace> orientedFaces, GeometryConfigurationStatus status,
        List<GeometryConfigurationDiagnostic> diagnostics) {
    public ClosedBoundaryResult { orientedFaces=List.copyOf(orientedFaces);diagnostics=List.copyOf(diagnostics); }
}
