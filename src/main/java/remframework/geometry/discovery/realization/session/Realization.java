package remframework.geometry.discovery.realization.session;
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
public record Realization(long seed, GeometryEmbeddingDimension dimension,
        List<Vector3d> initialCoordinates, List<Vector3d> finalCoordinates,
        double meanEdgeAccuracy, double maximumSpeed, boolean converged,
        boolean collisionFree, boolean edgeIntersectionFree, int effectiveDimension,
        RealizationStatus status, List<GeometryConfigurationDiagnostic> diagnostics) {
    public Realization {
        initialCoordinates=initialCoordinates.stream().map(Vector3d::new).toList();
        finalCoordinates=finalCoordinates.stream().map(Vector3d::new).toList();
        diagnostics=List.copyOf(diagnostics);
    }
}
