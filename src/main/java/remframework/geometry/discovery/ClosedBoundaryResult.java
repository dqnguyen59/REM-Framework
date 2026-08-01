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
public record ClosedBoundaryResult(boolean closed, boolean orientable, boolean manifold,
        boolean connected, boolean selfIntersecting, int eulerCharacteristic,
        List<DerivedFace> orientedFaces, GeometryConfigurationStatus status,
        List<GeometryConfigurationDiagnostic> diagnostics) {
    public ClosedBoundaryResult { orientedFaces=List.copyOf(orientedFaces);diagnostics=List.copyOf(diagnostics); }
}
