package remframework.measurement.detector;

import org.joml.Vector3d;
import org.joml.Vector3i;
import remframework.opengl.model.Lattice;
import remframework.opengl.model.entity.Node;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only, node-level CSV instrumentation for passive detectors.
 */
public final class DetectorMeasurementLogger {
    private static final String HEADER =
            "update,detectorName,direction,requestedRadius,requestedAngleDegrees,"
            + "nodeX,nodeY,nodeZ,actualEuclideanRadius,actualAngleDegrees,"
            + "coordinateRoundingErrorX,coordinateRoundingErrorY,"
            + "coordinateRoundingErrorMagnitude,"
            + "positionX,positionY,positionZ,equilibriumPositionX,"
            + "equilibriumPositionY,equilibriumPositionZ,"
            + "positionDeviationX,positionDeviationY,positionDeviationZ,"
            + "velocityX,velocityY,velocityZ,accelerationX,accelerationY,"
            + "accelerationZ,rawGradientX,rawGradientY,rawGradientZ,"
            + "cycleDerivedGradientX,cycleDerivedGradientY,"
            + "cycleDerivedGradientZ,cycleCompleted,completedCycleCount,"
            + "sourcePhaseDegrees";

    private BufferedWriter writer;
    private Path outputFile;
    private List<Detector> detectors = List.of();
    private Vector3i sourceIndex;
    private final Map<String, CycleState> cycles = new LinkedHashMap<>();
    private long updatesRecorded;
    private long rowsWritten;
    private double previousSourcePhase = Double.NaN;

    public synchronized void configure(Path file, List<Detector> configured,
                                       Vector3i sourceNodeIndex)
            throws IOException {
        close();
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        outputFile = file;
        detectors = List.copyOf(configured);
        sourceIndex = sourceNodeIndex == null
                      ? null : new Vector3i(sourceNodeIndex);
        cycles.clear();
        detectors.forEach(detector ->
                cycles.put(detector.getName(), new CycleState()));
        updatesRecorded = 0;
        rowsWritten = 0;
        previousSourcePhase = Double.NaN;
        writer.write(HEADER);
        writer.newLine();
    }

    public synchronized void sample(Lattice lattice, double sourcePhase)
            throws IOException {
        if (writer == null) return;
        boolean cycleCompleted = isCycleComplete(sourcePhase);
        updatesRecorded++;
        for (Detector detector : detectors) {
            Node node = lattice.getNodeByIndex(detector.getNodeIndex());
            if (node == null) {
                throw new IllegalStateException(
                        "Detector " + detector.getName()
                                + " did not resolve to an existing lattice node");
            }
            write(detector, node, sourcePhase, cycleCompleted);
            rowsWritten++;
        }
        writer.flush();
        previousSourcePhase = sourcePhase;
    }

    public synchronized void resolveNodes(Lattice lattice) {
        for (Detector detector : detectors) {
            if (lattice.getNodeByIndex(detector.getNodeIndex()) == null) {
                throw new IllegalStateException(
                        "Detector " + detector.getName()
                                + " did not resolve to an existing lattice node");
            }
        }
    }

    private boolean isCycleComplete(double phase) {
        if (Double.isNaN(phase)) {
            return updatesRecorded > 0 && updatesRecorded % 18 == 0;
        }
        return !Double.isNaN(previousSourcePhase) && phase < previousSourcePhase;
    }

    private void write(Detector detector, Node node, double sourcePhase,
                       boolean cycleCompleted) throws IOException {
        Vector3i index = detector.getNodeIndex();
        int dx = sourceIndex == null ? 0 : index.x - sourceIndex.x;
        int dy = sourceIndex == null ? 0 : index.y - sourceIndex.y;
        int dz = sourceIndex == null ? 0 : index.z - sourceIndex.z;
        double radius = Math.sqrt((double) dx * dx + (double) dy * dy
                + (double) dz * dz);
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        Double requestedAngle = detector.getRequestedAngleDegrees();
        Integer requestedRadius = detector.getRequestedRadius();
        double idealX;
        double idealY;
        if (requestedRadius != null && detector.getDirection() != null) {
            switch (detector.getDirection()) {
                case "PX" -> {
                    idealX = requestedRadius;
                    idealY = 0.0;
                }
                case "NX" -> {
                    idealX = -requestedRadius;
                    idealY = 0.0;
                }
                case "PY" -> {
                    idealX = 0.0;
                    idealY = requestedRadius;
                }
                case "NY" -> {
                    idealX = 0.0;
                    idealY = -requestedRadius;
                }
                default -> {
                    idealX = dx;
                    idealY = dy;
                }
            }
        } else {
            idealX = requestedRadius == null || requestedAngle == null
                     ? dx : requestedRadius * Math.cos(
                             Math.toRadians(requestedAngle));
            idealY = requestedRadius == null || requestedAngle == null
                     ? dy : requestedRadius * Math.sin(
                             Math.toRadians(requestedAngle));
        }
        double errorX = dx - idealX;
        double errorY = dy - idealY;

        Vector3d position = new Vector3d(node.getPosition());
        Vector3d equilibrium = new Vector3d(node.getEquilibriumPosition());
        Vector3d deviation = new Vector3d(position).sub(equilibrium);
        Vector3d velocity = new Vector3d(node.getVelocity());
        Vector3d acceleration = new Vector3d(node.getAcceleration());
        Vector3d raw = node.readGradientImbalance();
        CycleState cycle = cycles.get(detector.getName());
        cycle.include(raw);
        if (cycleCompleted) cycle.complete();

        csv(updatesRecorded, detector.getName(),
                nullable(detector.getDirection()), nullable(requestedRadius),
                nullable(requestedAngle), index.x, index.y, index.z, radius,
                angle, errorX, errorY, Math.hypot(errorX, errorY),
                position.x, position.y, position.z,
                equilibrium.x, equilibrium.y, equilibrium.z,
                deviation.x, deviation.y, deviation.z,
                velocity.x, velocity.y, velocity.z,
                acceleration.x, acceleration.y, acceleration.z,
                raw.x, raw.y, raw.z,
                cycle.derived.x, cycle.derived.y, cycle.derived.z,
                cycleCompleted, cycle.completedCount,
                Double.isNaN(sourcePhase) ? "" : sourcePhase);
    }

    private static Object nullable(Object value) {
        return value == null ? "" : value;
    }

    private void csv(Object... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) writer.write(',');
            String value = String.valueOf(values[i]);
            if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0) {
                writer.write('"');
                writer.write(value.replace("\"", "\"\""));
                writer.write('"');
            } else {
                writer.write(value);
            }
        }
        writer.newLine();
    }

    public synchronized void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException exception) {
                throw new IllegalStateException("Could not close detector CSV",
                        exception);
            } finally {
                writer = null;
            }
        }
    }

    public synchronized long getUpdatesRecorded() {
        return updatesRecorded;
    }

    public synchronized long getRowsWritten() {
        return rowsWritten;
    }

    public synchronized Path getOutputFile() {
        return outputFile;
    }

    private static final class CycleState {
        private final Vector3d minimum = new Vector3d(
                Double.POSITIVE_INFINITY);
        private final Vector3d maximum = new Vector3d(
                Double.NEGATIVE_INFINITY);
        private final Vector3d derived = new Vector3d();
        private long completedCount;

        private void include(Vector3d value) {
            minimum.min(value);
            maximum.max(value);
        }

        private void complete() {
            derived.set(maximum).add(minimum).mul(0.5);
            completedCount++;
            minimum.set(Double.POSITIVE_INFINITY);
            maximum.set(Double.NEGATIVE_INFINITY);
        }
    }
}
