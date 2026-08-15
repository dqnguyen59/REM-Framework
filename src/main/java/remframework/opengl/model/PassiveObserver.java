package remframework.opengl.model;

import org.joml.Vector3d;
import remframework.opengl.model.entity.Entity;

/** Shared render and read-only observation contract for passive probes. */
public interface PassiveObserver {
    record Observation(Vector3d position, Vector3d velocity, Vector3d acceleration,
                       Vector3d gradientImbalance) {
        public Observation {
            position = new Vector3d(position);
            velocity = new Vector3d(velocity);
            acceleration = new Vector3d(acceleration);
            gradientImbalance = new Vector3d(gradientImbalance);
        }

        @Override
        public Vector3d position() {
            return new Vector3d(position);
        }

        @Override
        public Vector3d velocity() {
            return new Vector3d(velocity);
        }

        @Override
        public Vector3d acceleration() {
            return new Vector3d(acceleration);
        }

        @Override
        public Vector3d gradientImbalance() {
            return new Vector3d(gradientImbalance);
        }
    }

    String getId();

    ProbeDetectorType getDetectorType();

    ProbeDefinition getDefinition();

    Entity getMarker();

    Observation getObservation();

    void setSelected(boolean selected);

    void setHovered(boolean hovered);

    void updateMarker();
}
