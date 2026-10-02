package remframework.opengl.model;

import org.joml.Vector3d;

/** Holds two generated passive probes and computes their live gradient comparison. */
public final class ProbeComparison {
    public enum Slot { A, B }

    public record Measurement(Vector3d gradient, Vector3d requestedDirection,
                              int requestedRadius, double radial, double sideways) {
        public Measurement {
            gradient = new Vector3d(gradient);
            requestedDirection = new Vector3d(requestedDirection);
        }
        @Override public Vector3d gradient() { return new Vector3d(gradient); }
        @Override public Vector3d requestedDirection() { return new Vector3d(requestedDirection); }
    }

    private PassiveObserver a;
    private PassiveObserver b;

    public PassiveObserver get(Slot slot) { return slot == Slot.A ? a : b; }

    /** Assigns the next open slot, or removes the probe when it is already assigned. */
    public boolean toggle(PassiveObserver observer) {
        if (a == observer) { a = null; return true; }
        if (b == observer) { b = null; return true; }
        if (a == null) { a = observer; return true; }
        if (b == null) { b = observer; return true; }
        return false;
    }

    /** Clears both comparison slots during reset or an explicit user clear. */
    public void clear() { a = null; b = null; }

    /** Calculates radial and sideways components from the observer's passive live observation. */
    public static Measurement calculate(PassiveObserver observer) {
        ProbeDefinition.Placement placement = observer.getDefinition().getPlacement();
        ProbeDefinition.Target target = placement == null ? null : placement.targets().getFirst();
        Vector3d direction = target == null ? new Vector3d() : target.requestedDirection();
        if (direction.lengthSquared() > 0.0) direction.normalize();
        Vector3d gradient = observer.getObservation().gradientImbalance();
        double radial = gradient.dot(direction);
        double sideways = new Vector3d(gradient).fma(-radial, direction).length();
        return new Measurement(gradient, direction, target == null ? 0 : target.requestedRadius(), radial, sideways);
    }

    /** Returns the live A-minus-B radial gradient difference without storing a sample. */
    public double deltaRadial() {
        return calculate(a).radial() - calculate(b).radial();
    }
}
