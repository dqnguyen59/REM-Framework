package remframework.geometry.discovery.viewport;

import org.joml.Vector3d;

/** Immutable label anchor in display coordinates; text is the canonical vertex index. */
public record CanonicalNodeLabel(int canonicalVertexIndex, String text, Vector3d position) {
    public CanonicalNodeLabel {
        position = new Vector3d(position);
        if (!text.equals(Integer.toString(canonicalVertexIndex)))
            throw new IllegalArgumentException("Label text must be the canonical vertex index.");
    }
}
