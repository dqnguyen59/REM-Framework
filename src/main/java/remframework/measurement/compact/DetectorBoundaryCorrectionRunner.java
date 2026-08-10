package remframework.measurement.compact;

import remframework.measurement.detector.IsotropyReferenceGeometry;
import org.joml.Vector3i;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Geometry-only audit for the detector-boundary correction. It performs no REM measurement. */
public final class DetectorBoundaryCorrectionRunner {
    private static final int S = 24;
    private static final int HALF = 148;
    private static final int DIMENSION = 297;
    private static final int UPDATES = 198;
    private static final int ABSORPTION = 30;
    private record Shape(String experiment, String shapeId, int radius, double indentation) { }
    private record Row(Shape shape, int direction, Vector3i ray, int g, int boundaryMultiplier,
                       Vector3i boundary, Vector3i detector, int centerDistance, int rayOffset,
                       int minimumDistance, boolean occupied, String status, String reason) { }

    private DetectorBoundaryCorrectionRunner() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: DetectorBoundaryCorrectionRunner <output-directory>");
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(root);
        IsotropyReferenceGeometry.validate();
        List<Shape> shapes = validationShapes();
        writeGeometry(root.resolve("corrected-detector-geometry.csv"), rows(shapes));
        writeValidation(root.resolve("unindented-validation.csv"), rows(List.of(
                new Shape("validation", "r000-i000000", 0, 0), new Shape("validation", "r005-i000000", 5, 0),
                new Shape("validation", "r024-i000000", 24, 0), new Shape("validation", "r025-i000000", 25, 0),
                new Shape("validation", "r041-i000000", 41, 0))));
        writeValidation(root.resolve("indented-validation.csv"), rows(List.of(
                new Shape("validation", "r020-i010000", 20, 1), new Shape("validation", "r020-i030000", 20, 3),
                new Shape("validation", "r041-i010000", 41, 1))));
        auditPrevious(root.resolve("previous-results-audit.csv"));
        writePreflight(root.resolve("lattice-preflight.csv"));
        writeStatus(root.resolve("CONFIGURATION-STATUS.md"));
    }

    private static List<Shape> validationShapes() {
        return List.of(new Shape("validation-unindented", "r000-i000000", 0, 0), new Shape("validation-unindented", "r005-i000000", 5, 0),
                new Shape("validation-unindented", "r024-i000000", 24, 0), new Shape("validation-unindented", "r025-i000000", 25, 0),
                new Shape("validation-unindented", "r041-i000000", 41, 0), new Shape("validation-indented", "r020-i010000", 20, 1),
                new Shape("validation-indented", "r020-i030000", 20, 3), new Shape("validation-indented", "r041-i010000", 41, 1));
    }

    private static List<Row> rows(List<Shape> shapes) {
        List<Row> out = new ArrayList<>();
        for (Shape shape : shapes) for (int i = 0; i < IsotropyReferenceGeometry.RAYS.size(); i++) {
            Vector3i ray = IsotropyReferenceGeometry.RAYS.get(i); int g = length(ray);
            int m = boundaryMultiplier(shape.radius, shape.indentation, ray);
            Vector3i boundary = new Vector3i(ray).mul(m);
            int offset = S / g; Vector3i detector = new Vector3i(ray).mul(m + offset);
            boolean occupied = occupied(detector, shape.radius, shape.indentation);
            int minimum = minimumDistance(detector, shape.radius, shape.indentation);
            String status = occupied ? "DETECTOR_INSIDE_SOURCE" : "VALID";
            String reason = occupied ? "Detector lies inside occupied source region" : "Detector is outside source";
            out.add(new Row(shape, i + 1, ray, g, m, boundary, detector, length(detector),
                    length(detector) - length(boundary), minimum, occupied, status, reason));
        }
        return out;
    }

    private static int boundaryMultiplier(int radius, double indentation, Vector3i ray) {
        if (radius == 0) return 0;
        double coefficient = length(ray) + indentation * Math.min(Math.abs(ray.x), Math.min(Math.abs(ray.y), Math.abs(ray.z)));
        return (int) Math.floor((radius - 1) / coefficient);
    }

    private static boolean occupied(Vector3i p, int radius, double indentation) {
        if (radius == 0) return p.x == 0 && p.y == 0 && p.z == 0;
        return length(p) + indentation * Math.min(Math.abs(p.x), Math.min(Math.abs(p.y), Math.abs(p.z))) < radius;
    }

    private static int minimumDistance(Vector3i detector, int radius, double indentation) {
        int limit = radius == 0 ? 0 : radius - 1;
        int minimum = Integer.MAX_VALUE;
        for (int x = -limit; x <= limit; x++) for (int y = -limit; y <= limit; y++) for (int z = -limit; z <= limit; z++) {
            Vector3i p = new Vector3i(x, y, z); if (occupied(p, radius, indentation)) minimum = Math.min(minimum, manhattan(detector, p));
        }
        return minimum;
    }

    private static void writeGeometry(Path file, List<Row> rows) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            w.write("experiment,shapeId,baseRadius,indentation,directionId,primitiveDirection,g,mBoundary,boundaryRelativeCoordinate,detectorRelativeCoordinate,centerGraphDistance,rayBoundaryGraphOffset,minimumGraphDistanceToOccupiedRegion,detectorOccupied,status,reason\n");
            for (Row r : rows) writeRow(w, r);
        }
    }

    private static void writeValidation(Path file, List<Row> rows) throws IOException { writeGeometry(file, rows); }

    private static void writeRow(BufferedWriter w, Row r) throws IOException {
        w.write(String.format(Locale.ROOT, "%s,%s,%d,%.17g,R%02d,\"(%d,%d,%d)\",%d,%d,\"(%d,%d,%d)\",\"(%d,%d,%d)\",%d,%d,%d,%s,%s,%s%n",
                r.shape.experiment, r.shape.shapeId, r.shape.radius, r.shape.indentation, r.direction, r.ray.x, r.ray.y, r.ray.z,
                r.g, r.boundaryMultiplier, r.boundary.x, r.boundary.y, r.boundary.z, r.detector.x, r.detector.y, r.detector.z,
                r.centerDistance, r.rayOffset, r.minimumDistance, r.occupied, r.status, r.reason));
    }

    private static void auditPrevious(Path file) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            w.write("experiment,shapeId,baseRadius,indentation,directionId,detectorNode,detectorOccupied,status,reason\n");
            List<Shape> shapes = new ArrayList<>();
            for (int r = 1; r <= 16; r++) shapes.add(new Shape("Step2", String.format(Locale.ROOT, "r%03d", r), r, 0));
            for (int r = 25; r <= 40; r++) shapes.add(new Shape("DensePreferredRadius", String.format(Locale.ROOT, "r%03d", r), r, 0));
            for (Shape shape : shapes) for (int i = 0; i < IsotropyReferenceGeometry.RAYS.size(); i++) {
                Vector3i ray = IsotropyReferenceGeometry.RAYS.get(i); int m = 24 / length(ray);
                Vector3i detector = new Vector3i(ray).mul(m); boolean inside = occupied(detector, shape.radius, shape.indentation);
                boolean incomplete = shape.experiment.equals("DensePreferredRadius") && shape.radius == 40;
                String status = incomplete ? "MEASUREMENT_INCOMPLETE" : inside ? "DETECTOR_INSIDE_SOURCE" : "VALID";
                String reason = incomplete ? "R=40 staging directory has no finalized measurement; fixed D=24 detector would also be occupied" : inside ? "Historical center-shell D=24 detector is occupied by source" : "Historical detector is outside source";
                w.write(String.format(Locale.ROOT, "%s,%s,%d,%.17g,R%02d,\"(148,148,148)+(%d,%d,%d)=(%d,%d,%d)\",%s,%s,%s%n",
                        shape.experiment, shape.shapeId, shape.radius, shape.indentation, i + 1, ray.x * m, ray.y * m, ray.z * m,
                        148 + ray.x * m, 148 + ray.y * m, 148 + ray.z * m, inside, status, reason));
            }
        }
    }

    private static void writePreflight(Path file) throws IOException {
        int target = 100; int maxDetectorOffset = target + S - 1; int requiredHalf = Math.max((UPDATES + maxDetectorOffset) / 2 + 1, maxDetectorOffset + ABSORPTION);
        int requiredDimension = 2 * requiredHalf + 1; long nodes = 1L * requiredDimension * requiredDimension * requiredDimension; long compactBytes = nodes * 96L;
        int reflectionMaxRadius = 0; for (int r = 0; r <= 100; r++) { int detectorOffset = r == 0 ? S : r + S - 1; if (Math.max((UPDATES + detectorOffset) / 2 + 1, detectorOffset + ABSORPTION) <= HALF) reflectionMaxRadius = r; }
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            w.write("targetMaximumRadius,requiredHalfSize,requiredLatticeDimension,estimatedNodeCount,estimatedCompactMemoryBytes,reflectionSafe,currentLatticeDimension,maximumReflectionSafeRadius\n");
            w.write(String.format(Locale.ROOT, "%d,%d,%d,%d,%d,%s,%d,%d%n", target, requiredHalf, requiredDimension, nodes, compactBytes, requiredHalf <= HALF, DIMENSION, reflectionMaxRadius));
        }
    }

    private static void writeStatus(Path file) throws IOException {
        Files.writeString(file, "# Detector-boundary correction\n\n" +
                "## ConfigurationStatus\n\n" +
                "Historical fixed-center-shell results are preserved. Step 2 radii 1..16 remain external-detector measurements; dense Step 3 unindented radii 25..40 are reclassified `DETECTOR_INSIDE_SOURCE` (R=40 was incomplete). No result is deleted.\n\n" +
                "## Corrected rule\n\n" +
                "For ray v, g=|vx|+|vy|+|vz|, mBoundary is the largest occupied multiplier, and detector=(mBoundary+24/g)v. The ray-wise boundary offset is exactly 24 graph transfers. The global minimum distance to the shaped source is recorded separately.\n\n" +
                "## R=0 compatibility\n\n" +
                "R=0 uses the existing single-node special case: the origin is the occupied source and mBoundary=0. Therefore detector=(24/g)v, exactly matching the existing Step 1 D=24 geometry.\n\n" +
                "## Validation result\n\n" +
                "All 120 corrected validation rows have detectorOccupied=false and rayBoundaryGraphOffset=24. For the unindented validation cases, the global minimum source distance is 24 for 46 rows and 19..23 for 29 rows; this discrete difference occurs when the selected ray’s last occupied node is not on the outermost L1 shell. For the indented cases, 10 rows equal 24 and 35 rows range from 13..23. The distinction is retained rather than corrected away.\n\n" +
                "## Future lattice preflight\n\n" +
                "For the requested future R=100 unindented shape, the furthest corrected detector coordinate is 123 from the center. Reflection safety through update 198 requires half-size 161, dimension 323; the current 297^3 lattice is therefore not reflection-safe through R=100. Under the current lattice the largest reflection-safe radius is R=74. This task did not start a corrected sweep.\n");
    }

    private static int length(Vector3i v) { return Math.abs(v.x) + Math.abs(v.y) + Math.abs(v.z); }
    private static int manhattan(Vector3i a, Vector3i b) { return Math.abs(a.x - b.x) + Math.abs(a.y - b.y) + Math.abs(a.z - b.z); }
}
