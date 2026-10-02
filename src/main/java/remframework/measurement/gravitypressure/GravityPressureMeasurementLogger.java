package remframework.measurement.gravitypressure;

import org.joml.Vector3d;
import org.joml.Vector3i;
import remframework.opengl.model.entity.Identity;
import remframework.opengl.model.entity.Node;
import remframework.opengl.model.entity.Occupancy;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dedicated receiver logger for Gravity Test 2.
 * <br><br>
 * Signed radial values use the unit vector from source to receiver:
 * positive is outward and negative is inward.
 */
public final class GravityPressureMeasurementLogger {
    private static final GravityPressureMeasurementLogger INSTANCE =
            new GravityPressureMeasurementLogger();
    private static final Pattern RECEIVER_NAME =
            Pattern.compile("R(\\d{3})_A(\\d{2})");
    private static final String[] HEADER = (
            "runId,experimentName,updateNumber,sourceEnabled,receiverConfiguration,receiverName," +
            "receiverRadiusRequested,receiverAngleRequested," +
            "sourceNodeX,sourceNodeY,sourceNodeZ,receiverInitialNodeX,receiverInitialNodeY,receiverInitialNodeZ," +
            "receiverNodeX,receiverNodeY,receiverNodeZ,nodeMoved," +
            "sourcePositionX,sourcePositionY,sourcePositionZ,receiverPositionX,receiverPositionY,receiverPositionZ," +
            "displacementX,displacementY,displacementZ,distance,planarDistance," +
            "receiverOffsetX,receiverOffsetY,receiverOffsetZ,receiverEuclideanRadius," +
            "receiverPlanarAngleDegrees,receiverManhattanDistance," +
            "actualEuclideanRadius,actualAngleDegrees," +
            "receiverActiveX,receiverActiveY,receiverActiveZ,receiverAmplitudeX,receiverAmplitudeY,receiverAmplitudeZ," +
            "receiverAmplitudeLength,receiverPhaseZ,receiverPhaseIncrementZ,receiverNodeVelocityZ," +
            "cycleCompleted,completedCycleCount,cycleGradientChanged," +
            "cycleMaxPositiveGradientX,cycleMaxPositiveGradientY,cycleMaxPositiveGradientZ," +
            "cycleMaxNegativeGradientX,cycleMaxNegativeGradientY,cycleMaxNegativeGradientZ," +
            "rawGradientX,rawGradientY,rawGradientZ,scaledGradientX,scaledGradientY,scaledGradientZ," +
            "cycleGradientBeforeX,cycleGradientBeforeY,cycleGradientBeforeZ," +
            "cycleGradientAfterX,cycleGradientAfterY,cycleGradientAfterZ," +
            "accelerationX,accelerationY,accelerationZ," +
            "momentumBeforeX,momentumBeforeY,momentumBeforeZ,momentumDeltaX,momentumDeltaY,momentumDeltaZ," +
            "momentumCandidateX,momentumCandidateY,momentumCandidateZ,momentumAfterX,momentumAfterY,momentumAfterZ," +
            "momentumClampedX,momentumClampedY,momentumClampedZ,momentumClampedAny," +
            "pressureBeforeX,pressureBeforeY,pressureBeforeZ,pressureIncrementX,pressureIncrementY,pressureIncrementZ," +
            "pressureAfterX,pressureAfterY,pressureAfterZ," +
            "thresholdCrossedX,thresholdCrossedY,thresholdCrossedZ,thresholdCrossedAny,dominantThresholdAxis," +
            "transferSuppressed,transferOccurred,gradientGain,accelerationGain,transportMomentumGain," +
            "transportMomentumMax,transferThreshold," +
            "rawGradientRadial,rawGradientPlanarTangentialMagnitude,scaledGradientRadial,scaledGradientPlanarTangentialMagnitude," +
            "cycleGradientBeforeRadial,cycleGradientBeforePlanarTangentialMagnitude," +
            "cycleGradientAfterRadial,cycleGradientAfterPlanarTangentialMagnitude," +
            "accelerationRadial,accelerationPlanarTangentialMagnitude," +
            "momentumBeforeRadial,momentumBeforePlanarTangentialMagnitude," +
            "momentumDeltaRadial,momentumDeltaPlanarTangentialMagnitude," +
            "momentumCandidateRadial,momentumCandidatePlanarTangentialMagnitude," +
            "momentumAfterRadial,momentumAfterPlanarTangentialMagnitude," +
            "pressureBeforeRadial,pressureBeforePlanarTangentialMagnitude," +
            "pressureIncrementRadial,pressureIncrementPlanarTangentialMagnitude," +
            "pressureAfterRadial,pressureAfterPlanarTangentialMagnitude").split(",");

    private BufferedWriter writer;
    private Path outputFile;
    private String experimentName;
    private String runId;
    private String receiverConfiguration;
    private boolean sourceEnabled;
    private boolean enabled;
    private Occupancy source;
    private final Set<Occupancy> receivers =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Occupancy, Vector3i> initialNodes = new IdentityHashMap<>();
    private final Map<Occupancy, Long> completedCycles = new IdentityHashMap<>();
    private long updateNumber;
    private long rowsWritten;
    private int rowsWrittenThisUpdate;
    private Context context;

    private GravityPressureMeasurementLogger() {
    }

    public static GravityPressureMeasurementLogger getInstance() {
        return INSTANCE;
    }

    public synchronized void configure(Path file, String name,
                                       boolean sourceOn, String configuration,
                                       Occupancy sourceOccupancy,
                                       List<Occupancy> receiverOccupancies)
            throws IOException {
        close();
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        outputFile = file;
        experimentName = name;
        runId = file.getFileName().toString().replace(".csv", "");
        sourceEnabled = sourceOn;
        receiverConfiguration = configuration;
        source = sourceOccupancy;
        receivers.clear();
        initialNodes.clear();
        completedCycles.clear();
        for (Occupancy occupancy : receiverOccupancies) {
            receivers.add(occupancy);
            initialNodes.put(occupancy, new Vector3i(occupancy.getNodeIndex()));
            completedCycles.put(occupancy, 0L);
        }
        updateNumber = 0;
        rowsWritten = 0;
        rowsWrittenThisUpdate = 0;
        writeCsv(HEADER);
    }

    public synchronized void setEnabled(boolean value) {
        enabled = value && writer != null;
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized boolean isDesignatedReceiver(Occupancy occupancy) {
        return enabled && receivers.contains(occupancy);
    }

    public synchronized void beginSimulationUpdate() {
        if (enabled) {
            updateNumber++;
            rowsWrittenThisUpdate = 0;
        }
    }

    public synchronized void beginReceiverUpdate(
            Occupancy occupancy, double gradientGain, double accelerationGain,
            double momentumGain, double momentumMax, double threshold) {
        if (!isDesignatedReceiver(occupancy)) return;
        context = new Context();
        context.gradientGain = gradientGain;
        context.accelerationGain = accelerationGain;
        context.momentumGain = momentumGain;
        context.momentumMax = momentumMax;
        context.threshold = threshold;
        context.cycleBefore.set(occupancy.getGradientImbalance());
        context.pressureBefore.set(occupancy.getPressure());
        context.momentumBefore.set(occupancy.getTransportMomentum());
        context.phaseZ = occupancy.getIdentity().getPhase().z;
        context.velocityZ = occupancy.getNode().getVelocity().z;
    }

    public synchronized void captureRawGradient(Occupancy occupancy, Vector3d v) {
        if (active(occupancy)) context.raw.set(v);
    }

    public synchronized void captureScaledGradient(Occupancy occupancy, Vector3d v) {
        if (active(occupancy)) context.scaled.set(v);
    }

    public synchronized void captureAcceleration(Occupancy occupancy, Vector3d v) {
        if (active(occupancy)) context.acceleration.set(v);
    }

    public synchronized void captureMomentumCandidate(Occupancy occupancy,
                                                       Vector3d before,
                                                       Vector3d candidate) {
        if (!active(occupancy)) return;
        context.momentumBefore.set(before);
        context.momentumCandidate.set(candidate);
        context.momentumDelta.set(candidate).sub(before);
    }

    public synchronized void captureMomentumAfter(Occupancy occupancy, Vector3d v) {
        if (active(occupancy)) context.momentumAfter.set(v);
    }

    public synchronized void capturePressure(Occupancy occupancy, Vector3d before,
                                             Vector3d increment, Vector3d after) {
        if (!active(occupancy)) return;
        context.pressureBefore.set(before);
        context.pressureIncrement.set(increment);
        context.pressureAfter.set(after);
    }

    public synchronized void completeSuppressedTransfer(Occupancy occupancy,
                                                        boolean cycleCompleted) {
        if (!active(occupancy)) return;
        try {
            context.cycleCompleted = cycleCompleted;
            if (cycleCompleted) {
                completedCycles.put(
                        occupancy, completedCycles.get(occupancy) + 1);
            }
            context.cycleAfter.set(occupancy.getGradientImbalance());
            context.cycleChanged = !context.cycleBefore.equals(context.cycleAfter);
            context.maxPositive.set(occupancy.getLastMaxPositiveGradient());
            context.maxNegative.set(occupancy.getLastMaxNegativeGradient());
            writeSnapshot(buildSnapshot(occupancy));
            rowsWritten++;
            rowsWrittenThisUpdate++;
            if (rowsWrittenThisUpdate == receivers.size()) {
                writer.flush();
            }
        } catch (IOException | RuntimeException e) {
            fail(e);
        } finally {
            context = null;
        }
    }

    private boolean active(Occupancy occupancy) {
        return enabled && receivers.contains(occupancy) && context != null;
    }

    private GravityPressureMeasurementSnapshot buildSnapshot(Occupancy occupancy) {
        Identity id = occupancy.getIdentity();
        Node sourceNode = source.getNode();
        Node receiverNode = occupancy.getNode();
        Vector3d sourcePosition = sourceNode.getPosition();
        Vector3d receiverPosition = receiverNode.getPosition();
        Vector3d displacement = new Vector3d(receiverPosition).sub(sourcePosition);
        Vector3i sourceIndex = source.getNodeIndex();
        Vector3i initialNode = initialNodes.get(occupancy);
        double distance = displacement.length();
        double planarDistance = Math.hypot(displacement.x, displacement.y);
        int offsetX = initialNode.x - sourceIndex.x;
        int offsetY = initialNode.y - sourceIndex.y;
        int offsetZ = initialNode.z - sourceIndex.z;
        double configuredRadius = Math.sqrt(
                offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ);
        double configuredAngle = Math.toDegrees(Math.atan2(offsetY, offsetX));
        int manhattanDistance =
                Math.abs(offsetX) + Math.abs(offsetY) + Math.abs(offsetZ);
        double ux = planarDistance > 0 ? displacement.x / planarDistance : Double.NaN;
        double uy = planarDistance > 0 ? displacement.y / planarDistance : Double.NaN;

        boolean crossedX = Math.abs(context.pressureAfter.x) >= context.threshold;
        boolean crossedY = Math.abs(context.pressureAfter.y) >= context.threshold;
        boolean crossedZ = Math.abs(context.pressureAfter.z) >= context.threshold;
        String dominant = "";
        if (context.cycleCompleted) {
            double ax = Math.abs(context.pressureAfter.x);
            double ay = Math.abs(context.pressureAfter.y);
            double az = Math.abs(context.pressureAfter.z);
            if (crossedX && ax >= ay && ax >= az) dominant = "X";
            else if (crossedY && ay >= az) dominant = "Y";
            else if (crossedZ) dominant = "Z";
        }
        Vector3i finalIndex = occupancy.getNodeIndex();
        List<String> values = new ArrayList<>();
        Matcher receiverMatcher = RECEIVER_NAME.matcher(id.getName());
        String requestedRadius = receiverMatcher.matches()
                                 ? receiverMatcher.group(1) : "";
        String requestedAngle = receiverMatcher.matches()
                                ? receiverMatcher.group(2) : "";
        add(values, runId, experimentName, updateNumber, sourceEnabled,
                receiverConfiguration, id.getName(),
                requestedRadius, requestedAngle,
                sourceIndex.x, sourceIndex.y, sourceIndex.z,
                initialNode.x, initialNode.y, initialNode.z,
                finalIndex.x, finalIndex.y, finalIndex.z,
                !initialNode.equals(finalIndex),
                sourcePosition.x, sourcePosition.y, sourcePosition.z,
                receiverPosition.x, receiverPosition.y, receiverPosition.z,
                displacement.x, displacement.y, displacement.z,
                distance, planarDistance,
                offsetX, offsetY, offsetZ, configuredRadius, configuredAngle,
                manhattanDistance, configuredRadius, configuredAngle,
                id.getActive().x, id.getActive().y, id.getActive().z,
                id.getAmplitude().x, id.getAmplitude().y, id.getAmplitude().z,
                id.getAmplitude().length(), context.phaseZ,
                id.getPhaseIncrement().z, context.velocityZ,
                context.cycleCompleted, completedCycles.get(occupancy),
                context.cycleChanged);
        addVector(values, context.maxPositive);
        addVector(values, context.maxNegative);
        addVector(values, context.raw);
        addVector(values, context.scaled);
        addVector(values, context.cycleBefore);
        addVector(values, context.cycleAfter);
        addVector(values, context.acceleration);
        addVector(values, context.momentumBefore);
        addVector(values, context.momentumDelta);
        addVector(values, context.momentumCandidate);
        addVector(values, context.momentumAfter);
        boolean clampX = context.momentumCandidate.x != context.momentumAfter.x;
        boolean clampY = context.momentumCandidate.y != context.momentumAfter.y;
        boolean clampZ = context.momentumCandidate.z != context.momentumAfter.z;
        add(values, clampX, clampY, clampZ, clampX || clampY || clampZ);
        addVector(values, context.pressureBefore);
        addVector(values, context.pressureIncrement);
        addVector(values, context.pressureAfter);
        add(values, crossedX, crossedY, crossedZ,
                crossedX || crossedY || crossedZ, dominant,
                true, false, context.gradientGain, context.accelerationGain,
                context.momentumGain, context.momentumMax, context.threshold);
        addPlanar(values, ux, uy, context.raw, context.scaled,
                context.cycleBefore, context.cycleAfter, context.acceleration,
                context.momentumBefore, context.momentumDelta,
                context.momentumCandidate, context.momentumAfter,
                context.pressureBefore, context.pressureIncrement,
                context.pressureAfter);
        return new GravityPressureMeasurementSnapshot(values.toArray(String[]::new));
    }

    private static void addPlanar(List<String> out, double ux, double uy,
                                  Vector3d... vectors) {
        for (Vector3d vector : vectors) {
            double radial = vector.x * ux + vector.y * uy;
            double tangential = Math.sqrt(Math.max(0,
                    vector.x * vector.x + vector.y * vector.y - radial * radial));
            add(out, radial, tangential);
        }
    }

    private static void addVector(List<String> out, Vector3d vector) {
        add(out, vector.x, vector.y, vector.z);
    }

    private static void add(List<String> out, Object... values) {
        for (Object value : values) out.add(String.valueOf(value));
    }

    private void writeSnapshot(GravityPressureMeasurementSnapshot snapshot)
            throws IOException {
        writeCsv(snapshot.values());
    }

    private void writeCsv(String[] values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) writer.write(',');
            writer.write(escape(values[i]));
        }
        writer.newLine();
    }

    private static String escape(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 &&
            value.indexOf('\n') < 0 && value.indexOf('\r') < 0) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private void fail(Exception e) {
        System.err.println("Gravity Test 2 logging disabled: " + e.getMessage());
        enabled = false;
        close();
    }

    public synchronized void close() {
        enabled = false;
        if (writer != null) {
            try {
                writer.flush();
                writer.close();
            } catch (IOException e) {
                System.err.println("Gravity Test 2 logger close failed: " + e.getMessage());
            }
            writer = null;
        }
    }

    public synchronized long getUpdatesRecorded() {
        return updateNumber;
    }

    public synchronized long getRowsWritten() {
        return rowsWritten;
    }

    public synchronized Path getOutputFile() {
        return outputFile;
    }

    private static final class Context {
        final Vector3d raw = new Vector3d();
        final Vector3d scaled = new Vector3d();
        final Vector3d cycleBefore = new Vector3d();
        final Vector3d cycleAfter = new Vector3d();
        final Vector3d maxPositive = new Vector3d();
        final Vector3d maxNegative = new Vector3d();
        final Vector3d acceleration = new Vector3d();
        final Vector3d momentumBefore = new Vector3d();
        final Vector3d momentumDelta = new Vector3d();
        final Vector3d momentumCandidate = new Vector3d();
        final Vector3d momentumAfter = new Vector3d();
        final Vector3d pressureBefore = new Vector3d();
        final Vector3d pressureIncrement = new Vector3d();
        final Vector3d pressureAfter = new Vector3d();
        double gradientGain;
        double accelerationGain;
        double momentumGain;
        double momentumMax;
        double threshold;
        double phaseZ;
        double velocityZ;
        boolean cycleCompleted;
        boolean cycleChanged;
    }
}
