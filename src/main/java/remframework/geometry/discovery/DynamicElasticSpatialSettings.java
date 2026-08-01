package remframework.geometry.discovery;
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

public record DynamicElasticSpatialSettings(
        boolean nonNeighborExclusionEnabled,
        double minimumNonNeighborDistance,
        boolean edgeCrossingPreventionEnabled,
        double edgeClearanceDistance,
        boolean localSpreadEnabled,
        double strength) {

    public DynamicElasticSpatialSettings {
        if (!nonNegativeFinite(minimumNonNeighborDistance)
                || !nonNegativeFinite(edgeClearanceDistance)
                || !nonNegativeFinite(strength))
            throw new IllegalArgumentException("Spatial constraint settings must be finite and non-negative.");
    }

    public static DynamicElasticSpatialSettings defaults(double targetDistance) {
        return new DynamicElasticSpatialSettings(true, .70 * targetDistance,
                true, .10 * targetDistance, false, .20);
    }

    public static DynamicElasticSpatialSettings disabled(double targetDistance) {
        return new DynamicElasticSpatialSettings(false, .70 * targetDistance,
                false, .10 * targetDistance, false, 0);
    }

    public DynamicElasticSpatialSettings withLocalSpreadEnabled(boolean value) {
        return new DynamicElasticSpatialSettings(nonNeighborExclusionEnabled,
                minimumNonNeighborDistance, edgeCrossingPreventionEnabled,
                edgeClearanceDistance, value, strength);
    }

    private static boolean nonNegativeFinite(double value) {
        return Double.isFinite(value) && value >= 0;
    }
}
