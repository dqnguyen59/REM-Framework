package remframework.opengl.model;

import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.EntityType;
import remframework.settings.Project;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityTransformMatrixTest {

    @Test
    void directParentCompositionMatchesPreviousLocalMatrixMultiplication() {
        float originalScale = Project.getInstance().settings.getScale();
        try {
            Project.getInstance().settings.setScale(1.25f);
            Entity parent = new Entity(null, null, new Vector3d(3.0, -2.0, 5.0),
                    new Vector3d(20.0, -15.0, 35.0), 1.5, EntityType.ENTITY);
            Entity child = new Entity(parent, null, new Vector3d(-4.0, 6.0, 2.0),
                    new Vector3d(-10.0, 25.0, 5.0), 0.75, EntityType.ENTITY);

            Matrix4d parentExpected = localMatrix(parent, 1.5);
            Matrix4d childLocal = localMatrix(child, 0.75);
            Matrix4d expected = parentExpected.mul(childLocal, new Matrix4d());

            assertTrue(expected.equals(child.getTransformMatrix(), 1.0e-12));
        } finally {
            Project.getInstance().settings.setScale(originalScale);
        }
    }

    private static Matrix4d localMatrix(Entity entity, double entityScale) {
        Vector3d rotation = entity.getRotation();
        return new Matrix4d()
                .identity()
                .translate(entity.getPosition())
                .setRotationXYZ(Math.toRadians(rotation.x), Math.toRadians(rotation.y),
                        Math.toRadians(rotation.z))
                .scale(Project.getInstance().settings.getScale())
                .scale(entityScale);
    }
}
