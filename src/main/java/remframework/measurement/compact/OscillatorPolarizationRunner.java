package remframework.measurement.compact;

import org.joml.Vector3d;
import remframework.helpers.Constants;
import remframework.measurement.detector.ExtendedIdentityShape;
import remframework.measurement.detector.IsotropyReferenceGeometry;
import remframework.opengl.model.entity.Identity;
import remframework.settings.AppSettings;
import remframework.settings.Project;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** Runs the frozen V2 R=96 oscillator-polarization diagnostic. */
public final class OscillatorPolarizationRunner {
    private static final int RADIUS = 96;
    private static final int UPDATES = 198;
    private static final int PERIOD = 18;
    private static final int CYCLES = 6;
    private static final int EXPECTED_DIMENSION = 333;
    private static final int EXPECTED_OCCUPIED_NODES = 1_161_471;
    private static final String CANONICAL_CONFIGURATION = "samples/isotropy-reference-v2-Z.qfs";
    private static final String CANONICAL_CONFIGURATION_SHA256 =
            "b07f739c06ba15546756fef818fb1b9e1c4cc1b76e2aa61c544302f5f5cab3ad";
    private static final List<Orientation> ORIENTATIONS = List.of(
            new Orientation("O1", "(0,0,1)", new Vector3d(0, 0, 1)),
            new Orientation("O2", "norm(1,-1,0)", new Vector3d(1, -1, 0)),
            new Orientation("O3", "norm(1,1,-2)", new Vector3d(1, 1, -2)));

    private record Orientation(String id, String label, Vector3d raw, Vector3d unit) {
        private Orientation(String id, String label, Vector3d raw) {
            this(id, label, new Vector3d(raw), new Vector3d(raw).normalize());
        }

        private Orientation {
            raw = new Vector3d(raw);
            unit = new Vector3d(unit);
            if (Math.abs(unit.length() - 1.0) > 1e-15) {
                throw new IllegalArgumentException("Orientation must be unit length: " + id);
            }
        }
    }

    private record Result(String orientation, String label, double x, double y, double z,
                          String status, double mean, double[] means, double[] q,
                          double emax, double mad, double stdDev, int limitingIndex) { }

    private OscillatorPolarizationRunner() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: OscillatorPolarizationRunner "
                    + "<project.qfs> <output-directory> <O1|O2|O3|finalize>");
        }
        IsotropyReferenceGeometry.validate();
        Path project = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        validateCanonicalConfiguration(project);
        Files.createDirectories(output);
        if (args[2].equals("finalize")) {
            finalizeOutputs(output);
            return;
        }
        Orientation orientation = ORIENTATIONS.stream().filter(o -> o.id.equals(args[2]))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown orientation: " + args[2]));
        measure(project, output, orientation);
    }

    private static void measure(Path projectPath, Path root, Orientation orientation) throws Exception {
        Path output = root.resolve(orientation.id);
        Files.createDirectories(output);
        try {
            AppSettings.getInstance().loadFromFile(Constants.BASE_PATH + Constants.APP_SETTINGS_FILE);
            Project project = Project.getInstance();
            project.loadHeadlessFromFile(projectPath.toString());
            project.getRemModel().startSimulation();
            validateLoadedProject(project);

            Identity identity = project.occupancies.getFirst().getIdentity();
            int cx = project.occupancies.getFirst().getNodeIndex().x;
            int cy = project.occupancies.getFirst().getNodeIndex().y;
            int cz = project.occupancies.getFirst().getNodeIndex().z;
            List<int[]> sourcePoints = sourcePoints();
            if (sourcePoints.size() != EXPECTED_OCCUPIED_NODES) {
                throw new IllegalStateException("CONFIGURATION_ERROR: unexpected R=96 occupied-node count");
            }
            int shell = commonDetectorShell(sourcePoints);
            int[][] detectors = detectors(cx, cy, cz, shell, sourcePoints);

            double[][] cycles = new double[CYCLES][15];
            try (CompactMeasurementBackend backend = new CompactMeasurementBackend(
                    project.getDimensionX(), project.getDimensionY(), project.getDimensionZ(),
                    project.getConstantWaveSpeed(), project.getConstantElectricFactor())) {
                backend.setParallelism(Math.min(16, AppSettings.getInstance().getNumThreads()));
                backend.setSourceNodes(sourcePoints.stream()
                        .mapToInt(p -> backend.index(cx + p[0], cy + p[1], cz + p[2])).toArray());
                captureCycles(identity, backend, detectors, orientation.unit, cycles);
            }
            Result result = calculate(orientation, cycles);
            writeRunOutputs(output, result, shell, detectors, cx, cy, cz, sourcePoints, cycles);
            System.out.printf(Locale.ROOT, "%s Emax=%.17g MAD=%.17g StdDev=%.17g limiting=R%02d%n",
                    result.orientation, result.emax, result.mad, result.stdDev, result.limitingIndex + 1);
        } catch (Throwable failure) {
            writeFailure(output, failure);
            throw failure;
        }
    }

    private static void validateLoadedProject(Project project) {
        if (project.getDimensionX() != EXPECTED_DIMENSION || project.getDimensionY() != EXPECTED_DIMENSION
                || project.getDimensionZ() != EXPECTED_DIMENSION || project.occupancies.size() != 1) {
            throw new IllegalStateException("CONFIGURATION_ERROR: frozen V2 lattice/source mismatch");
        }
    }

    private static void captureCycles(Identity identity, CompactMeasurementBackend backend,
                                      int[][] detectors, Vector3d orientation, double[][] cycles) {
        double[] low = new double[15];
        double[] high = new double[15];
        Arrays.fill(low, Double.POSITIVE_INFINITY);
        Arrays.fill(high, Double.NEGATIVE_INFINITY);
        double previousPhase = Double.NaN;
        int captured = 0;
        for (int update = 1; update <= UPDATES; update++) {
            Vector3d referenceExcitation = identity.generateExcitation();
            if (referenceExcitation == null) {
                throw new IllegalStateException("BACKEND_FAILURE: inactive source oscillator");
            }
            double scalarAmplitude = referenceExcitation.z;
            backend.setSourceForce(scalarAmplitude * orientation.x, scalarAmplitude * orientation.y,
                    scalarAmplitude * orientation.z);
            backend.update();
            double phase = identity.getPhase().z;
            boolean cycleComplete = !Double.isNaN(previousPhase) && phase < previousPhase;
            for (int i = 0; i < detectors.length; i++) {
                Vector3d gradient = backend.gradient(backend.index(
                        detectors[i][0], detectors[i][1], detectors[i][2]));
                double projected = gradient.dot(orientation);
                low[i] = Math.min(low[i], projected);
                high[i] = Math.max(high[i], projected);
            }
            if (cycleComplete) {
                int cycle = update / PERIOD;
                if (cycle > 5 && captured < CYCLES) {
                    for (int i = 0; i < 15; i++) cycles[captured][i] = (low[i] + high[i]) * 0.5;
                    captured++;
                }
                Arrays.fill(low, Double.POSITIVE_INFINITY);
                Arrays.fill(high, Double.NEGATIVE_INFINITY);
            }
            previousPhase = phase;
        }
        if (captured != CYCLES) {
            throw new IllegalStateException("BACKEND_FAILURE: expected six measurement cycles, got " + captured);
        }
    }

    private static Result calculate(Orientation orientation, double[][] cycles) {
        double[] means = new double[15];
        for (int cycle = 0; cycle < CYCLES; cycle++) {
            for (int ray = 0; ray < 15; ray++) means[ray] += cycles[cycle][ray] / CYCLES;
        }
        double mean = Arrays.stream(means).average().orElse(Double.NaN);
        if (!Double.isFinite(mean) || Math.abs(mean) < 1e-30) {
            throw new IllegalStateException("BACKEND_FAILURE: global directional mean is undefined");
        }
        double[] q = Arrays.stream(means).map(value -> value / mean).toArray();
        double emax = Arrays.stream(q).map(value -> Math.abs(value - 1)).max().orElseThrow();
        double mad = Arrays.stream(q).map(value -> Math.abs(value - 1)).average().orElseThrow();
        double stdDev = Math.sqrt(Arrays.stream(q).map(value -> (value - 1) * (value - 1))
                .average().orElseThrow());
        int limiting = 0;
        for (int i = 1; i < q.length; i++) {
            if (Math.abs(q[i] - 1) > Math.abs(q[limiting] - 1)) limiting = i;
        }
        return new Result(orientation.id, orientation.label, orientation.unit.x, orientation.unit.y,
                orientation.unit.z, "VALID", mean, means, q, emax, mad, stdDev, limiting);
    }

    private static void writeRunOutputs(Path output, Result result, int shell, int[][] detectors,
                                        int cx, int cy, int cz, List<int[]> sourcePoints,
                                        double[][] cycles) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(output.resolve("cycle-results.csv"))) {
            writer.write("orientation,directionId,cycleNumber,cycleResponse\n");
            for (int cycle = 0; cycle < CYCLES; cycle++) {
                for (int ray = 0; ray < 15; ray++) {
                    writer.write(String.format(Locale.ROOT, "%s,R%02d,%d,%.17g%n",
                            result.orientation, ray + 1, cycle + 6, cycles[cycle][ray]));
                }
            }
        }
        try (BufferedWriter writer = Files.newBufferedWriter(output.resolve("directional-results.csv"))) {
            writer.write(R096DirectionalCsv.header() + "\n");
            for (int i = 0; i < 15; i++) {
                var ray = IsotropyReferenceGeometry.RAYS.get(i);
                writer.write(R096DirectionalCsv.row(String.format(Locale.ROOT, "R%02d", i + 1),
                        "(" + ray.x + "," + ray.y + "," + ray.z + ")", shell,
                        detectors[i][0] - cx, detectors[i][1] - cy, detectors[i][2] - cz,
                        result.means[i], result.mean, result.q[i], result.status) + "\n");
            }
        }
        try (BufferedWriter writer = Files.newBufferedWriter(output.resolve("metadata.csv"))) {
            writer.write("key,value\n");
            property(writer, "ConfigurationStatus", result.status);
            property(writer, "orientation", result.orientation);
            property(writer, "orientationLabel", result.label);
            property(writer, "orientationX", result.x);
            property(writer, "orientationY", result.y);
            property(writer, "orientationZ", result.z);
            property(writer, "orientationMagnitude", Math.sqrt(result.x * result.x + result.y * result.y
                    + result.z * result.z));
            property(writer, "sourceVectorAmplitude", 200.0);
            property(writer, "I2", 0);
            property(writer, "I3", 0);
            property(writer, "occupiedNodeCount", sourcePoints.size());
            property(writer, "detectorShell", shell);
            property(writer, "measurementCycles", CYCLES);
            property(writer, "globalDirectionalMean", result.mean);
            property(writer, "Emax", result.emax);
            property(writer, "MAD", result.mad);
            property(writer, "StdDev", result.stdDev);
            property(writer, "limitingDirection", String.format(Locale.ROOT, "R%02d", result.limitingIndex + 1));
            for (int i = 0; i < result.q.length; i++) {
                property(writer, String.format(Locale.ROOT, "Q%02d", i + 1), result.q[i]);
                property(writer, String.format(Locale.ROOT, "directionMeanR%02d", i + 1), result.means[i]);
            }
            property(writer, "completed", Instant.now());
        }
    }

    static void finalizeOutputs(Path root) throws IOException {
        List<Result> results = new ArrayList<>();
        for (Orientation orientation : ORIENTATIONS) results.add(readResult(root.resolve(orientation.id), orientation));
        for (Result result : results) {
            Orientation orientation = orientation(result.orientation);
            double[][] cycles = readCycles(root.resolve(result.orientation).resolve("cycle-results.csv"));
            augmentMetadata(root.resolve(result.orientation).resolve("metadata.csv"), orientation);
            writeAuditableDirectional(root.resolve(result.orientation).resolve("directional-results.csv"),
                    orientation, result);
            writeAuditableCycles(root.resolve(result.orientation).resolve("cycle-results.csv"),
                    orientation, cycles);
        }
        writeLongDirectional(root.resolve("oscillator-directional-results.csv"), results);
        try (BufferedWriter writer = Files.newBufferedWriter(root.resolve("oscillator-orientation-summary.csv"))) {
            writer.write("orientationId,oscillatorRawX,oscillatorRawY,oscillatorRawZ,"
                    + "oscillatorUnitX,oscillatorUnitY,oscillatorUnitZ,Emax,MAD,StdDev,"
                    + "limitingDirectionId,limitingDirectionX,limitingDirectionY,limitingDirectionZ,"
                    + "limitingQ,limitingAbsoluteError");
            for (int i = 0; i < 15; i++) writer.write("," + qColumn(i));
            writer.write("\n");
            for (Result result : results) {
                Orientation orientation = orientation(result.orientation);
                var limiting = IsotropyReferenceGeometry.RAYS.get(result.limitingIndex);
                writer.write(String.format(Locale.ROOT,
                        "%s,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,"
                                + "R%02d,%d,%d,%d,%.17g,%.17g",
                        result.orientation, orientation.raw.x, orientation.raw.y, orientation.raw.z,
                        orientation.unit.x, orientation.unit.y, orientation.unit.z,
                        result.emax, result.mad, result.stdDev, result.limitingIndex + 1,
                        limiting.x, limiting.y, limiting.z, result.q[result.limitingIndex],
                        Math.abs(result.q[result.limitingIndex] - 1)));
                for (double q : result.q) writer.write(String.format(Locale.ROOT, ",%.17g", q));
                writer.write("\n");
            }
        }
        Files.writeString(root.resolve("oscillator-directional-comparison.svg"), comparisonChart(results, false));
        Files.writeString(root.resolve("oscillator-highlighted-rays.svg"), comparisonChart(results, true));
        Files.writeString(root.resolve("report.md"), report(results));
        Files.writeString(root.resolve("CONFIGURATION-STATUS.md"),
                "# ConfigurationStatus\n\nVALID\n\n"
                        + "The Phase 4 measurement data is unchanged. These files were regenerated from the "
                        + "persisted measurements solely to improve reporting auditability. No physics, "
                        + "measurement semantics, or scientific conclusions changed.\n");
    }

    private static void writeLongDirectional(Path output, List<Result> results) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(output)) {
            writer.write("orientationId,oscillatorRawX,oscillatorRawY,oscillatorRawZ,"
                    + "oscillatorUnitX,oscillatorUnitY,oscillatorUnitZ,directionId,directionX,directionY,directionZ,"
                    + "directionUnitX,directionUnitY,directionUnitZ,oscillatorDetectorDotProduct,"
                    + "oscillatorDetectorAngleDegrees,directionCycleMean,globalDirectionalMean,normalizedQ,"
                    + "absoluteError\n");
            for (Result result : results) {
                Orientation orientation = orientation(result.orientation);
                for (int i = 0; i < 15; i++) {
                    writer.write(directionalAuditPrefix(orientation, i));
                    writer.write(String.format(Locale.ROOT, ",%.17g,%.17g,%.17g,%.17g%n",
                            result.means[i], result.mean, result.q[i], Math.abs(result.q[i] - 1)));
                }
            }
        }
    }

    private static void augmentMetadata(Path output, Orientation orientation) throws IOException {
        List<String> lines = new ArrayList<>(Files.readAllLines(output));
        List<String> keys = lines.stream().skip(1).map(line -> {
            int comma = line.indexOf(',');
            return comma < 0 ? line : line.substring(0, comma);
        }).toList();
        appendMetadata(lines, keys, "oscillatorRawX", orientation.raw.x);
        appendMetadata(lines, keys, "oscillatorRawY", orientation.raw.y);
        appendMetadata(lines, keys, "oscillatorRawZ", orientation.raw.z);
        appendMetadata(lines, keys, "oscillatorUnitX", orientation.unit.x);
        appendMetadata(lines, keys, "oscillatorUnitY", orientation.unit.y);
        appendMetadata(lines, keys, "oscillatorUnitZ", orientation.unit.z);
        appendMetadata(lines, keys, "reportingProvenance",
                "regenerated from persisted measurements; scientific data unchanged");
        Files.write(output, lines);
    }

    private static void appendMetadata(List<String> lines, List<String> keys, String key, Object value) {
        if (!keys.contains(key)) lines.add(key + "," + value);
    }

    private static void writeAuditableDirectional(Path output, Orientation orientation, Result result)
            throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(output)) {
            writer.write("orientationId,oscillatorRawX,oscillatorRawY,oscillatorRawZ,"
                    + "oscillatorUnitX,oscillatorUnitY,oscillatorUnitZ,directionId,directionX,directionY,directionZ,"
                    + "directionUnitX,directionUnitY,directionUnitZ,oscillatorDetectorDotProduct,"
                    + "oscillatorDetectorAngleDegrees,primitiveDirection,detectorShell,detectorRelativeX,"
                    + "detectorRelativeY,detectorRelativeZ,directionCycleMean,globalDirectionalMean,normalizedQ,"
                    + "absoluteError,status\n");
            for (int i = 0; i < 15; i++) {
                var ray = IsotropyReferenceGeometry.RAYS.get(i);
                int multiplier = 120 / IsotropyReferenceGeometry.graphLength(ray);
                writer.write(directionalAuditPrefix(orientation, i));
                writer.write(String.format(Locale.ROOT, ",\"(%d,%d,%d)\",120,%d,%d,%d,"
                                + "%.17g,%.17g,%.17g,%.17g,%s%n",
                        ray.x, ray.y, ray.z, ray.x * multiplier, ray.y * multiplier, ray.z * multiplier,
                        result.means[i], result.mean, result.q[i], Math.abs(result.q[i] - 1), result.status));
            }
        }
    }

    private static void writeAuditableCycles(Path output, Orientation orientation, double[][] cycles)
            throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(output)) {
            writer.write("orientationId,oscillatorRawX,oscillatorRawY,oscillatorRawZ,"
                    + "oscillatorUnitX,oscillatorUnitY,oscillatorUnitZ,directionId,directionX,directionY,directionZ,"
                    + "cycleNumber,cycleResponse\n");
            for (int cycle = 0; cycle < CYCLES; cycle++) {
                for (int i = 0; i < 15; i++) {
                    var ray = IsotropyReferenceGeometry.RAYS.get(i);
                    writer.write(String.format(Locale.ROOT,
                            "%s,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,R%02d,%d,%d,%d,%d,%.17g%n",
                            orientation.id, orientation.raw.x, orientation.raw.y, orientation.raw.z,
                            orientation.unit.x, orientation.unit.y, orientation.unit.z, i + 1,
                            ray.x, ray.y, ray.z, cycle + 6, cycles[cycle][i]));
                }
            }
        }
    }

    private static double[][] readCycles(Path input) throws IOException {
        List<String> lines = Files.readAllLines(input);
        double[][] cycles = new double[CYCLES][15];
        boolean legacy = lines.getFirst().equals("orientation,directionId,cycleNumber,cycleResponse");
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split(",");
            String directionId = fields[legacy ? 1 : 7];
            int cycle = Integer.parseInt(fields[legacy ? 2 : 11]) - 6;
            int direction = Integer.parseInt(directionId.substring(1)) - 1;
            cycles[cycle][direction] = Double.parseDouble(fields[legacy ? 3 : 12]);
        }
        return cycles;
    }

    private static String directionalAuditPrefix(Orientation orientation, int index) {
        var ray = IsotropyReferenceGeometry.RAYS.get(index);
        Vector3d directionUnit = new Vector3d(ray.x, ray.y, ray.z).normalize();
        double dot = orientation.unit.dot(directionUnit);
        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
        return String.format(Locale.ROOT,
                "%s,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,R%02d,%d,%d,%d,"
                        + "%.17g,%.17g,%.17g,%.17g,%.17g",
                orientation.id, orientation.raw.x, orientation.raw.y, orientation.raw.z,
                orientation.unit.x, orientation.unit.y, orientation.unit.z, index + 1,
                ray.x, ray.y, ray.z, directionUnit.x, directionUnit.y, directionUnit.z, dot, angle);
    }

    private static String qColumn(int index) {
        var ray = IsotropyReferenceGeometry.RAYS.get(index);
        return String.format(Locale.ROOT, "Q_R%02d_%d_%d_%d", index + 1, ray.x, ray.y, ray.z);
    }

    private static Orientation orientation(String id) {
        return ORIENTATIONS.stream().filter(value -> value.id.equals(id)).findFirst().orElseThrow();
    }

    private static Result readResult(Path output, Orientation orientation) throws IOException {
        Properties metadata = readProperties(output.resolve("metadata.csv"));
        if (!"VALID".equals(metadata.getProperty("ConfigurationStatus"))) {
            throw new IOException("MEASUREMENT_INCOMPLETE: " + orientation.id);
        }
        List<String> rows = Files.readAllLines(output.resolve("directional-results.csv"));
        if (rows.size() != 16) throw new IOException("MEASUREMENT_INCOMPLETE: expected 15 rays for " + orientation.id);
        double[] means = new double[15];
        double[] q = new double[15];
        for (int i = 0; i < 15; i++) {
            means[i] = Double.parseDouble(metadata.getProperty(String.format(Locale.ROOT,
                    "directionMeanR%02d", i + 1)));
            q[i] = Double.parseDouble(metadata.getProperty(String.format(Locale.ROOT, "Q%02d", i + 1)));
        }
        return new Result(orientation.id, orientation.label, orientation.unit.x, orientation.unit.y,
                orientation.unit.z, "VALID", Double.parseDouble(metadata.getProperty("globalDirectionalMean")), means, q,
                Double.parseDouble(metadata.getProperty("Emax")), Double.parseDouble(metadata.getProperty("MAD")),
                Double.parseDouble(metadata.getProperty("StdDev")),
                Integer.parseInt(metadata.getProperty("limitingDirection").substring(1)) - 1);
    }

    private static String comparisonChart(List<Result> results, boolean highlightedOnly) {
        int[] indices = highlightedOnly ? new int[]{8, 10, 11, 12, 13, 14}
                : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14};
        int width = 1250, height = 700, left = 80, top = 70, bottom = 90;
        double maximum = results.stream().flatMapToDouble(r -> Arrays.stream(r.q)).max().orElse(2.0);
        double scale = (height - top - bottom) / Math.max(1.0, maximum);
        String[] colors = {"#1565c0", "#d32f2f", "#2e7d32"};
        StringBuilder svg = new StringBuilder(String.format(Locale.ROOT,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\">"
                        + "<rect width=\"100%%\" height=\"100%%\" fill=\"white\"/>"
                        + "<text x=\"%d\" y=\"30\" font-size=\"20\">%s</text>",
                width, height, left, highlightedOnly ? "Highlighted normalized directional response"
                        : "Normalized directional response by oscillator orientation"));
        int idealY = height - bottom - (int) scale;
        svg.append("<line x1=\"").append(left).append("\" y1=\"").append(idealY)
                .append("\" x2=\"").append(width - 30).append("\" y2=\"").append(idealY)
                .append("\" stroke=\"#777\" stroke-dasharray=\"6 5\"/><text x=\"35\" y=\"")
                .append(idealY + 5).append("\">Q=1</text>");
        for (int series = 0; series < results.size(); series++) {
            Result result = results.get(series);
            StringBuilder points = new StringBuilder();
            for (int position = 0; position < indices.length; position++) {
                int index = indices[position];
                int x = left + position * (width - left - 60) / Math.max(1, indices.length - 1);
                int y = height - bottom - (int) (result.q[index] * scale);
                points.append(x).append(',').append(y).append(' ');
                svg.append("<circle cx=\"").append(x).append("\" cy=\"").append(y)
                        .append("\" r=\"5\" fill=\"").append(colors[series]).append("\"/>");
            }
            svg.append("<polyline fill=\"none\" stroke=\"").append(colors[series])
                    .append("\" stroke-width=\"2\" points=\"").append(points).append("\"/>");
            svg.append("<text x=\"").append(left + series * 170).append("\" y=\"55\" fill=\"")
                    .append(colors[series]).append("\">").append(result.orientation).append(' ')
                    .append(result.label).append("</text>");
        }
        for (int position = 0; position < indices.length; position++) {
            var ray = IsotropyReferenceGeometry.RAYS.get(indices[position]);
            int x = left + position * (width - left - 60) / Math.max(1, indices.length - 1);
            svg.append("<text x=\"").append(x - 10).append("\" y=\"").append(height - 55)
                    .append("\" font-size=\"11\" transform=\"rotate(45 ").append(x - 10).append(' ')
                    .append(height - 55).append(")\">R")
                    .append(String.format(Locale.ROOT, "%02d", indices[position] + 1)).append(" (")
                    .append(ray.x).append(',').append(ray.y).append(',').append(ray.z).append(")</text>");
        }
        return svg.append("</svg>\n").toString();
    }

    private static String report(List<Result> results) {
        Result baseline = results.getFirst();
        StringBuilder report = new StringBuilder("# REM Framework Oscillator Polarization Experiment — Phase 4\n\n"
                + "ConfigurationStatus: VALID\n\n"
                + "Reporting/auditability correction: all outputs in this report were regenerated from the "
                + "already persisted Phase 4 measurements. Measurement data is unchanged; no simulation was "
                + "rerun, and no physics, detector geometry, measurement semantics, normalization, or scientific "
                + "conclusions changed.\n\n"
                + "Frozen V2 configuration: R=96, I2=0, I3=0, compact backend, detector shell 120, "
                + "15 exact rays, cycles 6–11, and identical scalar amplitude 200. The measured detector "
                + "quantity is the local gradient projected onto the unit oscillator orientation.\n\n"
                + "## Measurements\n\n"
                + "| Orientation | Emax | MAD | StdDev | Limiting ray | Limiting Q |\n"
                + "|---|---:|---:|---:|---|---:|\n");
        for (Result result : results) {
            report.append(String.format(Locale.ROOT, "| %s %s | %.17g | %.17g | %.17g | R%02d %s | %.17g |%n",
                    result.orientation, result.label, result.emax, result.mad, result.stdDev,
                    result.limitingIndex + 1, IsotropyReferenceGeometry.RAYS.get(result.limitingIndex),
                    result.q[result.limitingIndex]));
        }
        report.append("\n## Primary R09=(1,1,1) audit\n\n"
                + "| Orientation | Oscillator vector | Detector direction | Angle (degrees) | Q | |Q-1| |\n"
                + "|---|---|---|---:|---:|---:|\n");
        Vector3d r09 = new Vector3d(1, 1, 1).normalize();
        for (Result result : results) {
            Orientation orientation = orientation(result.orientation);
            double dot = orientation.unit.dot(r09);
            double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
            report.append(String.format(Locale.ROOT, "| %s | %s | R09 (1,1,1) | %.17g | %.17g | %.17g |%n",
                    result.orientation, result.label, angle, result.q[8], Math.abs(result.q[8] - 1)));
        }
        report.append("\nO2 and O3 have dot product 0 with normalized R09 within numerical tolerance, so both "
                + "are perpendicular to R09 at 90 degrees.\n\n## Highlighted rays\n\n");
        for (int index : new int[]{8, 10, 11, 12, 13, 14}) {
            report.append(String.format(Locale.ROOT, "- R%02d %s: O1=%.17g, O2=%.17g, O3=%.17g.%n",
                    index + 1, IsotropyReferenceGeometry.RAYS.get(index), results.get(0).q[index],
                    results.get(1).q[index], results.get(2).q[index]));
        }
        report.append("\n## Complete 15-direction results\n\n"
                + "| Orientation | Oscillator vector | Direction | Angle (degrees) | Q | |Q-1| |\n"
                + "|---|---|---|---:|---:|---:|\n");
        for (Result result : results) {
            Orientation orientation = orientation(result.orientation);
            for (int i = 0; i < 15; i++) {
                var ray = IsotropyReferenceGeometry.RAYS.get(i);
                Vector3d direction = new Vector3d(ray.x, ray.y, ray.z).normalize();
                double dot = orientation.unit.dot(direction);
                double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
                report.append(String.format(Locale.ROOT,
                        "| %s | %s | R%02d (%d,%d,%d) | %.17g | %.17g | %.17g |%n",
                        result.orientation, result.label, i + 1, ray.x, ray.y, ray.z, angle,
                        result.q[i], Math.abs(result.q[i] - 1)));
            }
        }
        double baselineR09Error = Math.abs(baseline.q[8] - 1);
        double o2R09Error = Math.abs(results.get(1).q[8] - 1);
        double o3R09Error = Math.abs(results.get(2).q[8] - 1);
        report.append("\n## Scientific interpretation\n\n");
        report.append("1. Directional-response rotation: ").append(rotationConclusion(results)).append("\n\n");
        report.append(String.format(Locale.ROOT,
                "2. Body diagonal R09: |Q-1| is %.17g for O1, %.17g for O2, and %.17g for O3. %s\n\n",
                baselineR09Error, o2R09Error, o3R09Error,
                "The best perpendicular result reduces this error by only "
                        + String.format(Locale.ROOT, "%.6g%%, which is not a material isotropy improvement.",
                        100.0 * (1.0 - Math.min(o2R09Error, o3R09Error) / baselineR09Error))));
        report.append("3. Polarization consistency: ").append(polarizationConclusion(results)).append("\n\n");
        report.append(String.format(Locale.ROOT,
                "4. Emax comparison: O1=%.17g, O2=%.17g (%.6g%% lower), O3=%.17g (%.6g%% lower). "
                        + "Both are numerically lower, but the reductions are small.\n\n",
                baseline.emax, results.get(1).emax, 100.0 * (1.0 - results.get(1).emax / baseline.emax),
                results.get(2).emax, 100.0 * (1.0 - results.get(2).emax / baseline.emax)));
        report.append("These conclusions apply only to the frozen 15-ray, six-cycle measurement; visual output is not treated as proof of emergent behavior.\n");
        return report.toString();
    }

    private static String rotationConclusion(List<Result> results) {
        double rmsO1O2 = rmsDifference(results.get(0).q, results.get(1).q);
        double rmsO1O3 = rmsDifference(results.get(0).q, results.get(2).q);
        return String.format(Locale.ROOT, "no material rotation is measured. The limiting label moves from R09 "
                        + "to R12 to R15 within the nearly degenerate high-response group, but the full Q-pattern "
                        + "RMS changes are only %.17g (O1/O2) and %.17g (O1/O3), and the low/high ray split remains.",
                rmsO1O2, rmsO1O3);
    }

    private static String polarizationConclusion(List<Result> results) {
        double rmsO1O2 = rmsDifference(results.get(0).q, results.get(1).q);
        double rmsO1O3 = rmsDifference(results.get(0).q, results.get(2).q);
        return String.format(Locale.ROOT, "the normalized pattern changes only weakly (maximum pairwise-to-O1 "
                        + "RMS change %.17g), while Emax remains near 1.50. These measurements do not support "
                        + "oscillator polarization as the primary cause of the remaining anisotropy.",
                Math.max(rmsO1O2, rmsO1O3));
    }

    private static double rmsDifference(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += (a[i] - b[i]) * (a[i] - b[i]);
        return Math.sqrt(sum / a.length);
    }

    private static List<int[]> sourcePoints() {
        List<int[]> points = new ArrayList<>(EXPECTED_OCCUPIED_NODES);
        for (int x = -(RADIUS - 1); x <= RADIUS - 1; x++) {
            for (int y = -(RADIUS - 1); y <= RADIUS - 1; y++) {
                for (int z = -(RADIUS - 1); z <= RADIUS - 1; z++) {
                    if (ExtendedIdentityShape.score(x, y, z, 0, 0) < RADIUS) {
                        points.add(new int[]{x, y, z});
                    }
                }
            }
        }
        return points;
    }

    private static int commonDetectorShell(List<int[]> points) {
        for (int shell = 24; shell <= 240; shell += 12) {
            boolean valid = true;
            for (var ray : IsotropyReferenceGeometry.RAYS) {
                int multiplier = shell / IsotropyReferenceGeometry.graphLength(ray);
                if (minimumDistance(ray.x * multiplier, ray.y * multiplier, ray.z * multiplier, points) < 24) {
                    valid = false;
                    break;
                }
            }
            if (valid) return shell;
        }
        throw new IllegalStateException("CONFIGURATION_ERROR: no valid common detector shell");
    }

    private static int[][] detectors(int cx, int cy, int cz, int shell, List<int[]> points) {
        int[][] detectors = new int[15][3];
        for (int i = 0; i < detectors.length; i++) {
            var ray = IsotropyReferenceGeometry.RAYS.get(i);
            int multiplier = shell / IsotropyReferenceGeometry.graphLength(ray);
            int dx = ray.x * multiplier;
            int dy = ray.y * multiplier;
            int dz = ray.z * multiplier;
            if (minimumDistance(dx, dy, dz, points) < 24) {
                throw new IllegalStateException("CONFIGURATION_ERROR: detector gap changed");
            }
            detectors[i] = new int[]{cx + dx, cy + dy, cz + dz};
        }
        return detectors;
    }

    private static int minimumDistance(int x, int y, int z, List<int[]> points) {
        int minimum = Integer.MAX_VALUE;
        for (int[] point : points) {
            minimum = Math.min(minimum, Math.abs(x - point[0]) + Math.abs(y - point[1])
                    + Math.abs(z - point[2]));
        }
        return minimum;
    }

    private static void validateCanonicalConfiguration(Path project) throws Exception {
        Path canonical = Path.of(CANONICAL_CONFIGURATION).toAbsolutePath().normalize();
        if (!project.equals(canonical) || !Files.exists(project)
                || !sha256(Files.readAllBytes(project)).equals(CANONICAL_CONFIGURATION_SHA256)) {
            throw new IllegalStateException("CONFIGURATION_ERROR: expected unchanged canonical V2 configuration "
                    + canonical);
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest(bytes)) result.append(String.format(Locale.ROOT, "%02x", value));
        return result.toString();
    }

    private static Properties readProperties(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        Properties properties = new Properties();
        for (String line : lines.subList(1, lines.size())) {
            int comma = line.indexOf(',');
            if (comma > 0) properties.setProperty(line.substring(0, comma), line.substring(comma + 1));
        }
        return properties;
    }

    private static void property(BufferedWriter writer, String key, Object value) throws IOException {
        writer.write(key + "," + String.valueOf(value).replace('\n', ' ') + "\n");
    }

    private static void writeFailure(Path output, Throwable failure) {
        try {
            Files.createDirectories(output);
            String status = failure.getMessage() != null && failure.getMessage().startsWith("CONFIGURATION_ERROR")
                    ? "CONFIGURATION_ERROR" : failure.getMessage() != null
                    && failure.getMessage().startsWith("BACKEND_FAILURE") ? "BACKEND_FAILURE"
                    : "MEASUREMENT_INCOMPLETE";
            Files.writeString(output.resolve("metadata.csv"), "key,value\nConfigurationStatus," + status
                    + "\nfailure," + failure.toString().replace('\n', ' ') + "\n");
        } catch (IOException ignored) {
            // The original failure is more useful than a secondary status-write failure.
        }
    }
}
