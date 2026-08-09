package remframework.measurement.compact;

import remframework.measurement.detector.IsotropyReferenceGeometry;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Preflight entry point for REM Framework Step 1 / Task 2.
 *
 * <p>The 60-cycle run is deliberately refused before allocation when the
 * reflection-safe compact lattice is not computationally practical. This
 * prevents an accidental fallback to the 297^3 Task 1 domain or a shortened
 * scientific configuration.</p>
 */
public final class Stability60Runner {
    private static final int CYCLES = 60;
    private static final int UPDATES_PER_CYCLE = IsotropyReferenceGeometry.SOURCE_PERIOD_UPDATES;
    private static final int MEASUREMENT_UPDATES = CYCLES * UPDATES_PER_CYCLE;
    private static final int MAX_OFFSET = IsotropyReferenceGeometry.maxOffset();
    private static final int HALF_SIZE = (MEASUREMENT_UPDATES + MAX_OFFSET) / 2 + 1;
    private static final int DIMENSION = 2 * HALF_SIZE + 1;
    private static final long NODE_COUNT = (long) DIMENSION * DIMENSION * DIMENSION;
    private static final long PERSISTENT_BYTES = NODE_COUNT * CompactMeasurementBackend.persistentBytesPerNode();

    private Stability60Runner() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Stability60Runner <output-directory>");
        }
        Path out = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(out);
        writePreflight(out);
        throw new IllegalStateException("60-cycle run refused: reflection-safe compact lattice is computationally impractical; see CONFIGURATION-STATUS.md");
    }

    private static void writePreflight(Path out) throws IOException {
        IsotropyReferenceGeometry.validate();
        try (BufferedWriter w = Files.newBufferedWriter(out.resolve("boundary-safety.csv"))) {
            w.write("measurementUpdates,maxOffset,requiredHalfSize,requiredDimension,nodeCount,persistentBytes,reflectionSafe\n");
            w.write(String.format(Locale.ROOT, "%d,%d,%d,%d,%d,%d,true%n",
                    MEASUREMENT_UPDATES, MAX_OFFSET, HALF_SIZE, DIMENSION, NODE_COUNT, PERSISTENT_BYTES));
        }
        try (BufferedWriter w = Files.newBufferedWriter(out.resolve("performance.csv"))) {
            w.write("oscillatorAxis,latticeDimension,nodeCount,persistentBytes,peakJvmHeapBytes,constructionNanos,timePerUpdateNanos,timePerCycleNanos,totalRuntimeNanos,status\n");
            for (String axis : new String[]{"X", "Y", "Z"}) {
                w.write(String.format(Locale.ROOT, "%s,%d,%d,%d,,,,,,NOT_RUN_COMPUTATIONALLY_IMPRACTICAL%n",
                        axis, DIMENSION, NODE_COUNT, PERSISTENT_BYTES));
            }
        }
        Files.writeString(out.resolve("CONFIGURATION-STATUS.md"), String.format(Locale.ROOT,
                "# REM Framework Step 1 / Task 2 — 60-cycle stability characterization\n\n" +
                "Status: `NOT_RUN_COMPUTATIONALLY_IMPRACTICAL`\n\n" +
                "The frozen reflection-safety derivation requires `%d` complete updates and outer detector offset `%d`. " +
                "Therefore the required symmetric half-size is `(%d + %d) / 2 + 1 = %d`, and the required lattice is `%d^3`.\n\n" +
                "That is `%d` nodes and `%d` persistent payload bytes (`9 * double`, 72 bytes/node), approximately %.3f GiB before JVM/object/runtime overhead. " +
                "The experiment was stopped before backend allocation. No 297^3 fallback, cycle shortening, or measured stability claim was made.\n",
                MEASUREMENT_UPDATES, MAX_OFFSET, MEASUREMENT_UPDATES, MAX_OFFSET, HALF_SIZE, DIMENSION,
                NODE_COUNT, PERSISTENT_BYTES, PERSISTENT_BYTES / (double) (1L << 30)));
        writeRequestedHeaders(out);
    }

    private static void writeRequestedHeaders(Path out) throws IOException {
        Files.writeString(out.resolve("cycle-response-all.csv"),
                "oscillatorAxis,directionId,primitiveDirection,graphShell,cycleNumber,cycleResponse\n");
        Files.writeString(out.resolve("rolling-cycle-stability.csv"),
                "oscillatorAxis,directionId,graphShell,cycleNumber,rollingMean,rollingStdDev,rollingRelativeVariation,rollingDrift\n");
        Files.writeString(out.resolve("cycle-window-summary.csv"),
                "oscillatorAxis,directionId,graphShell,windowStartCycle,windowEndCycle,mean,stdDev,relativeVariation,startToEndDrift\n");
        Files.writeString(out.resolve("isotropy-by-cycle.csv"),
                "oscillatorAxis,graphShell,cycleNumber,directionalMean,Emax,MAD,StdDev,meanCycleVariationAcrossDetectors,maximumCycleVariationAcrossDetectors\n");
        Files.writeString(out.resolve("first-meaningful-cycle.csv"),
                "oscillatorAxis,directionId,primitiveDirection,graphShell,firstNonZeroCycle\n");
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1200\" height=\"160\">"
                + "<text x=\"20\" y=\"30\">REM Framework Step 1 / Task 2 — 60-cycle run not executed</text>"
                + "<text x=\"20\" y=\"60\">Reflection-safe lattice: " + DIMENSION + "^3; see CONFIGURATION-STATUS.md</text>"
                + "</svg>\n";
        for (String name : new String[]{"cycle-response-vs-cycle.svg", "rolling-cycle-variation.svg",
                "isotropy-error-vs-cycle.svg", "first-meaningful-cycle-vs-shell.svg"}) {
            Files.writeString(out.resolve(name), svg);
        }
        Files.writeString(out.resolve("report.md"), "# REM Framework Step 1 / Task 2 — 60-cycle stability characterization\n\n"
                + "## Confirmed\n\n"
                + "The frozen reflection-safety derivation requires a " + DIMENSION + "³ lattice for 60 complete cycles.\n\n"
                + "## Response onset\n\nNot measured: the required run was not started.\n\n"
                + "## Cycle evolution\n\nNot measured.\n\n"
                + "## Stability\n\nNot classified; no arbitrary stability threshold was introduced.\n\n"
                + "## Isotropy evolution\n\nNot measured.\n\n"
                + "## X/Y/Z equivalence\n\nNot measured.\n\n"
                + "## Recommendation\n\nNo warm-up or measurement interval is recommended from this incomplete run.\n\n"
                + "See `CONFIGURATION-STATUS.md` for the preflight resource calculation.\n");
    }
}
