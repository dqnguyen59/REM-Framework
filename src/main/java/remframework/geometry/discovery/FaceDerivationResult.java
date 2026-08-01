package remframework.geometry.discovery;
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
import java.util.List;
public record FaceDerivationResult(List<DerivedFace> candidateFaces, List<DerivedFace> faces,
        boolean unique, boolean complete, GeometryConfigurationStatus status,
        List<GeometryConfigurationDiagnostic> diagnostics) {
    public FaceDerivationResult { candidateFaces=List.copyOf(candidateFaces);faces=List.copyOf(faces);diagnostics=List.copyOf(diagnostics); }
}
