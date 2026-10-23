package remframework.opengl.controller;

import org.joml.Vector3d;
import remframework.opengl.model.entity.Occupancy;

/** Experimental pressure-only bridge; no node motion, transfer, damping or caps. */
public final class DiamondOccupancyPressure {
    private DiamondOccupancyPressure() { }

    /**
     * g = gradientGain * local bond imbalance;<br>
     * a = accelerationGain * |g| * g / |Identity amplitude|;<br>
     * M(next) = M + momentumGain * a;<br>
     * P(next) = P + M(next).
     * <br><br>
     * The radial nonlinear response matches the cubic scalar response on an axis
     * but is an explicit experimental extension to arbitrary physical directions.
     * Zero amplitude gives zero new acceleration, retaining existing momentum and pressure.
     * Passive observers record the local sample without changing pressure or momentum.
     * Invalid inputs/candidates are rejected before modifying the occupancy.
     */
    public static void update(Occupancy occupancy, double gradientGain,
                              double accelerationGain, double momentumGain) {
        synchronized (occupancy) {
            updateLocked(occupancy, gradientGain, accelerationGain, momentumGain);
        }
    }

    private static void updateLocked(Occupancy occupancy, double gradientGain,
                                     double accelerationGain, double momentumGain) {
        if (occupancy.getNode() == null || occupancy.getOccupant() == null) return;
        if (!occupancy.getNode().isGeometricDistanceImbalance())
            throw new IllegalArgumentException("Diamond pressure requires geometric bond imbalance");
        if (!Double.isFinite(gradientGain) || gradientGain < 0
                || !Double.isFinite(accelerationGain) || accelerationGain < 0
                || !Double.isFinite(momentumGain) || momentumGain < 0)
            throw new IllegalArgumentException("Diamond pressure gains must be finite and nonnegative");
        Vector3d gradient = occupancy.getNode().readGradientImbalance().mul(gradientGain);
        if (!gradient.isFinite()) throw new IllegalStateException("Nonfinite diamond pressure gradient");
        if (!occupancy.getOccupant().canExcite() && !occupancy.getOccupant().canTransfer()) {
            occupancy.saveGradientToHistory(gradient);
            return;
        }
        if (occupancy.getIdentity() == null)
            throw new IllegalArgumentException("Diamond pressure currently supports Identity occupancies only");
        double amplitude = occupancy.getIdentity().getAmplitude().length();
        if (!Double.isFinite(amplitude)) throw new IllegalStateException("Nonfinite Identity amplitude");
        Vector3d acceleration = amplitude == 0 ? new Vector3d()
                : new Vector3d(gradient).mul(accelerationGain * gradient.length() / amplitude);
        Vector3d momentum = new Vector3d(occupancy.getTransportMomentum()).fma(momentumGain, acceleration);
        Vector3d pressure = new Vector3d(occupancy.getPressure()).add(momentum);
        if (!acceleration.isFinite() || !momentum.isFinite() || !pressure.isFinite())
            throw new IllegalStateException("Nonfinite diamond pressure state; update rejected");
        occupancy.saveGradientToHistory(gradient);
        occupancy.getGradientImbalance().set(gradient);
        occupancy.getAcceleration().set(acceleration);
        occupancy.getTransportMomentum().set(momentum);
        occupancy.getPressure().set(pressure);
    }
}
