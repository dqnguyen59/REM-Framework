package remframework.measurement.detector;

import remframework.helpers.Constants;
import remframework.opengl.model.Lattice;
import remframework.opengl.model.Scene;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.Node;
import remframework.opengl.model.entity.Occupancy;
import remframework.opengl.model.entity.Identity;
import remframework.settings.AppSettings;

import java.nio.file.Path;

/**
 * Non-rendering entry point for the Gravity Test 2E feasibility pilot.
 *
 * <br><br>
 * It executes the same source update, Node v0.1 calculation, position commit,
 * and passive detector sampling order as {@code Simulator}, without creating
 * a GLFW window or invoking GPU rendering.
 */
public final class GravityTest2EHeadlessRunner {
    private GravityTest2EHeadlessRunner() {
    }

    public static void main(String[] arguments) {
        if (arguments.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: GravityTest2EHeadlessRunner <project.qfs> <updates>");
        }
        Path projectPath = Path.of(arguments[0]).toAbsolutePath().normalize();
        int updates = Integer.parseInt(arguments[1]);
        if (updates <= 0) {
            throw new IllegalArgumentException("Updates must be positive");
        }

        AppSettings.getInstance().loadFromFile(
                Constants.BASE_PATH + Constants.APP_SETTINGS_FILE);
        remframework.settings.Project project =
                remframework.settings.Project.getInstance();
        project.loadHeadlessFromFile(projectPath.toString());
        project.getRemModel().startSimulation();

        Scene scene = project.scene;
        Lattice lattice = scene.getLattice();
        DetectorExperimentController detectors =
                DetectorExperimentController.getInstance();
        FeasibilityPilotTelemetry telemetry =
                FeasibilityPilotTelemetry.getInstance();

        try {
            lattice.clearEntities();
            lattice.buildHeadless();
            while (!project.getRemModel().isLoadingReady()) {
                Thread.sleep(10);
            }

            lattice.update();
            for (Occupancy occupancy : project.occupancies) {
                Node node = lattice.getNodeByIndex(occupancy.getNodeIndex());
                if (node == null) {
                    throw new IllegalStateException(
                            "Source Occupancy did not resolve to a lattice node");
                }
                occupancy.setNode(node);
            }
            detectors.resolveNodes(lattice);
            telemetry.markLatticeInitialized();

            int numThreads = AppSettings.getInstance().getNumThreads();
            int nodeCount = lattice.getNodeArray().length;
            int entityCount = lattice.getEntityArray().length;
            int nodeSegment = nodeCount > numThreads
                              ? nodeCount / numThreads + 1 : nodeCount;
            int entitySegment = entityCount > numThreads
                                ? entityCount / numThreads + 1 : entityCount;

            for (int update = 1; update <= updates; update++) {
                telemetry.beginUpdate();
                Entity.swapBuffer();
                for (Occupancy occupancy : project.occupancies) {
                    occupancy.update();
                }
                scene.processRemFieldEngine(numThreads, nodeSegment);
                scene.waitForAllTasksReady();
                scene.updateMatrix(numThreads, entitySegment);
                scene.waitForAllTasksReady();
                double phase = project.occupancies.isEmpty()
                               ? Double.NaN
                               : activeSourcePhase(project.occupancies
                                       .getFirst().getIdentity());
                detectors.sample(lattice, phase);
                telemetry.endUpdate(update);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Gravity Test 2E headless run interrupted", exception);
        } finally {
            detectors.close();
            telemetry.close();
        }
    }

    static double activeSourcePhase(Identity identity) {
        if (identity.getActive().x) return identity.getPhase().x;
        if (identity.getActive().y) return identity.getPhase().y;
        if (identity.getActive().z) return identity.getPhase().z;
        return Double.NaN;
    }
}
