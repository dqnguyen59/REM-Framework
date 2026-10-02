package remframework.opengl.view.renderer;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.EntityType;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RendererOverlaySynchronizationTest {

    @Test
    void probeSynchronizationDoesNotRequireOrMutateTheLatticeRenderer() {
        Renderer renderer = new Renderer();
        Entity first = entity();
        Entity second = entity();

        assertDoesNotThrow(() -> renderer.syncProbeEntities(List.of(first, second)));

        assertEquals(2, renderer.getProbeEntities().size());
        assertSame(first, renderer.getProbeEntities().get(0));
        assertSame(second, renderer.getProbeEntities().get(1));
    }

    @Test
    void stableOverlayMembershipIsNotRewrittenEveryFrame() {
        Renderer renderer = new Renderer();
        Entity first = entity();
        Entity second = entity();
        List<Entity> requested = List.of(first, second);
        renderer.syncGradientArrowEntities(requested);
        Iterator<Entity> existingIteration = renderer.getGradientArrowEntities().iterator();
        assertSame(first, existingIteration.next());

        renderer.syncGradientArrowEntities(requested);

        assertDoesNotThrow(() -> assertSame(second, existingIteration.next()));
    }

    private static Entity entity() {
        return new Entity(null, null, new Vector3d(), new Vector3d(), 1.0, EntityType.ENTITY);
    }
}
