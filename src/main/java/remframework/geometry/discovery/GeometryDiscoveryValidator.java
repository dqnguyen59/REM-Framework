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

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class GeometryDiscoveryValidator {
    public DiscoveryValidationResult validate(GeometryDiscoveryModel model,
                                              GeometryDiscoverySettings settings) {
        DiscoveryValidationResult result = new DiscoveryValidationResult();
        validateSettings(settings, result);
        Map<Integer, DiscoveryNode> nodes = new HashMap<>();
        for (DiscoveryNode node : model.nodes()) {
            if (nodes.put(node.id(), node) != null)
                result.error("DUPLICATE_NODE_ID", "Duplicate node ID: " + node.id());
            if (!node.position().isFinite())
                result.error("INVALID_POSITION", "Node " + node.id() + " has a non-finite position.");
        }
        DiscoveryNode anchor = nodes.get(0);
        if (anchor == null)
            result.error("MISSING_ANCHOR", "Anchor node 0 is required.");
        else if (!anchor.fixedPosition())
            result.error("UNFIXED_ANCHOR", "Anchor node 0 must have fixedPosition=true.");
        if (!nodes.containsKey(1))
            result.error("MISSING_ORIENTATION_NODE",
                    "Orientation node 1 is required to constrain the positive x-axis.");

        Set<Long> connections = new HashSet<>();
        for (DiscoveryConnection connection : model.connections()) {
            if (connection.firstNodeId() == connection.secondNodeId())
                result.error("SELF_CONNECTION",
                        "Node " + connection.firstNodeId() + " is connected to itself.");
            if (!connections.add(connection.undirectedKey()))
                result.error("DUPLICATE_CONNECTION", "Duplicate undirected connection: "
                        + connection.lowerId() + " - " + connection.upperId());
            if (!nodes.containsKey(connection.firstNodeId()))
                result.error("MISSING_ENDPOINT",
                        "Connection endpoint does not exist: " + connection.firstNodeId());
            if (!nodes.containsKey(connection.secondNodeId()))
                result.error("MISSING_ENDPOINT",
                        "Connection endpoint does not exist: " + connection.secondNodeId());
        }
        int components = componentCount(model, nodes);
        if (!nodes.isEmpty() && components != 1)
            result.error("DISCONNECTED_GRAPH",
                    "Discovery graph has " + components + " connected components.");
        return result;
    }

    private void validateSettings(GeometryDiscoverySettings settings,
                                  DiscoveryValidationResult result) {
        positiveFinite(settings.targetDistance(), "targetDistance", result);
        positiveFinite(settings.learningRate(), "learningRate", result);
        if (settings.maximumIterations() <= 0)
            result.error("INVALID_SETTING", "maximumIterations must be positive.");
        positiveFinite(settings.convergenceTolerance(), "convergenceTolerance", result);
        positiveFinite(settings.minimumNodeSeparation(), "minimumNodeSeparation", result);
        if (!Double.isFinite(settings.repulsionStrength()) || settings.repulsionStrength() < 0)
            result.error("INVALID_SETTING", "repulsionStrength must be finite and non-negative.");
        positiveFinite(settings.maximumCorrection(), "maximumCorrection", result);
        positiveFinite(settings.dimensionalityTolerance(), "dimensionalityTolerance", result);
        if (settings.progressInterval() <= 0)
            result.error("INVALID_SETTING", "progressInterval must be positive.");
    }

    private void positiveFinite(double value, String name, DiscoveryValidationResult result) {
        if (!Double.isFinite(value) || value <= 0)
            result.error("INVALID_SETTING", name + " must be finite and positive.");
    }

    static int componentCount(GeometryDiscoveryModel model, Map<Integer, DiscoveryNode> nodes) {
        if (nodes.isEmpty()) return 0;
        Set<Integer> visited = new HashSet<>();
        int components = 0;
        for (Integer start : nodes.keySet()) {
            if (visited.contains(start)) continue;
            components++;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                int id = queue.remove();
                if (!visited.add(id)) continue;
                DiscoveryNode node = nodes.get(id);
                if (node != null) queue.addAll(node.neighborIds());
            }
        }
        return components;
    }
}
