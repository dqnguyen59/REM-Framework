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

public final class DirectionalDiagnosticExamples {
    private DirectionalDiagnosticExamples() { }

    public static GeometryDiscoveryModel lineCenter() {
        return star("line-center", List.of(new Vector3d(1, 0, 0), new Vector3d(-1, 0, 0)));
    }

    public static GeometryDiscoveryModel equilateral2d() {
        List<Vector3d> directions = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double angle = 2 * Math.PI * i / 3;
            directions.add(new Vector3d(Math.cos(angle), Math.sin(angle), 0));
        }
        return star("equilateral-neighborhood", directions);
    }

    public static GeometryDiscoveryModel tetrahedral3d() {
        double s = 1 / Math.sqrt(3);
        return star("tetrahedral-neighborhood", List.of(
                new Vector3d(s, s, s), new Vector3d(s, -s, -s),
                new Vector3d(-s, s, -s), new Vector3d(-s, -s, s)));
    }

    public static GeometryDiscoveryModel regularTetrahedronGraph() {
        double h2 = Math.sqrt(3) / 2;
        double h3 = Math.sqrt(2.0 / 3.0);
        List<DiscoveryNode> nodes = List.of(
                new DiscoveryNode(0, new Vector3d(0, 0, 0), true),
                new DiscoveryNode(1, new Vector3d(1, 0, 0), false),
                new DiscoveryNode(2, new Vector3d(.5, h2, 0), false),
                new DiscoveryNode(3, new Vector3d(.5, h2 / 3, h3), false));
        return new GeometryDiscoveryModel("regular-tetrahedron",
                "Exact regular tetrahedron graph", nodes, List.of(
                new DiscoveryConnection(0, 1), new DiscoveryConnection(0, 2),
                new DiscoveryConnection(0, 3), new DiscoveryConnection(1, 2),
                new DiscoveryConnection(1, 3), new DiscoveryConnection(2, 3)));
    }

    public static GeometryDiscoveryModel octahedral3d() {
        return star("octahedral-neighborhood", List.of(
                new Vector3d(1, 0, 0), new Vector3d(-1, 0, 0),
                new Vector3d(0, 1, 0), new Vector3d(0, -1, 0),
                new Vector3d(0, 0, 1), new Vector3d(0, 0, -1)));
    }

    public static GeometryDiscoveryModel asymmetric() {
        return star("asymmetric-neighborhood", List.of(
                new Vector3d(1, 0, 0), new Vector3d(.9, .2, 0),
                new Vector3d(.8, .1, .1), new Vector3d(-.2, .8, .1)));
    }

    public static GeometryDiscoveryModel nearlyPlanar() {
        return star("nearly-planar", List.of(
                new Vector3d(1, 0, .000001), new Vector3d(-1, 0, -.000001),
                new Vector3d(0, 1, .000001), new Vector3d(0, -1, -.000001)));
    }

    public static GeometryDiscoveryModel directionalGap() {
        return star("directional-gap", List.of(
                new Vector3d(1, 0, 0), new Vector3d(.7, .7, 0),
                new Vector3d(.7, 0, .7), new Vector3d(.7, -.7, 0),
                new Vector3d(.7, 0, -.7)));
    }

    private static GeometryDiscoveryModel star(String name, List<Vector3d> directions) {
        List<DiscoveryNode> nodes = new ArrayList<>();
        List<DiscoveryConnection> edges = new ArrayList<>();
        nodes.add(new DiscoveryNode(0, new Vector3d(), true));
        for (int i = 0; i < directions.size(); i++) {
            nodes.add(new DiscoveryNode(i + 1, new Vector3d(directions.get(i)), false));
            edges.add(new DiscoveryConnection(0, i + 1));
        }
        return new GeometryDiscoveryModel(name, "Exact local directional reference", nodes, edges);
    }
}
