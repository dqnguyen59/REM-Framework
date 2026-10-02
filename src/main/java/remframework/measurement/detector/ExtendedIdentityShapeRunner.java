package remframework.measurement.detector;

import remframework.helpers.Constants;
import remframework.opengl.controller.TransportSystem;
import remframework.opengl.model.Lattice;
import remframework.opengl.model.Scene;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.Identity;
import remframework.opengl.model.entity.Node;
import remframework.opengl.model.entity.Occupancy;
import remframework.opengl.model.entity.OccupiedRegionDiagnostics;
import remframework.settings.AppSettings;
import remframework.settings.Project;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** One fixed-lattice, one-shape canonical REM measurement. */
public final class ExtendedIdentityShapeRunner {
    private static final int EXTERNAL_GRAPH_DISTANCE_OFFSET = 24;

    private ExtendedIdentityShapeRunner() { }

    public static void main(String[] arguments) {
        if (arguments.length != 7) {
            throw new IllegalArgumentException("Usage: ExtendedIdentityShapeRunner <project.qfs> <updates> <baseRadius> <indentation> <detector-results.csv> <metadata.csv> <shape-nodes.csv>");
        }
        Path projectPath = Path.of(arguments[0]).toAbsolutePath().normalize();
        int updates = Integer.parseInt(arguments[1]);
        int baseRadius = Integer.parseInt(arguments[2]);
        double indentation = Double.parseDouble(arguments[3]);
        Path detectorOutput = Path.of(arguments[4]);
        Path metadataOutput = Path.of(arguments[5]);
        Path shapeNodesOutput = Path.of(arguments[6]);
        if (updates <= 0 || baseRadius < 0 || !Double.isFinite(indentation) || indentation < 0.0) {
            throw new IllegalArgumentException("Invalid shape measurement arguments");
        }

        AppSettings.getInstance().loadFromFile(Constants.BASE_PATH + Constants.APP_SETTINGS_FILE);
        Project project = Project.getInstance();
        DetectorExperimentController detectors = DetectorExperimentController.getInstance();
        int lastCompletedUpdate = 0;
        Throwable failure = null;
        Occupancy source = null;
        try {
            project.loadHeadlessFromFile(projectPath.toString());
            project.getRemModel().startSimulation();
            TransportSystem.getInstance().setEnableTransport(false);
            Scene scene = project.scene;
            Lattice lattice = scene.getLattice();
            detectors.disable();
            lattice.clearEntities();
            lattice.buildHeadless();
            while (!project.getRemModel().isLoadingReady()) Thread.sleep(10);
            lattice.update();
            if (project.occupancies.isEmpty()) throw new IllegalStateException("No source occupancy configured");
            source = project.occupancies.getFirst();
            source.setGraphRadius(baseRadius);
            source.setBodyDiagonalIndentation(indentation);
            for (Occupancy occupancy : project.occupancies) {
                Node node = lattice.getNodeByIndex(occupancy.getNodeIndex());
                if (node == null) throw new IllegalStateException("Source occupancy did not resolve to a lattice node");
                occupancy.setNode(node);
            }
            assertDetectorGeometry(project.getDetectors(), source.getNodeIndex(), baseRadius);
            Files.createDirectories(detectorOutput.toAbsolutePath().getParent());
            Files.createDirectories(metadataOutput.toAbsolutePath().getParent());
            detectors.configure(detectorOutput, project.getDetectors(), source.getNodeIndex());
            detectors.resolveNodes(lattice);
            int threads = AppSettings.getInstance().getNumThreads();
            int nodeSegment = Math.max(1, lattice.getNodeArray().length / threads + 1);
            int entitySegment = Math.max(1, lattice.getEntityArray().length / threads + 1);
            for (int update = 0; update < updates; update++) {
                Entity.swapBuffer();
                for (Occupancy occupancy : project.occupancies) occupancy.update();
                scene.processRemFieldEngine(threads, nodeSegment);
                scene.waitForAllTasksReady();
                scene.updateMatrix(threads, entitySegment);
                scene.waitForAllTasksReady();
                detectors.sample(lattice, ExtendedIdentityIsotropyRunner.activeSourcePhase(source.getIdentity()));
                lastCompletedUpdate = update + 1;
            }
        } catch (Throwable throwable) {
            failure = throwable;
        } finally {
            detectors.close();
        }

        if (failure != null) {
            writeMetadata(metadataOutput, "MEASUREMENT_INCOMPLETE", baseRadius, indentation,
                    lastCompletedUpdate, source, project, failure.getClass().getSimpleName() + ": " + failure.getMessage());
            if (failure instanceof RuntimeException runtimeException) throw runtimeException;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException("Shape measurement failed", failure);
        }

        OccupiedRegionDiagnostics diagnostics = source.getOccupiedRegionDiagnostics();
        writeShapeNodes(shapeNodesOutput, source.getOccupiedNodes(), source.getNodeIndex());
        writeMetadata(metadataOutput, "VALID", baseRadius, indentation, lastCompletedUpdate,
                source, project, "");
        if (diagnostics.occupiedNodeCount() <= 0) throw new IllegalStateException("Shape has no occupied nodes");
    }

    private static void assertDetectorGeometry(List<Detector> detectors, org.joml.Vector3i source, int radius) {
        if (detectors.size() != 6) throw new IllegalStateException("Expected six strategic detectors");
        int expected = radius + EXTERNAL_GRAPH_DISTANCE_OFFSET;
        for (Detector detector : detectors) {
            org.joml.Vector3i index = detector.getNodeIndex();
            int distance = Math.abs(index.x - source.x) + Math.abs(index.y - source.y) + Math.abs(index.z - source.z);
            if (distance != expected) {
                throw new IllegalStateException("Detector " + detector.getName() + " graph distance " + distance + " != " + expected);
            }
        }
    }

    private static void writeShapeNodes(Path output, List<Node> nodes, org.joml.Vector3i center) {
        try (BufferedWriter writer = Files.newBufferedWriter(output)) {
            writer.write("x,y,z,dx,dy,dz,graphDistance\n");
            for (Node node : nodes) {
                org.joml.Vector3i index = node.getIndex();
                int dx = index.x - center.x, dy = index.y - center.y, dz = index.z - center.z;
                writer.write(index.x + "," + index.y + "," + index.z + "," + dx + "," + dy + "," + dz + ","
                        + (Math.abs(dx) + Math.abs(dy) + Math.abs(dz)) + "\n");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write shape nodes", exception);
        }
    }

    private static void writeMetadata(Path output, String status, int radius, double indentation,
                                      int lastUpdate, Occupancy source, Project project, String reason) {
        try (BufferedWriter writer = Files.newBufferedWriter(output)) {
            writer.write("key,value\n");
            put(writer, "status", status);
            put(writer, "baseRadius", radius);
            put(writer, "indentation", indentation);
            put(writer, "lastCompletedUpdate", lastUpdate);
            put(writer, "updates", 180);
            put(writer, "latticeSize", project.getDimensionX());
            put(writer, "externalOffset", EXTERNAL_GRAPH_DISTANCE_OFFSET);
            put(writer, "boundaryConfiguration", "canonical finite lattice absorption");
            put(writer, "occupiedNodeCount", source == null ? "" : source.getOccupiedRegionDiagnostics().occupiedNodeCount());
            put(writer, "boundaryNodeCount", source == null ? "" : source.getOccupiedRegionDiagnostics().boundaryNodeCount());
            put(writer, "boundaryCrossingEdgeCount", source == null ? "" : source.getOccupiedRegionDiagnostics().boundaryCrossingEdgeCount());
            put(writer, "centerNodeIndex", source == null ? "" : source.getNodeIndex());
            put(writer, "perNodeAmplitude", source == null ? "" : source.getIdentity().getAmplitude());
            put(writer, "failureReason", reason);
            if (source != null) {
                for (Detector detector : project.getDetectors()) {
                    int distance = minimumDistance(source.getOccupiedNodes(), detector.getNodeIndex());
                    put(writer, "detector." + detector.getName() + ".graphDistanceFromCenter", graphDistance(source.getNodeIndex(), detector.getNodeIndex()));
                    put(writer, "detector." + detector.getName() + ".minimumGraphDistanceFromOccupiedRegion", distance);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write shape metadata", exception);
        }
    }

    private static void put(BufferedWriter writer, String key, Object value) throws IOException {
        String text = String.valueOf(value).replace("\n", " ");
        writer.write(key + "," + text + "\n");
    }

    private static int graphDistance(org.joml.Vector3i a, org.joml.Vector3i b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y) + Math.abs(a.z - b.z);
    }

    private static int minimumDistance(List<Node> nodes, org.joml.Vector3i detector) {
        int minimum = Integer.MAX_VALUE;
        for (Node node : nodes) minimum = Math.min(minimum, graphDistance(node.getIndex(), detector));
        return minimum;
    }
}
