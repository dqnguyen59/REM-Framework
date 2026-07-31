package remframework.geometry.discovery;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Incremental velocity-based relaxation for a fixed regular graph.
 */
public final class DynamicElasticSession {
    private static final double EPSILON = 1e-12;
    private final FixedDegreeGraph graph;
    private DynamicElasticSettings settings;
    private final List<DynamicElasticNode> initialNodes;
    private List<DynamicElasticNode> nodes;
    private Map<Integer, DynamicElasticNode> byId;
    private DynamicElasticState state = DynamicElasticState.INITIALIZING;
    private int iteration;
    private int stableIterations;
    private final long startedNanos = System.nanoTime();
    private String failureReason = "";

    public DynamicElasticSession(FixedDegreeGraph graph, DynamicElasticSettings settings) {
        this(graph, settings, initialize(graph.nodeCount(), settings));
    }

    public DynamicElasticSession(FixedDegreeGraph graph, DynamicElasticSettings settings,
                                 List<DynamicElasticNode> initialNodes) {
        this.graph = graph;
        this.settings = settings;
        validateInitialNodes(graph, initialNodes);
        this.initialNodes = initialNodes.stream().map(DynamicElasticNode::copy).toList();
        reset();
    }

    public boolean step() {
        if (finished()) return false;
        state = DynamicElasticState.RELAXING;
        Map<Integer, Vector3d> forces = calculateForces();
        clampForces(forces);
        integrate(forces);
        applyAngularDamping();
        enforceAnchors();
        iteration++;
        if (!finite()) {
            state = DynamicElasticState.NUMERICAL_FAILURE;
            failureReason = "A non-finite position, velocity, or metric was produced.";
            return false;
        }
        DynamicElasticMetrics current = calculateMetrics();
        boolean equilibriumConditions = current.connectionsSatisfied()
                == current.connectionCount()
                && current.maximumNodeSpeed() <= settings.maximumEquilibriumSpeed();
        stableIterations = equilibriumConditions ? stableIterations + 1 : 0;
        if (stableIterations >= settings.requiredStableIterations())
            state = DynamicElasticState.EQUILIBRIUM_REACHED;
        else if (iteration >= settings.maximumIterations())
            state = DynamicElasticState.MAX_ITERATIONS_REACHED;
        else if (equilibriumConditions)
            state = DynamicElasticState.NEAR_EQUILIBRIUM;
        return true;
    }

    public DynamicElasticResult runUntilFinished() {
        while (!finished()) step();
        return result();
    }

    public void reset() {
        nodes = initialNodes.stream().map(DynamicElasticNode::copy).toList();
        byId = new HashMap<>();
        nodes.forEach(node -> byId.put(node.id(), node));
        iteration = 0;
        stableIterations = 0;
        failureReason = "";
        state = DynamicElasticState.INITIALIZING;
        enforceAnchors();
    }

    public Map<Integer, Vector3d> calculateForces() {
        Map<Integer, Vector3d> forces = new HashMap<>();
        nodes.forEach(node -> forces.put(node.id(), new Vector3d()));
        for (DiscoveryConnection edge : graph.connections()) {
            DynamicElasticNode first = byId.get(edge.firstNodeId());
            DynamicElasticNode second = byId.get(edge.secondNodeId());
            Vector3d displacement = new Vector3d(second.position()).sub(first.position());
            double distance = displacement.length();
            Vector3d direction = distance > EPSILON ? displacement.div(distance)
                    : deterministicDirection(edge.undirectedKey());
            double error = distance - settings.targetDistance();
            Vector3d force = direction.mul(settings.springStrength() * error);
            forces.get(first.id()).add(force);
            forces.get(second.id()).sub(force);
        }
        new DynamicElasticSpatialConstraints().addForces(graph, nodes, forces,
                settings.spatial(), settings.randomSeed());
        return forces;
    }

    private void clampForces(Map<Integer, Vector3d> forces) {
        for (Vector3d force : forces.values()) clamp(force, settings.maximumForce());
    }

    private void integrate(Map<Integer, Vector3d> forces) {
        for (DynamicElasticNode node : nodes) {
            if (node.fixed()) {
                node.velocity().zero();
                continue;
            }
            node.velocity().mul(settings.velocityRetention())
                    .add(new Vector3d(forces.get(node.id())).mul(settings.updateStrength()))
                    .mul(Math.max(0, 1 - settings.linearVelocityDamping()));
            clamp(node.velocity(), settings.maximumVelocity());
            node.position().add(node.velocity());
        }
    }

    private void applyAngularDamping() {
        if (settings.angularVelocityDamping() <= 0) return;
        double momentumX = 0;
        double inertiaX = 0;
        for (DynamicElasticNode node : nodes) {
            if (node.fixed()) continue;
            momentumX += node.position().y * node.velocity().z
                    - node.position().z * node.velocity().y;
            inertiaX += node.position().y * node.position().y
                    + node.position().z * node.position().z;
        }
        if (inertiaX <= EPSILON) return;
        double omega = momentumX / inertiaX * settings.angularVelocityDamping();
        for (DynamicElasticNode node : nodes) {
            if (node.fixed()) continue;
            node.velocity().sub(0, -omega * node.position().z,
                    omega * node.position().y);
        }
    }

    private void enforceAnchors() {
        DynamicElasticNode first = byId.get(0);
        DynamicElasticNode second = byId.get(1);
        if (first != null) { first.position().zero(); first.velocity().zero(); }
        if (second != null) {
            second.position().set(settings.targetDistance(), 0, 0);
            second.velocity().zero();
        }
    }

    public DynamicElasticMetrics metrics() {
        return calculateMetrics();
    }

    private DynamicElasticMetrics calculateMetrics() {
        List<DynamicElasticEdgeMetric> edgeMetrics = new ArrayList<>();
        int satisfied = 0;
        double accuracySum = 0;
        for (DiscoveryConnection edge : graph.connections()) {
            double distance = byId.get(edge.firstNodeId()).position()
                    .distance(byId.get(edge.secondNodeId()).position());
            double relativeError = Math.abs(distance - settings.targetDistance())
                    / settings.targetDistance();
            double accuracy = Math.max(0, Math.min(1, 1 - relativeError));
            boolean edgeSatisfied = accuracy >= settings.requiredAccuracy();
            if (edgeSatisfied) satisfied++;
            accuracySum += accuracy;
            edgeMetrics.add(new DynamicElasticEdgeMetric(edge.firstNodeId(),
                    edge.secondNodeId(), distance, settings.targetDistance(),
                    relativeError, accuracy, edgeSatisfied));
        }
        double maximumSpeed = nodes.stream().filter(node -> !node.fixed())
                .mapToDouble(node -> node.velocity().length()).max().orElse(0);
        SpatialMetrics spatial = spatialMetrics();
        int edgeCount = edgeMetrics.size();
        return new DynamicElasticMetrics(iteration, satisfied, edgeCount,
                edgeCount == 0 ? 1 : accuracySum / edgeCount,
                edgeCount == 0 ? 100 : 100.0 * satisfied / edgeCount,
                maximumSpeed, stableIterations, settings.requiredStableIterations(),
                state, edgeMetrics, spatial.minimumNonNeighborDistance(),
                spatial.edgeClearanceViolations(), spatial.meanLocalSpread(),
                spatial.minimumLocalSpread(),
                state == DynamicElasticState.EQUILIBRIUM_REACHED,
                spatial.spatiallyValid());
    }

    private SpatialMetrics spatialMetrics() {
        java.util.Set<Long> edgeKeys = graph.connections().stream()
                .map(DiscoveryConnection::undirectedKey)
                .collect(java.util.stream.Collectors.toSet());
        double minimumNonNeighbor = Double.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) for (int j = i + 1; j < nodes.size(); j++) {
            DynamicElasticNode a = nodes.get(i), b = nodes.get(j);
            long key = ((long) Math.min(a.id(), b.id()) << 32)
                    | (Math.max(a.id(), b.id()) & 0xffffffffL);
            if (!edgeKeys.contains(key))
                minimumNonNeighbor = Math.min(minimumNonNeighbor,
                        a.position().distance(b.position()));
        }
        int clearanceViolations = 0;
        List<DiscoveryConnection> edges = graph.connections();
        for (int i = 0; i < edges.size(); i++) for (int j = i + 1; j < edges.size(); j++) {
            DiscoveryConnection a = edges.get(i), b = edges.get(j);
            if (DynamicElasticSpatialConstraints.shareEndpoint(a, b)) continue;
            double distance = DynamicElasticSpatialConstraints.closestPoints(
                    byId.get(a.firstNodeId()).position(), byId.get(a.secondNodeId()).position(),
                    byId.get(b.firstNodeId()).position(), byId.get(b.secondNodeId()).position())
                    .distance();
            if (distance < settings.spatial().edgeClearanceDistance()) clearanceViolations++;
        }
        Map<Integer, java.util.Set<Integer>> adjacency = new HashMap<>();
        for (int id = 0; id < graph.nodeCount(); id++) adjacency.put(id, new java.util.TreeSet<>());
        for (DiscoveryConnection edge : edges) {
            adjacency.get(edge.firstNodeId()).add(edge.secondNodeId());
            adjacency.get(edge.secondNodeId()).add(edge.firstNodeId());
        }
        double spreadSum = 0, minimumSpread = Double.MAX_VALUE;
        int spreadCount = 0;
        for (int id = 0; id < graph.nodeCount(); id++) {
            if (adjacency.get(id).size() != 3) continue;
            int[] neighbor = adjacency.get(id).stream().mapToInt(Integer::intValue).toArray();
            Vector3d origin = byId.get(id).position();
            double spread = DynamicElasticSpatialConstraints.localSpread(
                    new Vector3d(byId.get(neighbor[0]).position()).sub(origin),
                    new Vector3d(byId.get(neighbor[1]).position()).sub(origin),
                    new Vector3d(byId.get(neighbor[2]).position()).sub(origin));
            spreadSum += spread;
            minimumSpread = Math.min(minimumSpread, spread);
            spreadCount++;
        }
        if (spreadCount == 0) minimumSpread = 0;
        boolean finiteGeometry = nodes.stream().allMatch(n -> n.position().isFinite());
        boolean valid = finiteGeometry
                && minimumNonNeighbor >= settings.spatial().minimumNonNeighborDistance()
                && clearanceViolations == 0;
        return new SpatialMetrics(minimumNonNeighbor, clearanceViolations,
                spreadCount == 0 ? 0 : spreadSum / spreadCount, minimumSpread, valid);
    }

    private record SpatialMetrics(double minimumNonNeighborDistance,
                                  int edgeClearanceViolations,
                                  double meanLocalSpread,
                                  double minimumLocalSpread,
                                  boolean spatiallyValid) {}

    public DynamicElasticResult result() {
        DynamicElasticResultStatus status = switch (state) {
            case EQUILIBRIUM_REACHED -> DynamicElasticResultStatus.STABLE_CANDIDATE_FOUND;
            case NUMERICAL_FAILURE -> DynamicElasticResultStatus.NUMERICAL_FAILURE;
            default -> DynamicElasticResultStatus.NO_EQUILIBRIUM_FOUND;
        };
        return new DynamicElasticResult("", graph, settings, initialNodes, nodes, metrics(),
                status, failureReason, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    public GeometryDiscoveryModel geometryModel() {
        List<DiscoveryNode> discoveryNodes = nodes.stream().map(node ->
                new DiscoveryNode(node.id(), node.position(), node.fixed())).toList();
        return new GeometryDiscoveryModel("dynamic-elastic-k" + graph.degree()
                + "-n" + graph.nodeCount(), "Fixed-degree dynamic elastic candidate",
                discoveryNodes, graph.connections());
    }

    public void updateSettings(DynamicElasticSettings value) {
        if (value.maximumIterations() < iteration)
            throw new IllegalArgumentException("Maximum iterations cannot be below "
                    + "the current iteration.");
        boolean mayContinue = state == DynamicElasticState.MAX_ITERATIONS_REACHED
                && value.maximumIterations() > iteration;
        settings = value;
        if (mayContinue) state = DynamicElasticState.RELAXING;
    }
    public List<DynamicElasticNode> nodes() {
        return nodes.stream().map(DynamicElasticNode::copy).toList();
    }
    public void setNodeState(int id, Vector3d position, Vector3d velocity) {
        DynamicElasticNode node = byId.get(id);
        if (node == null) throw new IllegalArgumentException("Unknown node ID " + id);
        if (node.fixed()) throw new IllegalArgumentException("Anchor nodes cannot be moved.");
        node.position().set(position);
        node.velocity().set(velocity);
        stableIterations = 0;
        state = DynamicElasticState.RELAXING;
    }
    public FixedDegreeGraph graph() { return graph; }
    public DynamicElasticSettings settings() { return settings; }
    public DynamicElasticState state() { return state; }
    public int iteration() { return iteration; }
    public boolean finished() {
        return state == DynamicElasticState.EQUILIBRIUM_REACHED
                || state == DynamicElasticState.MAX_ITERATIONS_REACHED
                || state == DynamicElasticState.NUMERICAL_FAILURE;
    }

    private boolean finite() {
        return nodes.stream().allMatch(node -> node.position().isFinite()
                && node.velocity().isFinite()) && metrics().allFinite();
    }
    private void clamp(Vector3d vector, double maximum) {
        double length = vector.length();
        if (length > maximum && length > 0) vector.mul(maximum / length);
    }
    private Vector3d deterministicDirection(long key) {
        SplittableRandom random = new SplittableRandom(key ^ settings.randomSeed());
        Vector3d value = new Vector3d(random.nextDouble(-1, 1),
                random.nextDouble(-1, 1), random.nextDouble(-1, 1));
        return value.lengthSquared() < EPSILON ? new Vector3d(1, 0, 0) : value.normalize();
    }
    private static List<DynamicElasticNode> initialize(int count,
                                                       DynamicElasticSettings settings) {
        SplittableRandom random = new SplittableRandom(settings.randomSeed());
        List<DynamicElasticNode> result = new ArrayList<>();
        for (int id = 0; id < count; id++) {
            Vector3d position;
            if (id == 0) position = new Vector3d();
            else if (id == 1) position = new Vector3d(settings.targetDistance(), 0, 0);
            else {
                double angle = random.nextDouble() * Math.PI * 2;
                double z = random.nextDouble(-1, 1);
                double planar = Math.sqrt(1 - z * z);
                position = new Vector3d(planar * Math.cos(angle),
                        planar * Math.sin(angle), z)
                        .mul(settings.targetDistance() * Math.max(1, Math.cbrt(count)));
            }
            result.add(new DynamicElasticNode(id, position, new Vector3d(), id < 2));
        }
        return result;
    }
    private static void validateInitialNodes(FixedDegreeGraph graph,
                                             List<DynamicElasticNode> initialNodes) {
        if (initialNodes.size() != graph.nodeCount())
            throw new IllegalArgumentException("Initial state must contain every graph node.");
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (DynamicElasticNode node : initialNodes) {
            if (node.id() < 0 || node.id() >= graph.nodeCount() || !ids.add(node.id()))
                throw new IllegalArgumentException("Initial node IDs must be unique and in range.");
            if (!node.position().isFinite() || !node.velocity().isFinite())
                throw new IllegalArgumentException("Initial positions and velocities must be finite.");
        }
        if (!ids.contains(0) || !ids.contains(1))
            throw new IllegalArgumentException("Anchor nodes 0 and 1 are required.");
    }
}
