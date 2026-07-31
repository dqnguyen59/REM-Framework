package remframework.geometry.discovery;

import org.joml.Vector3d;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic, topology-preserving spatial regularization. All corrections are
 * accumulated as forces; fixed anchors participate in force calculation and are
 * excluded only by the session integrator.
 */
public final class DynamicElasticSpatialConstraints {
    private static final double EPSILON = 1e-12;

    public void addForces(FixedDegreeGraph graph, List<DynamicElasticNode> nodes,
                          Map<Integer, Vector3d> forces,
                          DynamicElasticSpatialSettings settings, long seed) {
        Map<Integer, DynamicElasticNode> byId = nodes.stream().collect(
                java.util.stream.Collectors.toMap(DynamicElasticNode::id, n -> n));
        Set<Long> edges = graph.connections().stream().map(DiscoveryConnection::undirectedKey)
                .collect(java.util.stream.Collectors.toSet());
        if (settings.nonNeighborExclusionEnabled())
            addNonNeighborForces(nodes, forces, edges, settings, seed);
        if (settings.edgeCrossingPreventionEnabled())
            addEdgeClearanceForces(graph.connections(), byId, forces, settings, seed);
        if (settings.localSpreadEnabled())
            addLocalSpreadForces(graph, byId, forces, settings);
    }

    private void addNonNeighborForces(List<DynamicElasticNode> nodes,
            Map<Integer, Vector3d> forces, Set<Long> edges,
            DynamicElasticSpatialSettings settings, long seed) {
        double threshold = settings.minimumNonNeighborDistance();
        for (int i = 0; i < nodes.size(); i++) for (int j = i + 1; j < nodes.size(); j++) {
            DynamicElasticNode a = nodes.get(i), b = nodes.get(j);
            long key = edgeKey(a.id(), b.id());
            if (edges.contains(key)) continue;
            Vector3d delta = new Vector3d(b.position()).sub(a.position());
            double distance = delta.length();
            if (distance >= threshold) continue;
            Vector3d direction = distance > EPSILON ? delta.div(distance)
                    : deterministicDirection(key, seed);
            // Quadratic falloff is smooth at the threshold and bounded by strength.
            double ratio = threshold <= EPSILON ? 0 : (threshold - distance) / threshold;
            Vector3d force = direction.mul(settings.strength() * ratio * ratio);
            forces.get(a.id()).sub(force);
            forces.get(b.id()).add(force);
        }
    }

    private void addEdgeClearanceForces(List<DiscoveryConnection> edges,
            Map<Integer, DynamicElasticNode> byId, Map<Integer, Vector3d> forces,
            DynamicElasticSpatialSettings settings, long seed) {
        for (int i = 0; i < edges.size(); i++) for (int j = i + 1; j < edges.size(); j++) {
            DiscoveryConnection first = edges.get(i), second = edges.get(j);
            if (shareEndpoint(first, second)) continue;
            SegmentClosest closest = closestPoints(
                    byId.get(first.firstNodeId()).position(),
                    byId.get(first.secondNodeId()).position(),
                    byId.get(second.firstNodeId()).position(),
                    byId.get(second.secondNodeId()).position());
            if (closest.distance() >= settings.edgeClearanceDistance()) continue;
            Vector3d separation = new Vector3d(closest.second()).sub(closest.first());
            Vector3d direction = closest.distance() > EPSILON
                    ? separation.div(closest.distance())
                    : deterministicDirection(first.undirectedKey() ^ second.undirectedKey(), seed);
            double threshold = settings.edgeClearanceDistance();
            double ratio = threshold <= EPSILON ? 0 : (threshold - closest.distance()) / threshold;
            Vector3d correction = direction.mul(settings.strength() * ratio * ratio);
            distribute(forces, first, correction, closest.firstParameter(), -1);
            distribute(forces, second, correction, closest.secondParameter(), 1);
        }
    }

    private void addLocalSpreadForces(FixedDegreeGraph graph,
            Map<Integer, DynamicElasticNode> byId, Map<Integer, Vector3d> forces,
            DynamicElasticSpatialSettings settings) {
        Map<Integer, Set<Integer>> adjacency = adjacency(graph);
        for (int center = 0; center < graph.nodeCount(); center++) {
            if (byId.get(center).fixed()) continue;
            if (adjacency.get(center).size() != 3) continue;
            int[] neighbors = adjacency.get(center).stream().mapToInt(Integer::intValue)
                    .sorted().toArray();
            Vector3d origin = byId.get(center).position();
            Vector3d e1 = normalized(origin, byId.get(neighbors[0]).position());
            Vector3d e2 = normalized(origin, byId.get(neighbors[1]).position());
            Vector3d e3 = normalized(origin, byId.get(neighbors[2]).position());
            if (e1 == null || e2 == null || e3 == null) continue;
            Vector3d[] directions = {e1, e2, e3};
            // Local angular regularization remains weak even when collision
            // constraints need a stronger bounded separating force.
            double localStrength = settings.strength() * .05;
            for (int i = 0; i < 3; i++) {
                Vector3d normal = new Vector3d(directions[(i + 1) % 3])
                        .cross(directions[(i + 2) % 3]);
                if (normal.lengthSquared() <= EPSILON) continue;
                normal.normalize();
                double signed = normal.dot(directions[i]);
                double sign = Math.abs(signed) > EPSILON ? Math.signum(signed)
                        : (((center + neighbors[i] + i) & 1) == 0 ? 1 : -1);
                Vector3d correction = normal.mul(localStrength * sign
                        * (1 - Math.abs(signed)));
                // Each cyclic gradient component is equal-and-opposite and treats
                // all three incident directions identically.
                forces.get(neighbors[i]).add(correction);
                forces.get(center).sub(correction);
            }
        }
    }

    public static double localSpread(Vector3d e1, Vector3d e2, Vector3d e3) {
        Vector3d a = new Vector3d(e1), b = new Vector3d(e2), c = new Vector3d(e3);
        if (a.lengthSquared() <= EPSILON || b.lengthSquared() <= EPSILON
                || c.lengthSquared() <= EPSILON) return 0;
        return Math.abs(a.normalize().dot(b.normalize().cross(c.normalize())));
    }

    public static SegmentClosest closestPoints(Vector3d p1, Vector3d q1,
                                                Vector3d p2, Vector3d q2) {
        Vector3d d1 = new Vector3d(q1).sub(p1), d2 = new Vector3d(q2).sub(p2);
        Vector3d r = new Vector3d(p1).sub(p2);
        double a = d1.dot(d1), e = d2.dot(d2), f = d2.dot(r);
        double s, t;
        if (a <= EPSILON && e <= EPSILON) return new SegmentClosest(
                new Vector3d(p1), new Vector3d(p2), 0, 0);
        if (a <= EPSILON) { s = 0; t = clamp01(f / e); }
        else {
            double c = d1.dot(r);
            if (e <= EPSILON) { t = 0; s = clamp01(-c / a); }
            else {
                double b = d1.dot(d2), denominator = a * e - b * b;
                s = Math.abs(denominator) > EPSILON ? clamp01((b * f - c * e) / denominator) : 0;
                t = (b * s + f) / e;
                if (t < 0) { t = 0; s = clamp01(-c / a); }
                else if (t > 1) { t = 1; s = clamp01((b - c) / a); }
            }
        }
        return new SegmentClosest(new Vector3d(d1).mul(s).add(p1),
                new Vector3d(d2).mul(t).add(p2), s, t);
    }

    public static boolean shareEndpoint(DiscoveryConnection a, DiscoveryConnection b) {
        return a.firstNodeId() == b.firstNodeId() || a.firstNodeId() == b.secondNodeId()
                || a.secondNodeId() == b.firstNodeId() || a.secondNodeId() == b.secondNodeId();
    }

    private static void distribute(Map<Integer, Vector3d> forces, DiscoveryConnection edge,
            Vector3d correction, double parameter, double sign) {
        forces.get(edge.firstNodeId()).fma(sign * (1 - parameter), correction);
        forces.get(edge.secondNodeId()).fma(sign * parameter, correction);
    }
    private static Vector3d normalized(Vector3d origin, Vector3d end) {
        Vector3d value = new Vector3d(end).sub(origin);
        return value.lengthSquared() <= EPSILON ? null : value.normalize();
    }
    private static Map<Integer, Set<Integer>> adjacency(FixedDegreeGraph graph) {
        Map<Integer, Set<Integer>> result = new java.util.HashMap<>();
        for (int id = 0; id < graph.nodeCount(); id++) result.put(id, new java.util.TreeSet<>());
        for (DiscoveryConnection edge : graph.connections()) {
            result.get(edge.firstNodeId()).add(edge.secondNodeId());
            result.get(edge.secondNodeId()).add(edge.firstNodeId());
        }
        return result;
    }
    private static Vector3d deterministicDirection(long key, long seed) {
        java.util.SplittableRandom random = new java.util.SplittableRandom(key ^ seed);
        Vector3d result = new Vector3d(random.nextDouble(-1, 1),
                random.nextDouble(-1, 1), random.nextDouble(-1, 1));
        return result.lengthSquared() <= EPSILON ? new Vector3d(1, 0, 0) : result.normalize();
    }
    private static long edgeKey(int a, int b) {
        return ((long) Math.min(a, b) << 32) | (Math.max(a, b) & 0xffffffffL);
    }
    private static double clamp01(double value) { return Math.max(0, Math.min(1, value)); }

    public record SegmentClosest(Vector3d first, Vector3d second,
                                 double firstParameter, double secondParameter) {
        public double distance() { return first.distance(second); }
    }
}
