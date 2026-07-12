package org.smartblackbox.qfs.opengl.controller;

import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3d;
import org.smartblackbox.qfs.Constants;
import org.smartblackbox.qfs.opengl.model.RemModel;
import org.smartblackbox.qfs.opengl.model.entity.Node;
import org.smartblackbox.qfs.opengl.model.entity.Occupancy;
import org.smartblackbox.qfs.settings.Project;

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
public class TransportSystem {

    private final Project  project       = Project.getInstance();
    private final RemModel remModel      = project.getRemModel();

    private       double gradientGain = 0.00001;


    /**
     * Pressure required to transfer one lattice step.
     */
    private double transferThreshold = 0.00001;

    private double f = 0.01;

    /**
     * Updates the transport state of a single occupancy.
     *
     * @param occupancy the occupancy to update
     */
    public void update(Occupancy occupancy) {
        // gradient = measuredGradient * gradientGain
        measureGradient(occupancy);

        occupancy.updateAverageGradient();

        // transportMomentum += gradient
        updateTransportMomentum(occupancy);

        // pressure += transportProgress;
        updatePressure(occupancy);

        // if pressure > threshold then transfer to neighbor node
        transferOccupancy(occupancy);
    }

    /**
     * Measures the local recursive field gradient around the occupied node.
     */
    private void measureGradient(Occupancy occupancy) {
        occupancy.getGradient().mul(0.2).fma(gradientGain, occupancy.getNode().measureGradient());
    }

    /**
     * Updates the persistent transport momentum.
     * <br>
     * The local lattice gradient acts as the transport acceleration.
     * A constant gain controls how strongly the measured gradient
     * changes the current transport momentum.
     */
    private void updateTransportMomentum(Occupancy occupancy) {
        occupancy.getTransportMomentum().fma(0.00001, occupancy.getAverageGradient());
        double x = Math.clamp(occupancy.getTransportMomentum().x, -transferThreshold * f, transferThreshold * f);
        double y = Math.clamp(occupancy.getTransportMomentum().y, -transferThreshold * f, transferThreshold * f);
        double z = Math.clamp(occupancy.getTransportMomentum().z, -transferThreshold * f, transferThreshold * f);
        occupancy.getTransportMomentum().set(x, y, z);
    }

    /**
     * Accumulates transport pressure.
     * <br>
     * Transport velocity contributes continuously to the pressure.
     * When pressure reaches the transport threshold,
     * a node transfer may occur.
     */
    private void updatePressure(Occupancy occupancy) {
        occupancy.getPressure().add(occupancy.getTransportMomentum());
    }

    /**
     * Transfers the occupancy whenever sufficient transport pressure has accumulated.
     * <br>
     * The strongest pressure component determines the transport direction.
     */
    private void transferOccupancy(Occupancy occupancy) {

        Vector3d pressure = occupancy.getPressure();
        Node currentNode = occupancy.getNode();

        if (currentNode == null) return;

        double absX = Math.abs(pressure.x);
        double absY = Math.abs(pressure.y);
        double absZ = Math.abs(pressure.z);

        /*
         * X transport
         */
        int directionA          = -1;
        int directionB          = -1;
        if (absX >= transferThreshold) {
            System.out.println("absX: " + absX);
            directionA  = pressure.x > 0 ? Constants.NEIGHBOR_LEFT : Constants.NEIGHBOR_RIGHT;
            directionB  = pressure.x <= 0 ? Constants.NEIGHBOR_LEFT : Constants.NEIGHBOR_RIGHT;
            pressure.x = pressure.x > 0? absX - transferThreshold : transferThreshold - absX;
        }

        /*
         * Y transport
         */
        else if (absY >= transferThreshold) {
            System.out.println("absY: " + absY);
            directionA  = pressure.y > 0 ? Constants.NEIGHBOR_BOTTOM : Constants.NEIGHBOR_TOP;
            directionB  = pressure.y <= 0 ? Constants.NEIGHBOR_BOTTOM : Constants.NEIGHBOR_TOP;
            pressure.y = pressure.y > 0? absY - transferThreshold : transferThreshold - absY;
        }

        /*
         * Z transport
         */
        else if (absZ >= transferThreshold) {
            System.out.println("absZ: " + absZ);
            directionA  = pressure.z > 0 ? Constants.NEIGHBOR_BACK : Constants.NEIGHBOR_FRONT;
            directionB  = pressure.z <= 0 ? Constants.NEIGHBOR_BACK : Constants.NEIGHBOR_FRONT;
            pressure.sub(0, 0, pressure.z);
            pressure.z = pressure.z > 0? absZ - transferThreshold : transferThreshold - absZ;
        }

        if (directionA != -1) {
            Node targetNode = currentNode.getNeighbors()[directionA];
            // If moving occupancy to target fails, because the target is fixed (edge) or a wall
            if (!moveOccupancy(occupancy, targetNode)) {
                // Then If moving occupancy to target in the opposite direction
                targetNode = currentNode.getNeighbors()[directionB];
                moveOccupancy(occupancy, targetNode);
            }
        }
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
    private boolean moveOccupancy(Occupancy occupancy, Node targetNode) {

        if (targetNode == null) {
            return false;
        }

        if (targetNode.isFixed() || targetNode.isWall()) {
            return false;
        }

        Node currentNode = occupancy.getNode();

        if (currentNode == targetNode) {
            return false;
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

        return true;
    }

    public void print(Occupancy occupancy) {
        String s = String.format("[%s]: " + //
                                 "N: [%d, %d, %d]; === " + //
                                 "GX: %12.9f; === " + //
                                 "AX: %12.9f; === " + //
                                 "MX: %12.9f; === " + //
                                 "PrX: %12.9f; ", //
                                 occupancy.getIdentity().getName(),
                                 occupancy.getNodeIndex().x,
                                 occupancy.getNodeIndex().y,
                                 occupancy.getNodeIndex().z,
                                 occupancy.getGradient().x,
                                 occupancy.getAverageGradient().x,
                                 occupancy.getTransportMomentum().x,
                                 occupancy.getPressure().x);
        remModel.setCustomStatus(s);
        //System.out.println(s);
    }
}