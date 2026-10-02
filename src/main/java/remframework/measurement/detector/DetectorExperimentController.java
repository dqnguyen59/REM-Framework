package remframework.measurement.detector;

import org.joml.Vector3i;
import remframework.opengl.model.Lattice;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Lifecycle facade for passive detector instrumentation.
 */
public final class DetectorExperimentController {
    private static final DetectorExperimentController INSTANCE =
            new DetectorExperimentController();
    private final DetectorMeasurementLogger logger =
            new DetectorMeasurementLogger();
    private boolean enabled;

    private DetectorExperimentController() {
    }

    public static DetectorExperimentController getInstance() {
        return INSTANCE;
    }

    public synchronized void configure(Path output, List<Detector> detectors,
                                       Vector3i sourceIndex) throws IOException {
        logger.configure(output, detectors, sourceIndex);
        enabled = true;
    }

    public synchronized void disable() {
        enabled = false;
        logger.close();
    }

    public synchronized void close() {
        enabled = false;
        logger.close();
    }

    public synchronized void sample(Lattice lattice, double sourcePhase) {
        if (!enabled) return;
        try {
            logger.sample(lattice, sourcePhase);
        } catch (IOException exception) {
            enabled = false;
            throw new IllegalStateException(
                    "Passive detector logging failed", exception);
        }
    }

    public synchronized void resolveNodes(Lattice lattice) {
        if (enabled) logger.resolveNodes(lattice);
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public long getUpdatesRecorded() {
        return logger.getUpdatesRecorded();
    }

    public long getRowsWritten() {
        return logger.getRowsWritten();
    }

    public Path getOutputFile() {
        return logger.getOutputFile();
    }
}
