package remframework.opengl.model;

import org.joml.Vector3d;
import org.joml.Vector3i;
import remframework.opengl.model.entity.Node;

import java.util.ArrayList;
import java.util.List;

/** Pure interpolation-stencil and passive weighted-value calculations. */
public final class ProbeInterpolation {
    private static final double BOUNDARY_EPSILON = 1.0e-9;

    private record AxisCell(int lower, int upper, double fraction) {
    }

    private ProbeInterpolation() {
    }

    /** Builds the canonical four-node stencil without extrapolating beyond lattice bounds. */
    public static List<ProbeDefinition.InterpolationSource> createBilinearStencil(
            Vector3d target, String plane, Vector3i minimum, Vector3i maximum) {
        if (target == null || plane == null || minimum == null || maximum == null) return List.of();
        return switch (plane) {
            case "XY" -> bilinearXy(target, minimum, maximum);
            case "XZ" -> bilinearXz(target, minimum, maximum);
            case "YZ" -> bilinearYz(target, minimum, maximum);
            default -> List.of();
        };
    }

    /** Builds the canonical eight-node stencil and deterministic boundary weights. */
    public static List<ProbeDefinition.InterpolationSource> createTrilinearStencil(
            Vector3d target, Vector3i minimum, Vector3i maximum) {
        if (target == null || minimum == null || maximum == null) return List.of();
        AxisCell x = axisCell(target.x, minimum.x, maximum.x);
        AxisCell y = axisCell(target.y, minimum.y, maximum.y);
        AxisCell z = axisCell(target.z, minimum.z, maximum.z);
        if (x == null || y == null || z == null) return List.of();
        List<ProbeDefinition.InterpolationSource> sources = new ArrayList<>(8);
        for (int zSide = 0; zSide <= 1; zSide++) {
            for (int ySide = 0; ySide <= 1; ySide++) {
                for (int xSide = 0; xSide <= 1; xSide++) {
                    int xIndex = xSide == 0 ? x.lower() : x.upper();
                    int yIndex = ySide == 0 ? y.lower() : y.upper();
                    int zIndex = zSide == 0 ? z.lower() : z.upper();
                    double weight = factor(x.fraction(), xSide)
                            * factor(y.fraction(), ySide) * factor(z.fraction(), zSide);
                    sources.add(new ProbeDefinition.InterpolationSource(
                            new Vector3i(xIndex, yIndex, zIndex), weight));
                }
            }
        }
        return List.copyOf(sources);
    }

    /**
     * Reads copies of committed source-node fields and forms a passive weighted estimate without
     * mutating any source node or returned node vector.
     */
    public static PassiveObserver.Observation interpolate(
            List<Node> nodes, List<ProbeDefinition.InterpolationSource> sources) {
        if (nodes.size() != sources.size() || nodes.isEmpty()) {
            throw new IllegalArgumentException("Interpolation nodes and sources must have equal non-zero size.");
        }
        Vector3d position = new Vector3d();
        Vector3d velocity = new Vector3d();
        Vector3d acceleration = new Vector3d();
        Vector3d gradientImbalance = new Vector3d();
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            double weight = sources.get(index).weight();
            position.add(new Vector3d(node.getPosition()).mul(weight));
            velocity.add(new Vector3d(node.getVelocity()).mul(weight));
            acceleration.add(new Vector3d(node.getAcceleration()).mul(weight));
            gradientImbalance.add(new Vector3d(node.getGradientImbalance()).mul(weight));
        }
        return new PassiveObserver.Observation(position, velocity, acceleration, gradientImbalance);
    }

    private static List<ProbeDefinition.InterpolationSource> bilinearXy(
            Vector3d target, Vector3i minimum, Vector3i maximum) {
        AxisCell x = axisCell(target.x, minimum.x, maximum.x);
        AxisCell y = axisCell(target.y, minimum.y, maximum.y);
        Integer z = fixedIndex(target.z, minimum.z, maximum.z);
        if (x == null || y == null || z == null) return List.of();
        return bilinearSources(x, y,
                (first, second) -> new Vector3i(first, second, z));
    }

    private static List<ProbeDefinition.InterpolationSource> bilinearXz(
            Vector3d target, Vector3i minimum, Vector3i maximum) {
        AxisCell x = axisCell(target.x, minimum.x, maximum.x);
        AxisCell z = axisCell(target.z, minimum.z, maximum.z);
        Integer y = fixedIndex(target.y, minimum.y, maximum.y);
        if (x == null || z == null || y == null) return List.of();
        return bilinearSources(x, z,
                (first, second) -> new Vector3i(first, y, second));
    }

    private static List<ProbeDefinition.InterpolationSource> bilinearYz(
            Vector3d target, Vector3i minimum, Vector3i maximum) {
        AxisCell y = axisCell(target.y, minimum.y, maximum.y);
        AxisCell z = axisCell(target.z, minimum.z, maximum.z);
        Integer x = fixedIndex(target.x, minimum.x, maximum.x);
        if (y == null || z == null || x == null) return List.of();
        return bilinearSources(y, z,
                (first, second) -> new Vector3i(x, first, second));
    }

    private interface CoordinateFactory {
        Vector3i create(int first, int second);
    }

    private static List<ProbeDefinition.InterpolationSource> bilinearSources(
            AxisCell first, AxisCell second, CoordinateFactory coordinateFactory) {
        return List.of(
                new ProbeDefinition.InterpolationSource(
                        coordinateFactory.create(first.lower(), second.lower()),
                        factor(first.fraction(), 0) * factor(second.fraction(), 0)),
                new ProbeDefinition.InterpolationSource(
                        coordinateFactory.create(first.upper(), second.lower()),
                        factor(first.fraction(), 1) * factor(second.fraction(), 0)),
                new ProbeDefinition.InterpolationSource(
                        coordinateFactory.create(first.lower(), second.upper()),
                        factor(first.fraction(), 0) * factor(second.fraction(), 1)),
                new ProbeDefinition.InterpolationSource(
                        coordinateFactory.create(first.upper(), second.upper()),
                        factor(first.fraction(), 1) * factor(second.fraction(), 1)));
    }

    /**
     * Chooses the positive-side cell at an interior integer boundary and the final cell at the
     * maximum boundary; zero-weight corners remain present in the four/eight-source stencil.
     */
    private static AxisCell axisCell(double coordinate, int minimum, int maximum) {
        if (!Double.isFinite(coordinate) || maximum <= minimum
                || coordinate < minimum - BOUNDARY_EPSILON
                || coordinate > maximum + BOUNDARY_EPSILON) return null;
        double bounded = Math.max(minimum, Math.min(maximum, coordinate));
        int lower = (int) Math.floor(bounded);
        if (lower >= maximum) lower = maximum - 1;
        return new AxisCell(lower, lower + 1, bounded - lower);
    }

    private static Integer fixedIndex(double coordinate, int minimum, int maximum) {
        if (!Double.isFinite(coordinate) || coordinate < minimum - BOUNDARY_EPSILON
                || coordinate > maximum + BOUNDARY_EPSILON) return null;
        long rounded = Math.round(coordinate);
        if (Math.abs(coordinate - rounded) > BOUNDARY_EPSILON
                || rounded < minimum || rounded > maximum) return null;
        return (int) rounded;
    }

    private static double factor(double fraction, int upper) {
        return upper == 0 ? 1.0 - fraction : fraction;
    }
}
