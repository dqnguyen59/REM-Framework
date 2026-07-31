package remframework.geometry.discovery;

import org.joml.Vector3d;

public record GeometryDiscoveryRenderDiagnostics(String configurationId, int renderedVertexCount,
        int renderedEdgeCount, Vector3d geometryCentroid, double geometryBoundingRadius,
        double displayScale, Vector3d cameraPosition, Vector3d cameraTarget,
        double nearPlane, double farPlane, double cameraDistance,
        boolean geometryIntersectsFrustum) { }
