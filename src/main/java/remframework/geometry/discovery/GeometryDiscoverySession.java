package remframework.geometry.discovery;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GeometryDiscoverySession {
    private final GeometryDiscoveryModel initialModel;
    private GeometryDiscoveryModel model;
    private GeometryDiscoverySettings settings;
    private DiscoveryValidationResult validation;
    private final GeometryDiscoveryMetricsCalculator calculator =
            new GeometryDiscoveryMetricsCalculator();
    private List<DiscoveryNode> nodes;
    private Map<Integer, DiscoveryNode> byId;
    private Set<Long> connectedPairs;
    private final List<String> diagnostics = new ArrayList<>();
    private GeometryDiscoveryState.Status status;
    private int iteration;

    public GeometryDiscoverySession(GeometryDiscoveryModel input,
                                    GeometryDiscoverySettings settings) {
        initialModel = input.copy();
        this.settings = settings;
        reset();
    }

    public void start() {
        if (status == GeometryDiscoveryState.Status.READY
                || status == GeometryDiscoveryState.Status.PAUSED)
            status = GeometryDiscoveryState.Status.RUNNING;
    }

    public void pause() {
        if (status == GeometryDiscoveryState.Status.RUNNING)
            status = GeometryDiscoveryState.Status.PAUSED;
    }

    public boolean step() {
        if (!validation.isValid() || isFinished()) return false;
        if (iteration >= settings.maximumIterations()) {
            stopForLimit();
            return false;
        }
        Map<Integer, Vector3d> corrections = new HashMap<>();
        for (DiscoveryNode node : nodes) corrections.put(node.id(), new Vector3d());
        double maximumEdgeDeviation = accumulateEdgeCorrections(corrections);
        int separationViolations = accumulateRepulsion(corrections);
        double maximumAppliedCorrection = applyCorrections(corrections);
        applyRigidBodyConstraints();
        iteration++;
        if (!allFinite()) {
            diagnostics.add("Relaxation stopped because a non-finite coordinate was produced.");
            status = GeometryDiscoveryState.Status.STOPPED;
            return false;
        }
        if (maximumEdgeDeviation <= settings.convergenceTolerance()
                && separationViolations == 0
                && maximumAppliedCorrection <= settings.convergenceTolerance()) {
            status = GeometryDiscoveryState.Status.CONVERGED;
        } else if (iteration >= settings.maximumIterations()) {
            stopForLimit();
        }
        return true;
    }

    public GeometryDiscoveryResult runUntilConverged() {
        start();
        while (!isFinished()) step();
        return result();
    }

    public void reset() {
        model = initialModel.copy();
        validation = new GeometryDiscoveryValidator().validate(model, settings);
        nodes = model.sortedNodes();
        byId = new HashMap<>();
        for (DiscoveryNode node : nodes) byId.put(node.id(), node);
        connectedPairs = new HashSet<>();
        for (DiscoveryConnection connection : model.connections())
            connectedPairs.add(connection.undirectedKey());
        diagnostics.clear();
        iteration = 0;
        if (validation.isValid()) {
            applyRigidBodyConstraints();
            status = GeometryDiscoveryState.Status.READY;
        } else {
            diagnostics.add("Relaxation was not started because input validation failed.");
            status = GeometryDiscoveryState.Status.INVALID;
        }
    }

    public void updateSettings(GeometryDiscoverySettings settings) {
        this.settings = settings;
        validation = new GeometryDiscoveryValidator().validate(model, settings);
        if (!validation.isValid()) status = GeometryDiscoveryState.Status.INVALID;
    }

    public GeometryDiscoveryState state() {
        return new GeometryDiscoveryState(status, iteration,
                calculator.calculate(model, settings, iteration,
                        status == GeometryDiscoveryState.Status.CONVERGED),
                validation.isValid());
    }

    public GeometryDiscoveryResult result() {
        GeometryDiscoveryMetrics metrics = state().metrics();
        RuntimeCompatibility compatibility = new GeometryRuntimeCompatibilityAnalyzer()
                .analyze(model, Math.max(settings.dimensionalityTolerance(),
                        settings.convergenceTolerance() * 10));
        return new GeometryDiscoveryResult(model.copy(), settings, metrics, validation,
                compatibility, List.copyOf(diagnostics));
    }

    public GeometryDiscoveryModel model() { return model; }
    public GeometryDiscoverySettings settings() { return settings; }
    public boolean isRunning() { return status == GeometryDiscoveryState.Status.RUNNING; }
    public boolean isFinished() {
        return status == GeometryDiscoveryState.Status.CONVERGED
                || status == GeometryDiscoveryState.Status.STOPPED
                || status == GeometryDiscoveryState.Status.INVALID;
    }

    private double accumulateEdgeCorrections(Map<Integer, Vector3d> corrections) {
        double maximumDeviation = 0;
        for (DiscoveryConnection connection : model.connections()) {
            DiscoveryNode first = byId.get(connection.firstNodeId());
            DiscoveryNode second = byId.get(connection.secondNodeId());
            Vector3d delta = new Vector3d(second.position()).sub(first.position());
            double distance = delta.length();
            Vector3d direction = safeDirection(delta, distance,
                    connection.undirectedKey());
            double deviation = distance - settings.targetDistance();
            maximumDeviation = Math.max(maximumDeviation, Math.abs(deviation));
            Vector3d correction = direction.mul(settings.learningRate() * deviation);
            if (!first.fixedPosition()) corrections.get(first.id()).add(correction);
            if (!second.fixedPosition()) corrections.get(second.id()).sub(correction);
        }
        return maximumDeviation;
    }

    private int accumulateRepulsion(Map<Integer, Vector3d> corrections) {
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
                Vector3d direction = safeDirection(delta, distance, pair.undirectedKey());
                double overlap = (settings.minimumNodeSeparation() - distance)
                        / settings.minimumNodeSeparation();
                double magnitude = Math.min(settings.maximumCorrection(),
                        settings.repulsionStrength() * overlap
                                * settings.minimumNodeSeparation());
                Vector3d correction = direction.mul(magnitude);
                if (!first.fixedPosition()) corrections.get(first.id()).sub(correction);
                if (!second.fixedPosition()) corrections.get(second.id()).add(correction);
            }
        }
        return violations;
    }

    private double applyCorrections(Map<Integer, Vector3d> corrections) {
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

    private void applyRigidBodyConstraints() {
        DiscoveryNode anchor = byId.get(0);
        DiscoveryNode orientation = byId.get(1);
        if (anchor == null || orientation == null) return;
        anchor.position().zero();
        double x = Math.abs(orientation.position().x);
        if (x < 1.0e-12) x = 1.0e-12;
        orientation.position().set(x, 0, 0);
    }

    private Vector3d safeDirection(Vector3d delta, double distance, long pairKey) {
        if (distance > 1.0e-12) return delta.div(distance);
        long value = mix64(pairKey ^ settings.randomSeed());
        Vector3d direction = new Vector3d(signedUnit(value),
                signedUnit(mix64(value)),
                signedUnit(mix64(value ^ 0x9E3779B97F4A7C15L)));
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

    private boolean allFinite() {
        return nodes.stream().allMatch(node -> node.position().isFinite());
    }

    private void stopForLimit() {
        if (diagnostics.isEmpty())
            diagnostics.add("Maximum iteration count reached without convergence.");
        status = GeometryDiscoveryState.Status.STOPPED;
    }
}
