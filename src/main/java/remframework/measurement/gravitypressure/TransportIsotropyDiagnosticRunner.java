package remframework.measurement.gravitypressure;

import org.joml.Vector3i;
import remframework.helpers.Constants;
import remframework.opengl.helpers.Vector3b;
import remframework.opengl.controller.TransportSystem;
import remframework.opengl.model.Lattice;
import remframework.opengl.model.Scene;
import remframework.opengl.model.entity.Identity;
import remframework.opengl.model.entity.Node;
import remframework.opengl.model.entity.Occupancy;
import remframework.settings.AppSettings;
import remframework.settings.Project;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Runs the three-direction diagnostic through the real TransportSystem receiver path. */
public final class TransportIsotropyDiagnosticRunner {
    private static final int CENTER = 166;
    private static final int SOURCE_RADIUS = 96;
    private static final int RECEIVER_DISTANCE = 120;
    private static final int UPDATES = 198;
    private static final int[][] CASES = {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}};
    private static final String[] IDS = {"R01", "R08", "R09"};

    private TransportIsotropyDiagnosticRunner() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException(
                "Usage: TransportIsotropyDiagnosticRunner <isotropy-reference-v2-Z.qfs> <output-dir>");
        Path projectPath = Path.of(args[0]).toAbsolutePath().normalize();
        Path root = Path.of(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(root);
        for (int i = 0; i < CASES.length; i++) {
            try {
                runCase(projectPath, root, i);
            } catch (Throwable failure) {
                writeFailure(root, i, failure);
                System.err.println(IDS[i] + " BACKEND_FAILURE: " + failure);
            }
        }
    }

    private static void runCase(Path projectPath, Path root, int caseIndex) throws Exception {
        int[] primitive = CASES[caseIndex];
        int divisor = Math.abs(primitive[0]) + Math.abs(primitive[1]) + Math.abs(primitive[2]);
        int[] relative = {primitive[0] * RECEIVER_DISTANCE / divisor,
                primitive[1] * RECEIVER_DISTANCE / divisor,
                primitive[2] * RECEIVER_DISTANCE / divisor};
        if (Math.abs(relative[0]) + Math.abs(relative[1]) + Math.abs(relative[2]) != RECEIVER_DISTANCE)
            throw new IllegalStateException("RECEIVER_GEOMETRY_NOT_COMPARABLE");

        AppSettings.getInstance().loadFromFile(Constants.BASE_PATH + Constants.APP_SETTINGS_FILE);
        Project project = Project.getInstance();
        project.loadHeadlessFromFile(projectPath.toString());
        project.getRemModel().startSimulation();
        if (project.occupancies.size() != 1) throw new IllegalStateException("Expected one source occupancy");
        Occupancy source = project.occupancies.getFirst();
        source.setGraphRadius(SOURCE_RADIUS);
        Occupancy receiver = receiver(IDS[caseIndex], CENTER + relative[0], CENTER + relative[1], CENTER + relative[2]);
        project.occupancies.add(receiver);

        Scene scene = project.scene;
        Lattice lattice = scene.getLattice();
        lattice.clearEntities();
        lattice.buildHeadless();
        while (!project.getRemModel().isLoadingReady()) Thread.sleep(10);
        lattice.update();
        for (Occupancy occupancy : project.occupancies) {
            Node node = lattice.getNodeByIndex(occupancy.getNodeIndex());
            if (node == null) throw new IllegalStateException("Receiver node did not resolve");
            occupancy.setNode(node);
        }
        validateGeometry(source, receiver, relative);
        Path caseDir = root.resolve(IDS[caseIndex] + "_" + relative[0] + "_" + relative[1] + "_" + relative[2]);
        Files.createDirectories(caseDir);
        GravityPressureExperimentController controller = GravityPressureExperimentController.getInstance();
        controller.configure(caseDir.resolve("transport-state.csv"), IDS[caseIndex], true,
                "designated receiver; final transfer suppressed", source, List.of(receiver));
        controller.setEnabled(true);
        TransportSystem transport = TransportSystem.getInstance();
        int threads = Math.max(1, AppSettings.getInstance().getNumThreads());
        int nodeSegment = Math.max(1, lattice.getNodeArray().length / threads + 1);
        int entitySegment = Math.max(1, lattice.getEntityArray().length / threads + 1);
        try {
            for (int update = 1; update <= UPDATES; update++) {
                controller.beginSimulationUpdate();
                for (Occupancy occupancy : project.occupancies) {
                    occupancy.setNode(lattice.getNodeByIndex(occupancy.getNodeIndex()));
                    transport.update(occupancy);
                    occupancy.update();
                }
                scene.processRemFieldEngine(threads, nodeSegment);
                scene.waitForAllTasksReady();
                scene.updateMatrix(threads, entitySegment);
                scene.waitForAllTasksReady();
            }
        } finally {
            controller.close();
        }
    }

    private static Occupancy receiver(String id, int x, int y, int z) {
        Occupancy occupancy = new Occupancy();
        occupancy.getNodeIndex().set(x, y, z);
        Identity identity = new Identity(id);
        identity.setActive(new Vector3b());
        identity.getAmplitude().zero();
        occupancy.setIdentity(identity);
        return occupancy;
    }

    private static void validateGeometry(Occupancy source, Occupancy receiver, int[] relative) {
        Vector3i s = source.getNodeIndex();
        Vector3i r = receiver.getNodeIndex();
        int graph = Math.abs(r.x - s.x) + Math.abs(r.y - s.y) + Math.abs(r.z - s.z);
        int minimum = graph - (SOURCE_RADIUS - 1);
        if (graph != RECEIVER_DISTANCE || minimum != 25 ||
                r.x - s.x != relative[0] || r.y - s.y != relative[1] || r.z - s.z != relative[2])
            throw new IllegalStateException("RECEIVER_GEOMETRY_NOT_COMPARABLE");
    }

    private static void writeFailure(Path root, int caseIndex, Throwable failure) {
        int[] p = CASES[caseIndex];
        int divisor = Math.abs(p[0]) + Math.abs(p[1]) + Math.abs(p[2]);
        int[] r = {p[0] * RECEIVER_DISTANCE / divisor, p[1] * RECEIVER_DISTANCE / divisor,
                p[2] * RECEIVER_DISTANCE / divisor};
        Path dir = root.resolve(IDS[caseIndex] + "_" + r[0] + "_" + r[1] + "_" + r[2]);
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("metadata.csv"),
                    "key,value\nstatus,BACKEND_FAILURE\nprimitiveDirection,\"(" + p[0] + "," + p[1] + "," + p[2] + ")\"\n" +
                    "receiverRelative," + r[0] + ";" + r[1] + ";" + r[2] + "\n" +
                    "sourceCenter," + CENTER + ";" + CENTER + ";" + CENTER + "\n" +
                    "sourceGraphRadius," + SOURCE_RADIUS + "\nreceiverGraphDistance," + RECEIVER_DISTANCE +
                    "\nminimumGraphDistanceToOccupiedSource,25\nfailureReason,\"" + failure + "\"\n");
            Files.writeString(dir.resolve("transport-state.csv"), "\n");
        } catch (Exception writeFailure) {
            throw new IllegalStateException("Could not write failure artifacts", writeFailure);
        }
    }
}
