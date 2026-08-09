package remframework.measurement.compact;

import com.sun.management.OperatingSystemMXBean;
import org.joml.Vector3d;
import remframework.helpers.Constants;
import remframework.measurement.detector.IsotropyReferenceGeometry;
import remframework.opengl.model.entity.Identity;
import remframework.opengl.model.entity.Occupancy;
import remframework.settings.AppSettings;
import remframework.settings.Project;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** One-process, resumable frozen Step 2 / Task 1 radius measurement. */
public final class ExtendedRadiusSweepRunner {
    private static final int UPDATES = 198;
    private static final int D = 24;
    private static final int WARMUP_CYCLES = 5;
    private static final int MEASUREMENT_CYCLES = 6;
    private static final int PERIOD = IsotropyReferenceGeometry.SOURCE_PERIOD_UPDATES;
    private static final double BASELINE_EMAX = 5.970414069087843;
    private static final double BASELINE_MAD = 1.3422296705644439;
    private static final double BASELINE_STDDEV = 1.8973005846437383;

    private record Result(int radius, int occupied, int boundary, int crossing, double[] q,
                          double mean, double amplitude, double emax, double mad, double stddev,
                          long constructionNanos, long measurementNanos, long peakHeap,
                          long peakProcess, String status) { }

    private ExtendedRadiusSweepRunner() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException(
                "Usage: ExtendedRadiusSweepRunner <project.qfs> <output-directory> <graph-radius>");
        IsotropyReferenceGeometry.validate();
        Path project = Path.of(args[0]).toAbsolutePath().normalize();
        Path root = Path.of(args[1]).toAbsolutePath().normalize();
        int radius = Integer.parseInt(args[2]);
        if (radius < 1 || radius > 16) throw new IllegalArgumentException("graph-radius must be 1..16");
        Files.createDirectories(root);
        Path finalDir = root.resolve(String.format(Locale.ROOT, "r%03d", radius));
        if (isComplete(finalDir)) {
            updateGlobal(root);
            System.out.println("R=" + radius + " already COMPLETE; reused");
            return;
        }
        if (Files.exists(finalDir)) archiveIncomplete(finalDir, root);
        Path staging = root.resolve(String.format(Locale.ROOT, "r%03d.MEASUREMENT_INCOMPLETE", radius));
        if (Files.exists(staging)) archiveIncomplete(staging, root);
        Files.createDirectories(staging);
        Result result;
        try {
            result = measure(project, radius, staging);
            writeRadius(staging, result);
            Files.writeString(staging.resolve("completion.properties"), completion(result));
            Files.move(staging, finalDir, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception exception) {
            Files.writeString(staging.resolve("completion.properties"),
                    "status=MEASUREMENT_INCOMPLETE\nerror=" + exception + "\n");
            throw exception;
        }
        updateGlobal(root);
        System.out.printf(Locale.ROOT, "R=%d Emax=%.17g MAD=%.17g StdDev=%.17g%n",
                radius, result.emax, result.mad, result.stddev);
    }

    private static Result measure(Path projectPath, int radius, Path out) throws Exception {
        long constructedAt = System.nanoTime();
        AppSettings.getInstance().loadFromFile(Constants.BASE_PATH + Constants.APP_SETTINGS_FILE);
        Project project = Project.getInstance();
        project.loadHeadlessFromFile(projectPath.toString());
        project.getRemModel().startSimulation();
        if (project.occupancies.size() != 1) throw new IllegalStateException("Expected one source occupancy");
        Occupancy source = project.occupancies.getFirst();
        Identity identity = source.getIdentity();
        int nx = project.getDimensionX(), ny = project.getDimensionY(), nz = project.getDimensionZ();
        int cx = source.getNodeIndex().x, cy = source.getNodeIndex().y, cz = source.getNodeIndex().z;
        CompactMeasurementBackend backend = new CompactMeasurementBackend(nx, ny, nz,
                project.getConstantWaveSpeed(), project.getConstantElectricFactor());
        backend.setParallelism(Math.min(16, AppSettings.getInstance().getNumThreads()));
        int[] sourceNodes = offsets(radius).stream().mapToInt(p -> backend.index(cx + p[0], cy + p[1], cz + p[2])).toArray();
        backend.setSourceNodes(sourceNodes);
        long constructionNanos = System.nanoTime() - constructedAt;
        writeConfiguration(out, radius, nx, ny, nz, source, sourceNodes.length);
        long measurementStarted = System.nanoTime();
        double[][] cycles = new double[MEASUREMENT_CYCLES][15];
        double[] min = new double[15], max = new double[15];
        Arrays.fill(min, Double.POSITIVE_INFINITY); Arrays.fill(max, Double.NEGATIVE_INFINITY);
        int captured = 0; double previousPhase = Double.NaN;
        int[][] detectors = detectorCoordinates(cx, cy, cz);
        try (BufferedWriter raw = Files.newBufferedWriter(out.resolve("cycle-responses.csv"))) {
            raw.write("cycle," + rayHeader() + "\n");
            for (int update = 1; update <= UPDATES; update++) {
                Vector3d force = identity.generateExcitation();
                if (force == null) throw new IllegalStateException("Source identity is inactive");
                backend.setSourceForce(0, 0, force.z);
                backend.update();
                double phase = identity.getPhase().z;
                boolean complete = !Double.isNaN(previousPhase) && phase < previousPhase;
                for (int i = 0; i < 15; i++) {
                    double value = backend.gradient(backend.index(detectors[i][0], detectors[i][1], detectors[i][2])).z;
                    min[i] = Math.min(min[i], value); max[i] = Math.max(max[i], value);
                }
                if (complete) {
                    int cycle = update / PERIOD;
                    if (cycle > WARMUP_CYCLES && captured < MEASUREMENT_CYCLES) {
                        for (int i = 0; i < 15; i++) cycles[captured][i] = (min[i] + max[i]) * .5;
                        captured++;
                        raw.write(cycle + "," + csv(cycles[captured - 1]) + "\n");
                    }
                    Arrays.fill(min, Double.POSITIVE_INFINITY); Arrays.fill(max, Double.NEGATIVE_INFINITY);
                }
                previousPhase = phase;
            }
        } finally { backend.close(); }
        long measurementNanos = System.nanoTime() - measurementStarted;
        if (captured != MEASUREMENT_CYCLES) throw new IllegalStateException("Expected cycles 6..11, captured " + captured);
        double[] responses = new double[15];
        for (int i = 0; i < 15; i++) for (int cycle = 0; cycle < MEASUREMENT_CYCLES; cycle++) responses[i] += cycles[cycle][i] / MEASUREMENT_CYCLES;
        double mean = Arrays.stream(responses).average().orElseThrow();
        double[] q = Arrays.stream(responses).map(value -> value / mean).toArray();
        double emax = Arrays.stream(q).map(value -> Math.abs(value - 1)).max().orElseThrow();
        double mad = Arrays.stream(q).map(value -> Math.abs(value - 1)).average().orElseThrow();
        double stddev = Math.sqrt(Arrays.stream(q).map(value -> (value - 1) * (value - 1)).average().orElseThrow());
        int[] topology = topology(radius);
        long peakHeap = peakHeap();
        long peakProcess = peakProcessMemory();
        return new Result(radius, topology[0], topology[1], topology[2], q, mean,
                Math.abs(identity.getAmplitude().z), emax, mad, stddev, constructionNanos,
                measurementNanos, peakHeap, peakProcess, "MEASURED");
    }

    private static void writeRadius(Path out, Result r) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(out.resolve("metrics.csv"))) {
            w.write("graphRadius,cycleMeanResponse,Emax,MAD,StdDev\n");
            w.write(String.format(Locale.ROOT, "%d,%.17g,%.17g,%.17g,%.17g%n", r.radius, r.mean, r.emax, r.mad, r.stddev));
        }
        try (BufferedWriter w = Files.newBufferedWriter(out.resolve("directional-results.csv"))) {
            w.write("directionId,primitiveDirection,graphShell,normalizedQ\n");
            for (int i = 0; i < 15; i++) { var ray = IsotropyReferenceGeometry.RAYS.get(i); w.write(String.format(Locale.ROOT,
                    "R%02d,\"(%d,%d,%d)\",%d,%.17g%n", i + 1, ray.x, ray.y, ray.z, D, r.q[i])); }
        }
    }

    private static String completion(Result r) {
        return String.format(Locale.ROOT, "status=COMPLETE\ngraphRadius=%d\nlattice=297^3\nD=%d\nprimitiveRayCount=15\noccupiedNodeCount=%d\nperNodeAmplitude=%.17g\nupdates=%d\ncyclesDiscarded=1..5\nmeasurementCycles=6\ncycleRange=6..11\noscillatorAxis=Z\nconstructionNanos=%d\nmeasurementNanos=%d\ntimePerUpdateNanos=%.17g\npeakJvmHeapBytes=%d\npeakProcessMemoryBytes=%s\ncompletionEpoch=%s\n",
                r.radius, D, r.occupied, r.amplitude, UPDATES, r.constructionNanos, r.measurementNanos, r.measurementNanos / (double) UPDATES,
                r.peakHeap, r.peakProcess == -1 ? "unavailable" : Long.toString(r.peakProcess), Instant.now());
    }

    private static void updateGlobal(Path root) throws IOException {
        List<Result> rows = new ArrayList<>();
        for (Path dir : Files.list(root).filter(p -> p.getFileName().toString().matches("r\\d{3}")).sorted().toList()) if (isComplete(dir)) rows.add(readResult(dir));
        try (BufferedWriter w = Files.newBufferedWriter(root.resolve("extended-radius-summary.csv"))) {
            w.write("graphRadius,cycleEmax,cycleMAD,cycleStdDev,bestSoFarEmax,bestSoFarRadius,newRecord,status\n");
            double best = BASELINE_EMAX; int bestRadius = 0;
            w.write(String.format(Locale.ROOT, "0,%.17g,%.17g,%.17g,%.17g,0,false,BASELINE_STEP1_Z_D24%n", BASELINE_EMAX, BASELINE_MAD, BASELINE_STDDEV, best));
            for (Result r : rows) { boolean record = r.emax < best; if (record) { best = r.emax; bestRadius = r.radius; } w.write(String.format(Locale.ROOT,
                    "%d,%.17g,%.17g,%.17g,%.17g,%d,%s,%s%n", r.radius, r.emax, r.mad, r.stddev, best, bestRadius, record, r.status)); }
        }
        try (BufferedWriter w = Files.newBufferedWriter(root.resolve("extended-directional-results.csv"))) {
            w.write("graphRadius,directionId,primitiveDirection,graphShell,normalizedQ\n");
            for (Result r : rows) for (int i = 0; i < 15; i++) { var ray = IsotropyReferenceGeometry.RAYS.get(i); w.write(String.format(Locale.ROOT,
                    "%d,R%02d,\"(%d,%d,%d)\",%d,%.17g%n", r.radius, i + 1, ray.x, ray.y, ray.z, D, r.q[i])); }
        }
        try (BufferedWriter w = Files.newBufferedWriter(root.resolve("extended-source-strength.csv"))) {
            w.write("graphRadius,occupiedNodeCount,perNodeAmplitude,aggregateAbsoluteExcitation,constructionNanos,measurementNanos,peakJvmHeapBytes,peakProcessMemoryBytes,timePerUpdateNanos,status\n");
            for (Result r : rows) w.write(String.format(Locale.ROOT, "%d,%d,%.17g,%.17g,%d,%d,%d,%s,%.17g,%s%n", r.radius, r.occupied, r.amplitude, r.occupied * r.amplitude, r.constructionNanos, r.measurementNanos, r.peakHeap, r.peakProcess == -1 ? "unavailable" : Long.toString(r.peakProcess), r.measurementNanos / (double) UPDATES, r.status));
        }
    }

    private static Result readResult(Path dir) throws IOException {
        Properties p = new Properties(); try (var in = Files.newInputStream(dir.resolve("completion.properties"))) { p.load(in); }
        String[] m = Files.readAllLines(dir.resolve("metrics.csv")).get(1).split(",");
        List<String> lines = Files.readAllLines(dir.resolve("directional-results.csv")); double[] q = new double[15];
        for (int i = 0; i < 15; i++) { String line = lines.get(i + 1); q[i] = Double.parseDouble(line.substring(line.lastIndexOf(',') + 1)); }
        return new Result(Integer.parseInt(m[0]), Integer.parseInt(p.getProperty("occupiedNodeCount", "0")), 0, 0, q, Double.parseDouble(m[1]), Double.parseDouble(p.getProperty("perNodeAmplitude", "200")), Double.parseDouble(m[2]), Double.parseDouble(m[3]), Double.parseDouble(m[4]), Long.parseLong(p.getProperty("constructionNanos", "0")), Long.parseLong(p.getProperty("measurementNanos", "0")), Long.parseLong(p.getProperty("peakJvmHeapBytes", "0")), parseLong(p.getProperty("peakProcessMemoryBytes", "-1")), p.getProperty("status"));
    }

    private static boolean isComplete(Path dir) { return Files.isDirectory(dir) && Files.exists(dir.resolve("completion.properties")) && Files.exists(dir.resolve("metrics.csv")) && Files.exists(dir.resolve("directional-results.csv")) && completionStatus(dir).equals("COMPLETE"); }
    private static String completionStatus(Path dir) { try { Properties p = new Properties(); try (var in = Files.newInputStream(dir.resolve("completion.properties"))) { p.load(in); } return p.getProperty("status", ""); } catch (IOException e) { return ""; } }
    private static void archiveIncomplete(Path dir, Path root) throws IOException { Path archive = root.resolve(dir.getFileName() + ".MEASUREMENT_INCOMPLETE-" + System.currentTimeMillis()); Files.move(dir, archive, StandardCopyOption.REPLACE_EXISTING); }
    private static long parseLong(String s) { try { return Long.parseLong(s); } catch (Exception e) { return -1; } }
    private static String csv(double[] values) { StringBuilder b = new StringBuilder(); for (int i = 0; i < values.length; i++) { if (i > 0) b.append(','); b.append(String.format(Locale.ROOT, "%.17g", values[i])); } return b.toString(); }
    private static String rayHeader() { StringBuilder b = new StringBuilder(); for (int i = 0; i < 15; i++) b.append(i == 0 ? "R01" : ",R" + String.format(Locale.ROOT, "%02d", i + 1)); return b.toString(); }
    private static int[][] detectorCoordinates(int cx, int cy, int cz) { int[][] d = new int[15][3]; for (int i = 0; i < 15; i++) { var ray = IsotropyReferenceGeometry.RAYS.get(i); int m = D / IsotropyReferenceGeometry.graphLength(ray); d[i] = new int[]{cx + ray.x * m, cy + ray.y * m, cz + ray.z * m}; } return d; }
    private static List<int[]> offsets(int radius) { List<int[]> out = new ArrayList<>(); int limit = radius - 1; for (int x = -limit; x <= limit; x++) for (int y = -limit; y <= limit; y++) for (int z = -limit; z <= limit; z++) if (Math.abs(x) + Math.abs(y) + Math.abs(z) <= limit) out.add(new int[]{x, y, z}); return out; }
    private static int[] topology(int radius) { List<int[]> a = offsets(radius); int boundary = 0, edges = 0; for (int[] p : a) { boolean b = false; for (int[] d : new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}}) { boolean inside = Math.abs(p[0] + d[0]) + Math.abs(p[1] + d[1]) + Math.abs(p[2] + d[2]) <= radius - 1; if (!inside) { b = true; edges++; } } if (b) boundary++; } return new int[]{a.size(), boundary, edges}; }
    private static void writeConfiguration(Path out, int radius, int nx, int ny, int nz, Occupancy source, int occupied) throws IOException { Files.writeString(out.resolve("detector-geometry.csv"), "directionId,primitiveDirection,graphShell\n" + geometryRows()); Files.writeString(out.resolve("configuration.properties"), String.format(Locale.ROOT, "lattice=%d^3\ndimensions=%d,%d,%d\ngraphRadius=%d\noccupiedNodeCount=%d\nsourceNode=%d,%d,%d\noscillatorAxis=Z\namplitudeZ=%.17g\nD=%d\nprimitiveRayCount=15\nupdates=198\n", 297, nx, ny, nz, radius, occupied, source.getNodeIndex().x, source.getNodeIndex().y, source.getNodeIndex().z, source.getIdentity().getAmplitude().z, D)); }
    private static String geometryRows() { StringBuilder b = new StringBuilder(); for (int i = 0; i < 15; i++) { var r = IsotropyReferenceGeometry.RAYS.get(i); b.append(String.format(Locale.ROOT, "R%02d,\"(%d,%d,%d)\",%d%n", i + 1, r.x, r.y, r.z, D)); } return b.toString(); }
    private static long peakHeap() { MemoryMXBean m = ManagementFactory.getMemoryMXBean(); return Math.max(m.getHeapMemoryUsage().getUsed(), Runtime.getRuntime().totalMemory()); }
    private static long peakProcessMemory() { try { return ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime() < 0 ? -1 : readRss(); } catch (Exception e) { return -1; } }
    private static long readRss() throws IOException { for (String line : Files.readAllLines(Path.of("/proc/self/status"))) if (line.startsWith("VmHWM:")) return Long.parseLong(line.replaceAll("[^0-9]", "")) * 1024; return -1; }
}
