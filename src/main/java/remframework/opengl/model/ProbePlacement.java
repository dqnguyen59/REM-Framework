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
    public static final PlacementTolerances DEFAULT_TOLERANCES = new PlacementTolerances(1.0);

    private ProbePlacement() {
    }

    public record PlacementTolerances(double maximumRadialError) {
        public PlacementTolerances {
            if (!Double.isFinite(maximumRadialError) || maximumRadialError < 0.0) {
                throw new IllegalArgumentException("Maximum radial error must be finite and non-negative.");
            }
        }
    }

    public record ProbeReport(int shell, int ray, String rayPlane, int requestedRadius,
                              Vector3d requestedCoordinate, Vector3d requestedDirection,
                              ProbeDetectorType detectorType,
                              double realizedRadius, double angularDeviationDegrees,
                              Vector3i realizedDirection,
                              double angularDeviationFromFirstShellDegrees,
                              int graphDistance, Vector3i coordinate, boolean available,
                              String unavailableReason, List<String> representedTargets,
                              List<ProbeDefinition.InterpolationSource> interpolationSources) {
        public ProbeReport {
            requestedCoordinate = new Vector3d(requestedCoordinate);
            requestedDirection = new Vector3d(requestedDirection);
            realizedDirection = realizedDirection == null ? null : new Vector3i(realizedDirection);
            coordinate = coordinate == null ? null : new Vector3i(coordinate);
            representedTargets = List.copyOf(representedTargets);
            interpolationSources = List.copyOf(interpolationSources);
        }

        @Override
        public Vector3d requestedCoordinate() {
            return new Vector3d(requestedCoordinate);
        }

        @Override
        public Vector3d requestedDirection() {
            return new Vector3d(requestedDirection);
        }

        @Override
        public Vector3i realizedDirection() {
            return realizedDirection == null ? null : new Vector3i(realizedDirection);
        }

        @Override
        public Vector3i coordinate() {
            return coordinate == null ? null : new Vector3i(coordinate);
        }

        public boolean merged() {
            return representedTargets.size() > 1;
        }
    }

    public record Result(List<ProbeDefinition> definitions, String status, boolean valid,
                         List<ProbeReport> probeReports, String activeProbeSpace) {
    }

    private enum Axis {
        X, Y, Z
    }

    private record MeasurementSpace(String plane, String display, List<Axis> axes) {
        private boolean isThreeDimensional() {
            return axes.size() == 3;
        }
    }

    private record Ray(String description, Vector3d direction) {
    }

    private record Selection(int shell, int ray, Ray idealRay, int requestedRadius, Node node,
                             Vector3d requestedCoordinate, Vector3d requestedDirection,
                             double realizedRadius, double angularDeviationDegrees,
                             Vector3i realizedDirection,
                             double angularDeviationFromFirstShellDegrees,
                             int graphDistance, boolean available, String unavailableReason) {
    }

    private record IndexBounds(int minimumX, int maximumX, int minimumY, int maximumY,
                               int minimumZ, int maximumZ) {
    }

    /**
     * Places passive probes from continuous requested radii and ideal rays.<br><br>
     * Lattice nodes are the nearest discrete realizations. Candidates outside the radial
     * tolerance remain diagnostic-only, and graph distance never participates in placement.
     */
    public static Result place(Node center, int firstShellRadius, int shellDistance,
                               int numberOfShells, int probesPerPlane, int region,
                               boolean threeDimensional, Vector4f color,
                               Set<Node> blocked, String idPrefix) {
        return place(center, firstShellRadius, shellDistance, numberOfShells, probesPerPlane,
                     region, threeDimensional, color, blocked, idPrefix,
                     ProbeDetectorType.RAW_NODE, DEFAULT_TOLERANCES);
    }

    public static Result place(Node center, int firstShellRadius, int shellDistance,
                               int numberOfShells, int probesPerPlane, int region,
                               boolean threeDimensional, Vector4f color,
                               Set<Node> blocked, String idPrefix,
                               ProbeDetectorType detectorType) {
        return place(center, firstShellRadius, shellDistance, numberOfShells, probesPerPlane,
                     region, threeDimensional, color, blocked, idPrefix,
                     detectorType, DEFAULT_TOLERANCES);
    }

    public static Result place(Node center, int firstShellRadius, int shellDistance,
                               int numberOfShells, int probesPerPlane, int region,
                               boolean threeDimensional, Vector4f color,
                               Set<Node> blocked, String idPrefix,
                               PlacementTolerances tolerances) {
        return place(center, firstShellRadius, shellDistance, numberOfShells, probesPerPlane,
                     region, threeDimensional, color, blocked, idPrefix,
                     ProbeDetectorType.RAW_NODE, tolerances);
    }

    public static Result place(Node center, int firstShellRadius, int shellDistance,
                               int numberOfShells, int probesPerPlane, int region,
                               boolean threeDimensional, Vector4f color,
                               Set<Node> blocked, String idPrefix,
                               ProbeDetectorType detectorType,
                               PlacementTolerances tolerances) {
        if (tolerances == null) throw new IllegalArgumentException("Placement tolerances are required.");
        if (detectorType == null) throw new IllegalArgumentException("Probe detector type is required.");
        if (center == null) return invalid("The lattice center is unavailable.");
        if (center.isGeometricDistanceImbalance()) return invalid("Cubic probe placement is unavailable for diamond.");
        if (firstShellRadius < 1) return invalid("First Shell Radius must be at least 1.");
        if (shellDistance < 1) return invalid("Shell Distance must be at least 1.");
        if (numberOfShells < 1) return invalid("Number Of Shells must be at least 1.");
        if (probesPerPlane < 3) {
            return invalid(threeDimensional
                    ? "Probes Per Plane Per Shell must be at least 3."
                    : "Probes Per Shell must be at least 3.");
        }
        if (probesPerPlane % 2 == 0) {
            return invalid(threeDimensional
                    ? "Probes Per Plane Per Shell must be odd."
                    : "Probes Per Shell must be odd.");
        }
        if (threeDimensional) {
            if (region < 0 || region > 7) return invalid("The selected octant is unavailable.");
        } else {
            if (region < 0 || region > 3) return invalid("The selected quadrant is unavailable.");
        }

        List<Integer> requestedRadii = requestedRadii(firstShellRadius, shellDistance,
                                                       numberOfShells);
        if (requestedRadii.isEmpty()) {
            return invalid("Requested shell radius exceeds the supported integer range.");
        }

        int signX = (region & 1) == 0 ? 1 : -1;
        int signY = (region & 2) == 0 ? 1 : -1;
        int signZ = (region & 4) == 0 ? 1 : -1;
        Set<Node> excluded = blocked == null ? Set.of() : blocked;
        IndexBounds bounds = bounds(center);
        int largestRequestedRadius = requestedRadii.getLast();
        MeasurementSpace measurementSpace = measurementSpace(
                center, bounds, largestRequestedRadius, tolerances.maximumRadialError(),
                signX, signY, signZ, threeDimensional);
        if (measurementSpace.axes().size() < 2) {
            return new Result(List.of(), "Fewer than two signed lattice axes can support requested radius "
                    + largestRequestedRadius + ".", false, List.of(), measurementSpace.display());
        }
        List<Ray> rays = measurementSpace.isThreeDimensional()
                         ? generateThreeDimensionalRays(probesPerPlane)
                         : generateTwoDimensionalRays(probesPerPlane, measurementSpace.plane());
        if (detectorType != ProbeDetectorType.RAW_NODE) {
            ProbeDetectorType activeInterpolatedType = measurementSpace.isThreeDimensional()
                    ? ProbeDetectorType.INTERPOLATED_3D
                    : ProbeDetectorType.INTERPOLATED_2D;
            if (detectorType != activeInterpolatedType) {
                return new Result(List.of(), detectorType + " is incompatible with "
                        + measurementSpace.display() + ".", false, List.of(),
                        measurementSpace.display());
            }
            return placeInterpolated(center, requestedRadii, rays, region, measurementSpace,
                    bounds, signX, signY, signZ, color, idPrefix, detectorType);
        }
        List<Selection> selections = new ArrayList<>();
        List<Vector3i> firstShellDirections = new ArrayList<>(
                Collections.nCopies(rays.size(), null));

        int firstRequestedRadius = requestedRadii.getFirst();
        for (int ray = 0; ray < rays.size(); ray++) {
            Ray idealRay = rays.get(ray);
            Vector3d requestedDirection = signedDirection(
                    idealRay.direction(), signX, signY, signZ);
            Vector3d target = target(center, idealRay.direction(), firstRequestedRadius,
                                     signX, signY, signZ);
            Node selected = snapNearest(center, bounds, target, region,
                                        threeDimensional, signX, signY, signZ, excluded);
            if (selected == null) {
                selections.add(new Selection(0, ray, idealRay, firstRequestedRadius, null,
                        target, requestedDirection,
                        Double.NaN, Double.NaN, null, Double.NaN, -1, false,
                        "no eligible lattice node is available in the selected region."));
                continue;
            }
            double realizedRadius = realizedRadius(center, selected);
            double angularDeviation = angularDeviationDegrees(
                    center, selected, idealRay.direction(), signX, signY, signZ);
            Vector3i realizedDirection = primitiveDirection(center, selected);
            double radialError = Math.abs(realizedRadius - firstRequestedRadius);
            boolean available = radialError <= tolerances.maximumRadialError()
                    && realizedDirection != null;
            String reason;
            if (radialError > tolerances.maximumRadialError()) {
                reason = unavailableReason(realizedRadius, radialError,
                        tolerances.maximumRadialError(), target, bounds);
            } else if (realizedDirection == null) {
                reason = "nearest eligible lattice node is the center, so no discrete ray can be established.";
            } else {
                reason = "";
                firstShellDirections.set(ray, realizedDirection);
            }
            selections.add(new Selection(0, ray, idealRay, firstRequestedRadius, selected,
                    target, requestedDirection,
                    realizedRadius, angularDeviation, realizedDirection,
                    available ? 0.0 : Double.NaN, graphDistance(center, selected),
                    available, reason));
        }

        for (int shell = 1; shell < requestedRadii.size(); shell++) {
            int requestedRadius = requestedRadii.get(shell);
            for (int ray = 0; ray < rays.size(); ray++) {
                Ray idealRay = rays.get(ray);
                Vector3d requestedDirection = signedDirection(
                        idealRay.direction(), signX, signY, signZ);
                Vector3d requestedCoordinate = target(
                        center, idealRay.direction(), requestedRadius, signX, signY, signZ);
                Vector3i firstShellDirection = firstShellDirections.get(ray);
                if (firstShellDirection == null) {
                    selections.add(new Selection(shell, ray, idealRay, requestedRadius, null,
                            requestedCoordinate, requestedDirection,
                            Double.NaN, Double.NaN, null, Double.NaN, -1, false,
                            "the first-shell target is unavailable, so no discrete ray can be established."));
                    continue;
                }
                Node selected = snapNearestOnDiscreteRay(
                        center, bounds, firstShellDirection, requestedRadius, excluded);
                if (selected == null) {
                    selections.add(new Selection(shell, ray, idealRay, requestedRadius, null,
                            requestedCoordinate, requestedDirection,
                            Double.NaN, Double.NaN, null, Double.NaN, -1, false,
                            "no eligible lattice node is available on the first-shell discrete ray within lattice bounds."));
                    continue;
                }
                double realizedRadius = realizedRadius(center, selected);
                double angularDeviation = angularDeviationDegrees(
                        center, selected, idealRay.direction(), signX, signY, signZ);
                Vector3i realizedDirection = primitiveDirection(center, selected);
                double alignmentDeviation = angularDeviationDegrees(
                        realizedDirection, firstShellDirection);
                double radialError = Math.abs(realizedRadius - requestedRadius);
                boolean available = radialError <= tolerances.maximumRadialError();
                String reason = available ? "" : collinearUnavailableReason(
                        realizedRadius, radialError, tolerances.maximumRadialError(),
                        firstShellDirection);
                selections.add(new Selection(shell, ray, idealRay, requestedRadius, selected,
                        requestedCoordinate, requestedDirection,
                        realizedRadius, angularDeviation, realizedDirection, alignmentDeviation,
                        graphDistance(center, selected), available, reason));
            }
        }

        Map<Vector3i, List<Selection>> byCoordinate = groupValidSelections(selections);

        List<ProbeDefinition> definitions = new ArrayList<>();
        int uniqueProbe = 0;
        for (Map.Entry<Vector3i, List<Selection>> entry : byCoordinate.entrySet()) {
            List<ProbeDefinition.Target> targets = entry.getValue().stream()
                    .map(ProbePlacement::definitionTarget).toList();
            ProbeDefinition.Placement placement = new ProbeDefinition.Placement(
                    targets, region, measurementSpace.plane());
            definitions.add(new ProbeDefinition(idPrefix + "-" + uniqueProbe++, entry.getKey(),
                                                color, placement));
        }
        int unavailableTargets = (int) selections.stream().filter(selection -> !selection.available()).count();
        int validTargets = selections.size() - unavailableTargets;
        int mergedCollisions = validTargets - definitions.size();
        boolean valid = !definitions.isEmpty();
        String status = "Preview: requested targets " + selections.size()
                + " | unavailable targets " + unavailableTargets
                + " | merged collisions " + mergedCollisions
                + " | unique passive probes " + definitions.size() + "."
                + (valid ? "" : " Apply is unavailable because no valid probe remains.");
        return new Result(List.copyOf(definitions), status, valid, reports(selections, byCoordinate),
                          measurementSpace.display());
    }

    /** Binds continuous ideal targets to deterministic four/eight-node interpolation stencils. */
    private static Result placeInterpolated(Node center, List<Integer> requestedRadii,
                                            List<Ray> rays, int region,
                                            MeasurementSpace measurementSpace,
                                            IndexBounds bounds,
                                            int signX, int signY, int signZ,
                                            Vector4f color, String idPrefix,
                                            ProbeDetectorType detectorType) {
        Vector3i minimum = new Vector3i(bounds.minimumX(), bounds.minimumY(), bounds.minimumZ());
        Vector3i maximum = new Vector3i(bounds.maximumX(), bounds.maximumY(), bounds.maximumZ());
        List<ProbeDefinition> definitions = new ArrayList<>();
        List<ProbeReport> reports = new ArrayList<>();
        int unavailableTargets = 0;
        for (int shell = 0; shell < requestedRadii.size(); shell++) {
            int requestedRadius = requestedRadii.get(shell);
            for (int ray = 0; ray < rays.size(); ray++) {
                Ray idealRay = rays.get(ray);
                Vector3d requestedDirection = signedDirection(
                        idealRay.direction(), signX, signY, signZ);
                Vector3d requestedCoordinate = target(
                        center, idealRay.direction(), requestedRadius, signX, signY, signZ);
                List<ProbeDefinition.InterpolationSource> sources = detectorType
                        == ProbeDetectorType.INTERPOLATED_3D
                        ? ProbeInterpolation.createTrilinearStencil(
                                requestedCoordinate, minimum, maximum)
                        : ProbeInterpolation.createBilinearStencil(
                                requestedCoordinate, measurementSpace.plane(), minimum, maximum);
                String unavailableReason = "";
                if (sources.isEmpty()) {
                    unavailableReason = "exact requested coordinate is outside lattice bounds or has no complete interpolation cell.";
                } else if (!interpolationSourcesAvailable(center, sources)) {
                    unavailableReason = "one or more interpolation source nodes are unavailable.";
                }
                boolean available = unavailableReason.isEmpty();
                String label = "Shell " + (shell + 1) + " " + idealRay.description();
                if (!available) {
                    unavailableTargets++;
                    reports.add(new ProbeReport(shell, ray, idealRay.description(), requestedRadius,
                            requestedCoordinate, requestedDirection, detectorType,
                            Double.NaN, 0.0, null, Double.NaN, -1, null, false,
                            unavailableReason, List.of(label), sources));
                    continue;
                }
                ProbeDefinition.Target definitionTarget = new ProbeDefinition.Target(
                        shell, ray, idealRay.description(), requestedRadius,
                        requestedCoordinate, requestedDirection, Double.NaN,
                        0.0, null, Double.NaN, -1);
                ProbeDefinition.Placement placement = new ProbeDefinition.Placement(
                        List.of(definitionTarget), region, measurementSpace.plane());
                definitions.add(ProbeDefinition.interpolated(
                        idPrefix + "-" + definitions.size(), detectorType, color, placement, sources));
                reports.add(new ProbeReport(shell, ray, idealRay.description(), requestedRadius,
                        requestedCoordinate, requestedDirection, detectorType,
                        Double.NaN, 0.0, null, 0.0, -1, null, true,
                        "", List.of(label), sources));
            }
        }
        int requestedTargets = requestedRadii.size() * rays.size();
        boolean valid = !definitions.isEmpty();
        String status = "Preview: requested targets " + requestedTargets
                + " | unavailable targets " + unavailableTargets
                + " | merged collisions 0 | unique passive probes " + definitions.size() + "."
                + (valid ? "" : " Apply is unavailable because no valid probe remains.");
        return new Result(List.copyOf(definitions), status, valid, List.copyOf(reports),
                          measurementSpace.display());
    }

    private static boolean interpolationSourcesAvailable(
            Node center, List<ProbeDefinition.InterpolationSource> sources) {
        return sources.stream().allMatch(source -> nodeAt(center, source.nodeIndex()) != null);
    }

    /** Describes stored generated probes using their actual index offsets from the center. */
    public static List<ProbeReport> describe(Node center, List<ProbeDefinition> definitions) {
        if (center == null || center.isGeometricDistanceImbalance() || definitions.isEmpty()) return List.of();
        List<ProbeReport> reports = new ArrayList<>();
        for (ProbeDefinition definition : definitions) {
            ProbeDefinition.Placement placement = definition.getPlacement();
            if (placement == null) continue;
            if (definition.getDetectorType() != ProbeDetectorType.RAW_NODE) {
                if (!interpolationSourcesAvailable(center, definition.getInterpolationSources())) continue;
                for (ProbeDefinition.Target target : placement.targets()) {
                    reports.add(new ProbeReport(target.shell(), target.ray(), target.rayPlane(),
                            target.requestedRadius(), target.requestedCoordinate(),
                            target.requestedDirection(), definition.getDetectorType(),
                            Double.NaN, 0.0, null, 0.0,
                            -1, null, true, "", List.of(targetLabel(target)),
                            definition.getInterpolationSources()));
                }
                continue;
            }
            Node node = nodeAt(center, definition.getNodeIndex());
            if (node == null) continue;
            Vector3i realizedDirection = primitiveDirection(center, node);
            List<String> representedTargets = placement.targets().stream()
                    .map(ProbePlacement::targetLabel).toList();
            for (ProbeDefinition.Target target : placement.targets()) {
                reports.add(new ProbeReport(target.shell(), target.ray(), target.rayPlane(),
                        target.requestedRadius(), target.requestedCoordinate(),
                        target.requestedDirection(), definition.getDetectorType(),
                        realizedRadius(center, node),
                        target.angularDeviationDegrees(), realizedDirection,
                        angularDeviationDegrees(realizedDirection, target.realizedDirection()),
                        graphDistance(center, node), node.getIndex(), true, "", representedTargets,
                        List.of()));
            }
        }
        return List.copyOf(reports);
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

    public static String activeProbeSpace(Node center, int largestRequestedRadius, int region,
                                          boolean threeDimensional,
                                          PlacementTolerances tolerances) {
        if (center == null || largestRequestedRadius < 1 || tolerances == null) return "";
        if (center.isGeometricDistanceImbalance()) return "Cubic probe placement is unavailable for diamond.";
        int signX = (region & 1) == 0 ? 1 : -1;
        int signY = (region & 2) == 0 ? 1 : -1;
        int signZ = (region & 4) == 0 ? 1 : -1;
        return measurementSpace(center, bounds(center), largestRequestedRadius,
                tolerances.maximumRadialError(), signX, signY, signZ,
                threeDimensional).display();
    }

    public static boolean generatedDefinitionsCompatible(Node center,
                                                         List<ProbeDefinition> definitions,
                                                         boolean threeDimensional,
                                                         PlacementTolerances tolerances) {
        if (definitions.isEmpty()) return true;
        if (center == null || center.isGeometricDistanceImbalance() || tolerances == null) return false;
        List<ProbeDefinition.Placement> placements = definitions.stream()
                .map(ProbeDefinition::getPlacement).toList();
        if (placements.stream().anyMatch(java.util.Objects::isNull)) return false;
        int region = placements.getFirst().region();
        if (placements.stream().anyMatch(placement -> placement.region() != region)) return false;
        int largestRequestedRadius = placements.stream().flatMap(placement -> placement.targets().stream())
                .mapToInt(ProbeDefinition.Target::requestedRadius).max().orElse(0);
        int signX = (region & 1) == 0 ? 1 : -1;
        int signY = (region & 2) == 0 ? 1 : -1;
        int signZ = (region & 4) == 0 ? 1 : -1;
        IndexBounds currentBounds = bounds(center);
        MeasurementSpace space = measurementSpace(center, currentBounds, largestRequestedRadius,
                tolerances.maximumRadialError(), signX, signY, signZ, threeDimensional);
        if (space.axes().size() < 2) return false;
        for (int definitionIndex = 0; definitionIndex < definitions.size(); definitionIndex++) {
            ProbeDefinition definition = definitions.get(definitionIndex);
            ProbeDefinition.Placement placement = placements.get(definitionIndex);
            if (!placement.measurementSpace().equals(space.plane())) return false;
            if (definition.getDetectorType() != ProbeDetectorType.RAW_NODE) {
                ProbeDetectorType expectedType = space.isThreeDimensional()
                        ? ProbeDetectorType.INTERPOLATED_3D
                        : ProbeDetectorType.INTERPOLATED_2D;
                if (definition.getDetectorType() != expectedType
                        || placement.targets().size() != 1) return false;
                ProbeDefinition.Target target = placement.targets().getFirst();
                if (!directionAllowed(target.rayPlane(), space.plane())) return false;
                Vector3i centerIndex = center.getIndex();
                Vector3d expectedCoordinate = new Vector3d(target.requestedDirection())
                        .mul(target.requestedRadius())
                        .add(centerIndex.x, centerIndex.y, centerIndex.z);
                if (expectedCoordinate.distanceSquared(target.requestedCoordinate()) > 1.0e-18) {
                    return false;
                }
                Vector3i minimum = new Vector3i(
                        currentBounds.minimumX(), currentBounds.minimumY(), currentBounds.minimumZ());
                Vector3i maximum = new Vector3i(
                        currentBounds.maximumX(), currentBounds.maximumY(), currentBounds.maximumZ());
                List<ProbeDefinition.InterpolationSource> expectedSources = expectedType
                        == ProbeDetectorType.INTERPOLATED_3D
                        ? ProbeInterpolation.createTrilinearStencil(
                                target.requestedCoordinate(), minimum, maximum)
                        : ProbeInterpolation.createBilinearStencil(
                                target.requestedCoordinate(), space.plane(), minimum, maximum);
                if (!sameInterpolationSources(
                        expectedSources, definition.getInterpolationSources())
                        || !interpolationSourcesAvailable(
                                center, definition.getInterpolationSources())) return false;
                continue;
            }
            Node node = nodeAt(center, definition.getNodeIndex());
            if (node == null) return false;
            double realizedRadius = realizedRadius(center, node);
            Vector3i realizedDirection = primitiveDirection(center, node);
            if (realizedDirection == null) return false;
            for (ProbeDefinition.Target target : placement.targets()) {
                if (!directionAllowed(target.rayPlane(), space.plane())) return false;
                if (!realizedDirection.equals(target.realizedDirection())) return false;
                if (Math.abs(realizedRadius - target.requestedRadius())
                        > tolerances.maximumRadialError()) return false;
            }
        }
        return true;
    }

    private static boolean sameInterpolationSources(
            List<ProbeDefinition.InterpolationSource> left,
            List<ProbeDefinition.InterpolationSource> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            ProbeDefinition.InterpolationSource leftSource = left.get(index);
            ProbeDefinition.InterpolationSource rightSource = right.get(index);
            if (!leftSource.nodeIndex().equals(rightSource.nodeIndex())
                    || Math.abs(leftSource.weight() - rightSource.weight()) > 1.0e-12) return false;
        }
        return true;
    }

    private static boolean directionAllowed(String rayPlane, String activePlane) {
        if ("XYZ".equals(activePlane)) return true;
        return switch (activePlane) {
            case "XY" -> rayPlane.equals("X axis") || rayPlane.equals("Y axis")
                    || rayPlane.startsWith("XY ");
            case "XZ" -> rayPlane.equals("X axis") || rayPlane.equals("Z axis")
                    || rayPlane.startsWith("XZ ");
            case "YZ" -> rayPlane.equals("Y axis") || rayPlane.equals("Z axis")
                    || rayPlane.startsWith("YZ ");
            default -> false;
        };
    }

    private static MeasurementSpace measurementSpace(Node center, IndexBounds bounds,
                                                     int largestRequestedRadius,
                                                     double radialTolerance,
                                                     int signX, int signY, int signZ,
                                                     boolean threeDimensional) {
        List<Axis> supported = new ArrayList<>();
        if (axisExtent(center, bounds, Axis.X, signX) + radialTolerance
                >= largestRequestedRadius) supported.add(Axis.X);
        if (axisExtent(center, bounds, Axis.Y, signY) + radialTolerance
                >= largestRequestedRadius) supported.add(Axis.Y);
        if (threeDimensional && axisExtent(center, bounds, Axis.Z, signZ) + radialTolerance
                >= largestRequestedRadius) supported.add(Axis.Z);

        if (threeDimensional && supported.size() == 3) {
            return new MeasurementSpace("XYZ", "Active probe space: XYZ octant",
                                        List.of(Axis.X, Axis.Y, Axis.Z));
        }
        List<Axis> planeAxes;
        String plane;
        if (supported.contains(Axis.X) && supported.contains(Axis.Y)) {
            plane = "XY";
            planeAxes = List.of(Axis.X, Axis.Y);
        } else if (supported.contains(Axis.X) && supported.contains(Axis.Z)) {
            plane = "XZ";
            planeAxes = List.of(Axis.X, Axis.Z);
        } else if (supported.contains(Axis.Y) && supported.contains(Axis.Z)) {
            plane = "YZ";
            planeAxes = List.of(Axis.Y, Axis.Z);
        } else {
            String insufficient = unsupportedAxes(supported);
            return new MeasurementSpace("", "Active probe space: unavailable (" + insufficient
                    + " extent insufficient for requested radii)", List.copyOf(supported));
        }
        List<Axis> unsupported = new ArrayList<>(List.of(Axis.X, Axis.Y, Axis.Z));
        unsupported.removeAll(planeAxes);
        return new MeasurementSpace(plane, "Active probe space: " + plane + " plane ("
                + axisNames(unsupported) + " extent insufficient for requested radii)", planeAxes);
    }

    private static int axisExtent(Node center, IndexBounds bounds, Axis axis, int sign) {
        Vector3i index = center.getIndex();
        return switch (axis) {
            case X -> sign > 0 ? bounds.maximumX() - index.x : index.x - bounds.minimumX();
            case Y -> sign > 0 ? bounds.maximumY() - index.y : index.y - bounds.minimumY();
            case Z -> sign > 0 ? bounds.maximumZ() - index.z : index.z - bounds.minimumZ();
        };
    }

    private static String unsupportedAxes(List<Axis> supported) {
        List<Axis> unsupported = new ArrayList<>(List.of(Axis.X, Axis.Y, Axis.Z));
        unsupported.removeAll(supported);
        return axisNames(unsupported);
    }

    private static String axisNames(List<Axis> axes) {
        return axes.stream().map(Enum::name).reduce((left, right) -> left + "/" + right).orElse("");
    }

    /** Generates odd-P planar rays including both axes and the 45-degree face diagonal. */
    private static List<Ray> generateTwoDimensionalRays(int probesPerShell, String plane) {
        List<Ray> rays = new ArrayList<>();
        for (int ray = 0; ray < probesPerShell; ray++) {
            double angle = ray * 90.0 / (probesPerShell - 1);
            double radians = Math.toRadians(angle);
            double cosine = clean(Math.cos(radians));
            double sine = clean(Math.sin(radians));
            Vector3d direction = switch (plane) {
                case "XY" -> new Vector3d(cosine, sine, 0.0);
                case "XZ" -> new Vector3d(cosine, 0.0, sine);
                case "YZ" -> new Vector3d(0.0, cosine, sine);
                default -> throw new IllegalStateException("Unsupported plane " + plane);
            };
            rays.add(new Ray(planarDescription(plane, angle, direction), direction));
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

    private static Vector3d signedDirection(Vector3d direction,
                                            int signX, int signY, int signZ) {
        return new Vector3d(signX * direction.x, signY * direction.y, signZ * direction.z);
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

    /**
     * Snaps a later shell only to integer multiples of the successful first-shell primitive
     * direction, preserving its realized angle exactly across shells.
     */
    private static Node snapNearestOnDiscreteRay(Node center, IndexBounds bounds,
                                                 Vector3i primitiveDirection,
                                                 int requestedRadius, Set<Node> blocked) {
        Vector3i centerIndex = center.getIndex();
        int maximumMultiple = maximumRayMultiple(centerIndex, bounds, primitiveDirection);
        Node nearest = null;
        double nearestRadialError = Double.POSITIVE_INFINITY;
        for (int multiple = 1; multiple <= maximumMultiple; multiple++) {
            Vector3i coordinate = new Vector3i(
                    centerIndex.x + multiple * primitiveDirection.x,
                    centerIndex.y + multiple * primitiveDirection.y,
                    centerIndex.z + multiple * primitiveDirection.z);
            Node candidate = nodeAt(center, coordinate);
            if (candidate == null || blocked.contains(candidate)) continue;
            double radialError = Math.abs(realizedRadius(center, candidate) - requestedRadius);
            if (radialError < nearestRadialError) {
                nearest = candidate;
                nearestRadialError = radialError;
            }
        }
        return nearest;
    }

    private static int maximumRayMultiple(Vector3i center, IndexBounds bounds,
                                          Vector3i direction) {
        long maximum = Integer.MAX_VALUE;
        maximum = Math.min(maximum, maximumAxisMultiple(
                center.x, bounds.minimumX(), bounds.maximumX(), direction.x));
        maximum = Math.min(maximum, maximumAxisMultiple(
                center.y, bounds.minimumY(), bounds.maximumY(), direction.y));
        maximum = Math.min(maximum, maximumAxisMultiple(
                center.z, bounds.minimumZ(), bounds.maximumZ(), direction.z));
        return (int) maximum;
    }

    private static long maximumAxisMultiple(int center, int minimum, int maximum, int direction) {
        if (direction > 0) return (maximum - (long) center) / direction;
        if (direction < 0) return (center - (long) minimum) / -(long) direction;
        return Integer.MAX_VALUE;
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
        if(center.isGeometricDistanceImbalance()) throw new IllegalArgumentException("Cubic probe placement is unavailable for diamond.");
        return new IndexBounds(boundary(center, Neighbor.LEFT).getIndex().x,
                               boundary(center, Neighbor.RIGHT).getIndex().x,
                               boundary(center, Neighbor.BOTTOM).getIndex().y,
                               boundary(center, Neighbor.TOP).getIndex().y,
                               boundary(center, Neighbor.BACK).getIndex().z,
                               boundary(center, Neighbor.FRONT).getIndex().z);
    }

    private static Node boundary(Node center, Neighbor direction) {
        Node node = center;
        Set<Node> visited=Collections.newSetFromMap(new IdentityHashMap<>());
        while (node.getNeighbor(direction) != null) {
            if(!visited.add(node)) throw new IllegalArgumentException("Cyclic neighbor chain is not a cubic probe axis");
            node=node.getNeighbor(direction);
        }
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

    private static double angularDeviationDegrees(Node center, Node node, Vector3d idealDirection,
                                                  int signX, int signY, int signZ) {
        Vector3i centerIndex = center.getIndex();
        Vector3i index = node.getIndex();
        Vector3d realizedDirection = new Vector3d(index.x - centerIndex.x,
                                                  index.y - centerIndex.y,
                                                  index.z - centerIndex.z);
        if (realizedDirection.lengthSquared() == 0.0) return Double.NaN;
        Vector3d signedIdealDirection = new Vector3d(signX * idealDirection.x,
                                                     signY * idealDirection.y,
                                                     signZ * idealDirection.z);
        double cosine = realizedDirection.dot(signedIdealDirection)
                / (realizedDirection.length() * signedIdealDirection.length());
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosine))));
    }

    private static double angularDeviationDegrees(Vector3i realizedDirection,
                                                  Vector3i firstShellDirection) {
        if (realizedDirection == null || firstShellDirection == null) return Double.NaN;
        if (realizedDirection.equals(firstShellDirection)) return 0.0;
        Vector3d realized = new Vector3d(realizedDirection);
        Vector3d firstShell = new Vector3d(firstShellDirection);
        double cosine = realized.dot(firstShell) / (realized.length() * firstShell.length());
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosine))));
    }

    private static Vector3i primitiveDirection(Node center, Node node) {
        Vector3i centerIndex = center.getIndex();
        Vector3i nodeIndex = node.getIndex();
        int dx = nodeIndex.x - centerIndex.x;
        int dy = nodeIndex.y - centerIndex.y;
        int dz = nodeIndex.z - centerIndex.z;
        int divisor = gcd(gcd(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        if (divisor == 0) return null;
        return new Vector3i(dx / divisor, dy / divisor, dz / divisor);
    }

    private static int gcd(int left, int right) {
        while (right != 0) {
            int remainder = left % right;
            left = right;
            right = remainder;
        }
        return left;
    }

    private static String unavailableReason(double realizedRadius, double radialError,
                                            double radialTolerance, Vector3d target,
                                            IndexBounds bounds) {
        String boundary = reachedBoundary(target, bounds);
        String diagnostic = String.format(Locale.ROOT,
                "nearest eligible lattice node has realized radius %.3f; radial error %.3f exceeds tolerance %.3f",
                realizedRadius, radialError, radialTolerance);
        return boundary.isEmpty() ? diagnostic + "." : diagnostic + " because " + boundary + ".";
    }

    private static String collinearUnavailableReason(double realizedRadius, double radialError,
                                                     double radialTolerance,
                                                     Vector3i primitiveDirection) {
        return String.format(Locale.ROOT,
                "nearest eligible collinear node on realized direction (%d,%d,%d) has realized radius %.3f; radial error %.3f exceeds tolerance %.3f.",
                primitiveDirection.x, primitiveDirection.y, primitiveDirection.z,
                realizedRadius, radialError, radialTolerance);
    }

    private static String reachedBoundary(Vector3d target, IndexBounds bounds) {
        List<String> boundaries = new ArrayList<>();
        if (target.x < bounds.minimumX()) boundaries.add("the -X lattice boundary is reached");
        if (target.x > bounds.maximumX()) boundaries.add("the +X lattice boundary is reached");
        if (target.y < bounds.minimumY()) boundaries.add("the -Y lattice boundary is reached");
        if (target.y > bounds.maximumY()) boundaries.add("the +Y lattice boundary is reached");
        if (target.z < bounds.minimumZ()) boundaries.add("the -Z lattice boundary is reached");
        if (target.z > bounds.maximumZ()) boundaries.add("the +Z lattice boundary is reached");
        if (boundaries.isEmpty()) return "";
        if (boundaries.size() == 1) return boundaries.getFirst();
        StringJoiner joined = new StringJoiner(", ", "", "");
        for (int index = 0; index < boundaries.size() - 1; index++) joined.add(boundaries.get(index));
        return joined + " and " + boundaries.getLast();
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

    /**
     * Multiple ideal directions may resolve to one discrete lattice coordinate; such collisions
     * are merged into one passive observer while retaining every represented target label.
     */
    private static Map<Vector3i, List<Selection>> groupValidSelections(List<Selection> selections) {
        Map<Vector3i, List<Selection>> grouped = new LinkedHashMap<>();
        for (Selection selection : selections) {
            if (!selection.available()) continue;
            grouped.computeIfAbsent(new Vector3i(selection.node().getIndex()),
                                    ignored -> new ArrayList<>()).add(selection);
        }
        return grouped;
    }

    private static ProbeDefinition.Target definitionTarget(Selection selection) {
        return new ProbeDefinition.Target(selection.shell(), selection.ray(),
                selection.idealRay().description(), selection.requestedRadius(),
                selection.requestedCoordinate(), selection.requestedDirection(),
                selection.realizedRadius(), selection.angularDeviationDegrees(),
                selection.realizedDirection(),
                selection.angularDeviationFromFirstShellDegrees(),
                selection.graphDistance());
    }

    private static List<ProbeReport> reports(List<Selection> selections,
                                             Map<Vector3i, List<Selection>> byCoordinate) {
        return selections.stream().map(selection -> {
            Vector3i coordinate = selection.node() == null ? null
                                                            : new Vector3i(selection.node().getIndex());
            List<String> representedTargets = selection.available()
                    ? byCoordinate.get(coordinate).stream().map(ProbePlacement::selectionLabel).toList()
                    : List.of(selectionLabel(selection));
            return new ProbeReport(selection.shell(), selection.ray(),
                    selection.idealRay().description(), selection.requestedRadius(),
                    selection.requestedCoordinate(), selection.requestedDirection(),
                    ProbeDetectorType.RAW_NODE,
                    selection.realizedRadius(), selection.angularDeviationDegrees(),
                    selection.realizedDirection(),
                    selection.angularDeviationFromFirstShellDegrees(),
                    selection.graphDistance(), coordinate, selection.available(),
                    selection.unavailableReason(), representedTargets, List.of());
        }).toList();
    }

    private static String selectionLabel(Selection selection) {
        return "Shell " + (selection.shell() + 1) + " " + selection.idealRay().description();
    }

    private static String targetLabel(ProbeDefinition.Target target) {
        return "Shell " + (target.shell() + 1) + " " + target.rayPlane();
    }

    private static Result invalid(String status) {
        return new Result(List.of(), status, false, List.of(), "");
    }
}
