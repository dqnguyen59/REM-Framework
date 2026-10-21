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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GeometryDiscoveryExamples {
    private GeometryDiscoveryExamples() {
    }

    public static GeometryDiscoveryModel create(String name, double targetDistance) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "line" -> line(targetDistance);
            case "square", "square-cycle" -> squareCycle(targetDistance);
            case "diamond", "diamond-reference" -> remframework.geometry.discovery.research.diamond.DiamondReference.create(3, targetDistance);
            case "tetrahedron" -> tetrahedron(targetDistance);
            case "cube", "cubic", "small-cubic" -> smallCubic(targetDistance);
            default -> throw new IllegalArgumentException("Unknown discovery model '" + name
                    + "'. Available models: line, square, tetrahedron, cube, diamond-reference.");
        };
    }

    public static GeometryDiscoveryModel line(double d) {
        return model("line", "Four-node line discovery example",
                List.of(p(0, 0, 0, 0, true), p(1, d * 1.3, 0, 0, false),
                        p(2, d * 2.1, d * 0.15, 0, false),
                        p(3, d * 3.4, -d * 0.1, 0, false)),
                new int[][]{{0, 1}, {1, 2}, {2, 3}});
    }

    public static GeometryDiscoveryModel squareCycle(double d) {
        return model("square-cycle", "Four-node square cycle discovery example",
                List.of(p(0, 0, 0, 0, true), p(1, d * 1.25, 0, 0, false),
                        p(2, d * 1.1, d * 0.8, 0, false),
                        p(3, -d * 0.1, d * 1.15, 0, false)),
                new int[][]{{0, 1}, {1, 2}, {2, 3}, {3, 0}});
    }

    public static GeometryDiscoveryModel tetrahedron(double d) {
        double h2 = Math.sqrt(3.0) / 2.0 * d;
        double h3 = Math.sqrt(2.0 / 3.0) * d;
        return model("tetrahedron", "Four-node complete tetrahedral graph",
                List.of(p(0, 0, 0, 0, true), p(1, d * 1.18, 0, 0, false),
                        p(2, d * 0.42, h2 * 1.12, d * 0.04, false),
                        p(3, d * 0.55, h2 / 3.0, h3 * 0.82, false)),
                new int[][]{{0, 1}, {0, 2}, {0, 3}, {1, 2}, {1, 3}, {2, 3}});
    }

    public static GeometryDiscoveryModel smallCubic(double d) {
        List<DiscoveryNode> nodes = new ArrayList<>();
        for (int z = 0; z < 2; z++)
            for (int y = 0; y < 2; y++)
                for (int x = 0; x < 2; x++) {
                    int id = z * 4 + y * 2 + x;
                    nodes.add(p(id, x * d, y * d, z * d, id == 0));
                }
        List<DiscoveryConnection> connections = new ArrayList<>();
        for (int z = 0; z < 2; z++)
            for (int y = 0; y < 2; y++)
                for (int x = 0; x < 2; x++) {
                    int id = z * 4 + y * 2 + x;
                    if (x < 1) connections.add(new DiscoveryConnection(id, id + 1));
                    if (y < 1) connections.add(new DiscoveryConnection(id, id + 2));
                    if (z < 1) connections.add(new DiscoveryConnection(id, id + 4));
                }
        return new GeometryDiscoveryModel("small-cubic",
                "Eight-node supplied cubic topology", nodes, connections);
    }

    private static GeometryDiscoveryModel model(String name, String description,
                                                List<DiscoveryNode> nodes, int[][] edges) {
        List<DiscoveryConnection> connections = new ArrayList<>();
        for (int[] edge : edges) connections.add(new DiscoveryConnection(edge[0], edge[1]));
        return new GeometryDiscoveryModel(name, description, nodes, connections);
    }

    private static DiscoveryNode p(int id, double x, double y, double z, boolean fixed) {
        return new DiscoveryNode(id, new Vector3d(x, y, z), fixed);
    }
}
