package remframework.geometry.discovery;

import org.joml.Vector3d;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class GeometryDiscoveryComparator {
    public GeometryComparisonReport compare(Path first, Path second) throws IOException {
        GeometryDiscoveryCandidateLoader loader = new GeometryDiscoveryCandidateLoader();
        return compare(loader.load(first).storedResult(), loader.load(second).storedResult());
    }

    public GeometryComparisonReport compare(GeometryDiscoveryResult first,
                                            GeometryDiscoveryResult second) {
        GeometryDiscoveryMetrics a = first.metrics();
        GeometryDiscoveryMetrics b = second.metrics();
        Map<Integer, Vector3d> firstAligned = aligned(first.model());
        Map<Integer, Vector3d> secondAligned = aligned(second.model());
        Set<Integer> common = new TreeSet<>(firstAligned.keySet());
        common.retainAll(secondAligned.keySet());
        double squared = 0;
        for (Integer id : common)
            squared += firstAligned.get(id).distanceSquared(secondAligned.get(id));
        double scale = Math.max(1.0e-12, (a.meanConnectedNeighborDistance()
                + b.meanConnectedNeighborDistance()) * 0.5);
        double normalized = common.isEmpty() ? Double.NaN
                : Math.sqrt(squared / common.size()) / scale;
        return new GeometryComparisonReport(
                first.model().nodes().size() - second.model().nodes().size(),
                first.model().connections().size() - second.model().connections().size(),
                a.meanConnectedNeighborDistance() - b.meanConnectedNeighborDistance(),
                a.minimumConnectedNeighborDistance() - b.minimumConnectedNeighborDistance(),
                a.maximumConnectedNeighborDistance() - b.maximumConnectedNeighborDistance(),
                a.effectiveDimensionality(), b.effectiveDimensionality(),
                a.extentX() - b.extentX(), a.extentY() - b.extentY(),
                a.extentZ() - b.extentZ(),
                first.runtimeCompatibility().compatible()
                        == second.runtimeCompatibility().compatible(),
                first.converged() == second.converged(), common.size(), normalized,
                "node 0 translated to origin; node 1 aligned to +x; node 2 aligned to xy plane");
    }

    private Map<Integer, Vector3d> aligned(GeometryDiscoveryModel model) {
        Map<Integer, DiscoveryNode> nodes = new HashMap<>();
        for (DiscoveryNode node : model.nodes()) nodes.put(node.id(), node);
        Vector3d origin = nodes.containsKey(0) ? nodes.get(0).position() : centroid(model);
        Vector3d xAxis = nodes.containsKey(1)
                ? new Vector3d(nodes.get(1).position()).sub(origin)
                : new Vector3d(1, 0, 0);
        if (xAxis.lengthSquared() < 1.0e-20) xAxis.set(1, 0, 0);
        xAxis.normalize();
        Vector3d yAxis = nodes.containsKey(2)
                ? new Vector3d(nodes.get(2).position()).sub(origin)
                : perpendicular(xAxis);
        yAxis.fma(-yAxis.dot(xAxis), xAxis);
        if (yAxis.lengthSquared() < 1.0e-20) yAxis.set(perpendicular(xAxis));
        yAxis.normalize();
        Vector3d zAxis = new Vector3d(xAxis).cross(yAxis).normalize();
        Map<Integer, Vector3d> result = new HashMap<>();
        for (DiscoveryNode node : model.nodes()) {
            Vector3d relative = new Vector3d(node.position()).sub(origin);
            result.put(node.id(), new Vector3d(relative.dot(xAxis), relative.dot(yAxis),
                    relative.dot(zAxis)));
        }
        return result;
    }

    private Vector3d centroid(GeometryDiscoveryModel model) {
        Vector3d center = new Vector3d();
        for (DiscoveryNode node : model.nodes()) center.add(node.position());
        return model.nodes().isEmpty() ? center : center.div(model.nodes().size());
    }

    private Vector3d perpendicular(Vector3d axis) {
        Vector3d candidate = Math.abs(axis.y) < 0.9 ? new Vector3d(0, 1, 0)
                : new Vector3d(0, 0, 1);
        return candidate.fma(-candidate.dot(axis), axis).normalize();
    }
}
