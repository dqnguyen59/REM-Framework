package remframework.opengl.model.entity;

/**
 * Capability contract for an occupant participating in an Occupancy–Node relation.
 * <br><br>
 * Measurement is independent of both capabilities; they only govern physical effects.
 */
public interface Occupant {

    /** Returns whether this occupant may generate excitation and apply node force. */
    boolean canExcite();

    /** Returns whether this occupant may accumulate and transfer transport state. */
    boolean canTransfer();
}
