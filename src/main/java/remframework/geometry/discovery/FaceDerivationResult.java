package remframework.geometry.discovery;
import java.util.List;
public record FaceDerivationResult(List<DerivedFace> candidateFaces, List<DerivedFace> faces,
        boolean unique, boolean complete, GeometryConfigurationStatus status,
        List<GeometryConfigurationDiagnostic> diagnostics) {
    public FaceDerivationResult { candidateFaces=List.copyOf(candidateFaces);faces=List.copyOf(faces);diagnostics=List.copyOf(diagnostics); }
}
