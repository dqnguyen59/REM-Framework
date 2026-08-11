package remframework.measurement.gravitypressure;

import org.joml.Vector3d;
import remframework.opengl.model.entity.Occupancy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Narrow lifecycle and capture facade for Gravity Test 2.
 */
public final class GravityPressureExperimentController {
    private static final GravityPressureExperimentController INSTANCE =
            new GravityPressureExperimentController();
    private final GravityPressureMeasurementLogger logger =
            GravityPressureMeasurementLogger.getInstance();

    private GravityPressureExperimentController() {
    }

    public static GravityPressureExperimentController getInstance() {
        return INSTANCE;
    }

    public Path configure(String experimentName, boolean sourceEnabled,
                          String receiverConfiguration, Occupancy source,
                          Occupancy receiver) throws IOException {
        return configure(experimentName, sourceEnabled, receiverConfiguration,
                source, List.of(receiver));
    }

    public Path configure(String experimentName, boolean sourceEnabled,
                          String receiverConfiguration, Occupancy source,
                          List<Occupancy> receivers) throws IOException {
        int run = 1;
        Path output;
        do {
            output = Path.of("measurements",
                    String.format("%s-run-%03d.csv", experimentName, run++));
        } while (Files.exists(output));
        logger.configure(output, experimentName, sourceEnabled,
                receiverConfiguration, source, receivers);
        return output;
    }

    /** Configures the existing receiver logger at an explicit diagnostic path. */
    public Path configure(Path output, String experimentName, boolean sourceEnabled,
                          String receiverConfiguration, Occupancy source,
                          List<Occupancy> receivers) throws IOException {
        logger.configure(output, experimentName, sourceEnabled,
                receiverConfiguration, source, receivers);
        return output;
    }

    public void setEnabled(boolean enabled) {
        logger.setEnabled(enabled);
    }

    public boolean isEnabled() {
        return logger.isEnabled();
    }

    public void beginSimulationUpdate() {
        logger.beginSimulationUpdate();
    }

    public boolean isDesignatedReceiver(Occupancy occupancy) {
        return logger.isDesignatedReceiver(occupancy);
    }

    public void beginReceiverUpdate(Occupancy occupancy, double gradientGain,
                                    double accelerationGain,
                                    double momentumGain, double momentumMax,
                                    double threshold) {
        logger.beginReceiverUpdate(occupancy, gradientGain, accelerationGain,
                momentumGain, momentumMax, threshold);
    }

    public void captureRawGradient(Occupancy occupancy, Vector3d value) {
        logger.captureRawGradient(occupancy, value);
    }

    public void captureScaledGradient(Occupancy occupancy, Vector3d value) {
        logger.captureScaledGradient(occupancy, value);
    }

    public void captureAcceleration(Occupancy occupancy, Vector3d value) {
        logger.captureAcceleration(occupancy, value);
    }

    public void captureMomentumCandidate(Occupancy occupancy, Vector3d before,
                                         Vector3d candidate) {
        logger.captureMomentumCandidate(occupancy, before, candidate);
    }

    public void captureMomentumAfter(Occupancy occupancy, Vector3d value) {
        logger.captureMomentumAfter(occupancy, value);
    }

    public void capturePressure(Occupancy occupancy, Vector3d before,
                                Vector3d increment, Vector3d after) {
        logger.capturePressure(occupancy, before, increment, after);
    }

    public void completeSuppressedTransfer(Occupancy occupancy,
                                           boolean cycleCompleted) {
        logger.completeSuppressedTransfer(occupancy, cycleCompleted);
    }

    public void close() {
        logger.close();
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
