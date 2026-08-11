package remframework.opengl.controller;

import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;

import lombok.Getter;
import lombok.Setter;
import org.ini4j.Wini;
import org.joml.Vector3d;
import remframework.helpers.Constants;
import remframework.measurement.gravity.GravityMeasurementLogger;
import remframework.measurement.gravitypressure.GravityPressureExperimentController;
import remframework.opengl.model.RemModel;
import remframework.opengl.model.entity.Node;
import remframework.opengl.model.entity.Occupancy;
import remframework.settings.Project;
import remframework.helpers.AbstractSettings;
import remframework.helpers.ISettings;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * REM-FIELD Transport System.
 *
 * <p>
 * The TransportSystem is responsible for evolving the transport state of every
 * occupancy. It is the only component that translates local lattice
 * measurements into transport.
 * </p>
 *
 * <p>
 * An occupancy has no global knowledge. It does not know:
 * <ul>
 *     <li>where other identities are,</li>
 *     <li>how many identities exist,</li>
 *     <li>their distance,</li>
 *     <li>their direction,</li>
 *     <li>or their velocity.</li>
 * </ul>
 * <p>
 * It only measures the state of its occupied node and the six neighboring nodes.
 * </p>
 *
 * <p>
 * The transport algorithm follows these stages:
 * <ol>
 *     <li>Measure the local recursive field gradient.</li>
 *     <li>Update the persistent transport vector.</li>
 *     <li>Advance the transfer phase.</li>
 *     <li>Transfer occupancy whenever the phase exceeds the threshold.</li>
 * </ol>
 * </p>
 *
 * <p>
 * Transport is therefore an emergent property of local recursive field
 * asymmetry and not the result of direct identity-to-identity interaction.
 * </p>
 */
@Getter
@Setter
public class TransportSystem extends AbstractSettings implements ISettings {

    private static TransportSystem                     instance;
    private final  Project                             project                   = Project.getInstance();
    private final  RemModel                            remModel                  = project.getRemModel();
    private final  GravityMeasurementLogger            gravityMeasurementLogger  =
            GravityMeasurementLogger.getInstance();
    private final  GravityPressureExperimentController gravityPressureController =
            GravityPressureExperimentController.getInstance();

    private boolean logToConsole     = false;
    private LogType logType          = LogType.none;
    private double  gradientGain     = 0.0001;
    private double  accelerationGain = 0.0000001;
    private double  impulseGain      = 0.000001;

    private double transportMomentumGain = 0.000001;
    private double transportMomentumMax  = 0.00000005;

    private boolean enableTransport = true;

    /**
     * Pressure required to transfer one lattice step.
     */
    private double transferThreshold     = 0.0000003;
    private double lastGradientImbalance = Double.MAX_VALUE;
    private int    cyclesToMove          = 10;

    private BufferedWriter receiverStatusWriter;
    private boolean        receiverStatusEnabled;
    private long           receiverStatusUpdate;

    public static TransportSystem getInstance() {
        if (instance == null) {
            instance = new TransportSystem();
        }
        return instance;
    }

    @Override
    public AbstractSettings clone() {
        return null;
    }

    @Override
    public void reset() {

    }

    @Override
    public void loadFromFile(Wini ini, String section, int index) {
        System.out.println("TransportSystem.loadFromFile()");
        super.loadFromFile(ini, section, index);
        logToConsole          = getBool(section + index, "logToConsole", false);
        enableTransport       = getBool(section + index, "enableTransport", true);
        gradientGain          = getDouble(section + index, "gradientGain", 0.00001);
        accelerationGain      = getDouble(section + index, "accelerationGain", 0.0000001);
        transportMomentumGain = getDouble(section + index, "transportMomentumGain", 0.00001);
        transportMomentumMax  = getDouble(section + index, "transportMomentumMax", 0.0000002);
        transferThreshold     = getDouble(section + index, "transferThreshold", 0.00001);
    }

    @Override
    public void saveToFile(Wini ini, String section, int index) {
        System.out.println("TransportSystem.saveToFile()");
        super.saveToFile(ini, section, index);
        put(section + index, "logToConsole", logToConsole);
        put(section + index, "enableTransport", enableTransport);
        put(section + index, "gradientGain", gradientGain);
        put(section + index, "accelerationGain", accelerationGain);
        put(section + index, "transportMomentumGain", transportMomentumGain);
        put(section + index, "transportMomentumMax", transportMomentumMax);
        put(section + index, "transferThreshold", transferThreshold);
    }

    /**
     * Updates the transport state of a single occupancy.
     * <br><br>
     * G = Gradient Imbalance<br>
     * a(G) = Acceleration<br>
     * M = Transport Momentum<br>
     * P = Pressure<br>
     * T = Transport<br>
     *
     * @param occupancy the occupancy to update
     */
    public void update(Occupancy occupancy) {
        boolean suppressTransfer = gravityPressureController.isDesignatedReceiver(occupancy);
        if (suppressTransfer) {
            gravityPressureController.beginReceiverUpdate(
                    occupancy, gradientGain, accelerationGain,
                    transportMomentumGain, transportMomentumMax, transferThreshold);
        }

        // G = measureGradientImbalance * gradientGain
        storeMeasuredGradientImbalance(occupancy);

        // C is the inverseFactor
        // a(G)=sign(G) * 1 / (C + G²) - 1 / C
        updateAcceleration(occupancy);

        // Where km is transportMomentumGain
        // Mt+1 = Mt + km a(G)
        updateTransportMomentum(occupancy);

        // Pt+1 = Pt + Mt+1;
        updatePressure(occupancy);

        // if |P| > Pthreshold then transfer to neighbor node
        if (suppressTransfer) {
            /*
             * Gravity Test 2 preserves the real late-cycle refresh, but skips
             * all subsequent pressure reset, cycle-count and movement handling
             * for its one designated fixed receiver.
             */
            gravityPressureController.completeSuppressedTransfer(
                    occupancy, occupancy.isCycleCompleted());
        } else {
            transferOccupancy(occupancy);
        }

        if (receiverStatusEnabled && project.occupancies.size() > 1
            && occupancy == project.occupancies.get(1)) {
            writeReceiverStatus(occupancy);
        }
    }

    /**
     * Enables the detectGradient8 receiver status trace.
     */
    public synchronized void configureReceiverStatusLogging(Path outputFile) {
        closeReceiverStatusLogging();
        try {
            Path parent = outputFile.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            receiverStatusWriter = Files.newBufferedWriter(outputFile,
                                                           StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                                                           StandardOpenOption.WRITE);
            receiverStatusWriter.write(receiverStatusHeader());
            receiverStatusWriter.newLine();
            receiverStatusEnabled = true;
            receiverStatusUpdate  = 0;
        }
        catch (Exception exception) {
            receiverStatusEnabled = false;
            receiverStatusWriter  = null;
            System.err.println("Receiver status CSV disabled: " + exception.getMessage());
        }
    }

    public synchronized void closeReceiverStatusLogging() {
        receiverStatusEnabled = false;
        if (receiverStatusWriter != null) {
            try {
                receiverStatusWriter.close();
            }
            catch (Exception ignored) {
                // Best effort during application shutdown.
            }
            receiverStatusWriter = null;
        }
    }

    public boolean isReceiverStatusEnabled() {
        return receiverStatusEnabled;
    }

    public String getReceiverStatus() {
        if (!receiverStatusEnabled || project.occupancies.size() < 2) return "";
        return formatReceiverStatus(project.occupancies.get(1));
    }

    private synchronized void writeReceiverStatus(Occupancy occupancy) {
        if (!receiverStatusEnabled || receiverStatusWriter == null) return;
        try {
            receiverStatusUpdate++;
            StringBuilder row = new StringBuilder();
            append(row, receiverStatusUpdate);
            append(row, occupancy.getIdentity() == null ? "" : occupancy.getIdentity().getName());
            append(row, occupancy.getNodeIndex().x);
            append(row, occupancy.getNodeIndex().y);
            append(row, occupancy.getNodeIndex().z);
            append(row, occupancy.getGradientImbalance());
            append(row, occupancy.getLastMaxPositiveGradient());
            append(row, occupancy.getLastMaxNegativeGradient());
            append(row, occupancy.getAcceleration());
            append(row, occupancy.getTransportMomentum());
            append(row, occupancy.getPressure());
            append(row, occupancy.getNode().getVelocity());
            append(row, occupancy.getCycleSampleCountForAudit());
            append(row, occupancy.getSamplesPerCycleForAudit());
            append(row, occupancy.wasCycleCompletedThisUpdate());
            receiverStatusWriter.write(row.toString());
            receiverStatusWriter.newLine();
            receiverStatusWriter.flush();
        }
        catch (Exception exception) {
            closeReceiverStatusLogging();
            System.err.println("Receiver status CSV stopped: " + exception.getMessage());
        }
    }

    private static String receiverStatusHeader() {
        return "update,identity,nodeX,nodeY,nodeZ,gradientImbalanceX,gradientImbalanceY,gradientImbalanceZ," +
               "maxPositiveGradientX,maxPositiveGradientY,maxPositiveGradientZ," +
               "maxNegativeGradientX,maxNegativeGradientY,maxNegativeGradientZ," +
               "accelerationX,accelerationY,accelerationZ," +
               "transportMomentumX,transportMomentumY,transportMomentumZ," +
               "pressureX,pressureY,pressureZ,nodeVelocityX,nodeVelocityY,nodeVelocityZ," +
               "cycleSampleCount,samplesPerCycle,cycleCompleted";
    }

    private static String formatReceiverStatus(Occupancy occupancy) {
        return String.format("Receiver G=(% .6e,% .6e,% .6e) " +
                             "cycleMax+=(% .6e,% .6e,% .6e) cycleMax-=(% .6e,% .6e,% .6e) " +
                             "A=(% .6e,% .6e,% .6e) M=(% .6e,% .6e,% .6e) " +
                             "P=(% .6e,% .6e,% .6e) Vnode=(% .6e,% .6e,% .6e) " +
                             "samples=%d/%d complete=%s",
                             occupancy.getGradientImbalance().x, occupancy.getGradientImbalance().y,
                             occupancy.getGradientImbalance().z,
                             occupancy.getLastMaxPositiveGradient().x, occupancy.getLastMaxPositiveGradient().y,
                             occupancy.getLastMaxPositiveGradient().z,
                             occupancy.getLastMaxNegativeGradient().x, occupancy.getLastMaxNegativeGradient().y,
                             occupancy.getLastMaxNegativeGradient().z,
                             occupancy.getAcceleration().x, occupancy.getAcceleration().y,
                             occupancy.getAcceleration().z,
                             occupancy.getTransportMomentum().x, occupancy.getTransportMomentum().y,
                             occupancy.getTransportMomentum().z,
                             occupancy.getPressure().x, occupancy.getPressure().y, occupancy.getPressure().z,
                             occupancy.getNode() == null ? 0 : occupancy.getNode().getVelocity().x,
                             occupancy.getNode() == null ? 0 : occupancy.getNode().getVelocity().y,
                             occupancy.getNode() == null ? 0 : occupancy.getNode().getVelocity().z,
                             occupancy.getCycleSampleCountForAudit(), occupancy.getSamplesPerCycleForAudit(),
                             occupancy.wasCycleCompletedThisUpdate());
    }

    private static void append(StringBuilder row, Object value) {
        if (row.length() > 0) row.append(',');
        row.append(value);
    }

    private static void append(StringBuilder row, Vector3d value) {
        append(row, value.x);
        append(row, value.y);
        append(row, value.z);
    }

    /**
     * Measures the local recursive field gradient around the occupied node.
     */
    private void storeMeasuredGradientImbalance(Occupancy occupancy) {
        Vector3d measuredGradient = occupancy.getNode().measureGradientImbalance();
        gravityMeasurementLogger.captureRawGradient(occupancy, measuredGradient);
        gravityPressureController.captureRawGradient(occupancy, measuredGradient);
        measuredGradient.mul(gradientGain);
        gravityPressureController.captureScaledGradient(occupancy, measuredGradient);
        occupancy.saveGradientToHistory(measuredGradient);
    }

    /**
     * a(G)=sign(G) * 1 / (C + G²) - 1 / C
     *
     * @param occupancy: The node occupied by Identity
     */
    private void updateAcceleration(Occupancy occupancy) {
        occupancy.getAcceleration()
                 .set(calculateAcceleration(occupancy.getGradientImbalance().x,
                                            occupancy.getIdentity().getAmplitude().length()),
                      calculateAcceleration(occupancy.getGradientImbalance().y,
                                            occupancy.getIdentity().getAmplitude().length()),
                      calculateAcceleration(occupancy.getGradientImbalance().z,
                                            occupancy.getIdentity().getAmplitude().length()))
                 .add(occupancy.getAddAccX(), occupancy.getAddAccY(), occupancy.getAddAccZ());
        occupancy.setAddAccX(0);
        occupancy.setAddAccY(0);
        occupancy.setAddAccZ(0);
        gravityPressureController.captureAcceleration(
                occupancy, occupancy.getAcceleration());
    }

    /**
     * a(G)=sign(G) / (inverseFactor + G²)
     *
     * @param gradientImbalance the imbalance of a node of an axis
     * @param amplitude         of Identity
     * @return acceleration
     */
    double calculateAcceleration(double gradientImbalance, double amplitude) {
        if (amplitude == 0) return 0;

        double imbalancedSquared = gradientImbalance * gradientImbalance;
        double newValue          = accelerationGain * imbalancedSquared / amplitude;
        return gradientImbalance < 0 ? newValue * -1.0 : newValue;
    }

    /**
     * Updates the persistent transport momentum.
     * <br>
     * The local lattice gradient acts as the transport acceleration.
     * A constant gain controls how strongly the measured gradient
     * changes the current transport momentum.
     */
    private void updateTransportMomentum(Occupancy occupancy) {
        Vector3d momentumBefore = new Vector3d(occupancy.getTransportMomentum());
        occupancy.getTransportMomentum().fma(transportMomentumGain, occupancy.getAcceleration());
        gravityPressureController.captureMomentumCandidate(
                occupancy, momentumBefore, occupancy.getTransportMomentum());
        double x = Math.clamp(occupancy.getTransportMomentum().x, -transportMomentumMax,
                              transportMomentumMax);
        double y = Math.clamp(occupancy.getTransportMomentum().y, -transportMomentumMax,
                              transportMomentumMax);
        double z = Math.clamp(occupancy.getTransportMomentum().z, -transportMomentumMax,
                              transportMomentumMax);
        occupancy.getTransportMomentum().set(x, y, z);
        gravityPressureController.captureMomentumAfter(
                occupancy, occupancy.getTransportMomentum());
    }

    /**
     * Accumulates transport pressure.
     * <br>
     * Transport velocity contributes continuously to the pressure.
     * When pressure reaches the transport threshold,
     * a node transfer may occur.
     */
    private void updatePressure(Occupancy occupancy) {
        Vector3d pressureBefore = new Vector3d(occupancy.getPressure());
        occupancy.getPressure().add(occupancy.getTransportMomentum());
        gravityPressureController.capturePressure(
                occupancy, pressureBefore, occupancy.getTransportMomentum(),
                occupancy.getPressure());
    }

    /**
     * Transfers the occupancy whenever sufficient transport pressure has accumulated.
     */
    private void transferOccupancy(Occupancy occupancy) {
        Vector3d pressure    = occupancy.getPressure();
        Node     currentNode = occupancy.getNode();

        double absX = Math.abs(pressure.x);
        double absY = Math.abs(pressure.y);
        double absZ = Math.abs(pressure.z);

        int directionA;
        int directionB;

        if (occupancy.isCycleCompleted()) {

            /*
             * X transport
             */
            if (currentNode != null &&
                absX >= transferThreshold &&
                absX >= absY &&
                absX >= absZ) {
                //System.out.println("absX: " + absX);
                occupancy.incrementTransportCycleX();
                if (occupancy.getTransportCyclesX() >= cyclesToMove) {
                    directionA  = pressure.x <= 0 ? Constants.NEIGHBOR_LEFT : Constants.NEIGHBOR_RIGHT;
                    directionB  = pressure.x > 0 ? Constants.NEIGHBOR_LEFT : Constants.NEIGHBOR_RIGHT;
                    currentNode = moveNode(occupancy, currentNode, directionA, directionB);
                    if (currentNode != null)
                        occupancy.setTransportCyclesX(0);
                }
                pressure.x = 0;
            }

            /*
             * Y transport
             */
            else if (currentNode != null && absY >= transferThreshold &&
                     absY >= absZ) {
                //System.out.println("absY: " + absY);
                occupancy.incrementTransportCycleY();
                if (occupancy.getTransportCyclesY() >= cyclesToMove) {
                    directionA  = pressure.y <= 0 ? Constants.NEIGHBOR_BOTTOM : Constants.NEIGHBOR_TOP;
                    directionB  = pressure.y > 0 ? Constants.NEIGHBOR_BOTTOM : Constants.NEIGHBOR_TOP;
                    currentNode = moveNode(occupancy, currentNode, directionA, directionB);
                    if (currentNode != null)
                        occupancy.setTransportCyclesY(0);
                }
                pressure.y = 0;
            }

            /*
             * Z transport
             */
            else if (currentNode != null && absZ >= transferThreshold) {
                //System.out.println("absZ: " + absZ);
                occupancy.incrementTransportCycleZ();
                if (occupancy.getTransportCyclesZ() >= cyclesToMove) {
                    directionA  = pressure.z <= 0 ? Constants.NEIGHBOR_BACK : Constants.NEIGHBOR_FRONT;
                    directionB  = pressure.z > 0 ? Constants.NEIGHBOR_BACK : Constants.NEIGHBOR_FRONT;
                    currentNode = moveNode(occupancy, currentNode, directionA, directionB);
                    if (currentNode != null)
                        occupancy.setTransportCyclesZ(0);
                }
                pressure.z = 0;
            }
        }

    }

    private Node moveNode(Occupancy occupancy, Node currentNode, int directionA, int directionB) {
        if (!enableTransport) return null;
        Node targetNode = currentNode.getNeighbors()[directionA];
        Node newNode    = moveOccupancy(occupancy, targetNode);
        // If moving occupancy to target fails, because the target is fixed (edge) or a wall
        if (newNode == null) {
            // Then If moving occupancy to target in the opposite direction
            targetNode = currentNode.getNeighbors()[directionB];
            newNode    = moveOccupancy(occupancy, targetNode);
        }
        return newNode;
    }

    /**
     * Moves an occupancy relation from one node to another.
     * <br><br>
     * The identity does not move independently.
     * The Identity-Node relation changes through Occupancy.
     * <br><br>
     * Transport velocity is preserved because movement contains
     * momentum-like behavior.
     *
     * @param occupancy  occupancy being transported
     * @param targetNode destination node
     */
    private Node moveOccupancy(Occupancy occupancy, Node targetNode) {

        if (targetNode == null) {
            return null;
        }

        if (targetNode.isFixed() || targetNode.isWall()) {
            return null;
        }

        Node currentNode = occupancy.getNode();

        if (currentNode == targetNode) {
            return null;
        }


        /*
         * Future:
         * Check if target node is already occupied.
         *
         * if (targetNode.isOccupied()) {
         *     return;
         * }
         */


        /*
         * Move the occupancy relation.
         */
        occupancy.setNode(targetNode);

        return targetNode;
    }

    public void printOccupancy(Occupancy occupancy) {
        String s = String.format("[%s]: " + //
                                 "N: [%d, %d, %d]; | " + //
                                 "S: %3d; | " + //
                                 "G: %12.9f; " + //
                                 "G-: %12.9f; " + //
                                 "G+: %12.9f; | " + //
                                 "A: %12.9f; " + //
                                 "V: %12.9f; " + //
                                 "P: %12.18f; " + //
                                 "T: %12.18f; ", //
                                 occupancy.getIdentity().getName(),
                                 occupancy.getNodeIndex().x,
                                 occupancy.getNodeIndex().y,
                                 occupancy.getNodeIndex().z,
                                 occupancy.getSamplesPerCycle(),
                                 occupancy.getGradientImbalance().x,
                                 occupancy.getLastMaxNegativeGradient().x,
                                 occupancy.getLastMaxPositiveGradient().x,
                                 occupancy.getAcceleration().x,
                                 occupancy.getTransportMomentum().x,
                                 occupancy.getPressure().x,
                                 transferThreshold);
        remModel.setCustomStatus(s);

        if (logToConsole) {
            System.out.println(s);
        }
    }

    public void printDistanceG(Occupancy occupancyA, Occupancy occupancyB) {
        if (occupancyB.getGradientImbalance().x != lastGradientImbalance) {
            String s = String.format("r: %3d; " + //
                                     "G: %12.9f; " + //
                                     "MPosG: %12.18f; " + //
                                     "MNegG: %12.18f; ", //
                                     occupancyB.getNodeIndex().x - occupancyA.getNodeIndex().x,
                                     occupancyB.getGradientImbalance().x,
                                     occupancyB.getLastMaxPositiveGradient().x,
                                     occupancyB.getLastMaxNegativeGradient().x
                                    );
            remModel.setCustomStatus(s);

            lastGradientImbalance = occupancyB.getGradientImbalance().x;
            if (logToConsole) {
                System.out.println(s);
            }
        }
    }
}
