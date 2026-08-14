package remframework.opengl.model;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;
import remframework.opengl.helpers.Neighbor;
import remframework.opengl.model.entity.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.StringJoiner;

/** Pure read-only geometric placement calculations for the Add Probes workspace. */
public final class ProbePlacement {
    private static final double DIRECTION_EPSILON = 1.0e-12;

    private ProbePlacement() {
    }

    public record ProbeReport(int shell, int ray, String rayPlane, int requestedRadius,
                              double realizedRadius, int graphDistance,
                              Vector3i coordinate, boolean collision) {
        public ProbeReport {
            coordinate = new Vector3i(coordinate);
        }

        @Override
        public Vector3i coordinate() {
            return new Vector3i(coordinate);
        }

        private ProbeReport withCollision(boolean value) {
            return new ProbeReport(shell, ray, rayPlane, requestedRadius, realizedRadius,
                                   graphDistance, coordinate, value);
        }
    }

    public record Result(List<ProbeDefinition> definitions, String status, boolean valid,
                         List<ProbeReport> probeReports) {
    }

    private record Ray(String description, Vector3d direction) {
    }

    private record Selection(int shell, int ray, Ray idealRay, int requestedRadius, Node node,
                             double realizedRadius, int graphDistance) {
    }

    private record IndexBounds(int minimumX, int maximumX, int minimumY, int maximumY,
                               int minimumZ, int maximumZ) {
    }

    /**
     * Places passive probes from continuous requested radii and ideal rays.<br><br>
     * Lattice nodes are the nearest discrete realizations; graph distance is calculated only
     * after snapping and never participates in placement.
     */
    public static Result place(Node center, int firstShellRadius, int shellDistance,
                               int numberOfShells, int probesPerPlane, int region,
                               boolean threeDimensional, Vector4f color,
                               Set<Node> blocked, String idPrefix) {
        if (center == null) return invalid("The lattice center is unavailable.");
        if (firstShellRadius < 1) return invalid("First Shell Radius must be at least 1.");
        if (shellDistance < 1) return invalid("Shell Distance must be at least 1.");
        if (numberOfShells < 1) return invalid("Number Of Shells must be at least 1.");
        if (threeDimensional) {
            if (probesPerPlane < 3) {
                return invalid("Probes Per Plane Per Shell must be at least 3 in 3D.");
            }
            if (probesPerPlane % 2 == 0) {
                return invalid("Probes Per Plane Per Shell must be odd in 3D.");
            }
            if (region < 0 || region > 7) return invalid("The selected octant is unavailable.");
        } else {
            if (probesPerPlane < 1) return invalid("Probes Per Shell must be at least 1 in 2D.");
            if (region < 0 || region > 3) return invalid("The selected quadrant is unavailable.");
        }

        List<Integer> requestedRadii = requestedRadii(firstShellRadius, shellDistance,
                                                       numberOfShells);
        if (requestedRadii.isEmpty()) {
            return invalid("Requested shell radius exceeds the supported integer range.");
        }

        List<Ray> rays = threeDimensional ? generateThreeDimensionalRays(probesPerPlane)
                                          : generateTwoDimensionalRays(probesPerPlane);
        int signX = (region & 1) == 0 ? 1 : -1;
        int signY = (region & 2) == 0 ? 1 : -1;
        int signZ = (region & 4) == 0 ? 1 : -1;
        Set<Node> excluded = blocked == null ? Set.of() : blocked;
        IndexBounds bounds = bounds(center);
        List<Selection> selections = new ArrayList<>();

        for (int shell = 0; shell < requestedRadii.size(); shell++) {
            int requestedRadius = requestedRadii.get(shell);
            for (int ray = 0; ray < rays.size(); ray++) {
                Ray idealRay = rays.get(ray);
                Vector3d target = target(center, idealRay.direction(), requestedRadius,
                                         signX, signY, signZ);
                Node selected = snapNearest(center, bounds, target, region,
                                            threeDimensional, signX, signY, signZ, excluded);
                if (selected == null) {
                    return new Result(List.of(), "No eligible lattice node is available for shell "
                            + (shell + 1) + ", ray " + (ray + 1) + " ("
                            + idealRay.description() + ").", false, reports(selections, Set.of()));
                }
                selections.add(new Selection(shell, ray, idealRay, requestedRadius, selected,
                        realizedRadius(center, selected), graphDistance(center, selected)));
            }
        }

        Map<Vector3i, List<Selection>> byCoordinate = new LinkedHashMap<>();
        for (Selection selection : selections) {
            byCoordinate.computeIfAbsent(new Vector3i(selection.node().getIndex()),
                                         ignored -> new ArrayList<>()).add(selection);
        }
        Set<Vector3i> collisionCoordinates = new java.util.HashSet<>();
        StringJoiner collisionStatus = new StringJoiner("; ");
        for (Map.Entry<Vector3i, List<Selection>> entry : byCoordinate.entrySet()) {
            if (entry.getValue().size() < 2) continue;
            collisionCoordinates.add(new Vector3i(entry.getKey()));
            StringJoiner targets = new StringJoiner(", ");
            for (Selection selection : entry.getValue()) {
                targets.add("shell " + (selection.shell() + 1) + " ray "
                        + (selection.ray() + 1) + " (" + selection.idealRay().description() + ")");
            }
            collisionStatus.add(formatCoordinate(entry.getKey()) + " <- " + targets);
        }
        List<ProbeReport> reports = reports(selections, collisionCoordinates);
        if (!collisionCoordinates.isEmpty()) {
            return new Result(List.of(), "Snapped-node collision: " + collisionStatus
                    + ". Change the requested geometry before applying.", false, reports);
        }

        List<ProbeDefinition> definitions = new ArrayList<>();
        for (Selection selection : selections) {
            ProbeDefinition.Placement placement = new ProbeDefinition.Placement(
                    selection.shell(), selection.ray(), selection.idealRay().description(),
                    selection.requestedRadius(), selection.realizedRadius(),
                    selection.graphDistance());
            definitions.add(new ProbeDefinition(idPrefix + "-" + selection.shell() + "-"
                    + selection.ray(), selection.node().getIndex(), color, placement));
        }
        return new Result(List.copyOf(definitions), "Preview: " + definitions.size()
                + " passive probes on " + numberOfShells + " requested shell(s).", true, reports);
    }

    /** Describes stored generated probes using their actual index offsets from the center. */
    public static List<ProbeReport> describe(Node center, List<ProbeDefinition> definitions) {
        if (center == null || definitions.isEmpty()) return List.of();
        List<ProbeReport> reports = new ArrayList<>();
        for (ProbeDefinition definition : definitions) {
            ProbeDefinition.Placement placement = definition.getPlacement();
            if (placement == null) continue;
            Node node = nodeAt(center, definition.getNodeIndex());
            if (node == null) continue;
            reports.add(new ProbeReport(placement.shell(), placement.ray(), placement.rayPlane(),
                    placement.requestedRadius(), realizedRadius(center, node),
                    graphDistance(center, node), node.getIndex(), false));
        }
        Map<Vector3i, Integer> counts = new LinkedHashMap<>();
        for (ProbeReport report : reports) counts.merge(report.coordinate(), 1, Integer::sum);
        return reports.stream().map(report -> report.withCollision(
                counts.getOrDefault(report.coordinate(), 0) > 1)).toList();
    }

    private static List<Integer> requestedRadii(int firstShellRadius, int shellDistance,
                                                int numberOfShells) {
        List<Integer> radii = new ArrayList<>();
        for (int shell = 0; shell < numberOfShells; shell++) {
            long radius = firstShellRadius + (long) shell * shellDistance;
            if (radius > Integer.MAX_VALUE) return List.of();
            radii.add((int) radius);
        }
        return radii;
    }

    /** Generates fixed 2D angles from the selected X direction toward the selected Y direction. */
    private static List<Ray> generateTwoDimensionalRays(int probesPerShell) {
        List<Ray> rays = new ArrayList<>();
        for (int ray = 0; ray < probesPerShell; ray++) {
            double angle = (ray + 1) * 90.0 / probesPerShell;
            double radians = Math.toRadians(angle);
            rays.add(new Ray(String.format(Locale.ROOT, "XY %.3f°", angle),
                    new Vector3d(clean(Math.cos(radians)), clean(Math.sin(radians)), 0.0)));
        }
        return List.copyOf(rays);
    }

    /**
     * Generates odd-P rays in the XY, XZ, and YZ planes, removes their shared axes, and appends
     * one explicit body-diagonal ray. Plane angles run from the first named axis toward the second,
     * and the resulting set is reused unchanged for every shell.
     */
    private static List<Ray> generateThreeDimensionalRays(int probesPerPlane) {
        List<Ray> rays = new ArrayList<>();
        for (String plane : List.of("XY", "XZ", "YZ")) {
            for (int ray = 0; ray < probesPerPlane; ray++) {
                double angle = ray * 90.0 / (probesPerPlane - 1);
                double radians = Math.toRadians(angle);
                double cosine = clean(Math.cos(radians));
                double sine = clean(Math.sin(radians));
                Vector3d direction = switch (plane) {
                    case "XY" -> new Vector3d(cosine, sine, 0.0);
                    case "XZ" -> new Vector3d(cosine, 0.0, sine);
                    case "YZ" -> new Vector3d(0.0, cosine, sine);
                    default -> throw new IllegalStateException("Unsupported plane " + plane);
                };
                addUniqueRay(rays, new Ray(planarDescription(plane, angle, direction), direction));
            }
        }
        double component = 1.0 / Math.sqrt(3.0);
        addUniqueRay(rays, new Ray("XYZ body diagonal",
                                   new Vector3d(component, component, component)));
        return List.copyOf(rays);
    }

    private static String planarDescription(String plane, double angle, Vector3d direction) {
        if (direction.equals(new Vector3d(1.0, 0.0, 0.0))) return "X axis";
        if (direction.equals(new Vector3d(0.0, 1.0, 0.0))) return "Y axis";
        if (direction.equals(new Vector3d(0.0, 0.0, 1.0))) return "Z axis";
        return String.format(Locale.ROOT, "%s %.3f°", plane, angle);
    }

    private static void addUniqueRay(List<Ray> rays, Ray candidate) {
        boolean duplicate = rays.stream().anyMatch(ray -> ray.direction().distanceSquared(
                candidate.direction()) <= DIRECTION_EPSILON * DIRECTION_EPSILON);
        if (!duplicate) rays.add(candidate);
    }

    private static double clean(double value) {
        if (Math.abs(value) < DIRECTION_EPSILON) return 0.0;
        if (Math.abs(value - 1.0) < DIRECTION_EPSILON) return 1.0;
        return value;
    }

    private static Vector3d target(Node center, Vector3d direction, int requestedRadius,
                                   int signX, int signY, int signZ) {
        Vector3i index = center.getIndex();
        return new Vector3d(index.x + signX * requestedRadius * direction.x,
                            index.y + signY * requestedRadius * direction.y,
                            index.z + signZ * requestedRadius * direction.z);
    }

    /**
     * Snaps one continuous target independently by Euclidean index-space distance.<br><br>
     * Blocked nodes are ineligible, but nodes selected by other targets are deliberately not
     * excluded so duplicate snaps can be reported instead of redirected.
     */
    private static Node snapNearest(Node center, IndexBounds bounds, Vector3d target,
                                    int region, boolean threeDimensional,
                                    int signX, int signY, int signZ, Set<Node> blocked) {
        Comparator<Node> order = (left, right) -> compareSnapCandidates(
                left, right, center, target, signX, signY, signZ);
        PriorityQueue<Node> candidates = new PriorityQueue<>(order);
        Set<Node> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Integer> xSeeds = nearestIntegers(target.x, bounds.minimumX(), bounds.maximumX());
        List<Integer> ySeeds = nearestIntegers(target.y, bounds.minimumY(), bounds.maximumY());
        List<Integer> zSeeds = threeDimensional
                               ? nearestIntegers(target.z, bounds.minimumZ(), bounds.maximumZ())
                               : List.of(center.getIndex().z);
        for (int x : xSeeds) {
            for (int y : ySeeds) {
                for (int z : zSeeds) {
                    Node seed = nodeAt(center, new Vector3i(x, y, z));
                    if (seed != null && visited.add(seed)) candidates.add(seed);
                }
            }
        }

        while (!candidates.isEmpty()) {
            Node candidate = candidates.remove();
            if (inRegion(center, candidate, region, threeDimensional) && !blocked.contains(candidate)) {
                return candidate;
            }
            for (Neighbor neighbor : Neighbor.values()) {
                Node next = candidate.getNeighbor(neighbor);
                if (next != null && visited.add(next)) candidates.add(next);
            }
        }
        return null;
    }

    private static int compareSnapCandidates(Node left, Node right, Node center, Vector3d target,
                                             int signX, int signY, int signZ) {
        int result = Double.compare(distanceSquared(left, target), distanceSquared(right, target));
        if (result != 0) return result;
        Vector3i centerIndex = center.getIndex();
        Vector3i leftIndex = left.getIndex();
        Vector3i rightIndex = right.getIndex();
        result = Integer.compare(signX * (leftIndex.x - centerIndex.x),
                                 signX * (rightIndex.x - centerIndex.x));
        if (result == 0) {
            result = Integer.compare(signY * (leftIndex.y - centerIndex.y),
                                     signY * (rightIndex.y - centerIndex.y));
        }
        if (result == 0) {
            result = Integer.compare(signZ * (leftIndex.z - centerIndex.z),
                                     signZ * (rightIndex.z - centerIndex.z));
        }
        if (result == 0) result = Integer.compare(leftIndex.x, rightIndex.x);
        if (result == 0) result = Integer.compare(leftIndex.y, rightIndex.y);
        if (result == 0) result = Integer.compare(leftIndex.z, rightIndex.z);
        return result;
    }

    private static double distanceSquared(Node node, Vector3d target) {
        Vector3i index = node.getIndex();
        double x = index.x - target.x;
        double y = index.y - target.y;
        double z = index.z - target.z;
        return x * x + y * y + z * z;
    }

    private static List<Integer> nearestIntegers(double value, int minimum, int maximum) {
        int lower = clamp((long) Math.floor(value), minimum, maximum);
        int upper = clamp((long) Math.ceil(value), minimum, maximum);
        return lower == upper ? List.of(lower) : List.of(lower, upper);
    }

    private static int clamp(long value, int minimum, int maximum) {
        if (value < minimum) return minimum;
        if (value > maximum) return maximum;
        return (int) value;
    }

    private static IndexBounds bounds(Node center) {
        return new IndexBounds(boundary(center, Neighbor.LEFT).getIndex().x,
                               boundary(center, Neighbor.RIGHT).getIndex().x,
                               boundary(center, Neighbor.BOTTOM).getIndex().y,
                               boundary(center, Neighbor.TOP).getIndex().y,
                               boundary(center, Neighbor.BACK).getIndex().z,
                               boundary(center, Neighbor.FRONT).getIndex().z);
    }

    private static Node boundary(Node center, Neighbor direction) {
        Node node = center;
        while (node.getNeighbor(direction) != null) node = node.getNeighbor(direction);
        return node;
    }

    private static Node nodeAt(Node center, Vector3i target) {
        Node node = center;
        node = move(node, target.x - node.getIndex().x, Neighbor.RIGHT, Neighbor.LEFT);
        if (node == null) return null;
        node = move(node, target.y - node.getIndex().y, Neighbor.TOP, Neighbor.BOTTOM);
        if (node == null) return null;
        return move(node, target.z - node.getIndex().z, Neighbor.FRONT, Neighbor.BACK);
    }

    private static Node move(Node node, int distance, Neighbor positive, Neighbor negative) {
        Neighbor direction = distance >= 0 ? positive : negative;
        for (int step = 0; step < Math.abs(distance); step++) {
            node = node.getNeighbor(direction);
            if (node == null) return null;
        }
        return node;
    }

    private static boolean inRegion(Node center, Node node, int region, boolean threeDimensional) {
        Vector3i centerIndex = center.getIndex();
        Vector3i index = node.getIndex();
        int signX = (region & 1) == 0 ? 1 : -1;
        int signY = (region & 2) == 0 ? 1 : -1;
        int signZ = (region & 4) == 0 ? 1 : -1;
        if (signX * (index.x - centerIndex.x) < 0) return false;
        if (signY * (index.y - centerIndex.y) < 0) return false;
        return !threeDimensional || signZ * (index.z - centerIndex.z) >= 0;
    }

    private static double realizedRadius(Node center, Node node) {
        Vector3i centerIndex = center.getIndex();
        Vector3i index = node.getIndex();
        double x = index.x - centerIndex.x;
        double y = index.y - centerIndex.y;
        double z = index.z - centerIndex.z;
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static int graphDistance(Node center, Node node) {
        Vector3i centerIndex = center.getIndex();
        Vector3i index = node.getIndex();
        return Math.abs(index.x - centerIndex.x) + Math.abs(index.y - centerIndex.y)
                + Math.abs(index.z - centerIndex.z);
    }

    private static List<ProbeReport> reports(List<Selection> selections,
                                             Set<Vector3i> collisionCoordinates) {
        return selections.stream().map(selection -> new ProbeReport(
                selection.shell(), selection.ray(), selection.idealRay().description(),
                selection.requestedRadius(), selection.realizedRadius(),
                selection.graphDistance(), selection.node().getIndex(),
                collisionCoordinates.contains(selection.node().getIndex()))).toList();
    }

    private static String formatCoordinate(Vector3i coordinate) {
        return "(" + coordinate.x + "," + coordinate.y + "," + coordinate.z + ")";
    }

    private static Result invalid(String status) {
        return new Result(List.of(), status, false, List.of());
    }
}
