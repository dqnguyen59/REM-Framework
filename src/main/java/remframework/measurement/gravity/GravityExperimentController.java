package remframework.measurement.gravity;

import remframework.opengl.model.entity.Identity;

import java.nio.file.Path;

/**
 * Minimal configuration facade for Gravity Test 1.
 */
public final class GravityExperimentController implements AutoCloseable {

    private static final GravityExperimentController INSTANCE = new GravityExperimentController();

    private final GravityMeasurementLogger logger = GravityMeasurementLogger.getInstance();

    private GravityExperimentController() {
    }

    public static GravityExperimentController getInstance() {
        return INSTANCE;
    }

    public void configure(Path outputFile) {
        logger.configure(outputFile);
    }

    public void designateSourceIdentity(Identity identity) {
        logger.designateSourceIdentity(identity);
    }

    public void clearMeasurementIdentities() {
        logger.clearMeasurementIdentities();
    }

    public void addMeasurementIdentity(Identity identity) {
        logger.addMeasurementIdentity(identity);
    }

    public void setEnabled(boolean enabled) {
        logger.setEnabled(enabled);
    }

    public void beginSimulationUpdate() {
        logger.beginSimulationUpdate();
    }

    public long getUpdatesRecorded() {
        return logger.getUpdateNumber();
    }

    public long getRowsWritten() {
        return logger.getRowsWritten();
    }

    public int getMeasurementPointCount() {
        return logger.getMeasurementPointCount();
    }

    public Path getOutputFile() {
        return logger.getOutputFile();
    }

    @Override
    public void close() {
        logger.close();
    }
}
