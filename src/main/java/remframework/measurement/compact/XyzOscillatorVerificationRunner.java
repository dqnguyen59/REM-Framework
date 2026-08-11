package remframework.measurement.compact;

import org.joml.Vector3d;
import remframework.helpers.Constants;
import remframework.measurement.detector.ExtendedIdentityShape;
import remframework.opengl.helpers.OscillatorType;
import remframework.opengl.model.entity.Identity;
import remframework.settings.AppSettings;
import remframework.settings.Project;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Verifies the existing Identity multi-axis oscillator on a small compact lattice. */
public final class XyzOscillatorVerificationRunner {
    static final int UPDATES = 36;
    static final double TOLERANCE = 1e-12;
    private static final int DIMENSION = 11;
    private static final int CENTER = DIMENSION / 2;
    private static final List<SourceNode> SOURCE_NODES = List.of(
            new SourceNode("center", 0, 0, 0),
            new SourceNode("axis-region", 1, 0, 0),
            new SourceNode("face-diagonal-region", 1, 1, 0),
            new SourceNode("body-diagonal-region", 1, 1, 1));

    private record SourceNode(String region, int x, int y, int z) { }
    private record Trace(int update, Vector3d phase, Vector3d excitation, double magnitude) { }

    private XyzOscillatorVerificationRunner() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: XyzOscillatorVerificationRunner "
                    + "<xyz-in-phase.qfs> <canonical-z.qfs> <output-directory>");
        }
        Path xyzConfiguration = Path.of(args[0]).toAbsolutePath().normalize();
        Path zConfiguration = Path.of(args[1]).toAbsolutePath().normalize();
        Path output = Path.of(args[2]).toAbsolutePath().normalize();
        Files.createDirectories(output);
        try {
            AppSettings.getInstance().loadFromFile(Constants.BASE_PATH + Constants.APP_SETTINGS_FILE);
            Identity xyz = loadSingleIdentity(xyzConfiguration);
            validateXyzConfiguration(xyz);
            List<Trace> traces = runXyzAudit(xyz, output);
            verifyNegativeWrap(xyz);
            Identity zOnly = loadSingleIdentity(zConfiguration);
            validateZOnlyConfiguration(zOnly);
            runZOnlyRegression(zOnly);
            writeReport(output, xyz, traces);
            Files.writeString(output.resolve("CONFIGURATION-STATUS.md"),
                    "# ConfigurationStatus\n\nVALID\n\n"
                            + "IN_PHASE_BODY_DIAGONAL_CONTROL\n\n"
                            + "This is an oscillator/source-application verification only. No R=96 lattice, "
                            + "detectors, isotropy metrics, indentation, or optimization were run.\n");
            System.out.println("ConfigurationStatus=VALID control=IN_PHASE_BODY_DIAGONAL_CONTROL updates=" + UPDATES);
        } catch (Throwable failure) {
            writeFailure(output, failure);
            throw failure;
        }
    }

    private static Identity loadSingleIdentity(Path configuration) throws Exception {
        if (!Files.isRegularFile(configuration)) {
            throw new IllegalStateException("CONFIGURATION_ERROR: missing configuration " + configuration);
        }
        Project project = Project.getInstance();
        project.loadHeadlessFromFile(configuration.toString());
        project.getRemModel().startSimulation();
        if (project.occupancies.size() != 1) {
            throw new IllegalStateException("CONFIGURATION_ERROR: expected exactly one Identity occupancy");
        }
        Identity identity = project.occupancies.getFirst().getIdentity();
        identity.reset();
        return identity;
    }

    static void validateXyzConfiguration(Identity identity) {
        if (!identity.getActive().x || !identity.getActive().y || !identity.getActive().z
                || identity.getOscillatorType().x != OscillatorType.sin
                || identity.getOscillatorType().y != OscillatorType.sin
                || identity.getOscillatorType().z != OscillatorType.sin
                || !identity.getAmplitude().equals(200, 200, 200)
                || !identity.getPhaseIncrement().equals(20, 20, 20)
                || !identity.getPhaseAngle().equals(0, 0, 0)) {
            throw new IllegalStateException("CONFIGURATION_ERROR: XYZ in-phase oscillator settings differ");
        }
    }

    private static void validateZOnlyConfiguration(Identity identity) {
        if (identity.getActive().x || identity.getActive().y || !identity.getActive().z
                || identity.getOscillatorType().z != OscillatorType.sin
                || identity.getAmplitude().z != 200 || identity.getPhaseIncrement().z != 20) {
            throw new IllegalStateException("CONFIGURATION_ERROR: canonical Z-only oscillator settings differ");
        }
    }

    private static List<Trace> runXyzAudit(Identity identity, Path output) throws IOException {
        List<Trace> traces = new ArrayList<>();
        try (CompactMeasurementBackend backend = backend();
             BufferedWriter cycle = Files.newBufferedWriter(output.resolve("oscillator-cycle.csv"));
             BufferedWriter source = Files.newBufferedWriter(output.resolve("source-force-audit.csv"))) {
            int[] selectedIndices = SOURCE_NODES.stream().mapToInt(node -> backend.index(
                    CENTER + node.x, CENTER + node.y, CENTER + node.z)).toArray();
            int[] sourceIndices = smallExtendedIdentityIndices(backend);
            backend.setSourceNodes(sourceIndices);
            cycle.write("update,activeX,activeY,activeZ,phaseX,phaseY,phaseZ,phaseAngleX,phaseAngleY,phaseAngleZ,"
                    + "phaseIncrementX,phaseIncrementY,phaseIncrementZ,oscillatorTypeX,oscillatorTypeY,"
                    + "oscillatorTypeZ,amplitudeX,amplitudeY,amplitudeZ,excitationX,excitationY,excitationZ,"
                    + "excitationMagnitude\n");
            source.write("update,sourceRegion,relativeX,relativeY,relativeZ,nodeIndex,excitationX,excitationY,"
                    + "excitationZ,appliedForceX,appliedForceY,appliedForceZ,absoluteMismatch\n");
            for (int update = 1; update <= UPDATES; update++) {
                Vector3d excitation = new Vector3d(identity.generateExcitation());
                Vector3d phase = new Vector3d(identity.getPhase());
                verifyTrace(update, identity, phase, excitation);
                backend.setSourceForce(excitation.x, excitation.y, excitation.z);
                backend.update();
                for (int i = 0; i < SOURCE_NODES.size(); i++) {
                    SourceNode node = SOURCE_NODES.get(i);
                    Vector3d applied = backend.lastAppliedSourceForce(selectedIndices[i]);
                    double mismatch = applied.distance(excitation);
                    if (mismatch > TOLERANCE) {
                        throw new IllegalStateException("SOURCE_FORCE_MISMATCH: update=" + update
                                + " region=" + node.region + " mismatch=" + mismatch);
                    }
                    source.write(String.format(Locale.ROOT,
                            "%d,%s,%d,%d,%d,%d,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g%n",
                            update, node.region, node.x, node.y, node.z, selectedIndices[i],
                            excitation.x, excitation.y, excitation.z, applied.x, applied.y, applied.z, mismatch));
                }
                double magnitude = excitation.length();
                traces.add(new Trace(update, phase, excitation, magnitude));
                cycle.write(cycleRow(update, identity, phase, excitation, magnitude));
            }
        }
        return traces;
    }

    private static void verifyTrace(int update, Identity identity, Vector3d phase, Vector3d excitation) {
        double expectedPhase = (update * 20.0) % 360.0;
        if (!phase.equals(expectedPhase, expectedPhase, expectedPhase)) {
            throw new IllegalStateException("OSCILLATOR_PHASE_ERROR: first divergence at update " + update
                    + " expected=" + expectedPhase + " actual=" + phase);
        }
        if (Math.abs(excitation.x - excitation.y) > TOLERANCE
                || Math.abs(excitation.x - excitation.z) > TOLERANCE) {
            throw new IllegalStateException("OSCILLATOR_PHASE_ERROR: axes diverged at update " + update);
        }
        double expectedExcitation = Math.sin(Math.toRadians(expectedPhase)) * 200.0;
        double expectedMagnitude = Math.sqrt(3) * Math.abs(expectedExcitation);
        if (Math.abs(excitation.x - expectedExcitation) > TOLERANCE
                || Math.abs(excitation.length() - expectedMagnitude) > TOLERANCE) {
            throw new IllegalStateException("OSCILLATOR_PHASE_ERROR: excitation mismatch at update " + update);
        }
        if (!identity.getActive().x || !identity.getActive().y || !identity.getActive().z) {
            throw new IllegalStateException("CONFIGURATION_ERROR: active axis changed at update " + update);
        }
    }

    private static String cycleRow(int update, Identity identity, Vector3d phase,
                                   Vector3d excitation, double magnitude) {
        return String.format(Locale.ROOT,
                "%d,%s,%s,%s,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,"
                        + "%s,%s,%s,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g%n",
                update, identity.getActive().x, identity.getActive().y, identity.getActive().z,
                phase.x, phase.y, phase.z, identity.getPhaseAngle().x, identity.getPhaseAngle().y,
                identity.getPhaseAngle().z, identity.getPhaseIncrement().x, identity.getPhaseIncrement().y,
                identity.getPhaseIncrement().z, identity.getOscillatorType().x, identity.getOscillatorType().y,
                identity.getOscillatorType().z, identity.getAmplitude().x, identity.getAmplitude().y,
                identity.getAmplitude().z, excitation.x, excitation.y, excitation.z, magnitude);
    }

    private static void verifyNegativeWrap(Identity source) {
        Identity negative = source.clone();
        negative.reset();
        negative.getPhaseIncrement().set(-20, -20, -20);
        for (int update = 1; update <= 18; update++) negative.generateExcitation();
        if (!negative.getPhase().equals(0, 0, 0)) {
            throw new IllegalStateException("OSCILLATOR_PHASE_ERROR: negative phase wrap failed at -360 degrees");
        }
    }

    private static void runZOnlyRegression(Identity identity) {
        try (CompactMeasurementBackend direct = backend(); CompactMeasurementBackend legacy = backend()) {
            int source = direct.index(CENTER, CENTER, CENTER);
            direct.setSourceNodes(new int[]{source});
            legacy.setSourceNodes(new int[]{source});
            for (int update = 1; update <= UPDATES; update++) {
                Vector3d excitation = new Vector3d(identity.generateExcitation());
                if (excitation.x != 0 || excitation.y != 0) {
                    throw new IllegalStateException("SOURCE_FORCE_MISMATCH: Z-only Identity generated X/Y force");
                }
                direct.setSourceForce(excitation.x, excitation.y, excitation.z);
                legacy.setSourceForce(0, 0, excitation.z);
                direct.update();
                legacy.update();
                Vector3d directApplied = direct.lastAppliedSourceForce(source);
                Vector3d legacyApplied = legacy.lastAppliedSourceForce(source);
                if (directApplied.distance(legacyApplied) > TOLERANCE
                        || directApplied.distance(new Vector3d(0, 0, excitation.z)) > TOLERANCE) {
                    throw new IllegalStateException("SOURCE_FORCE_MISMATCH: Z-only regression at update " + update);
                }
            }
        }
    }

    private static CompactMeasurementBackend backend() {
        return new CompactMeasurementBackend(DIMENSION, DIMENSION, DIMENSION, 1, 1);
    }

    private static int[] smallExtendedIdentityIndices(CompactMeasurementBackend backend) {
        List<Integer> indices = new ArrayList<>();
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    if (ExtendedIdentityShape.score(x, y, z, 0, 0) < 4) {
                        indices.add(backend.index(CENTER + x, CENTER + y, CENTER + z));
                    }
                }
            }
        }
        return indices.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void writeReport(Path output, Identity identity, List<Trace> traces) throws IOException {
        StringBuilder report = new StringBuilder("# REM Framework Phase 5 / Task 1 — Multi-Axis Identity Oscillator Verification\n\n"
                + "ConfigurationStatus: VALID\n\n"
                + "Control classification: IN_PHASE_BODY_DIAGONAL_CONTROL. This verifies an in-phase vector "
                + "along (1,1,1) or (-1,-1,-1); it is not interpreted as isotropic motion.\n\n"
                + "## Configuration\n\n"
                + "| Setting | X | Y | Z |\n|---|---:|---:|---:|\n");
        report.append("| active | ").append(identity.getActive().x).append(" | ").append(identity.getActive().y)
                .append(" | ").append(identity.getActive().z).append(" |\n")
                .append("| oscillatorType | ").append(identity.getOscillatorType().x).append(" | ")
                .append(identity.getOscillatorType().y).append(" | ").append(identity.getOscillatorType().z).append(" |\n")
                .append("| amplitude | 200 | 200 | 200 |\n| phaseIncrement | 20 | 20 | 20 |\n")
                .append("| phaseAngle | 0 | 0 | 0 |\n\n## Phase and excitation trace — first complete cycle\n\n")
                .append("| Update | Phase X | Phase Y | Phase Z | Excitation X | Excitation Y | Excitation Z | Magnitude |\n")
                .append("|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Trace trace : traces.subList(0, 18)) {
            report.append(String.format(Locale.ROOT,
                    "| %d | %.17g | %.17g | %.17g | %.17g | %.17g | %.17g | %.17g |%n",
                    trace.update, trace.phase.x, trace.phase.y, trace.phase.z, trace.excitation.x,
                    trace.excitation.y, trace.excitation.z, trace.magnitude));
        }
        report.append("\nAll 36 updates are recorded in `oscillator-cycle.csv`. Every update satisfied "
                + "X=Y=Z excitation and `magnitude=sqrt(x²+y²+z²)=sqrt(3)*abs(x)` within 1e-12. "
                + "Each axis advanced exactly once per update and wrapped from 340 to 0 at +360 degrees; "
                + "an auxiliary cloned-Identity audit also verified the -360-to-0 wrap. No first divergence occurred.\n\n"
                + "## Source application\n\nThe small 11³ compact lattice used an unindented R=4 Extended Identity. "
                + "The audit sampled four occupied nodes: center, axis-region, face-diagonal-region, and "
                + "body-diagonal-region. One Identity excitation was generated per update, then applied coherently "
                + "to the entire source. All 144 sampled applications matched exactly; "
                + "see `source-force-audit.csv`. Extended Identity node count never advanced oscillator phase.\n\n"
                + "## Z-only regression\n\nA separate small-lattice control loaded the unchanged canonical V2 Z-only "
                + "configuration. For 36 updates, direct-vector application `(excitation.x, excitation.y, excitation.z)` "
                + "matched the existing `(0,0,excitation.z)` path exactly.\n\n"
                + "## Scope\n\nNo R=96 run, detector measurement, Emax, MAD, StdDev, shape change, indentation, "
                + "phase optimization, or frequency optimization was performed.\n");
        Files.writeString(output.resolve("report.md"), report.toString());
    }

    private static void writeFailure(Path output, Throwable failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        String status = message.startsWith("CONFIGURATION_ERROR") ? "CONFIGURATION_ERROR"
                : message.startsWith("OSCILLATOR_PHASE_ERROR") ? "OSCILLATOR_PHASE_ERROR"
                : message.startsWith("SOURCE_FORCE_MISMATCH") ? "SOURCE_FORCE_MISMATCH" : "BACKEND_FAILURE";
        try {
            Files.createDirectories(output);
            Files.writeString(output.resolve("CONFIGURATION-STATUS.md"),
                    "# ConfigurationStatus\n\n" + status + "\n\n" + failure + "\n");
        } catch (IOException ignored) {
            // Preserve the primary verification failure.
        }
    }
}
