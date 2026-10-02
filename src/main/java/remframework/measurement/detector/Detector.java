package remframework.measurement.detector;

import lombok.Getter;
import org.joml.Vector3i;

import java.util.Objects;

/**
 * Immutable description of a passive lattice measurement point.
 * <br><br>
 * A detector belongs only to instrumentation. It neither creates nor owns an
 * Identity or Occupancy and contains no evolving simulation state.
 */
public final class Detector {
    @Getter
    private final String name;
    private final Vector3i nodeIndex;
    @Getter
    private final Integer requestedRadius;
    @Getter
    private final Double requestedAngleDegrees;
    @Getter
    private final String direction;
    @Getter
    private final String description;

    public Detector(String name, Vector3i nodeIndex, Integer requestedRadius,
                    Double requestedAngleDegrees, String description) {
        this(name, nodeIndex, requestedRadius, requestedAngleDegrees, null,
                description);
    }

    public Detector(String name, Vector3i nodeIndex, Integer requestedRadius,
                    Double requestedAngleDegrees, String direction,
                    String description) {
        this.name = Objects.requireNonNull(name, "Detector name");
        this.nodeIndex = new Vector3i(Objects.requireNonNull(
                nodeIndex, "Detector node index"));
        this.requestedRadius = requestedRadius;
        this.requestedAngleDegrees = requestedAngleDegrees;
        this.direction = direction;
        this.description = description;
    }

    public Vector3i getNodeIndex() {
        return new Vector3i(nodeIndex);
    }
}
