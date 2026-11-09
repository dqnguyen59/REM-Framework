package remframework.geometry.discovery.research.diamond;

import org.joml.Vector3d;

/** Prescribed experimental source trajectory, independent of the Node equation. */
public record DiamondSphericalSweep(int updatesPerCycle, int turns, int measurementCycles) {
    public DiamondSphericalSweep {
        if (turns < 1 || turns > 64 || updatesPerCycle < 32L * turns || updatesPerCycle > 1_000_000
                || updatesPerCycle % 2 != 0 || measurementCycles < 1 || measurementCycles > 1000)
            throw new IllegalArgumentException("Use 1..64 turns, an even period >= 32 * turns (<= 1000000), and 1..1000 measurement cycles");
    }

    /**
     * Equal-area latitude progression: z = 1 - 2q, phi = 2*pi*turns*q.
     * The second half retraces the first in reverse, balancing rotation sense.
     * <br><br>
     * A finite spiral approximates positional coverage; it does not guarantee equal
     * coverage of every rotation axis. Velocities are finite update differences.
     */
    public Vector3d direction(long update) {
        double phase = Math.floorMod(update, updatesPerCycle) / (double) updatesPerCycle;
        double q = phase <= .5 ? 2 * phase : 2 * (1 - phase);
        double z = 1 - 2 * q;
        double radius = Math.sqrt(Math.max(0, 1 - z * z));
        double phi = 2 * Math.PI * turns * q;
        return new Vector3d(radius * Math.cos(phi), radius * Math.sin(phi), z);
    }
}
