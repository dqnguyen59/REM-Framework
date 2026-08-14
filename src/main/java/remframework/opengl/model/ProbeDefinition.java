package remframework.opengl.model;

import lombok.Getter;
import org.joml.Vector3i;
import org.joml.Vector4f;

/** In-memory definition of a passive probe that references one lattice node. */
@Getter
public final class ProbeDefinition {
    /** Geometric placement diagnostics retained with a generated passive probe. */
    public record Placement(int shell, int ray, String rayPlane, int requestedRadius,
                            double realizedRadius, int graphDistance) {
    }

    public static final String GENERATED_ID_PREFIX = "probe-";
    private final String id;
    private final Vector3i nodeIndex;
    private final Vector4f displayColor;
    private final Placement placement;

    public ProbeDefinition(String id, Vector3i nodeIndex, Vector4f displayColor) {
        this(id, nodeIndex, displayColor, null);
    }

    public ProbeDefinition(String id, Vector3i nodeIndex, Vector4f displayColor,
                           Placement placement) {
        this.id = id;
        this.nodeIndex = new Vector3i(nodeIndex);
        this.displayColor = new Vector4f(displayColor);
        this.placement = placement;
    }

    public ProbeDefinition withId(String newId) {
        return new ProbeDefinition(newId, nodeIndex, displayColor, placement);
    }

    public ProbeDefinition withColor(Vector4f newColor) {
        return new ProbeDefinition(id, nodeIndex, newColor, placement);
    }

    /** Identifies definitions created by the Add/Edit Passive Probes workflow. */
    public boolean isGenerated() {
        return id.startsWith(GENERATED_ID_PREFIX);
    }
}
