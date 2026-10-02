package remframework.measurement.detector;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Runtime and heap instrumentation for passive-detector feasibility pilots.
 *
 * <br><br>
 * This class observes wall-clock and JVM management state only. It does not
 * read or modify lattice physics state.
 */
public final class FeasibilityPilotTelemetry {
    private static final FeasibilityPilotTelemetry INSTANCE =
            new FeasibilityPilotTelemetry();
    private static final long MEBIBYTE = 1024L * 1024L;

    private final Runtime runtime = Runtime.getRuntime();
    private final List<GarbageCollectorMXBean> garbageCollectors =
            ManagementFactory.getGarbageCollectorMXBeans();

    private BufferedWriter writer;
    private Path outputFile;
    private String testIdentifier;
    private int latticeSize;
    private long configuredUpdates;
    private long configuredAtNanos;
    private long updateStartedNanos;
    private long simulationStartedNanos;
    private long peakUsedBytes;
    private long completedUpdates;
    private double totalUpdateMilliseconds;

    private FeasibilityPilotTelemetry() {
    }

    public static FeasibilityPilotTelemetry getInstance() {
        return INSTANCE;
    }

    public synchronized void configure(
            Path file, String identifier, int size, long updates)
            throws IOException {
        close();
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        outputFile = file;
        testIdentifier = identifier;
        latticeSize = size;
        configuredUpdates = updates;
        configuredAtNanos = System.nanoTime();
        simulationStartedNanos = 0L;
        completedUpdates = 0L;
        totalUpdateMilliseconds = 0.0;
        peakUsedBytes = usedHeapBytes();
        writer.write("event,update,testIdentifier,latticeSize,"
                + "configuredUpdates,elapsedMilliseconds,"
                + "updateMilliseconds,averageUpdateMilliseconds,"
                + "usedHeapBytes,totalHeapBytes,maxHeapBytes,peakUsedHeapBytes,"
                + "gcCount,gcTimeMilliseconds");
        writer.newLine();
        writeEvent("before-lattice-allocation", 0, 0.0);
    }

    public synchronized void markDetectorsInitialized() {
        writeEventUnchecked("detectors-initialized", 0, 0.0);
    }

    public synchronized void markLatticeInitialized() {
        writeEventUnchecked("lattice-initialized", 0, 0.0);
        simulationStartedNanos = System.nanoTime();
    }

    public synchronized void beginUpdate() {
        if (writer != null) updateStartedNanos = System.nanoTime();
    }

    public synchronized void endUpdate(long update) {
        if (writer == null || updateStartedNanos == 0L) return;
        double milliseconds =
                (System.nanoTime() - updateStartedNanos) / 1_000_000.0;
        completedUpdates++;
        totalUpdateMilliseconds += milliseconds;
        writeEventUnchecked("update", update, milliseconds);
        if (update % 25 == 0 || update == configuredUpdates) {
            System.out.printf(
                    "Gravity Test 2E progress: update %d/%d, avg %.3f ms, "
                            + "used %d MiB, peak %d MiB%n",
                    update, configuredUpdates,
                    totalUpdateMilliseconds / completedUpdates,
                    usedHeapBytes() / MEBIBYTE, peakUsedBytes / MEBIBYTE);
        }
    }

    private void writeEventUnchecked(
            String event, long update, double updateMilliseconds) {
        try {
            writeEvent(event, update, updateMilliseconds);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not write feasibility telemetry", exception);
        }
    }

    private void writeEvent(
            String event, long update, double updateMilliseconds)
            throws IOException {
        if (writer == null) return;
        long used = usedHeapBytes();
        peakUsedBytes = Math.max(peakUsedBytes, used);
        long now = System.nanoTime();
        long origin = simulationStartedNanos == 0L
                      ? configuredAtNanos : simulationStartedNanos;
        double elapsedMilliseconds = (now - origin) / 1_000_000.0;
        double average = completedUpdates == 0
                         ? 0.0 : totalUpdateMilliseconds / completedUpdates;
        writer.write(String.join(",",
                event,
                Long.toString(update),
                testIdentifier,
                Integer.toString(latticeSize),
                Long.toString(configuredUpdates),
                Double.toString(elapsedMilliseconds),
                Double.toString(updateMilliseconds),
                Double.toString(average),
                Long.toString(used),
                Long.toString(runtime.totalMemory()),
                Long.toString(runtime.maxMemory()),
                Long.toString(peakUsedBytes),
                Long.toString(gcCount()),
                Long.toString(gcTimeMilliseconds())));
        writer.newLine();
        writer.flush();
    }

    private long usedHeapBytes() {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private long gcCount() {
        return garbageCollectors.stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionCount)
                .filter(value -> value >= 0)
                .sum();
    }

    private long gcTimeMilliseconds() {
        return garbageCollectors.stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionTime)
                .filter(value -> value >= 0)
                .sum();
    }

    public synchronized boolean isEnabled() {
        return writer != null;
    }

    public synchronized Path getOutputFile() {
        return outputFile;
    }

    public synchronized void close() {
        if (writer == null) return;
        try {
            writeEvent("simulation-complete", completedUpdates, 0.0);
            writer.close();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not close feasibility telemetry", exception);
        } finally {
            writer = null;
        }
    }
}
