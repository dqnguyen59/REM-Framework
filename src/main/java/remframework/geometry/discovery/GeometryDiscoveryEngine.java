package remframework.geometry.discovery;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GeometryDiscoveryEngine {
    @FunctionalInterface
    public interface ProgressListener {
        void update(int iteration, GeometryDiscoveryMetrics metrics);
    }

    public GeometryDiscoveryResult relax(GeometryDiscoveryModel input,
                                         GeometryDiscoverySettings settings) {
        return relax(input, settings, null);
    }

    public GeometryDiscoveryResult relax(GeometryDiscoveryModel input,
                                         GeometryDiscoverySettings settings,
                                         ProgressListener progressListener) {
        GeometryDiscoveryModel model = input.copy();
        DiscoveryValidationResult validation =
                new GeometryDiscoveryValidator().validate(model, settings);
        GeometryDiscoveryMetricsCalculator calculator = new GeometryDiscoveryMetricsCalculator();
        List<String> diagnostics = new ArrayList<>();
        if (!validation.isValid()) {
            diagnostics.add("Relaxation was not started because input validation failed.");
            GeometryDiscoveryMetrics metrics = calculator.calculate(model, settings, 0, false);
            return result(model, settings, metrics, validation, diagnostics);
        }

        List<DiscoveryNode> nodes = model.sortedNodes();
        Map<Integer, DiscoveryNode> byId = new HashMap<>();
        for (DiscoveryNode node : nodes) byId.put(node.id(), node);
        Set<Long> connectedPairs = new HashSet<>();
        for (DiscoveryConnection connection : model.connections())
            connectedPairs.add(connection.undirectedKey());
        applyRigidBodyConstraints(byId);

        boolean converged = false;
        int iterations = 0;
        for (int iteration = 1; iteration <= settings.maximumIterations(); iteration++) {
            Map<Integer, Vector3d> corrections = new HashMap<>();
            for (DiscoveryNode node : nodes) corrections.put(node.id(), new Vector3d());
            double maximumEdgeDeviation = accumulateEdgeCorrections(
                    model, byId, corrections, settings);
            int separationViolations = accumulateRepulsion(
                    nodes, connectedPairs, corrections, settings);
            double maximumAppliedCorrection = applyCorrections(
                    nodes, corrections, settings);
            applyRigidBodyConstraints(byId);
            iterations = iteration;

            if (!allFinite(nodes)) {
                diagnostics.add("Relaxation stopped because a non-finite coordinate was produced.");
                break;
            }
            if (maximumEdgeDeviation <= settings.convergenceTolerance()
                    && separationViolations == 0
                    && maximumAppliedCorrection <= settings.convergenceTolerance()) {
                converged = true;
                break;
            }
            if (progressListener != null && iteration % settings.progressInterval() == 0) {
                progressListener.update(iteration,
                        calculator.calculate(model, settings, iteration, false));
            }
        }
        if (!converged && diagnostics.isEmpty())
            diagnostics.add("Maximum iteration count reached without convergence.");
        GeometryDiscoveryMetrics metrics =
                calculator.calculate(model, settings, iterations, converged);
        return result(model, settings, metrics, validation, diagnostics);
    }

    private double accumulateEdgeCorrections(GeometryDiscoveryModel model,
                                             Map<Integer, DiscoveryNode> nodes,
                                             Map<Integer, Vector3d> corrections,
                                             GeometryDiscoverySettings settings) {
        double maximumDeviation = 0;
        for (DiscoveryConnection connection : model.connections()) {
            DiscoveryNode first = nodes.get(connection.firstNodeId());
            DiscoveryNode second = nodes.get(connection.secondNodeId());
            Vector3d delta = new Vector3d(second.position()).sub(first.position());
            double distance = delta.length();
            Vector3d direction = safeDirection(delta, distance,
                    connection.undirectedKey(), settings.randomSeed());
            double deviation = distance - settings.targetDistance();
            maximumDeviation = Math.max(maximumDeviation, Math.abs(deviation));
            Vector3d correction = direction.mul(settings.learningRate() * deviation);
            if (!first.fixedPosition()) corrections.get(first.id()).add(correction);
            if (!second.fixedPosition()) corrections.get(second.id()).sub(correction);
        }
        return maximumDeviation;
    }

    private int accumulateRepulsion(List<DiscoveryNode> nodes, Set<Long> connectedPairs,
                                    Map<Integer, Vector3d> corrections,
                                    GeometryDiscoverySettings settings) {
        int violations = 0;
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                DiscoveryNode first = nodes.get(i);
                DiscoveryNode second = nodes.get(j);
                DiscoveryConnection pair = new DiscoveryConnection(first.id(), second.id());
                if (connectedPairs.contains(pair.undirectedKey())) continue;
                Vector3d delta = new Vector3d(second.position()).sub(first.position());
                double distance = delta.length();
                if (distance >= settings.minimumNodeSeparation()) continue;
                violations++;
                Vector3d direction = safeDirection(delta, distance, pair.undirectedKey(),
                        settings.randomSeed());
                double normalizedOverlap =
                        (settings.minimumNodeSeparation() - distance)
                                / settings.minimumNodeSeparation();
                double magnitude = Math.min(settings.maximumCorrection(),
                        settings.repulsionStrength() * normalizedOverlap
                                * settings.minimumNodeSeparation());
                Vector3d correction = direction.mul(magnitude);
                if (!first.fixedPosition()) corrections.get(first.id()).sub(correction);
                if (!second.fixedPosition()) corrections.get(second.id()).add(correction);
            }
        }
        return violations;
    }

    private double applyCorrections(List<DiscoveryNode> nodes,
                                    Map<Integer, Vector3d> corrections,
                                    GeometryDiscoverySettings settings) {
        double maximum = 0;
        for (DiscoveryNode node : nodes) {
            node.previousPosition().set(node.position());
            if (node.fixedPosition()) continue;
            Vector3d correction = corrections.get(node.id());
            double length = correction.length();
            if (length > settings.maximumCorrection())
                correction.mul(settings.maximumCorrection() / length);
            maximum = Math.max(maximum, correction.length());
            node.position().add(correction);
        }
        return maximum;
    }

    private void applyRigidBodyConstraints(Map<Integer, DiscoveryNode> nodes) {
        DiscoveryNode anchor = nodes.get(0);
        anchor.position().zero();
        DiscoveryNode orientation = nodes.get(1);
        double x = Math.abs(orientation.position().x);
        if (x < 1.0e-12) x = 1.0e-12;
        orientation.position().set(x, 0, 0);
    }

    private Vector3d safeDirection(Vector3d delta, double distance, long pairKey, long seed) {
        if (distance > 1.0e-12) return delta.div(distance);
        long value = mix64(pairKey ^ seed);
        double x = signedUnit(value);
        double y = signedUnit(mix64(value));
        double z = signedUnit(mix64(value ^ 0x9E3779B97F4A7C15L));
        Vector3d direction = new Vector3d(x, y, z);
        if (direction.lengthSquared() < 1.0e-18) direction.set(1, 0, 0);
        return direction.normalize();
    }

    private long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private double signedUnit(long value) {
        return ((value >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }

    private boolean allFinite(List<DiscoveryNode> nodes) {
        return nodes.stream().allMatch(node -> node.position().isFinite());
    }

    private GeometryDiscoveryResult result(GeometryDiscoveryModel model,
                                           GeometryDiscoverySettings settings,
                                           GeometryDiscoveryMetrics metrics,
                                           DiscoveryValidationResult validation,
                                           List<String> diagnostics) {
        RuntimeCompatibility compatibility = new GeometryRuntimeCompatibilityAnalyzer()
                .analyze(model, Math.max(settings.dimensionalityTolerance(),
                        settings.convergenceTolerance() * 10));
        return new GeometryDiscoveryResult(model, settings, metrics, validation,
                compatibility, List.copyOf(diagnostics));
    }
}
