package remframework.measurement.compact;

import org.joml.Vector3d;
import remframework.helpers.Constants;
import remframework.measurement.gravitypressure.GravityPressureExperimentController;
import remframework.opengl.controller.TransportSystem;
import remframework.opengl.model.Lattice;
import remframework.opengl.model.Scene;
import remframework.opengl.model.entity.Node;
import remframework.opengl.model.entity.Occupancy;
import remframework.settings.AppSettings;
import remframework.settings.Project;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Strict object/compact equivalence gate for the measurement-only receiver mirror. */
public final class CompactTransportEquivalenceRunner {
    private static final int UPDATES = 198;
    private static final int[][] RECEIVERS = {{8, 5, 5}, {7, 7, 5}, {6, 6, 6}};
    private static final String[] IDS = {"A_X_AXIS", "B_DIAGONAL_2D", "C_DIAGONAL_3D"};
    private static final double TOLERANCE = 1e-12;

    private CompactTransportEquivalenceRunner() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException(
                "Usage: CompactTransportEquivalenceRunner <equivalence.qfs> <output-dir>");
        Path projectPath = Path.of(args[0]).toAbsolutePath().normalize();
        Path root = Path.of(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(root);
        List<String> summary = new ArrayList<>();
        boolean allValid = true;
        for (int i = 0; i < IDS.length; i++) {
            try {
                Result result = runScenario(projectPath, root, i);
                summary.add(result.csv());
                allValid &= result.valid;
            } catch (Throwable failure) {
                allValid = false;
                summary.add(String.join(",", IDS[i], "0", "NaN", "NaN", "NaN", "NaN", "NaN", "false", "BACKEND_FAILURE"));
                Files.writeString(root.resolve("failure-" + IDS[i] + ".txt"), failure.toString() + "\n");
            }
        }
        try (BufferedWriter out = Files.newBufferedWriter(root.resolve("equivalence-summary.csv"))) {
            out.write("scenarioId,updates,maxRawGradientDifference,maxScaledGradientDifference,maxGradientImbalanceDifference,maxAccelerationDifference,maxMomentumDifference,maxPressureDifference,cycleCompletionMatch,status\n");
            for (String row : summary) { out.write(row); out.newLine(); }
        }
        Files.writeString(root.resolve("CONFIGURATION-STATUS.md"), "# Configuration Status\n\nStatus is VALID only when every scenario passes update-by-update comparison at tolerance " + TOLERANCE + ".\n\nThe compact state mirrors the existing Occupancy cycle history, extrema, acceleration, momentum, and pressure sequence.\n");
        Files.writeString(root.resolve("report.md"), "# Compact TransportSystem Equivalence\n\nThe compact receiver is measurement-only and uses `CompactMeasurementBackend.gradient(index)` for the same six-neighbor distance imbalance as `Node.measureGradientImbalance()`.\n\nScenarios: X-axis, 2D diagonal, and 3D diagonal. See `equivalence-summary.csv` and `update-equivalence.csv`.\n\nOverall status: " + (allValid ? "VALID" : "COMPACT_TRANSPORT_EQUIVALENCE_FAILED") + "\n");
        if (!allValid) throw new IllegalStateException("COMPACT_TRANSPORT_EQUIVALENCE_FAILED");
    }

    private static Result runScenario(Path projectPath, Path root, int scenario) throws Exception {
        AppSettings.getInstance().loadFromFile(Constants.BASE_PATH + Constants.APP_SETTINGS_FILE);
        Project project = Project.getInstance();
        project.loadHeadlessFromFile(projectPath.toString());
        project.getRemModel().startSimulation();
        Occupancy source = project.occupancies.getFirst();
        Occupancy production = project.occupancies.get(1);
        production.getNodeIndex().set(RECEIVERS[scenario][0], RECEIVERS[scenario][1], RECEIVERS[scenario][2]);
        Scene scene = project.scene;
        Lattice lattice = scene.getLattice();
        lattice.clearEntities(); lattice.buildHeadless();
        while (!project.getRemModel().isLoadingReady()) Thread.sleep(5);
        lattice.update();
        source.setNode(lattice.getNodeByIndex(source.getNodeIndex()));
        production.setNode(lattice.getNodeByIndex(production.getNodeIndex()));
        CompactMeasurementBackend compactLattice = new CompactMeasurementBackend(
                project.getDimensionX(), project.getDimensionY(), project.getDimensionZ(),
                project.getConstantWaveSpeed(), project.getConstantElectricFactor());
        compactLattice.setSource(source.getNodeIndex().x, source.getNodeIndex().y, source.getNodeIndex().z);
        CompactTransportReceiver compact = new CompactTransportReceiver();
        TransportSystem transport = TransportSystem.getInstance();
        GravityPressureExperimentController pressureController = GravityPressureExperimentController.getInstance();
        pressureController.configure(Path.of("/tmp", "compact-transport-equivalence-" + IDS[scenario] + "-" + System.nanoTime() + ".csv"),
                IDS[scenario], true, "equivalence designated receiver", source, List.of(production));
        pressureController.setEnabled(true);
        Path trace = root.resolve("update-equivalence.csv");
        boolean writeHeader = !Files.exists(trace);
        double maxRaw = 0, maxScaled = 0, maxImbalance = 0, maxAcceleration = 0, maxMomentum = 0, maxPressure = 0;
        boolean cycleMatch = true, signMatch = true, sampleMatch = true, samplesPerCycleMatch = true, velocityMatch = true; int completed = 0;
        try (BufferedWriter out = Files.newBufferedWriter(trace, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
            if (writeHeader) out.write("scenarioId,update,stage,productionValue,compactValue,absoluteDifference,relativeDifference\n");
            for (int update = 1; update <= UPDATES; update++) {
                Node node = production.getNode();
                Vector3d productionRaw = node.measureGradientImbalance();
                Vector3d compactRaw = compactLattice.gradient(compactLattice.index(
                        production.getNodeIndex().x, production.getNodeIndex().y, production.getNodeIndex().z));
                maxRaw = Math.max(maxRaw, difference(productionRaw, compactRaw, IDS[scenario], update, "rawGradient", out));
                CompactTransportReceiver.Snapshot compactState = compact.update(compactRaw,
                        compactLattice.velocityZ(compactLattice.index(production.getNodeIndex().x, production.getNodeIndex().y, production.getNodeIndex().z)),
                        production.getIdentity().getAmplitude().length(), transport.getGradientGain(),
                        transport.getAccelerationGain(), transport.getTransportMomentumGain(), transport.getTransportMomentumMax());
                transport.update(source); transport.update(production);
                compareState(out, scenario, update, production, compactState);
                maxScaled = Math.max(maxScaled, difference(production.getLastMeasuredGradient(), compactState.scaledGradient(), IDS[scenario], update, "scaledGradient", out));
                maxImbalance = Math.max(maxImbalance, difference(production.getGradientImbalance(), compactState.gradientImbalance(), IDS[scenario], update, "gradientImbalance", out));
                maxAcceleration = Math.max(maxAcceleration, difference(production.getAcceleration(), compactState.acceleration(), IDS[scenario], update, "acceleration", out));
                maxMomentum = Math.max(maxMomentum, difference(production.getTransportMomentum(), compactState.momentumAfter(), IDS[scenario], update, "momentum", out));
                maxPressure = Math.max(maxPressure, difference(production.getPressure(), compactState.pressureAfter(), IDS[scenario], update, "pressure", out));
                cycleMatch &= production.wasCycleCompletedThisUpdate() == compactState.cycleCompleted();
                signMatch &= production.isSignIsPositiveZ() == compactState.signIsPositiveZ();
                sampleMatch &= production.getCycleSampleCountForAudit() == compactState.cycleSampleCount();
                samplesPerCycleMatch &= production.getSamplesPerCycleForAudit() == compactState.samplesPerCycle();
                if (production.wasCycleCompletedThisUpdate()) completed++;
                source.update(); production.update();
                Vector3d force = source.getIdentity().getExcitation();
                compactLattice.setSourceForce(force.x, force.y, force.z); compactLattice.update();
                scene.processRemFieldEngine(Math.max(1, AppSettings.getInstance().getNumThreads()),
                        Math.max(1, lattice.getNodeArray().length / Math.max(1, AppSettings.getInstance().getNumThreads()) + 1));
                scene.waitForAllTasksReady();
                scene.updateMatrix(Math.max(1, AppSettings.getInstance().getNumThreads()),
                        Math.max(1, lattice.getEntityArray().length / Math.max(1, AppSettings.getInstance().getNumThreads()) + 1));
                scene.waitForAllTasksReady();
                Vector3d productionVelocity = production.getNode().getVelocity();
                Vector3d compactVelocity = new Vector3d(compactLattice.velocityX(compactLattice.index(production.getNodeIndex().x, production.getNodeIndex().y, production.getNodeIndex().z)), compactLattice.velocityY(compactLattice.index(production.getNodeIndex().x, production.getNodeIndex().y, production.getNodeIndex().z)), compactLattice.velocityZ(compactLattice.index(production.getNodeIndex().x, production.getNodeIndex().y, production.getNodeIndex().z)));
                double velocityDifference = difference(productionVelocity, compactVelocity, IDS[scenario], update, "receiverVelocity", out);
                velocityMatch &= velocityDifference <= TOLERANCE;
            }
        } finally { compactLattice.close(); pressureController.close(); }
        boolean valid = cycleMatch && signMatch && sampleMatch && samplesPerCycleMatch && velocityMatch && completed > 0 && maxRaw <= TOLERANCE && maxScaled <= TOLERANCE &&
                maxImbalance <= TOLERANCE && maxAcceleration <= TOLERANCE && maxMomentum <= TOLERANCE && maxPressure <= TOLERANCE;
        return new Result(valid, String.format(Locale.ROOT, "%s,%d,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%s,%s",
                IDS[scenario], UPDATES, maxRaw, maxScaled, maxImbalance, maxAcceleration, maxMomentum, maxPressure,
                cycleMatch, valid ? "VALID" : "COMPACT_TRANSPORT_EQUIVALENCE_FAILED"));
    }

    private static void compareState(BufferedWriter out, int scenario, int update,
                                     Occupancy production, CompactTransportReceiver.Snapshot compact) throws Exception {
        difference(production.getLastMaxPositiveGradient(), compact.maxPositiveGradient(), IDS[scenario], update, "maxPositiveGradient", out);
        difference(production.getLastMaxNegativeGradient(), compact.maxNegativeGradient(), IDS[scenario], update, "maxNegativeGradient", out);
    }

    private static double difference(Vector3d a, Vector3d b, String scenario, int update,
                                     String stage, BufferedWriter out) throws Exception {
        double max = Math.max(Math.abs(a.x-b.x), Math.max(Math.abs(a.y-b.y), Math.abs(a.z-b.z)));
        double scale = Math.max(1.0, Math.max(Math.abs(a.x), Math.max(Math.abs(a.y), Math.abs(a.z))));
        out.write(String.format(Locale.ROOT, "%s,%d,%s,%.17g,%.17g,%.17g,%.17g\n",
                scenario, update, stage, a.x, b.x, max, max/scale));
        return max;
    }

    private record Result(boolean valid, String csv) { }
}
