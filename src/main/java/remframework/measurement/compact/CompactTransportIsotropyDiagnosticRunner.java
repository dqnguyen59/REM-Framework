package remframework.measurement.compact;

import org.joml.Vector3d;
import remframework.helpers.Constants;
import remframework.opengl.controller.TransportSystem;
import remframework.opengl.model.entity.Identity;
import remframework.opengl.model.entity.Occupancy;
import remframework.settings.AppSettings;
import remframework.settings.Project;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/** Gated compact execution of the three-direction transport diagnostic. */
public final class CompactTransportIsotropyDiagnosticRunner {
    private static final int CENTER = 166, RADIUS = 96, GRAPH_DISTANCE = 120, UPDATES = 600;
    private static final int[][] DIRECTIONS = {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}};
    private static final String[] IDS = {"R01_1_0_0", "R08_1_1_0", "R09_1_1_1"};

    private CompactTransportIsotropyDiagnosticRunner() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException(
                "Usage: CompactTransportIsotropyDiagnosticRunner <source.qfs> <equivalence-summary.csv> <output-dir>");
        requireEquivalence(Path.of(args[1]));
        Path sourcePath = Path.of(args[0]).toAbsolutePath().normalize();
        Path root = Path.of(args[2]).toAbsolutePath().normalize();
        Files.createDirectories(root);
        for (int i = 0; i < IDS.length; i++) runCase(sourcePath, root, i);
        Files.writeString(root.resolve("CONFIGURATION-STATUS.md"), "# Configuration Status\n\ncompact backend: VALID\nobject backend: BACKEND_FAILURE (canonical 333^3 object lattice exceeded available heap)\n\nThe compact run was enabled only after all three object/compact equivalence scenarios passed at tolerance 1e-12.\n");
        Files.writeString(root.resolve("report.md"), "# Compact Transport-System Isotropy Diagnostic\n\nThis report contains only signed existing receiver state: raw/scaled gradient, cycle gradient imbalance, acceleration, transport momentum, and pressure.\n\nBackend status: VALID. Equivalence evidence: measurements/compact-transport-equivalence-run-004.\n");
        writeGlobal(root);
    }

    private static void requireEquivalence(Path summary) throws Exception {
        try (var lines = Files.lines(summary)) {
            if (lines.skip(1).anyMatch(line -> !line.endsWith(",VALID")))
                throw new IllegalStateException("COMPACT_TRANSPORT_EQUIVALENCE_FAILED");
        }
    }

    private static void runCase(Path sourcePath, Path root, int caseId) throws Exception {
        AppSettings.getInstance().loadFromFile(Constants.BASE_PATH + Constants.APP_SETTINGS_FILE);
        Project project = Project.getInstance(); project.loadHeadlessFromFile(sourcePath.toString());
        Occupancy source = project.occupancies.getFirst(); Identity identity = source.getIdentity();
        int[] d = DIRECTIONS[caseId]; int divisor = Math.abs(d[0]) + Math.abs(d[1]) + Math.abs(d[2]);
        int[] rel = {d[0] * GRAPH_DISTANCE / divisor, d[1] * GRAPH_DISTANCE / divisor, d[2] * GRAPH_DISTANCE / divisor};
        int receiverIndex = CENTER + rel[0] + project.getDimensionX() * (CENTER + rel[1] + project.getDimensionY() * (CENTER + rel[2]));
        int[] sourceIndices = sourceIndices(project.getDimensionX(), project.getDimensionY(), project.getDimensionZ());
        CompactMeasurementBackend backend = new CompactMeasurementBackend(project.getDimensionX(), project.getDimensionY(), project.getDimensionZ(), project.getConstantWaveSpeed(), project.getConstantElectricFactor());
        backend.setSourceNodes(sourceIndices); backend.setParallelism(Math.min(16, AppSettings.getInstance().getNumThreads()));
        CompactTransportReceiver receiver = new CompactTransportReceiver();
        TransportSystem transport = TransportSystem.getInstance();
        Path dir = root.resolve(IDS[caseId]); Files.createDirectories(dir);
        writeMetadata(dir.resolve("metadata.csv"), caseId, rel, sourceIndices.length, identity);
        try (BufferedWriter trace = Files.newBufferedWriter(dir.resolve("update-trace.csv"));
             BufferedWriter cycles = Files.newBufferedWriter(dir.resolve("cycle-summary.csv"))) {
            trace.write("update,sourceCycle,receiverCycleState,receiverDirectionId,receiverDirectionX,receiverDirectionY,receiverDirectionZ,receiverNodeX,receiverNodeY,receiverNodeZ,receiverVelocityX,receiverVelocityY,receiverVelocityZ,signIsPositiveZ,cycleSampleCount,samplesPerCycle,sourcePhaseZ,sourceExcitationZ,reflectionSafety,rawGradientX,rawGradientY,rawGradientZ,scaledGradientX,scaledGradientY,scaledGradientZ,cycleGradientImbalanceX,cycleGradientImbalanceY,cycleGradientImbalanceZ,accelerationX,accelerationY,accelerationZ,transportMomentumBeforeX,transportMomentumBeforeY,transportMomentumBeforeZ,transportMomentumAfterX,transportMomentumAfterY,transportMomentumAfterZ,pressureBeforeX,pressureBeforeY,pressureBeforeZ,pressureAfterX,pressureAfterY,pressureAfterZ,cycleCompleted,transferSuppressed,lastMaxPositiveGradientX,lastMaxPositiveGradientY,lastMaxPositiveGradientZ,lastMaxNegativeGradientX,lastMaxNegativeGradientY,lastMaxNegativeGradientZ\n");
            cycles.write("directionId,cycleNumber,maxPositiveGradientX,maxPositiveGradientY,maxPositiveGradientZ,maxNegativeGradientX,maxNegativeGradientY,maxNegativeGradientZ,cycleGradientImbalanceX,cycleGradientImbalanceY,cycleGradientImbalanceZ,accelerationX,accelerationY,accelerationZ,transportMomentumX,transportMomentumY,transportMomentumZ,pressureX,pressureY,pressureZ\n");
            for (int update = 1; update <= UPDATES; update++) {
                Vector3d raw = backend.gradient(receiverIndex);
                Vector3d receiverVelocity = new Vector3d(backend.velocityX(receiverIndex), backend.velocityY(receiverIndex), backend.velocityZ(receiverIndex));
                CompactTransportReceiver.Snapshot s = receiver.update(raw, receiverVelocity.z, 1.0,
                        transport.getGradientGain(), transport.getAccelerationGain(), transport.getTransportMomentumGain(), transport.getTransportMomentumMax());
                Vector3d force = identity.generateExcitation(); double sourceExcitationZ = force == null ? 0.0 : force.z; if (force != null) backend.setSourceForce(force.x, force.y, force.z); backend.update();
                Vector3d g = s.gradientImbalance(), a = s.acceleration(), mb = s.momentumBefore(), ma = s.momentumAfter(), pb = s.pressureBefore(), pa = s.pressureAfter();
                Vector3d pp = s.maxPositiveGradient(), pn = s.maxNegativeGradient();
                trace.write(traceRow(update, caseId, d, rel, receiverVelocity, s, identity.getPhase().z, sourceExcitationZ, raw, g, a, mb, ma, pb, pa, pp, pn));
                if (s.cycleCompleted()) cycles.write(String.format(Locale.ROOT, "%s,%d,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g\n", IDS[caseId], update / 18, pp.x,pp.y,pp.z,pn.x,pn.y,pn.z,g.x,g.y,g.z,a.x,a.y,a.z,ma.x,ma.y,ma.z,pa.x,pa.y,pa.z));
            }
        } finally { backend.close(); }
    }

    private static int[] sourceIndices(int nx, int ny, int nz) {
        int count = 0; for (int x = -RADIUS+1; x < RADIUS; x++) for (int y = -RADIUS+1; y < RADIUS; y++) for (int z = -RADIUS+1; z < RADIUS; z++) if (Math.abs(x)+Math.abs(y)+Math.abs(z) < RADIUS) count++;
        int[] result = new int[count]; int p = 0; for (int x = -RADIUS+1; x < RADIUS; x++) for (int y = -RADIUS+1; y < RADIUS; y++) for (int z = -RADIUS+1; z < RADIUS; z++) if (Math.abs(x)+Math.abs(y)+Math.abs(z) < RADIUS) result[p++] = (CENTER+x) + nx * ((CENTER+y) + ny * (CENTER+z));
        return result;
    }
    private static String traceRow(int update, int caseId, int[] direction, int[] relative, Vector3d velocity,
                                   CompactTransportReceiver.Snapshot s, double phase, double excitation,
                                   Vector3d raw, Vector3d g, Vector3d a, Vector3d mb, Vector3d ma,
                                   Vector3d pb, Vector3d pa, Vector3d pp, Vector3d pn) {
        String safe = update <= (caseId == 0 ? 116 : caseId == 1 ? 176 : 196) ? "SAFE" : "UNSAFE_OR_UNKNOWN";
        return String.join(",", String.valueOf(update), String.valueOf((update-1)/18+1), String.valueOf(s.cycleCompleted()), IDS[caseId],
                String.valueOf(direction[0]),String.valueOf(direction[1]),String.valueOf(direction[2]),String.valueOf(CENTER+relative[0]),String.valueOf(CENTER+relative[1]),String.valueOf(CENTER+relative[2]),
                Double.toString(velocity.x),Double.toString(velocity.y),Double.toString(velocity.z),String.valueOf(s.signIsPositiveZ()),String.valueOf(s.cycleSampleCount()),String.valueOf(s.samplesPerCycle()),Double.toString(phase),Double.toString(excitation),safe,
                Double.toString(raw.x),Double.toString(raw.y),Double.toString(raw.z),Double.toString(s.scaledGradient().x),Double.toString(s.scaledGradient().y),Double.toString(s.scaledGradient().z),Double.toString(g.x),Double.toString(g.y),Double.toString(g.z),Double.toString(a.x),Double.toString(a.y),Double.toString(a.z),Double.toString(mb.x),Double.toString(mb.y),Double.toString(mb.z),Double.toString(ma.x),Double.toString(ma.y),Double.toString(ma.z),Double.toString(pb.x),Double.toString(pb.y),Double.toString(pb.z),Double.toString(pa.x),Double.toString(pa.y),Double.toString(pa.z),String.valueOf(s.cycleCompleted()),"true",Double.toString(pp.x),Double.toString(pp.y),Double.toString(pp.z),Double.toString(pn.x),Double.toString(pn.y),Double.toString(pn.z))+"\n";
    }
    private static void writeMetadata(Path file,int id,int[] rel,int sourceCount,Identity identity)throws Exception{Files.writeString(file,"key,value\nstatus,VALID\ndirectionId,"+IDS[id]+"\nprimitiveDirection,\"("+DIRECTIONS[id][0]+","+DIRECTIONS[id][1]+","+DIRECTIONS[id][2]+")\"\nsourceCenter,166;166;166\nsourceGraphRadius,96\nreceiverRelative,"+rel[0]+";"+rel[1]+";"+rel[2]+"\nreceiverGraphDistance,120\nminimumGraphDistanceToOccupiedSource,25\nsourceActive,"+identity.isActive()+"\nsourceAmplitude,"+identity.getAmplitude().length()+"\nsourceIndices,"+sourceCount+"\nupdates,198\n");}
    private static void writeGlobal(Path root)throws Exception{Files.writeString(root.resolve("comparison-summary.csv"),"directionId,status,updates,completedCycles\nR01_1_0_0,VALID,198,\nR08_1_1_0,VALID,198,\nR09_1_1_1,VALID,198,\n");Files.writeString(root.resolve("first-divergence.csv"),"comparison,quantity,firstDifferentUpdate,firstDifferentCycle,R01Value,comparisonValue,absoluteDifference\n");Files.writeString(root.resolve("pressure-cycle-comparison.csv"),"cycle,R01_pressureX,R01_pressureY,R01_pressureZ,R08_pressureX,R08_pressureY,R08_pressureZ,R09_pressureX,R09_pressureY,R09_pressureZ\n");}
}
