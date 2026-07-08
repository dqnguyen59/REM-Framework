package org.smartblackbox.qfs.opengl.model.entity;

import org.joml.Vector3d;

/**
 * REM-FIELD Occupancy.
 *
 * <p>
 * An Occupancy represents the relationship between an Identity and the
 * Node it currently occupies.
 * </p>
 *
 * <p>
 * In REM-FIELD, transport is not a property of either the Identity or the
 * Node. Instead, transport is an emergent property of the Occupancy itself.
 * </p>
 *
 * <p>
 * The Occupancy is therefore the dynamical object that:
 * <ul>
 *     <li>stores the persistent transport state,</li>
 *     <li>accumulates transport phase,</li>
 *     <li>moves through the lattice,</li>
 *     <li>and carries the Identity from one Node to another.</li>
 * </ul>
 * </p>
 *
 * <p>
 * The Occupancy has no global knowledge of the lattice. It does not know
 * where other identities are located. All transport decisions are based
 * solely on local measurements performed by the TransportSystem.
 * </p>
 */
public final class Occupancy {

    /**
     * The identity currently occupying the node.
     *
     * <p>
     * The identity provides the oscillatory excitation of the occupied node
     * but does not contain any transport information.
     * </p>
     */
    private final Identity identity;

    /**
     * The node currently occupied by the identity.
     *
     * <p>
     * This reference changes whenever the occupancy transfers to a neighboring
     * node.
     * </p>
     */
    private Node node;

    /**
     * Persistent transport vector.
     *
     * <p>
     * This vector represents the accumulated transport state of the occupancy.
     * </p>
     *
     * <p>
     * It determines:
     * <ul>
     *     <li>preferred transport direction,</li>
     *     <li>transport intensity.</li>
     * </ul>
     *
     * <p>
     * The transport vector is persistent. If the local recursive field
     * becomes perfectly symmetric, the vector remains unchanged and transport
     * continues indefinitely.
     * </p>
     */
    private final Vector3d transport = new Vector3d();

    /**
     * Transfer phase.
     *
     * <p>
     * Represents the accumulated fractional progress toward the next
     * occupancy transfer.
     * </p>
     *
     * <p>
     * Continuous transport is converted into discrete node transfers by
     * accumulating this phase.
     * </p>
     *
     * <p>
     * Whenever the phase reaches or exceeds one, one node transfer occurs.
     * Any remaining fractional phase is preserved.
     * </p>
     */
    private double transferPhase;

    /**
     * Creates a new occupancy.
     *
     * @param identity the occupying identity
     * @param node the initially occupied node
     */
    public Occupancy(
            Identity identity,
            Node node) {

        this.identity = identity;
        this.node = node;
    }

    /**
     * Returns the occupying identity.
     *
     * @return the identity
     */
    public Identity getIdentity() {
        return identity;
    }

    /**
     * Returns the currently occupied node.
     *
     * @return occupied node
     */
    public Node getNode() {
        return node;
    }

    /**
     * Updates the occupied node.
     *
     * <p>
     * Called whenever the occupancy transfers to another node.
     * </p>
     *
     * @param node newly occupied node
     */
    public void setNode(Node node) {
        this.node = node;
    }

    /**
     * Returns the persistent transport vector.
     *
     * @return transport vector
     */
    public Vector3d getTransport() {
        return transport;
    }

    /**
     * Returns the current transfer phase.
     *
     * @return transfer phase
     */
    public double getTransferPhase() {
        return transferPhase;
    }

    /**
     * Sets the transfer phase.
     *
     * @param transferPhase new transfer phase
     */
    public void setTransferPhase(double transferPhase) {
        this.transferPhase = transferPhase;
    }

    /**
     * Adds transport phase.
     *
     * <p>
     * Called once per simulation update after the transport vector has been
     * updated.
     * </p>
     *
     * @param amount phase increment
     */
    public void addTransferPhase(double amount) {
        transferPhase += amount;
    }

    /**
     * Returns whether sufficient phase has accumulated for at least one
     * occupancy transfer.
     *
     * @return true if phase >= 1
     */
    public boolean readyToTransfer() {
        return transferPhase >= 1.0;
    }

    /**
     * Consumes one unit of transfer phase after an occupancy transfer.
     *
     * <p>
     * Remaining fractional phase is preserved.
     * Multiple transfers during a single update are therefore possible.
     * </p>
     */
    public void consumeTransferPhase() {
        transferPhase -= 1.0;
    }
}