package remframework.geometry.discovery.measurement.regularity;

import org.joml.Vector3d;
import remframework.geometry.discovery.DiscoveryConnection;
import remframework.geometry.discovery.configuration.Configuration;
import remframework.geometry.discovery.experiment.AngleConditionMode;

import java.util.ArrayList;
import java.util.List;

/** Qualifies and measures a single ordered boundary cycle in its deterministic reference plane. */
public final class TwoDRegularityCalculator {
    public TwoDRegularityMetrics calculate(Configuration graph, List<Vector3d> coordinates,
            List<Integer> cycle, double edgeTarget, double edgeTolerance,
            double planarityTolerance, double degeneracyTolerance,
            double angleTolerance, AngleConditionMode angleMode) {
        List<String> failures = new ArrayList<>(); int n = cycle.size();
        boolean topology = n == graph.nodeCount() && n >= 3 && cycle.stream().distinct().count() == n;
        if (!topology) failures.add("Immutable topology is not one simple boundary cycle containing all vertices.");
        Vector3d centroid = new Vector3d(); cycle.forEach(i -> centroid.add(coordinates.get(i))); centroid.div(Math.max(1, n));
        Vector3d normal = new Vector3d();
        for (int i = 0; i < n; i++) { Vector3d p = coordinates.get(cycle.get(i)), q = coordinates.get(cycle.get((i + 1) % n));
            normal.x += (p.y - q.y) * (p.z + q.z); normal.y += (p.z - q.z) * (p.x + q.x); normal.z += (p.x - q.x) * (p.y + q.y); }
        boolean nondegenerateNormal = normal.lengthSquared() > degeneracyTolerance * degeneracyTolerance;
        if (nondegenerateNormal) normal.normalize(); else normal.set(0, 0, 1);
        List<Double> distances = cycle.stream().map(i -> Math.abs(new Vector3d(coordinates.get(i)).sub(centroid).dot(normal))).toList();
        boolean planar = distances.stream().allMatch(d -> d <= planarityTolerance);
        if (!planar) failures.add("A boundary vertex exceeds the planarity tolerance.");
        Vector3d axisU = projectedAxis(normal), axisV = new Vector3d(normal).cross(axisU).normalize();
        List<Point> points = cycle.stream().map(i -> { Vector3d d = new Vector3d(coordinates.get(i)).sub(centroid); return new Point(d.dot(axisU), d.dot(axisV)); }).toList();
        boolean simple = !selfIntersects(points);
        if (!simple) failures.add("Boundary cycle self-intersects.");
        double signedArea = signedArea(points), area = Math.abs(signedArea);
        boolean positiveArea = area > degeneracyTolerance && nondegenerateNormal;
        if (!positiveArea) failures.add("Enclosed area does not exceed the degeneracy tolerance.");
        List<Double> edges = new ArrayList<>(), interior = new ArrayList<>(), turning = new ArrayList<>(), radii = new ArrayList<>();
        boolean consecutiveDistinct = true;
        for (int i = 0; i < n; i++) {
            Vector3d previous = coordinates.get(cycle.get((i - 1 + n) % n));
            Vector3d current = coordinates.get(cycle.get(i)); Vector3d next = coordinates.get(cycle.get((i + 1) % n));
            double length = current.distance(next); edges.add(length); if (length <= degeneracyTolerance) consecutiveDistinct = false;
            Vector3d incoming = new Vector3d(previous).sub(current), outgoing = new Vector3d(next).sub(current);
            double angle = Math.acos(clamp(incoming.dot(outgoing) / Math.max(1e-300, incoming.length() * outgoing.length()))); interior.add(angle);
            Point a = points.get((i - 1 + n) % n), b = points.get(i), c = points.get((i + 1) % n);
            double cross = (b.x-a.x)*(c.y-b.y)-(b.y-a.y)*(c.x-b.x);
            double dot = (b.x-a.x)*(c.x-b.x)+(b.y-a.y)*(c.y-b.y);
            turning.add(Math.copySign(Math.atan2(Math.abs(cross), dot), signedArea));
            radii.add(current.distance(centroid));
        }
        if (!consecutiveDistinct) failures.add("Consecutive boundary vertices coincide.");
        Statistics edgeStats = Statistics.of(edges), angleStats = Statistics.of(interior);
        double targetInterior = (n - 2.0) * Math.PI / n, targetTurn = 2 * Math.PI / n;
        double maxDeviation = interior.stream().mapToDouble(v -> Math.abs(v - targetInterior)).max().orElse(Double.NaN);
        boolean equalEdges = edges.stream().allMatch(v -> Math.abs(v - edgeTarget) <= edgeTolerance);
        if (!equalEdges) failures.add("Connected-edge target tolerance is not satisfied.");
        boolean equalAngles = maxDeviation <= angleTolerance;
        if (angleMode == AngleConditionMode.REQUIRE_EQUAL_ANGLES && !equalAngles)
            failures.add("Interior angles exceed the explicitly required equality tolerance.");
        double perimeter = edges.stream().mapToDouble(Double::doubleValue).sum();
        double circularity = perimeter > 0 ? 4 * Math.PI * area / (perimeter * perimeter) : Double.NaN;
        boolean valid = topology && planar && simple && positiveArea && consecutiveDistinct && equalEdges
                && (angleMode != AngleConditionMode.REQUIRE_EQUAL_ANGLES || equalAngles);
        return new TwoDRegularityMetrics(valid, planar, simple, signedArea != 0, positiveArea,
                equalEdges, equalAngles, distances, normal, edgeStats, angleStats,
                Statistics.of(turning), Statistics.of(radii), targetInterior, targetTurn,
                maxDeviation, area, perimeter, circularity, failures);
    }
    private static Vector3d projectedAxis(Vector3d n) {
        Vector3d seed = Math.abs(n.x) < .8 ? new Vector3d(1,0,0) : new Vector3d(0,1,0);
        return seed.sub(new Vector3d(n).mul(seed.dot(n))).normalize();
    }
    private static boolean selfIntersects(List<Point> p) {
        for (int i=0;i<p.size();i++) for(int j=i+1;j<p.size();j++) {
            if (j==i+1 || i==0&&j==p.size()-1) continue;
            if (segments(p.get(i),p.get((i+1)%p.size()),p.get(j),p.get((j+1)%p.size()))) return true;
        } return false;
    }
    private static boolean segments(Point a, Point b, Point c, Point d) {
        double o1=orient(a,b,c),o2=orient(a,b,d),o3=orient(c,d,a),o4=orient(c,d,b);
        return o1*o2 < -1e-14 && o3*o4 < -1e-14;
    }
    private static double orient(Point a,Point b,Point c){return(b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);}
    private static double signedArea(List<Point> p){double a=0;for(int i=0;i<p.size();i++){Point x=p.get(i),y=p.get((i+1)%p.size());a+=x.x*y.y-y.x*x.y;}return a/2;}
    private static double clamp(double v){return Math.max(-1,Math.min(1,v));}
    private record Point(double x,double y){}
}
