package remframework.opengl.model;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.Node;
import remframework.settings.Project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneCameraOrbitTest {
    private final Project project = Project.getInstance();
    private final Scene scene = new Scene();

    @AfterEach
    void leaveInspection() {
        scene.exitNeighborhoodInspection();
    }

    @Test
    void normalOrbitUsesBasePositionAndPreservesDistanceAndBaseRotation() {
        Entity base = new Entity(null, null, new Vector3d(10, 2, -4), new Vector3d(), 1.0,
                remframework.opengl.model.entity.EntityType.ENTITY);
        scene.getLattice().setBaseEntity(base);
        project.camera.setPosition(10, 2, 20);
        project.camera.setRotation(0, 0, 0);
        Vector3d beforeRotation = new Vector3d(base.getRotation());
        double distance = new Vector3d(project.camera.getPosition())
                .distance(scene.getActiveCameraTarget());

        scene.orbitCameraAroundTarget(0, 20, 0);

        assertEquals(new Vector3d(10, 2, -4), scene.getActiveCameraTarget());
        assertEquals(distance, new Vector3d(project.camera.getPosition())
                .distance(scene.getActiveCameraTarget()), 1.0e-5);
        assertEquals(beforeRotation, new Vector3d(base.getRotation()));
        assertForwardPointsAtTarget();
    }

    @Test
    void inspectionUsesLiveMarkerAndExitRestoresCamera() {
        Node node = new Node(null, null, new Vector3d(4, 5, 6), new Vector3d(), 1.0);
        node.setIndex(new Vector3i(1, 1, 1));
        Entity marker = new Entity(null, null, new Vector3d(4, 5, 6), new Vector3d(), 1.0,
                remframework.opengl.model.entity.EntityType.ENTITY);
        ProbeDefinition definition = new ProbeDefinition("orbit-probe", node.getIndex(),
                new Vector4f(1, 0.5f, 0, 1));
        Probe probe = new Probe(definition, node, marker);
        scene.selectProbe(probe);

        project.camera.setPosition(30, 31, 32);
        project.camera.setRotation(4, 5, 6);
        Vector3d oldPosition = new Vector3d(project.camera.getPosition());
        Vector3d oldRotation = new Vector3d(project.camera.getRotation());
        scene.enterNeighborhoodInspection();
        scene.updateCameraTransition(0.8);

        marker.setPosition(20, 21, 22);
        marker.updateMatrix();
        scene.updateInspectionCameraTarget();
        assertEquals(new Vector3d(20, 21, 22), scene.getActiveCameraTarget());
        scene.orbitCameraAroundTarget(10, 15, 0);
        assertForwardPointsAtTarget();

        scene.exitNeighborhoodInspection();
        scene.updateCameraTransition(0.8);
        assertEquals(oldPosition, new Vector3d(project.camera.getPosition()));
        assertEquals(oldRotation, new Vector3d(project.camera.getRotation()));
        assertNotEquals(new Vector3d(20, 21, 22), scene.getActiveCameraTarget());
    }

    @Test
    void normalZoomMovesTowardFixedBaseTarget() {
        Entity base = new Entity(null, null, new Vector3d(0, 0, 0), new Vector3d(), 1.0,
                remframework.opengl.model.entity.EntityType.ENTITY);
        scene.getLattice().setBaseEntity(base);
        project.camera.setPosition(0, 0, 20);
        project.camera.setRotation(0, 0, 0);
        Vector3d target = scene.getActiveCameraTarget();
        scene.zoomCameraAroundTarget(0.5 / 12.0);

        assertEquals(target, scene.getActiveCameraTarget());
        assertEquals(19.5, new Vector3d(project.camera.getPosition()).distance(target), 1.0e-6);
    }

    @Test
    void inspectionZoomUsesProbeTargetAndClampsDistance() {
        Node node = new Node(null, null, new Vector3d(5, 6, 7), new Vector3d(), 1.0);
        node.setIndex(new Vector3i(2, 2, 2));
        Entity marker = new Entity(null, null, new Vector3d(5, 6, 7), new Vector3d(), 1.0,
                remframework.opengl.model.entity.EntityType.ENTITY);
        Probe probe = new Probe(new ProbeDefinition("zoom-probe", node.getIndex(),
                new Vector4f(1, 0.5f, 0, 1)), node, marker);
        scene.selectProbe(probe);
        project.camera.setPosition(5, 6, 31);
        double initialDistance = new Vector3d(project.camera.getPosition())
                .distance(scene.getActiveCameraTarget());
        scene.enterNeighborhoodInspection();
        scene.updateCameraTransition(0.8);
        Vector3d target = scene.getActiveCameraTarget();

        scene.zoomCameraAroundTarget(1.0 / 12.0);
        assertEquals(target, scene.getActiveCameraTarget());
        assertEquals(initialDistance - 1.0,
                new Vector3d(project.camera.getPosition()).distance(target), 1.0e-4);

        scene.zoomCameraAroundTarget(1000.0);
        assertEquals(2.0, new Vector3d(project.camera.getPosition()).distance(target), 1.0e-6);
        scene.zoomCameraAroundTarget(-100000.0);
        assertEquals(1000.0, new Vector3d(project.camera.getPosition()).distance(target), 1.0e-3);
    }

    @Test
    void inspectionEntryUsesEasedCameraOnlyTransition() {
        Entity base = new Entity(null, null, new Vector3d(), new Vector3d(), 1.0,
                remframework.opengl.model.entity.EntityType.ENTITY);
        scene.getLattice().setBaseEntity(base);
        Node node = new Node(null, null, new Vector3d(0, 0, 20), new Vector3d(), 1.0);
        node.setIndex(new Vector3i(3, 3, 3));
        Entity marker = new Entity(null, null, new Vector3d(0, 0, 20), new Vector3d(), 1.0,
                remframework.opengl.model.entity.EntityType.ENTITY);
        Probe probe = new Probe(new ProbeDefinition("transition-probe", node.getIndex(),
                new Vector4f(1, 0.5f, 0, 1)), node, marker);
        scene.selectProbe(probe);
        project.camera.setPosition(0, 0, 100);
        project.camera.setRotation(0, 0, 0);
        Vector3d oldPosition = new Vector3d(project.camera.getPosition());
        scene.enterNeighborhoodInspection();
        assertEquals(oldPosition, new Vector3d(project.camera.getPosition()));
        assertFalse(scene.isNeighborhoodInspectionActive());

        double previousZ = oldPosition.z;
        for (int i = 0; i < 3; i++) {
            scene.updateCameraTransition(0.2);
            double currentZ = project.camera.getPosition().z;
            org.junit.jupiter.api.Assertions.assertTrue(currentZ >= previousZ);
            previousZ = currentZ;
        }
        assertFalse(scene.isNeighborhoodInspectionActive());
        scene.updateCameraTransition(0.2);
        assertTrue(scene.isNeighborhoodInspectionActive());
        assertEquals(new Vector3d(0, 0, 20), scene.getActiveCameraTarget());
    }

    private void assertForwardPointsAtTarget() {
        Vector3d target = scene.getActiveCameraTarget();
        Vector3d camera = new Vector3d(project.camera.getPosition());
        Vector3d expected = target.sub(camera).normalize();
        double pitch = Math.toRadians(project.camera.getRotation().x);
        double yaw = Math.toRadians(project.camera.getRotation().y);
        Vector3d forward = new Vector3d(
                Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                -Math.cos(yaw) * Math.cos(pitch)).normalize();
        assertEquals(expected.x, forward.x, 1.0e-5);
        assertEquals(expected.y, forward.y, 1.0e-5);
        assertEquals(expected.z, forward.z, 1.0e-5);
    }
}
