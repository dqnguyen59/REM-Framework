package remframework.opengl.model.entity;

import org.joml.Vector3i;

/** Cached graph-topology diagnostics for one Extended Identity region. */
public record OccupiedRegionDiagnostics(int graphRadius, int occupiedNodeCount,
        int boundaryNodeCount, int boundaryCrossingEdgeCount, Vector3i centerNodeIndex) {
    public OccupiedRegionDiagnostics {
        centerNodeIndex = centerNodeIndex == null ? null : new Vector3i(centerNodeIndex);
    }
    @Override public Vector3i centerNodeIndex() {
        return centerNodeIndex == null ? null : new Vector3i(centerNodeIndex);
    }
}
