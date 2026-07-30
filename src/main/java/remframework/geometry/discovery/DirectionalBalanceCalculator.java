package remframework.geometry.discovery;

import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

public final class DirectionalBalanceCalculator {
    public DirectionalBalanceMetrics calculate(GeometryDiscoveryModel model) {
        Map<Integer, DiscoveryNode> nodes = new HashMap<>();
        model.nodes().forEach(n -> nodes.put(n.id(), n));
        if (nodes.isEmpty()) return new DirectionalBalanceMetrics(0, 0, 0, -1, 0, 0);
        double sum = 0, sumSquares = 0, tensorSum = 0, maximum = -1;
        int worst = -1, insufficient = 0;
        for (DiscoveryNode node : model.sortedNodes()) {
            Vector3d vectorSum = new Vector3d();
            double[][] moment = new double[3][3];
            int usable = 0;
            for (int neighborId : node.neighborIds()) {
                DiscoveryNode neighbor = nodes.get(neighborId);
                if (neighbor == null) continue;
                Vector3d unit = new Vector3d(neighbor.position()).sub(node.position());
                double length = unit.length();
                if (!(length > 1e-12) || !Double.isFinite(length)) continue;
                unit.div(length);
                vectorSum.add(unit);
                double[] u = {unit.x, unit.y, unit.z};
                for (int row = 0; row < 3; row++)
                    for (int column = 0; column < 3; column++)
                        moment[row][column] += u[row] * u[column];
                usable++;
            }
            if (usable < 3) insufficient++;
            double imbalance = usable == 0 ? 0 : vectorSum.length() / usable;
            double tensorError = 0;
            if (usable > 0) {
                for (int row = 0; row < 3; row++)
                    for (int column = 0; column < 3; column++) {
                        double target = row == column ? 1.0 / 3.0 : 0;
                        tensorError += Math.pow(moment[row][column] / usable - target, 2);
                    }
                tensorError = Math.sqrt(tensorError);
            }
            sum += imbalance;
            sumSquares += imbalance * imbalance;
            tensorSum += tensorError;
            if (imbalance > maximum) { maximum = imbalance; worst = node.id(); }
        }
        double mean = sum / nodes.size();
        double variance = Math.max(0, sumSquares / nodes.size() - mean * mean);
        return new DirectionalBalanceMetrics(mean, maximum, Math.sqrt(variance), worst,
                tensorSum / nodes.size(), insufficient);
    }
}
