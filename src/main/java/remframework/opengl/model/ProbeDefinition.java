package remframework.opengl.model;

import lombok.Getter;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;

import java.util.List;

/** In-memory definition of a raw-node or exact-position interpolated passive probe. */
@Getter
public final class ProbeDefinition {
    /** One ideal target represented by a generated passive probe. */
    public record Target(int shell, int ray, String rayPlane, int requestedRadius,
                         Vector3d requestedCoordinate, Vector3d requestedDirection,
                         double realizedRadius, double angularDeviationDegrees,
                         Vector3i realizedDirection,
                         double angularDeviationFromFirstShellDegrees,
                         int graphDistance) {
        public Target {
            requestedCoordinate = new Vector3d(requestedCoordinate);
            requestedDirection = new Vector3d(requestedDirection);
            realizedDirection = realizedDirection == null ? null : new Vector3i(realizedDirection);
        }

        @Override
        public Vector3d requestedCoordinate() {
            return new Vector3d(requestedCoordinate);
        }

        @Override
        public Vector3d requestedDirection() {
            return new Vector3d(requestedDirection);
        }

        @Override
        public Vector3i realizedDirection() {
            return realizedDirection == null ? null : new Vector3i(realizedDirection);
        }
    }

    /** One lattice source and its immutable interpolation weight. */
    public record InterpolationSource(Vector3i nodeIndex, double weight) {
        public InterpolationSource {
            nodeIndex = new Vector3i(nodeIndex);
            if (!Double.isFinite(weight) || weight < -1.0e-12 || weight > 1.0 + 1.0e-12) {
                throw new IllegalArgumentException("Interpolation weight must be finite and within [0,1].");
            }
        }

        @Override
        public Vector3i nodeIndex() {
            return new Vector3i(nodeIndex);
        }
    }

    /** Geometric placement diagnostics retained with a generated passive probe. */
    public record Placement(List<Target> targets, int region, String measurementSpace) {
        public Placement {
            targets = List.copyOf(targets);
            if (targets.isEmpty()) throw new IllegalArgumentException("Placement targets are required.");
            if (measurementSpace == null || measurementSpace.isEmpty()) {
                throw new IllegalArgumentException("Measurement space is required.");
            }
        }
    }

    public static final String GENERATED_ID_PREFIX = "probe-";
    private final String id;
    private final ProbeDetectorType detectorType;
    private final Vector3i nodeIndex;
    private final Vector4f displayColor;
    private final Placement placement;
    private final List<InterpolationSource> interpolationSources;

    public ProbeDefinition(String id, Vector3i nodeIndex, Vector4f displayColor) {
        this(id, nodeIndex, displayColor, null);
    }

    public ProbeDefinition(String id, Vector3i nodeIndex, Vector4f displayColor,
                           Placement placement) {
        this(id, ProbeDetectorType.RAW_NODE, nodeIndex, displayColor, placement, List.of());
    }

    private ProbeDefinition(String id, ProbeDetectorType detectorType, Vector3i nodeIndex,
                            Vector4f displayColor, Placement placement,
                            List<InterpolationSource> interpolationSources) {
        this.id = id;
        this.detectorType = detectorType;
        this.nodeIndex = nodeIndex == null ? null : new Vector3i(nodeIndex);
        this.displayColor = new Vector4f(displayColor);
        this.placement = placement;
        this.interpolationSources = List.copyOf(interpolationSources);
        validateBinding();
    }

    public static ProbeDefinition interpolated(
            String id, ProbeDetectorType detectorType, Vector4f displayColor,
            Placement placement, List<InterpolationSource> interpolationSources) {
        return new ProbeDefinition(id, detectorType, null, displayColor,
                                   placement, interpolationSources);
    }

    public ProbeDefinition withId(String newId) {
        return new ProbeDefinition(newId, detectorType, nodeIndex, displayColor,
                                   placement, interpolationSources);
    }

    public ProbeDefinition withColor(Vector4f newColor) {
        return new ProbeDefinition(id, detectorType, nodeIndex, newColor,
                                   placement, interpolationSources);
    }

    /** Identifies definitions created by the Add/Edit Passive Probes workflow. */
    public boolean isGenerated() {
        return id.startsWith(GENERATED_ID_PREFIX);
    }

    private void validateBinding() {
        if (detectorType == null) throw new IllegalArgumentException("Probe detector type is required.");
        if (detectorType == ProbeDetectorType.RAW_NODE) {
            if (nodeIndex == null) throw new IllegalArgumentException("Raw probe node index is required.");
            if (!interpolationSources.isEmpty()) {
                throw new IllegalArgumentException("Raw probe cannot have interpolation sources.");
            }
            return;
        }
        if (nodeIndex != null) throw new IllegalArgumentException("Interpolated probe cannot reference one raw node.");
        int requiredSources = detectorType == ProbeDetectorType.INTERPOLATED_2D ? 4 : 8;
        if (interpolationSources.size() != requiredSources) {
            throw new IllegalArgumentException("Interpolated probe requires " + requiredSources + " sources.");
        }
        if (placement == null || placement.targets().size() != 1) {
            throw new IllegalArgumentException("Interpolated probe requires exactly one requested target.");
        }
        double weightSum = interpolationSources.stream()
                .mapToDouble(InterpolationSource::weight).sum();
        if (Math.abs(weightSum - 1.0) > 1.0e-9) {
            throw new IllegalArgumentException("Interpolation weights must sum to one.");
        }
    }
}
