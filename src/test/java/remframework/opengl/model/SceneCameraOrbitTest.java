package remframework.opengl.model;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.Node;
import remframework.opengl.helpers.Neighbor;
import remframework.settings.AppSettings;
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
        scene.enterNeighborhoodInspection();
        scene.updateCameraTransition(0.8);
        Vector3d target = scene.getActiveCameraTarget();
        double framedDistance = new Vector3d(project.camera.getPosition()).distance(target);

        scene.zoomCameraAroundTarget(1.0 / 12.0);
        assertEquals(target, scene.getActiveCameraTarget());
        assertEquals(framedDistance - 1.0,
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
            org.junit.jupiter.api.Assertions.assertTrue(currentZ <= previousZ);
            previousZ = currentZ;
        }
        assertFalse(scene.isNeighborhoodInspectionActive());
        scene.updateCameraTransition(0.2);
        assertTrue(scene.isNeighborhoodInspectionActive());
        assertEquals(new Vector3d(0, 0, 20), scene.getActiveCameraTarget());
    }

    @Test
    void inspectionFramingDistanceGrowsWithNeighborhoodExtent() {
        Node source = new Node(null, null, new Vector3d(0, 0, 0), new Vector3d(), 1.0);
        source.setIndex(new Vector3i(4, 4, 4));
        Node neighbor = new Node(null, null, new Vector3d(1, 0, 0), new Vector3d(), 1.0);
        neighbor.setIndex(new Vector3i(5, 4, 4));
        source.setNeighbor(Neighbor.RIGHT, neighbor);
        Entity marker = new Entity(null, null, new Vector3d(0, 0, 0), new Vector3d(), 1.0,
                remframework.opengl.model.entity.EntityType.ENTITY);
        Probe probe = new Probe(new ProbeDefinition("framing-probe", source.getIndex(),
                new Vector4f(1, 0.5f, 0, 1)), source, marker);

        double compactDistance = scene.calculateInspectionFraming(probe).cameraDistance();
        neighbor.setPosition(new Vector3d(100, 0, 0));
        neighbor.updateMatrix();
        double expandedDistance = scene.calculateInspectionFraming(probe).cameraDistance();

        assertTrue(expandedDistance > compactDistance);
    }

    @Test
    void inspectionFramingTargetsMarkerAndFitsAllDirectPoints() {
        Node source = new Node(null, null, new Vector3d(), new Vector3d(), 1.0);
        source.setIndex(new Vector3i(6, 6, 6));
        Vector3d[] positions = {
                new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1),
                new Vector3d(-1, 0, 0), new Vector3d(0, -1, 0), new Vector3d(0, 0, -1)
        };
        Neighbor[] directions = Neighbor.values();
        for (int index = 0; index < directions.length; index++) {
            Node neighbor = new Node(null, null, positions[index], new Vector3d(), 1.0);
            neighbor.setIndex(new Vector3i(6, 6, 6).add(
                    directions[index] == Neighbor.RIGHT ? 1 : directions[index] == Neighbor.LEFT ? -1 : 0,
                    directions[index] == Neighbor.TOP ? 1 : directions[index] == Neighbor.BOTTOM ? -1 : 0,
                    directions[index] == Neighbor.FRONT ? 1 : directions[index] == Neighbor.BACK ? -1 : 0));
            source.setNeighbor(directions[index], neighbor);
        }
        Entity marker = new Entity(null, null, new Vector3d(), new Vector3d(), 1.0,
                remframework.opengl.model.entity.EntityType.ENTITY);
        Probe probe = new Probe(new ProbeDefinition("fit-probe", source.getIndex(),
                new Vector4f(1, 0.5f, 0, 1)), source, marker);
        scene.selectProbe(probe);
        Entity base = new Entity(null, null, new Vector3d(), new Vector3d(), 1.0,
                remframework.opengl.model.entity.EntityType.ENTITY);
        scene.getLattice().setBaseEntity(base);
        project.camera.setPosition(0, 0, 100);

        scene.enterNeighborhoodInspection();
        scene.updateCameraTransition(0.8);

        Vector3d target = new Vector3d(project.camera.getOrbitTarget());
        assertEquals(new Vector3d(marker.getPosition()), target);
        Vector3d camera = new Vector3d(project.camera.getPosition());
        Vector3d forward = target.sub(camera).normalize();
        double halfVerticalFov = Math.toRadians(project.camera.getFieldOfView()) * 0.5;
        double halfHorizontalFov = Math.atan(Math.tan(halfVerticalFov)
                * ((double) AppSettings.getInstance().getWindowWidth()
                / AppSettings.getInstance().getWindowHeight()));
        for (Vector3d point : positions) {
            Vector3d toPoint = new Vector3d(point).sub(camera);
            double angle = Math.acos(Math.clamp(forward.dot(new Vector3d(toPoint).normalize()), -1.0, 1.0));
            assertTrue(angle <= Math.min(halfVerticalFov, halfHorizontalFov));
        }
    }

    @Test
    void inspectionExitRestoresCompleteNavigationSnapshotImmediatelyAndIdempotently() {
        Node node = new Node(null, null, new Vector3d(2, 3, 4), new Vector3d(), 1.0);
        node.setIndex(new Vector3i(9, 9, 9));
        Entity marker = new Entity(null, null, new Vector3d(2, 3, 4), new Vector3d(), 1.0,
                remframework.opengl.model.entity.EntityType.ENTITY);
        Probe probe = new Probe(new ProbeDefinition("restore-probe", node.getIndex(),
                new Vector4f(1, 0.5f, 0, 1)), node, marker);
        scene.selectProbe(probe);

        Vector3d originalPosition = new Vector3d(20, 30, 40);
        Vector3d originalTarget = new Vector3d(3, 4, 5);
        Vector3d originalRotation = new Vector3d(17, 231, -12);
        project.camera.setOrbitState(originalPosition, originalTarget);
        project.camera.setRotation((float) originalRotation.x, (float) originalRotation.y,
                                   (float) originalRotation.z);
        project.camera.setFieldOfView(57.0f);
        project.camera.setZ_near(0.2f);
        project.camera.setZ_far(4321.0f);
        double originalDistance = project.camera.getOrbitDistance();

        scene.enterNeighborhoodInspection();
        for (int index = 0; index < 5; index++) scene.orbitCameraAroundTarget(3, 7, 2);
        scene.exitNeighborhoodInspection();

        assertTrue(scene.isNeighborhoodInspectionTransitionActive());
        assertTrue(new Vector3d(project.camera.getPosition()).distance(originalPosition) > 1.0e-4);
        assertTrue(new Vector3d(project.camera.getRotation()).distance(originalRotation) > 1.0e-4);
        scene.updateCameraTransition(0.4);
        assertTrue(new Vector3d(project.camera.getPosition()).distance(originalPosition) > 1.0e-4);
        assertTrue(new Vector3d(project.camera.getRotation()).distance(originalRotation) > 1.0e-4);
        scene.updateCameraTransition(0.8);
        assertNavigationEquals(originalPosition, originalRotation, originalTarget, originalDistance,
                               57.0f, 0.2f, 4321.0f);
        scene.updateCameraTransition(10.0);
        assertNavigationEquals(originalPosition, originalRotation, originalTarget, originalDistance,
                               57.0f, 0.2f, 4321.0f);
        scene.exitNeighborhoodInspection();
        assertNavigationEquals(originalPosition, originalRotation, originalTarget, originalDistance,
                               57.0f, 0.2f, 4321.0f);

        for (int cycle = 0; cycle < 3; cycle++) {
            scene.enterNeighborhoodInspection();
            scene.updateCameraTransition(0.8);
            scene.exitNeighborhoodInspection();
            scene.updateCameraTransition(0.8);
            assertNavigationEquals(originalPosition, originalRotation, originalTarget, originalDistance,
                                   57.0f, 0.2f, 4321.0f);
        }
    }

    private void assertNavigationEquals(Vector3d position, Vector3d rotation, Vector3d target,
                                         double distance, float fov, float near, float far) {
        assertVectorEquals(position, new Vector3d(project.camera.getPosition()));
        assertVectorEquals(rotation, new Vector3d(project.camera.getRotation()));
        assertVectorEquals(target, project.camera.getOrbitTarget());
        assertEquals(distance, project.camera.getOrbitDistance(), 1.0e-6);
        assertEquals(fov, project.camera.getFieldOfView(), 1.0e-6);
        assertEquals(near, project.camera.getZ_near(), 1.0e-6);
        assertEquals(far, project.camera.getZ_far(), 1.0e-6);
    }

    private void assertVectorEquals(Vector3d expected, Vector3d actual) {
        assertEquals(expected.x, actual.x, 1.0e-6);
        assertEquals(expected.y, actual.y, 1.0e-6);
        assertEquals(expected.z, actual.z, 1.0e-6);
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
