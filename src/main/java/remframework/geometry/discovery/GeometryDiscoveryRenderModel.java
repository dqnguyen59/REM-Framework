package remframework.geometry.discovery;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/** Immutable render adapter for topology previews and incremental realizations. */
public record GeometryDiscoveryRenderModel(String configurationId,
        GeometryDiscoveryModel model, GeometryDiscoveryDisplayTransform transform,
        boolean topologyPreview, long revision) {
    public static GeometryDiscoveryRenderModel topologyPreview(GeometryConfiguration configuration,
            long revision) {
        List<Vector3d> coordinates = new ArrayList<>();
        int count = configuration.nodeCount();
        for (int id = 0; id < count; id++) {
            double angle = 2 * Math.PI * id / Math.max(1, count);
            double z = count > 4 ? .35 * Math.sin(angle * 2) : 0;
            coordinates.add(new Vector3d(Math.cos(angle), Math.sin(angle), z));
        }
        return of(configuration, coordinates, true, revision);
    }

    public static GeometryDiscoveryRenderModel realization(GeometryConfiguration configuration,
            List<Vector3d> coordinates, long revision) {
        return of(configuration, coordinates, false, revision);
    }

    private static GeometryDiscoveryRenderModel of(GeometryConfiguration configuration,
            List<Vector3d> scientificCoordinates, boolean preview, long revision) {
        GeometryDiscoveryDisplayTransform transform =
                GeometryDiscoveryDisplayTransform.calculate(scientificCoordinates, 4.0);
        List<DiscoveryNode> nodes = new ArrayList<>();
        for (int id = 0; id < configuration.nodeCount(); id++)
            nodes.add(new DiscoveryNode(id, transform.display(scientificCoordinates.get(id)), id < 2));
        GeometryDiscoveryModel model = new GeometryDiscoveryModel(
                "Phase 2 " + (preview ? "Topology Preview" : "Geometry Realization"),
                preview ? "Graph visualization only - not a geometric realization."
                        : "Display-only transform of an emergent geometry realization.",
                nodes, configuration.edges());
        return new GeometryDiscoveryRenderModel(configuration.id().toString(), model,
                transform, preview, revision);
    }
}
