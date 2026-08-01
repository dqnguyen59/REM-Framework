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

import java.util.Map;
import java.util.Set;

/**
 * Canonical geometric angle forces. These are non-topology constraints: they
 * neither create connections nor change graph degree.
 */
public final class DynamicElasticAngleConstraints {
    private static final double EPSILON = 1e-12;

    public void addForces(FixedDegreeGraph graph, Map<Integer, DynamicElasticNode> nodes,
                          Map<Integer, Vector3d> forces,
                          DynamicElasticCanonicalGeometrySettings settings) {
        if (!settings.angleConstraintsEnabled() || graph.canonicalTopology() == null) return;
        Map<Integer, Set<Integer>> adjacency = adjacency(graph);
        double targetCosine = Math.cos(Math.toRadians(settings.targetAngleDegrees()));
        for (int center = 0; center < graph.nodeCount(); center++) {
            int[] neighbors = adjacency.get(center).stream().mapToInt(Integer::intValue)
                    .sorted().toArray();
            for (int first = 0; first < neighbors.length; first++)
                for (int second = first + 1; second < neighbors.length; second++)
                    addPair(center, neighbors[first], neighbors[second], nodes, forces,
                            targetCosine, settings.angleStrength());
        }
    }

    private void addPair(int centerId, int firstId, int secondId,
                         Map<Integer, DynamicElasticNode> nodes,
                         Map<Integer, Vector3d> forces,
                         double targetCosine, double strength) {
        Vector3d a = new Vector3d(nodes.get(firstId).position())
                .sub(nodes.get(centerId).position());
        Vector3d b = new Vector3d(nodes.get(secondId).position())
                .sub(nodes.get(centerId).position());
        double aLength = a.length(), bLength = b.length();
        if (aLength <= EPSILON || bLength <= EPSILON) return;
        Vector3d u = a.div(aLength), v = b.div(bLength);
        double dot = Math.max(-1, Math.min(1, u.dot(v)));
        double factor = 2 * strength * (dot - targetCosine);
        Vector3d gradientA = new Vector3d(v).fma(-dot, u).mul(factor / aLength);
        Vector3d gradientB = new Vector3d(u).fma(-dot, v).mul(factor / bLength);
        // Force is the negative energy gradient. The center receives the
        // opposite sum, preserving translation symmetry and net zero force.
        forces.get(firstId).sub(gradientA);
        forces.get(secondId).sub(gradientB);
        forces.get(centerId).add(gradientA).add(gradientB);
    }

    public static double energy(Vector3d first, Vector3d second,
                                double targetAngleDegrees, double strength) {
        if (first.lengthSquared() <= EPSILON || second.lengthSquared() <= EPSILON) return 0;
        double dot = new Vector3d(first).normalize().dot(new Vector3d(second).normalize());
        double error = dot - Math.cos(Math.toRadians(targetAngleDegrees));
        return strength * error * error;
    }

    private Map<Integer, Set<Integer>> adjacency(FixedDegreeGraph graph) {
        Map<Integer, Set<Integer>> result = new java.util.HashMap<>();
        for (int id = 0; id < graph.nodeCount(); id++) result.put(id, new java.util.TreeSet<>());
        for (DiscoveryConnection edge : graph.connections()) {
            result.get(edge.firstNodeId()).add(edge.secondNodeId());
            result.get(edge.secondNodeId()).add(edge.firstNodeId());
        }
        return result;
    }
}
