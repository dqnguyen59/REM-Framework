package remframework.measurement.gravity;

import org.joml.Vector3d;
import org.joml.Vector3i;
import remframework.opengl.model.Lattice;
import remframework.opengl.model.entity.Identity;
import remframework.opengl.model.entity.Node;
import remframework.opengl.model.entity.Occupancy;
import remframework.settings.Project;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Records the raw gradient direction measured at designated measurement Identities.
 */
public final class GravityMeasurementLogger implements AutoCloseable {

    private static final String HEADER = String.join(",",
            "updateNumber", "measurementIndex", "measurementName",
            "sourceNodeIndexX", "sourceNodeIndexY", "sourceNodeIndexZ",
            "measurementNodeIndexX", "measurementNodeIndexY", "measurementNodeIndexZ",
            "sourcePositionX", "sourcePositionY", "sourcePositionZ",
            "measurementPositionX", "measurementPositionY", "measurementPositionZ",
            "dx", "dy", "dz", "sourceDistance",
            "rawGradientX", "rawGradientY", "rawGradientZ",
            "radialGradient", "tangentialGradientMagnitude", "angularErrorDegrees",
            "planarSourceDistance", "planarRadialGradient",
            "planarTangentialGradientMagnitude", "planarAngularErrorDegrees");

    private static final GravityMeasurementLogger INSTANCE = new GravityMeasurementLogger();

    private final Project project = Project.getInstance();
    private final Set<Identity> measurementIdentities =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private volatile boolean enabled;
    private volatile Identity sourceIdentity;
    private BufferedWriter writer;
    private Path outputFile;
    private long updateNumber;
    private long rowsWritten;
    private int measurementsWrittenThisUpdate;

    private GravityMeasurementLogger() {
    }

    public static GravityMeasurementLogger getInstance() {
        return INSTANCE;
    }

    public synchronized void configure(Path outputFile) {
        closeWriter();
        enabled = false;
        updateNumber = 0;
        rowsWritten = 0;
        this.outputFile = outputFile;

        try {
            Path parent = outputFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            writer = Files.newBufferedWriter(
                    outputFile,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            writer.write(HEADER);
            writer.newLine();
        }
        catch (Exception exception) {
            disableAfterFailure();
        }
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled && writer != null;
    }

    public void designateSourceIdentity(Identity sourceIdentity) {
        this.sourceIdentity = sourceIdentity;
    }

    public synchronized void clearMeasurementIdentities() {
        measurementIdentities.clear();
    }

    public synchronized void addMeasurementIdentity(Identity identity) {
        measurementIdentities.add(identity);
    }

    public synchronized void beginSimulationUpdate() {
        updateNumber++;
        measurementsWrittenThisUpdate = 0;
    }

    public void captureRawGradient(Occupancy occupancy, Vector3d rawGradient) {
        Identity source = sourceIdentity;
        if (!enabled || source == null || !isMeasurementIdentity(occupancy.getIdentity())) {
            return;
        }

        try {
            Occupancy sourceOccupancy = findOccupancy(source);
            if (sourceOccupancy == null || occupancy.getNode() == null) {
                return;
            }

            Node sourceNode = Lattice.getInstance().getNodeByIndex(sourceOccupancy.getNodeIndex());
            Node testNode = occupancy.getNode();
            if (sourceNode == null) {
                return;
            }

            Vector3i sourceIndex = sourceNode.getIndex();
            Vector3i testIndex = testNode.getIndex();
            Vector3d sourcePosition = sourceNode.getPosition();
            Vector3d testPosition = testNode.getPosition();

            int sourceNodeIndexX = sourceIndex.x;
            int sourceNodeIndexY = sourceIndex.y;
            int sourceNodeIndexZ = sourceIndex.z;
            int measurementIndex = project.occupancies.indexOf(occupancy);
            String measurementName = occupancy.getIdentity().getName();
            int measurementNodeIndexX = testIndex.x;
            int measurementNodeIndexY = testIndex.y;
            int measurementNodeIndexZ = testIndex.z;
            double sourcePositionX = sourcePosition.x;
            double sourcePositionY = sourcePosition.y;
            double sourcePositionZ = sourcePosition.z;
            double testPositionX = testPosition.x;
            double testPositionY = testPosition.y;
            double testPositionZ = testPosition.z;
            double rawGradientX = rawGradient.x;
            double rawGradientY = rawGradient.y;
            double rawGradientZ = rawGradient.z;

            double dx = sourcePositionX - testPositionX;
            double dy = sourcePositionY - testPositionY;
            double dz = sourcePositionZ - testPositionZ;
            double sourceDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            double radialGradient = Double.NaN;
            double tangentialGradientMagnitude = Double.NaN;
            double angularErrorDegrees = Double.NaN;
            double planarSourceDistance = Math.sqrt(dx * dx + dy * dy);
            double planarRadialGradient = Double.NaN;
            double planarTangentialGradientMagnitude = Double.NaN;
            double planarAngularErrorDegrees = Double.NaN;

            if (sourceDistance > 0.0) {
                double directionX = dx / sourceDistance;
                double directionY = dy / sourceDistance;
                double directionZ = dz / sourceDistance;
                radialGradient = rawGradientX * directionX
                        + rawGradientY * directionY
                        + rawGradientZ * directionZ;

                double tangentialX = rawGradientX - directionX * radialGradient;
                double tangentialY = rawGradientY - directionY * radialGradient;
                double tangentialZ = rawGradientZ - directionZ * radialGradient;
                tangentialGradientMagnitude = Math.sqrt(
                        tangentialX * tangentialX
                                + tangentialY * tangentialY
                                + tangentialZ * tangentialZ);

                double rawGradientLength = Math.sqrt(
                        rawGradientX * rawGradientX
                                + rawGradientY * rawGradientY
                                + rawGradientZ * rawGradientZ);
                if (rawGradientLength > 0.0) {
                    double cosAngle = Math.clamp(radialGradient / rawGradientLength, -1.0, 1.0);
                    angularErrorDegrees = Math.toDegrees(Math.acos(cosAngle));
                }
            }

            if (planarSourceDistance > 0.0) {
                double planarDirectionX = dx / planarSourceDistance;
                double planarDirectionY = dy / planarSourceDistance;
                planarRadialGradient = rawGradientX * planarDirectionX
                        + rawGradientY * planarDirectionY;

                double planarGradientMagnitudeSquared =
                        rawGradientX * rawGradientX + rawGradientY * rawGradientY;
                planarTangentialGradientMagnitude = Math.sqrt(Math.max(
                        0.0,
                        planarGradientMagnitudeSquared
                                - planarRadialGradient * planarRadialGradient));

                double planarGradientMagnitude = Math.sqrt(planarGradientMagnitudeSquared);
                if (planarGradientMagnitude > 0.0) {
                    double planarCosAngle = Math.clamp(
                            planarRadialGradient / planarGradientMagnitude, -1.0, 1.0);
                    planarAngularErrorDegrees = Math.toDegrees(Math.acos(planarCosAngle));
                }
            }

            GravityMeasurementSnapshot snapshot = new GravityMeasurementSnapshot(
                    updateNumber,
                    measurementIndex, measurementName,
                    sourceNodeIndexX, sourceNodeIndexY, sourceNodeIndexZ,
                    measurementNodeIndexX, measurementNodeIndexY, measurementNodeIndexZ,
                    sourcePositionX, sourcePositionY, sourcePositionZ,
                    testPositionX, testPositionY, testPositionZ,
                    dx, dy, dz, sourceDistance,
                    rawGradientX, rawGradientY, rawGradientZ,
                    radialGradient, tangentialGradientMagnitude, angularErrorDegrees,
                    planarSourceDistance, planarRadialGradient,
                    planarTangentialGradientMagnitude, planarAngularErrorDegrees);
            write(snapshot);
            flushCompletedUpdate();
        }
        catch (Exception exception) {
            disableAfterFailure();
        }
    }

    private synchronized boolean isMeasurementIdentity(Identity identity) {
        return measurementIdentities.contains(identity);
    }

    private Occupancy findOccupancy(Identity identity) {
        for (Occupancy occupancy : project.occupancies) {
            if (occupancy.getIdentity() == identity) {
                return occupancy;
            }
        }
        return null;
    }

    private synchronized void write(GravityMeasurementSnapshot value) throws IOException {
        if (!enabled || writer == null) {
            return;
        }

        StringBuilder row = new StringBuilder();
        append(row, value.updateNumber());
        append(row, value.measurementIndex());
        append(row, value.measurementName());
        append(row, value.sourceNodeIndexX());
        append(row, value.sourceNodeIndexY());
        append(row, value.sourceNodeIndexZ());
        append(row, value.measurementNodeIndexX());
        append(row, value.measurementNodeIndexY());
        append(row, value.measurementNodeIndexZ());
        append(row, value.sourcePositionX());
        append(row, value.sourcePositionY());
        append(row, value.sourcePositionZ());
        append(row, value.measurementPositionX());
        append(row, value.measurementPositionY());
        append(row, value.measurementPositionZ());
        append(row, value.dx());
        append(row, value.dy());
        append(row, value.dz());
        append(row, value.sourceDistance());
        append(row, value.rawGradientX());
        append(row, value.rawGradientY());
        append(row, value.rawGradientZ());
        append(row, value.radialGradient());
        append(row, value.tangentialGradientMagnitude());
        append(row, value.angularErrorDegrees());
        append(row, value.planarSourceDistance());
        append(row, value.planarRadialGradient());
        append(row, value.planarTangentialGradientMagnitude());
        append(row, value.planarAngularErrorDegrees());
        writer.write(row.toString());
        writer.newLine();
        rowsWritten++;
    }

    private synchronized void flushCompletedUpdate() throws IOException {
        measurementsWrittenThisUpdate++;
        if (measurementsWrittenThisUpdate == measurementIdentities.size()) {
            writer.flush();
        }
    }

    private static void append(StringBuilder row, long value) {
        appendSeparator(row);
        row.append(value);
    }

    private static void append(StringBuilder row, double value) {
        appendSeparator(row);
        row.append(value);
    }

    private static void append(StringBuilder row, String value) {
        appendSeparator(row);
        if (value == null) {
            return;
        }
        row.append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '"') {
                row.append('"');
            }
            row.append(character);
        }
        row.append('"');
    }

    private static void appendSeparator(StringBuilder row) {
        if (!row.isEmpty()) {
            row.append(',');
        }
    }

    private synchronized void disableAfterFailure() {
        enabled = false;
        closeWriter();
    }

    private void closeWriter() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        }
        catch (IOException ignored) {
            // Measurement failures must not affect the simulation.
        }
        finally {
            writer = null;
        }
    }

    @Override
    public synchronized void close() {
        enabled = false;
        closeWriter();
    }

    public synchronized long getUpdateNumber() {
        return updateNumber;
    }

    public synchronized long getRowsWritten() {
        return rowsWritten;
    }

    public synchronized int getMeasurementPointCount() {
        return measurementIdentities.size();
    }

    public synchronized Path getOutputFile() {
        return outputFile;
    }
}
