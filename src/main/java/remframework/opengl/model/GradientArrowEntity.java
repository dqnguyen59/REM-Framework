package remframework.opengl.model;

import org.joml.Matrix4d;
import org.joml.Vector3d;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.EntityType;

/** Renderer-only arrow entity whose direction is independent of generic Entity Euler rotation. */
public final class GradientArrowEntity extends Entity {
    private static final Vector3d WORLD_UP = new Vector3d(0, 1, 0);
    private static final Vector3d FALLBACK_UP = new Vector3d(1, 0, 0);
    private static final double PARALLEL_UP_LIMIT = 0.999;

    private final Vector3d direction = new Vector3d();

    public GradientArrowEntity(Entity parent, ObjFileModel model, Vector3d position, double scale) {
        super(parent, model, position, new Vector3d(), scale, EntityType.ENTITY);
    }

    public Vector3d getDirection() {
        return new Vector3d(direction);
    }

    public void setDirection(Vector3d direction) {
        this.direction.set(direction);
    }

    @Override
    public void updateMatrix() {
        if (direction == null) {
            super.updateMatrix();
            return;
        }
        updatePosition();
        if (direction.lengthSquared() <= 1.0e-24) {
            setVisible(false);
            return;
        }

        Vector3d unitDirection = new Vector3d(direction).normalize();
        // JOML's rotateTowards convention maps the arrow mesh's local +Z tip
        // onto this look direction in the renderer's matrix convention.
        Vector3d lookDirection = new Vector3d(unitDirection);
        Vector3d up = Math.abs(unitDirection.dot(WORLD_UP)) >= PARALLEL_UP_LIMIT
                ? new Vector3d(FALLBACK_UP)
                : new Vector3d(WORLD_UP);
        Matrix4d look = new Matrix4d().rotateTowards(lookDirection, up);

        Matrix4d matrix = getTransformMatrix().identity().translate(position).mul(look).scale(scale);
        if (parent != null) parent.getTransformMatrix().mul(matrix, matrix);
        transformMatrix4fList[swapBufIndex].set(matrix);

        matrix = getTransformMatrix().identity().translate(position).mul(look)
                .scale(settings.scale).scale(scale);
        if (parent != null) parent.getTransformMatrix().mul(matrix, matrix);
        transformMatrix4fList[swapBufIndex].set(matrix);
        setColor(FIXED_COLOR);
    }
}
