package org.smartblackbox.qfs.opengl.controller;

import lombok.Getter;
import lombok.Setter;
import org.smartblackbox.qfs.opengl.model.entity.Occupancy;

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
 *
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

    private double velocityGain = 1.0;

    /**
     * Updates the transport state of a single occupancy.
     *
     * @param occupancy the occupancy to update
     */
    public void update(Occupancy occupancy) {
        measureGradient(occupancy);
        updateVelocity(occupancy);
        updatePressure(occupancy);
        transferOccupancy(occupancy);
    }

    /**
     * Measures the local recursive field gradient around the occupied node.
     */
    private void measureGradient(Occupancy occupancy) {
        occupancy.getGradient().set(occupancy.getNode().measureGradient());
    }

    /**
     * Updates the persistent transport velocity.
     * <br>
     * The local lattice gradient acts as the transport acceleration.
     * A constant gain controls how strongly the measured gradient
     * changes the current transport velocity.
     */
    private void updateVelocity(Occupancy occupancy) {
        occupancy.getTransportVelocity().fma(velocityGain, occupancy.getGradient());
    }

    /**
     * Accumulates transport pressure.
     * <br>
     * Transport velocity contributes continuously to the pressure.
     * When pressure reaches the transport threshold,
     * a node transfer may occur.
     */
    private void updatePressure(Occupancy occupancy) {
        occupancy.getPressure().add(occupancy.getTransportVelocity());
    }


    /**
     * Transfers the occupancy whenever sufficient transport phase has accumulated.
     */
    private void transferOccupancy(Occupancy occupancy) {

    }

    public void print(Occupancy occupancy) {
        String s = String.format("[%s]: " + //
                                 "GX: %12.8f; GY: %12.8f; GZ: %12.8f === " + //
                                 "VX: %12.8f; VY: %12.8f; VZ: %12.8f === " + //
                                 "PX: %12.8f; PY: %12.8f; PZ: %12.8f; ", //
                                 occupancy.getIdentity().getName(),
                                 occupancy.getGradient().x, occupancy.getGradient().y, occupancy.getGradient().z, //
                                 occupancy.getTransportVelocity().x, occupancy.getTransportVelocity().y, occupancy.getTransportVelocity().z, //
                                 occupancy.getPressure().x, occupancy.getPressure().y, occupancy.getPressure().z //
                                );
        System.out.println(s);

    }
}