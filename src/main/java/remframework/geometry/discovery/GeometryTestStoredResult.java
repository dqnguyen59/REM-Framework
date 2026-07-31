package remframework.geometry.discovery;

import java.time.Instant;
import org.joml.Vector3d;
import java.util.List;

/** Persisted summary that can be reopened without rerunning the experiment. */
public record GeometryTestStoredResult(GeometryConfigurationId configurationId,
        GeometryConfigurationStatusCode status, String explanation,
        boolean realizationFound, boolean uniqueFaces, boolean closedBoundary,
        boolean areaAvailable, boolean volumeAvailable, Instant savedAt,
        List<Vector3d> finalCoordinates) {
    public GeometryTestStoredResult {
        finalCoordinates = finalCoordinates == null ? List.of()
                : finalCoordinates.stream().map(Vector3d::new).toList();
    }
}
