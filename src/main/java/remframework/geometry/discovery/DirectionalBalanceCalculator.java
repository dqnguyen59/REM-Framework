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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * Calculates local angular diagnostics. Directional bias is deliberately distinct
 * from isotropy: non-centrosymmetric symmetric neighborhoods can have non-zero bias.
 */
public final class DirectionalBalanceCalculator {
    public DirectionalBalanceMetrics calculate(GeometryDiscoveryModel model) {
        return calculate(model, TopologyDiagnosticSettings.defaults());
    }

    public DirectionalBalanceMetrics calculate(GeometryDiscoveryModel model,
                                               TopologyDiagnosticSettings settings) {
        Map<Integer, DiscoveryNode> nodes = new HashMap<>();
        model.nodes().forEach(n -> nodes.put(n.id(), n));
        Map<Integer, java.util.Set<Integer>> adjacency =
                TopologyConstraintValidator.adjacency(model);
        int globalDimension = globalDimension(model, settings.eigenvalueRelativeTolerance());
        Map<Integer, LocalDirectionalDiagnostics> local = new LinkedHashMap<>();
        for (DiscoveryNode node : model.sortedNodes())
            local.put(node.id(), calculateNode(node,
                    adjacency.getOrDefault(node.id(), java.util.Set.of()), nodes, settings));
        if (local.isEmpty()) return empty(globalDimension);

        double biasSum = 0, biasSquares = 0, maxBias = -1;
        double tensor = 0, angular = 0, pairMean = 0, pairVariance = 0;
        double coverageMean = 0, coverageVariance = 0, maxGap = 0;
        double oppositeMean = 0, oppositeMax = 0;
        int worst = -1, tensorCount = 0, angularCount = 0, pairCount = 0;
        int coverageCount = 0, oppositeCount = 0, missingOpposites = 0;
        int deficient = 0, mismatch = 0;
        for (LocalDirectionalDiagnostics value : local.values()) {
            double bias = value.normalizedDirectionalBias();
            biasSum += bias;
            biasSquares += bias * bias;
            if (bias > maxBias) { maxBias = bias; worst = value.nodeId(); }
            if (value.degree() > 0) {
                tensor += value.tensorIsotropyError();
                tensorCount++;
            }
            if (value.angularReferenceApplicable()) {
                angular += value.knownReferenceAngularError();
                angularCount++;
            }
            if (!value.pairwiseDotProducts().isEmpty()) {
                pairMean += value.meanPairwiseDotProduct();
                pairVariance += value.pairwiseDotProductVariance();
                pairCount++;
            }
            if (value.sphericalCoverageApplicable()) {
                coverageMean += value.meanNearestDirectionAngle();
                coverageVariance += value.angularCoverageVariance();
                maxGap = Math.max(maxGap, value.maximumUncoveredAngle());
                coverageCount++;
            }
            if (value.oppositeDirectionApplicable()) {
                oppositeMean += value.meanOppositeDirectionMismatch();
                oppositeMax = Math.max(oppositeMax, value.maximumOppositeDirectionMismatch());
                missingOpposites += value.missingOppositeDirectionCount();
                oppositeCount++;
            }
            if (value.localDimensionality() < globalDimension) deficient++;
            if (value.degree() > 0 && value.localDimensionality() != globalDimension) mismatch++;
        }
        double meanBias = biasSum / local.size();
        return new DirectionalBalanceMetrics(meanBias, maxBias,
                Math.sqrt(Math.max(0, biasSquares / local.size() - meanBias * meanBias)), worst,
                average(tensor, tensorCount), tensorCount, average(angular, angularCount), angularCount,
                average(pairMean, pairCount), average(pairVariance, pairCount),
                average(coverageMean, coverageCount), maxGap,
                average(coverageVariance, coverageCount), coverageCount,
                average(oppositeMean, oppositeCount), oppositeMax, missingOpposites, oppositeCount,
                deficient, mismatch, globalDimension,
                Collections.unmodifiableMap(new LinkedHashMap<>(local)));
    }

    private LocalDirectionalDiagnostics calculateNode(DiscoveryNode node,
                                                      java.util.Set<Integer> neighborIds,
                                                      Map<Integer, DiscoveryNode> nodes,
                                                      TopologyDiagnosticSettings settings) {
        List<Vector3d> directions = new ArrayList<>();
        for (int neighborId : neighborIds) {
            DiscoveryNode neighbor = nodes.get(neighborId);
            if (neighbor == null) continue;
            Vector3d direction = new Vector3d(neighbor.position()).sub(node.position());
            double length = direction.length();
            if (length > 1e-12 && Double.isFinite(length)) directions.add(direction.div(length));
        }
        int degree = directions.size();
        double[][] moment = moment(directions);
        double[] eigenvalues = eigenvalues(moment);
        int dimension = dimension(eigenvalues, settings.eigenvalueRelativeTolerance());
        Vector3d sum = new Vector3d();
        directions.forEach(sum::add);
        double rawBias = sum.length();
        double normalizedBias = degree == 0 ? 0 : rawBias / degree;
        double tensorError = tensorError(moment, Math.max(1, dimension));

        List<Double> dots = pairwiseDots(directions);
        double dotMean = dots.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double dotVariance = dots.stream().mapToDouble(v -> Math.pow(v - dotMean, 2))
                .average().orElse(0);
        AngularReferenceType reference = reference(degree, dimension);
        boolean referenceApplicable = reference != AngularReferenceType.NONE;
        double referenceError = referenceApplicable ? referenceError(dots, reference) : 0;

        Coverage coverage = dimension == 3 && degree > 0
                ? coverage(directions, settings.sphericalSampleCount()) : Coverage.unavailable();
        Opposite opposite = degree > 1
                ? opposite(directions, settings.oppositeDirectionToleranceRadians())
                : Opposite.unavailable();
        return new LocalDirectionalDiagnostics(node.id(), degree, dimension,
                Arrays.stream(eigenvalues).boxed().toList(), normalizedBias, rawBias, tensorError,
                List.copyOf(dots), dotMean, dotVariance, referenceApplicable, reference,
                referenceError, coverage.applicable, coverage.mean, coverage.maximum,
                coverage.variance, opposite.applicable, opposite.mean, opposite.maximum,
                opposite.missing);
    }

    static double[] eigenvalues(double[][] matrix) {
        double[][] a = new double[3][3];
        for (int i = 0; i < 3; i++) a[i] = matrix[i].clone();
        for (int iteration = 0; iteration < 32; iteration++) {
            int p = 0, q = 1;
            if (Math.abs(a[0][2]) > Math.abs(a[p][q])) { p = 0; q = 2; }
            if (Math.abs(a[1][2]) > Math.abs(a[p][q])) { p = 1; q = 2; }
            if (Math.abs(a[p][q]) < 1e-15) break;
            double angle = .5 * Math.atan2(2 * a[p][q], a[q][q] - a[p][p]);
            double c = Math.cos(angle), s = Math.sin(angle);
            double app = c * c * a[p][p] - 2 * s * c * a[p][q] + s * s * a[q][q];
            double aqq = s * s * a[p][p] + 2 * s * c * a[p][q] + c * c * a[q][q];
            for (int r = 0; r < 3; r++) if (r != p && r != q) {
                double arp = c * a[r][p] - s * a[r][q];
                double arq = s * a[r][p] + c * a[r][q];
                a[r][p] = a[p][r] = arp;
                a[r][q] = a[q][r] = arq;
            }
            a[p][p] = app;
            a[q][q] = aqq;
            a[p][q] = a[q][p] = 0;
        }
        double[] values = {Math.max(0, a[0][0]), Math.max(0, a[1][1]), Math.max(0, a[2][2])};
        Arrays.sort(values);
        return new double[]{values[2], values[1], values[0]};
    }

    private static double[][] moment(List<Vector3d> directions) {
        double[][] result = new double[3][3];
        if (directions.isEmpty()) return result;
        for (Vector3d u : directions) {
            double[] value = {u.x, u.y, u.z};
            for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++)
                result[r][c] += value[r] * value[c] / directions.size();
        }
        return result;
    }

    private static double tensorError(double[][] moment, int dimension) {
        double error = 0;
        double target = 1.0 / dimension;
        double[] eigenvalues = eigenvalues(moment);
        for (int i = 0; i < 3; i++) {
            double expected = i < dimension ? target : 0;
            error += Math.pow(eigenvalues[i] - expected, 2);
        }
        return Math.sqrt(error);
    }

    private static int dimension(double[] eigenvalues, double tolerance) {
        if (eigenvalues[0] <= 1e-15) return 0;
        double threshold = eigenvalues[0] * tolerance;
        int result = 0;
        for (double value : eigenvalues) if (value > threshold) result++;
        return result;
    }

    private static int globalDimension(GeometryDiscoveryModel model, double tolerance) {
        if (model.nodes().size() < 2) return model.nodes().isEmpty() ? 0 : 1;
        Vector3d mean = new Vector3d();
        model.nodes().forEach(n -> mean.add(n.position()));
        mean.div(model.nodes().size());
        List<Vector3d> centered = model.nodes().stream()
                .map(n -> new Vector3d(n.position()).sub(mean)).toList();
        return dimension(eigenvalues(moment(centered)), tolerance);
    }

    private static List<Double> pairwiseDots(List<Vector3d> directions) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < directions.size(); i++)
            for (int j = i + 1; j < directions.size(); j++)
                result.add(clamp(directions.get(i).dot(directions.get(j))));
        result.sort(Double::compare);
        return result;
    }

    private static AngularReferenceType reference(int degree, int dimension) {
        if (degree == 2 && dimension == 1) return AngularReferenceType.OPPOSITE_PAIR_1D;
        if (degree == 3 && dimension == 2) return AngularReferenceType.EQUILATERAL_2D;
        if (degree == 4 && dimension == 3) return AngularReferenceType.TETRAHEDRAL_3D;
        if (degree == 6 && dimension == 3) return AngularReferenceType.OCTAHEDRAL_3D;
        return AngularReferenceType.NONE;
    }

    private static double referenceError(List<Double> actual, AngularReferenceType type) {
        double[] expected = switch (type) {
            case OPPOSITE_PAIR_1D -> new double[]{-1};
            case EQUILATERAL_2D -> new double[]{-.5, -.5, -.5};
            case TETRAHEDRAL_3D -> new double[]{-1.0 / 3, -1.0 / 3, -1.0 / 3,
                    -1.0 / 3, -1.0 / 3, -1.0 / 3};
            case OCTAHEDRAL_3D -> new double[]{-1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
            case NONE -> new double[0];
        };
        if (actual.size() != expected.length) return 0;
        double sum = 0;
        for (int i = 0; i < expected.length; i++) sum += Math.pow(actual.get(i) - expected[i], 2);
        return Math.sqrt(sum / expected.length);
    }

    private static Coverage coverage(List<Vector3d> directions, int samples) {
        int count = Math.max(16, samples);
        double sum = 0, squares = 0, maximum = 0;
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < count; i++) {
            double y = 1 - 2.0 * (i + .5) / count;
            double radius = Math.sqrt(Math.max(0, 1 - y * y));
            double angle = golden * i;
            Vector3d sample = new Vector3d(Math.cos(angle) * radius, y,
                    Math.sin(angle) * radius);
            double nearest = Math.PI;
            for (Vector3d direction : directions)
                nearest = Math.min(nearest, Math.acos(clamp(sample.dot(direction))));
            sum += nearest;
            squares += nearest * nearest;
            maximum = Math.max(maximum, nearest);
        }
        double mean = sum / count;
        return new Coverage(true, mean, maximum, Math.max(0, squares / count - mean * mean));
    }

    private static Opposite opposite(List<Vector3d> directions, double tolerance) {
        double sum = 0, maximum = 0;
        int missing = 0;
        for (int i = 0; i < directions.size(); i++) {
            double bestDot = 1;
            for (int j = 0; j < directions.size(); j++)
                if (i != j) bestDot = Math.min(bestDot, directions.get(i).dot(directions.get(j)));
            double mismatch = Math.acos(clamp(-bestDot));
            sum += mismatch;
            maximum = Math.max(maximum, mismatch);
            if (mismatch > tolerance) missing++;
        }
        return new Opposite(true, sum / directions.size(), maximum, missing);
    }

    private static double average(double value, int count) { return count == 0 ? 0 : value / count; }
    private static double clamp(double value) { return Math.max(-1, Math.min(1, value)); }
    private record Coverage(boolean applicable, double mean, double maximum, double variance) {
        static Coverage unavailable() { return new Coverage(false, 0, 0, 0); }
    }
    private record Opposite(boolean applicable, double mean, double maximum, int missing) {
        static Opposite unavailable() { return new Opposite(false, 0, 0, 0); }
    }
    private static DirectionalBalanceMetrics empty(int dimension) {
        return new DirectionalBalanceMetrics(0, 0, 0, -1, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, dimension, Map.of());
    }
}
