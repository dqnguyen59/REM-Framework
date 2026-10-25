package remframework.opengl.model;

import org.joml.Vector3d;

/** Read-only choices for passive probe arrows, all in lattice-parent coordinates. */
public enum ProbeArrowQuantity {
    CURRENT_IMBALANCE("Bond-distance imbalance (current)"),
    VELOCITY("Velocity (current)"),
    CYCLE_IMBALANCE("Bond-distance imbalance (cycle summary)");

    private final String label;
    ProbeArrowQuantity(String label) { this.label = label; }
    public String label() { return label; }

    /** Copies a measured vector; never normalizes or changes the underlying observation. */
    public Vector3d read(PassiveObserver probe, Vector3d destination) {
        if (this == CYCLE_IMBALANCE) return destination.set(probe.getStableGradientImbalance());
        if (this == VELOCITY) {
            if (probe instanceof Probe raw) return destination.set(raw.getNode().getVelocity());
            if (probe instanceof InterpolatedProbe interpolated) {
                destination.zero();
                var sources = probe.getDefinition().getInterpolationSources();
                for (int i = 0; i < sources.size(); i++)
                    destination.fma(sources.get(i).weight(), interpolated.getSourceNodes().get(i).getVelocity());
                return destination;
            }
            return destination.set(probe.getObservation().velocity());
        }
        var occupancies = probe.getOccupancies();
        if (occupancies.isEmpty()) return destination.set(probe.getObservation().gradientImbalance());
        if (probe.getDetectorType() == ProbeDetectorType.RAW_NODE)
            return occupancies.getFirst().copyLastMeasuredGradientForAudit(destination);
        destination.zero();
        var sources = probe.getDefinition().getInterpolationSources();
        for (int i = 0; i < occupancies.size(); i++)
            occupancies.get(i).accumulateLastMeasuredGradientForAudit(destination, sources.get(i).weight());
        return destination;
    }
}
