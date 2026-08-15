package remframework.opengl.model;

import lombok.Getter;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.Node;
import remframework.settings.Project;

/** A passive visual and observational reference to a lattice node. */
@Getter
public final class Probe implements PassiveObserver {

    private static final Vector4f SELECTED_COLOR = new Vector4f(1.0f, 0.35f, 0.0f, 1.0f);

    private final String id;
    private final ProbeDetectorType detectorType = ProbeDetectorType.RAW_NODE;
    private final ProbeDefinition definition;
    private final Node node;
    private final Entity marker;
    private final Vector4f displayColor;
    private final ProbeDefinition.Placement placement;
    private boolean selected;
    private boolean hovered;

    public Probe(String id, Node node, Entity marker, Vector4f displayColor,
                 ProbeDefinition.Placement placement) {
        this(new ProbeDefinition(id, node.getIndex(), displayColor, placement), node, marker);
    }

    public Probe(ProbeDefinition definition, Node node, Entity marker) {
        if (definition.getDetectorType() != ProbeDetectorType.RAW_NODE) {
            throw new IllegalArgumentException("Raw probe requires RAW_NODE detector type.");
        }
        this.id = definition.getId();
        this.definition = definition;
        this.node = node;
        this.marker = marker;
        this.displayColor = new Vector4f(definition.getDisplayColor());
        this.placement = definition.getPlacement();
    }

    /** Returns the live coordinate of the referenced node. */
    public Vector3i getNodeCoordinate() {
        return new Vector3i(node.getIndex());
    }

    /** Returns the live position of the referenced node for rendering and observation. */
    public Vector3d getNodePosition() {
        return new Vector3d(node.getPosition());
    }

    /** Returns copies of the directly referenced node's current committed values. */
    @Override
    public Observation getObservation() {
        return new Observation(node.getPosition(), node.getVelocity(), node.getAcceleration(),
                               node.getGradientImbalance());
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    /** Synchronizes only the render marker from the referenced node. */
    public void updateMarker() {
        marker.setPosition(getNodePosition());
        marker.setCustomColor(selected ? SELECTED_COLOR
                                       : hovered ? new Vector4f(Project.getInstance().settings.selectedColor)
                                                 : displayColor);
        marker.updateMatrix();
    }
}
