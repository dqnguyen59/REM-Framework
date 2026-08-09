package remframework.measurement.detector;

import remframework.helpers.Constants;
import remframework.opengl.controller.TransportSystem;
import remframework.opengl.model.Lattice;
import remframework.opengl.model.Scene;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.Occupancy;
import remframework.opengl.model.entity.Node;
import remframework.opengl.model.entity.OccupiedRegionDiagnostics;
import remframework.opengl.model.entity.Identity;
import remframework.settings.AppSettings;
import remframework.settings.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * One-radius headless Extended Identity run using the existing passive logger.<br><br>
 * Invoke once per radius so each run starts from the identical project snapshot:
 * {@code ExtendedIdentityIsotropyRunner project.qfs updates radius output.csv}.
 */
public final class ExtendedIdentityIsotropyRunner {
    private static final int EXTERNAL_GRAPH_DISTANCE_OFFSET = 24;
    private static final int ABSORPTION_LAYERS = 30;
    private ExtendedIdentityIsotropyRunner() { }

    public static void main(String[] arguments) {
        if (arguments.length != 4) {
            throw new IllegalArgumentException("Usage: ExtendedIdentityIsotropyRunner <project.qfs> <updates> <graphRadius> <output.csv>");
        }
        Path projectPath=Path.of(arguments[0]).toAbsolutePath().normalize();
        int updates=Integer.parseInt(arguments[1]);
        int radius=Integer.parseInt(arguments[2]);
        if (updates<=0) throw new IllegalArgumentException("updates must be positive");
        if (radius<0) throw new IllegalArgumentException("graphRadius must be non-negative");
        AppSettings.getInstance().loadFromFile(Constants.BASE_PATH+Constants.APP_SETTINGS_FILE);
        Project project=Project.getInstance();
        project.loadHeadlessFromFile(projectPath.toString());
        project.getRemModel().startSimulation();
        TransportSystem.getInstance().setEnableTransport(false);
        Scene scene=project.scene; Lattice lattice=scene.getLattice();
        DetectorExperimentController detectors=DetectorExperimentController.getInstance();
        detectors.disable();
        try {
            lattice.clearEntities(); lattice.buildHeadless();
            while (!project.getRemModel().isLoadingReady()) Thread.sleep(10);
            lattice.update();
            if (project.occupancies.isEmpty()) throw new IllegalStateException("No source occupancy configured");
            Occupancy source=project.occupancies.getFirst(); source.setGraphRadius(radius);
            for (Occupancy occupancy:project.occupancies) {
                Node node=lattice.getNodeByIndex(occupancy.getNodeIndex());
                if (node==null) throw new IllegalStateException("Source occupancy did not resolve to a lattice node");
                occupancy.setNode(node);
            }
            assertDetectorGeometry(project.getDetectors(), source.getNodeIndex(), radius);
            detectors.configure(Path.of(arguments[3]),project.getDetectors(),source.getNodeIndex());
            detectors.resolveNodes(lattice);
            int threads=AppSettings.getInstance().getNumThreads();
            int nodeSegment=Math.max(1,lattice.getNodeArray().length/threads+1);
            int entitySegment=Math.max(1,lattice.getEntityArray().length/threads+1);
            long started=System.nanoTime();
            for (int update=0;update<updates;update++) {
                Entity.swapBuffer();
                for (Occupancy occupancy:project.occupancies) occupancy.update();
                scene.processRemFieldEngine(threads,nodeSegment); scene.waitForAllTasksReady();
                scene.updateMatrix(threads,entitySegment); scene.waitForAllTasksReady();
                detectors.sample(lattice,activeSourcePhase(source.getIdentity()));
            }
            OccupiedRegionDiagnostics d=source.getOccupiedRegionDiagnostics();
            Path metadata=Path.of(arguments[3]+".metadata");
            Files.writeString(metadata,"graphRadius="+d.graphRadius()+"\noccupiedNodeCount="+d.occupiedNodeCount()+"\nboundaryNodeCount="+d.boundaryNodeCount()+"\nboundaryCrossingEdgeCount="+d.boundaryCrossingEdgeCount()+"\ncenterNodeIndex="+d.centerNodeIndex()+"\nperNodeAmplitude="+source.getIdentity().getAmplitude()+"\nupdates="+updates+"\nthroughputUpdatesPerSecond="+(updates*1_000_000_000d/(System.nanoTime()-started))+"\n");
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("Extended Identity run interrupted",exception); }
        catch (IOException exception) { throw new IllegalStateException("Extended Identity output failed",exception); }
        finally { detectors.close(); }
    }

    /** Validates the fixed external shell used by the preferred-radius sweep. */
    private static void assertDetectorGeometry(List<Detector> detectors,
                                               org.joml.Vector3i source,
                                               int graphRadius) {
        int requiredDistance = graphRadius + EXTERNAL_GRAPH_DISTANCE_OFFSET;
        if (detectors.size() != 6) {
            throw new IllegalStateException("Expected six strategic passive detectors, found "
                    + detectors.size());
        }
        for (Detector detector : detectors) {
            org.joml.Vector3i index = detector.getNodeIndex();
            int distance = Math.abs(index.x - source.x)
                    + Math.abs(index.y - source.y)
                    + Math.abs(index.z - source.z);
            if (distance != requiredDistance) {
                throw new IllegalStateException("Detector " + detector.getName()
                        + " has graph distance " + distance + ", expected "
                        + requiredDistance + " (R=" + graphRadius + ", S="
                        + EXTERNAL_GRAPH_DISTANCE_OFFSET + ")");
            }
            if (index.equals(source)) {
                throw new IllegalStateException("Detector " + detector.getName()
                        + " overlaps the source");
            }
        }
    }

    static double activeSourcePhase(Identity identity) {
        if (identity.getActive().x) return identity.getPhase().x;
        if (identity.getActive().y) return identity.getPhase().y;
        if (identity.getActive().z) return identity.getPhase().z;
        return Double.NaN;
    }
}
