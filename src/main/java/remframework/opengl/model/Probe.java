package remframework.opengl.model;

import lombok.Getter;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.Node;

/** A passive visual and observational reference to a lattice node. */
@Getter
public final class Probe {

    private static final Vector4f SELECTED_COLOR = new Vector4f(1.0f, 0.35f, 0.0f, 1.0f);

    private final String id;
    private final Node node;
    private final Entity marker;
    private final Vector4f displayColor;
    private final ProbeDefinition.Placement placement;
    private boolean selected;

    public Probe(String id, Node node, Entity marker, Vector4f displayColor,
                 ProbeDefinition.Placement placement) {
        this.id = id;
        this.node = node;
        this.marker = marker;
        this.displayColor = new Vector4f(displayColor);
        this.placement = placement;
    }

    /** Returns the live coordinate of the referenced node. */
    public Vector3i getNodeCoordinate() {
        return new Vector3i(node.getIndex());
    }

    /** Returns the live position of the referenced node for rendering and observation. */
    public Vector3d getNodePosition() {
        return new Vector3d(node.getPosition());
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    /** Synchronizes only the render marker from the referenced node. */
    public void updateMarker() {
        marker.setPosition(getNodePosition());
        marker.setCustomColor(selected ? SELECTED_COLOR : displayColor);
        marker.updateMatrix();
    }
}
