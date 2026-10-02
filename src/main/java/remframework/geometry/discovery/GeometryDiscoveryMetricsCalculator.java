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

import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class GeometryDiscoveryMetricsCalculator {
    GeometryDiscoveryMetrics calculate(GeometryDiscoveryModel model,
                                       GeometryDiscoverySettings settings,
                                       int iterations, boolean converged) {
        Map<Integer, DiscoveryNode> nodes = new HashMap<>();
        for (DiscoveryNode node : model.nodes()) nodes.put(node.id(), node);
        double sum = 0;
        double squaredError = 0;
        double minEdge = Double.POSITIVE_INFINITY;
        double maxEdge = 0;
        for (DiscoveryConnection connection : model.connections()) {
            double distance = nodes.get(connection.firstNodeId()).position()
                    .distance(nodes.get(connection.secondNodeId()).position());
            sum += distance;
            double error = distance - settings.targetDistance();
            squaredError += error * error;
            minEdge = Math.min(minEdge, distance);
            maxEdge = Math.max(maxEdge, distance);
        }
        int edgeCount = model.connections().size();
        double mean = edgeCount == 0 ? 0 : sum / edgeCount;
        double variance = 0;
        for (DiscoveryConnection connection : model.connections()) {
            double distance = nodes.get(connection.firstNodeId()).position()
                    .distance(nodes.get(connection.secondNodeId()).position());
            variance += (distance - mean) * (distance - mean);
        }
        double standardDeviation = edgeCount == 0 ? 0 : Math.sqrt(variance / edgeCount);
        if (edgeCount == 0) minEdge = 0;

        List<DiscoveryNode> list = model.nodes();
        double minDistinct = list.size() < 2 ? 0 : Double.POSITIVE_INFINITY;
        int collisions = 0;
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                double distance = list.get(i).position().distance(list.get(j).position());
                minDistinct = Math.min(minDistinct, distance);
                if (distance < settings.minimumNodeSeparation()) collisions++;
            }
        }
        double[] extents = extents(list);
        int nearZero = 0;
        for (double extent : extents)
            if (extent <= settings.dimensionalityTolerance()) nearZero++;
        String dimensionality = switch (nearZero) {
            case 3, 2 -> "approximately 1D (basic extent diagnostic)";
            case 1 -> "approximately 2D (basic extent diagnostic)";
            default -> "3D (basic extent diagnostic)";
        };
        return new GeometryDiscoveryMetrics(iterations, converged, squaredError, mean, minEdge,
                maxEdge, standardDeviation, minDistinct, collisions,
                GeometryDiscoveryValidator.componentCount(model, nodes),
                extents[0], extents[1], extents[2], dimensionality);
    }

    private double[] extents(List<DiscoveryNode> nodes) {
        if (nodes.isEmpty()) return new double[]{0, 0, 0};
        Vector3d min = new Vector3d(Double.POSITIVE_INFINITY);
        Vector3d max = new Vector3d(Double.NEGATIVE_INFINITY);
        for (DiscoveryNode node : nodes) {
            Vector3d p = node.position();
            min.min(p);
            max.max(p);
        }
        return new double[]{max.x - min.x, max.y - min.y, max.z - min.z};
    }
}
