package remframework.opengl.model;

import lombok.Getter;
import org.joml.Vector3d;
import org.joml.Vector4f;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.Node;
import remframework.settings.Project;

import java.util.List;

/** Passive observer whose live values are a bilinear or trilinear estimate. */
@Getter
public final class InterpolatedProbe implements PassiveObserver {
    private static final Vector4f SELECTED_COLOR = new Vector4f(1.0f, 0.35f, 0.0f, 1.0f);

    private final String id;
    private final ProbeDetectorType detectorType;
    private final ProbeDefinition definition;
    private final Entity marker;
    private final Vector4f displayColor;
    private final ProbeDefinition.Placement placement;
    private final List<Node> sourceNodes;
    private boolean selected;
    private boolean hovered;

    public InterpolatedProbe(ProbeDefinition definition, List<Node> sourceNodes, Entity marker) {
        if (definition.getDetectorType() == ProbeDetectorType.RAW_NODE) {
            throw new IllegalArgumentException("Interpolated probe requires an interpolated detector type.");
        }
        if (sourceNodes.size() != definition.getInterpolationSources().size()) {
            throw new IllegalArgumentException("Interpolated probe source-node count does not match its definition.");
        }
        for (int index = 0; index < sourceNodes.size(); index++) {
            if (sourceNodes.get(index) == null || !sourceNodes.get(index).getIndex().equals(
                    definition.getInterpolationSources().get(index).nodeIndex())) {
                throw new IllegalArgumentException(
                        "Interpolated probe source nodes must match the definition stencil.");
            }
        }
        this.id = definition.getId();
        this.detectorType = definition.getDetectorType();
        this.definition = definition;
        this.marker = marker;
        this.displayColor = new Vector4f(definition.getDisplayColor());
        this.placement = definition.getPlacement();
        this.sourceNodes = List.copyOf(sourceNodes);
    }

    /** Returns a newly allocated weighted estimate from the current committed source values. */
    @Override
    public Observation getObservation() {
        return ProbeInterpolation.interpolate(sourceNodes, definition.getInterpolationSources());
    }

    @Override
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    /** Places the marker at the weighted live physical position of this interpolated estimate. */
    @Override
    public void updateMarker() {
        Vector3d position = getObservation().position();
        marker.setPosition(position);
        marker.setCustomColor(selected ? SELECTED_COLOR
                                       : hovered ? new Vector4f(Project.getInstance().settings.selectedColor)
                                                 : displayColor);
        marker.updateMatrix();
    }
}
