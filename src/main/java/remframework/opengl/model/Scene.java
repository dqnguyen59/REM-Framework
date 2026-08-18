/*
 * Copyright (C) 2023  Duy Quoc Nguyen <d.q.nguyen@smartblackbox.org> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 01/01/2023
 */
package remframework.opengl.model;

import lombok.Getter;
import lombok.Setter;
import org.joml.*;
import remframework.helpers.Constants;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.EntityType;
import remframework.opengl.model.entity.Node;
import remframework.opengl.controller.TransportSystem;
import remframework.opengl.model.lights.DirectionalLight;
import remframework.opengl.helpers.OBJFormatLoader;
import remframework.settings.AppSettings;
import remframework.settings.Project;
import remframework.settings.Settings;
import remframework.settings.SlitWallSettings;

import java.lang.Math;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class Scene {

    private AppSettings appSettings = AppSettings.getInstance();

    private Project          project  = Project.getInstance();
    private Settings         settings = project.settings;
    private SlitWallSettings slitWall = project.slitWall;
    private RemModel         REMModel = project.getRemModel();

    private List<Terrain> terrains;

    private Lattice lattice;

    private ArrayList<Vector3i> wallNodeIndexList = new ArrayList<>();
    private ArrayList<Node>     hiLightedNodes    = new ArrayList<>();
    private ArrayList<PassiveObserver> probes        = new ArrayList<>();
    private ArrayList<PassiveObserver> previewProbes = new ArrayList<>();
    private boolean             replacementProbePreviewActive;
    private PassiveObserver     selectedProbe;
    private PassiveObserver     hoveredProbe;
    private final ProbeComparison comparison = new ProbeComparison();
    private final Map<PassiveObserver, Entity> gradientArrowEntities = new IdentityHashMap<>();
    private ObjFileModel gradientArrowModel;
    private boolean showGradientArrows = true;
    private double gradientArrowScaleExponent = 70.0;
    private boolean showCalibrationArrow;
    private CalibrationDirection calibrationDirection = CalibrationDirection.NEGATIVE_X;
    private Entity calibrationArrow;
    private Node                firstHiLightedNode;

    private boolean neighborhoodInspectionActive;
    private PassiveObserver inspectedProbe;
    private Entity inspectionProbeMarker;
    private InspectionNavigationSnapshot savedInspectionNavigation;
    private double inspectionCameraDistance;
    private Vector3d inspectionFrameTarget = new Vector3d();
    private Vector3d previousInspectionMarkerPosition = new Vector3d();
    private double inspectionFrameDistance;
    private boolean inspectionTransitionActive;
    private boolean inspectionTransitionEntering;
    private double inspectionTransitionProgress;
    private final Vector3d transitionStartPosition = new Vector3d();
    private final Vector3d transitionStartTarget = new Vector3d();
    private final Vector3d transitionEndPosition = new Vector3d();
    private final Vector3d transitionEndTarget = new Vector3d();
    private final Vector3d transitionStartRotation = new Vector3d();
    private final Vector3d transitionEndRotation = new Vector3d();
    private double transitionStartDistance;
    private double transitionEndDistance;
    private float transitionStartFov;
    private float transitionEndFov;
    private float transitionStartNear;
    private float transitionEndNear;
    private float transitionStartFar;
    private float transitionEndFar;
    private static final double INSPECTION_TRANSITION_SECONDS = 0.8;
    private static final double INSPECTION_MIN_RADIUS = 2.0;
    private static final double INSPECTION_MARKER_RADIUS = 1.0;
    private static final double INSPECTION_SAFETY_MARGIN = 1.20;
    private static final double INSPECTION_MIN_DISTANCE = 6.0;

    private record CameraState(Vector3f position, Vector3f rotation, float fov, float near, float far,
                               Vector3d orbitTarget, double orbitDistance) {
        static CameraState capture(Camera camera) {
            return new CameraState(new Vector3f(camera.getPosition()), new Vector3f(camera.getRotation()),
                                   camera.getFieldOfView(), camera.getZ_near(), camera.getZ_far(),
                                   camera.getOrbitTarget(), camera.getOrbitDistance());
        }
        CameraState {
            position = new Vector3f(position);
            rotation = new Vector3f(rotation);
            orbitTarget = new Vector3d(orbitTarget);
        }
        void restore(Camera camera) {
            camera.setFieldOfView(fov);
            camera.setZ_near(near);
            camera.setZ_far(far);
            camera.setOrbitState(new Vector3d(position), orbitTarget, orbitDistance);
            camera.setRotation(rotation.x, rotation.y, rotation.z);
        }
    }

    /** Immutable inspection-entry snapshot, including any pre-existing camera flight state. */
    private record InspectionNavigationSnapshot(
            CameraState camera,
            boolean transitionActive,
            boolean transitionEntering,
            double transitionProgress,
            Vector3d transitionStartPosition,
            Vector3d transitionStartTarget,
            Vector3d transitionEndPosition,
            Vector3d transitionEndTarget) {
        InspectionNavigationSnapshot {
            transitionStartPosition = new Vector3d(transitionStartPosition);
            transitionStartTarget = new Vector3d(transitionStartTarget);
            transitionEndPosition = new Vector3d(transitionEndPosition);
            transitionEndTarget = new Vector3d(transitionEndTarget);
        }

        static InspectionNavigationSnapshot capture(Scene scene) {
            return new InspectionNavigationSnapshot(
                    CameraState.capture(scene.project.camera),
                    scene.inspectionTransitionActive,
                    scene.inspectionTransitionEntering,
                    scene.inspectionTransitionProgress,
                    scene.transitionStartPosition,
                    scene.transitionStartTarget,
                    scene.transitionEndPosition,
                    scene.transitionEndTarget);
        }

        void restore(Scene scene) {
            camera.restore(scene.project.camera);
            scene.inspectionTransitionActive = transitionActive;
            scene.inspectionTransitionEntering = transitionEntering;
            scene.inspectionTransitionProgress = transitionProgress;
            scene.transitionStartPosition.set(transitionStartPosition);
            scene.transitionStartTarget.set(transitionStartTarget);
            scene.transitionEndPosition.set(transitionEndPosition);
            scene.transitionEndTarget.set(transitionEndTarget);
        }
    }

    private DirectionalLight directionalLight;

    private boolean animated;

    protected double animateInc;

    private int animateDirection = -1;

    public String status = "";

    List<Thread> threads = new ArrayList<>();

    public Scene() {
        System.out.println("Scene.Scene()");
        terrains = new ArrayList<>();
        lattice  = Lattice.getInstance();
    }

    public void build() {
        System.out.println("Scene.build()");
        clearProbes();
        clearPreviewProbes();
        Entity baseEntity = lattice.build();
        // Draw xyz arrows on entity baseField.
        Vector3i dimension = new Vector3i();
        project.getDimension(dimension);

        int sizeI = dimension.x;
        int sizeJ = dimension.y;
        int sizeK = dimension.z;
        createArrows(baseEntity, Constants.EP_DEFAULT_DISTANCE, lattice.getModelSphere(), sizeI, sizeJ, sizeK);
    }

    public void update() {
        System.out.println("Scene.update()");
        lattice.update();
        updateWalls();
    }

    public void createArrows(
            Entity baseField, float step, ObjFileModel modelSphere, int sizeI, int sizeJ, int sizeK) {
        System.out.println("Scene.createArrows()");
        ObjFileModel modelArrow = new OBJFormatLoader().loadOBJModel(Constants.MODEL_ARROW);
        float        scale      = 0.5f;

        Entity arrowC = lattice.createEntity(baseField, modelSphere,
                                             new Vector3d(-(sizeI + 1.0) / 2 * step,
                                                          -(sizeJ / 2.0 + 1) * step,
                                                          -(sizeK / 2.0 + 1) * step),
                                             new Vector3d(0, 0, 0), scale);
        arrowC.setOverrideModelMaterial(true);
        arrowC.getMaterial().setDiffuseColor(new Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
        arrowC.getMaterial().setAmbientColor(new Vector4f(10.0f, 10.0f, 10.0f, 1.0f));
        arrowC.getMaterial().setShininess(0.0f);

        Entity arrowX = lattice.createEntity(baseField, modelArrow,
                                             new Vector3d(-(sizeI + 1.0) / 2 * step,
                                                          -(sizeJ / 2.0 + 1) * step,
                                                          -(sizeK / 2.0 + 1) * step),
                                             new Vector3d(0, 90, 0), scale);
        arrowX.setOverrideModelMaterial(true);
        arrowX.getMaterial().setDiffuseColor(new Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
        arrowX.getMaterial().setAmbientColor(new Vector4f(10.0f, 4.5f, 4.5f, 1.0f));
        arrowX.getMaterial().setShininess(0.0f);

        Entity arrowY = lattice.createEntity(baseField, modelArrow,
                                             new Vector3d(-(sizeI + 1.0) / 2 * step,
                                                          -(sizeJ / 2.0 + 1) * step,
                                                          -(sizeK / 2.0 + 1) * step),
                                             new Vector3d(0, 0, 0), scale);
        arrowY.setOverrideModelMaterial(true);
        arrowY.getMaterial().setDiffuseColor(new Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
        arrowY.getMaterial().setAmbientColor(new Vector4f(4.5f, 4.5f, 10.0f, 1.0f));
        arrowY.getMaterial().setShininess(0.0f);

        Entity arrowZ = lattice.createEntity(baseField, modelArrow,
                                             new Vector3d(-(sizeI + 1.0) / 2 * step,
                                                          -(sizeJ / 2.0 + 1) * step,
                                                          -(sizeK / 2.0 + 1) * step),
                                             new Vector3d(-90, 0, 0), scale);
        arrowZ.setOverrideModelMaterial(true);
        arrowZ.getMaterial().setDiffuseColor(new Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
        arrowZ.getMaterial().setAmbientColor(new Vector4f(4.5f, 10.0f, 4.5f, 1.0f));
        arrowZ.getMaterial().setShininess(0.0f);
    }

    public void addTerrain(Terrain terrain) {
        terrains.add(terrain);
    }

    public void clearHiLightNodes() {
        for (Node node : hiLightedNodes) {
            node.setHiLighted(false);
        }
        hiLightedNodes.clear();
    }

    public void addNodeHiLight(int x, int y, int z) {
        Node node = (Node) lattice.getNodeArray()[z * project.getDimensionX() * project.getDimensionY() +
                                                  y * project.getDimensionX() + x];
        node.setHiLighted(true);
        hiLightedNodes.add(node);
    }

    public void removeNodeHiLight(int x, int y, int z) {
        Node node = (Node) lattice.getNodeArray()[z * project.getDimensionX() * project.getDimensionY() +
                                                  y * project.getDimensionX() + x];
        node.setHiLighted(false);
        hiLightedNodes.remove(node);
    }

    public void addProbe(PassiveObserver probe) {
        probes.add(probe);
    }

    /** Reconstructs committed probe measurements through TransportSystem after lattice creation. */
    public void updateProbeMeasurements(TransportSystem transportSystem) {
        for (PassiveObserver probe : probes) {
            if (probe instanceof Probe rawProbe) rawProbe.updateMeasurements(transportSystem);
            else if (probe instanceof InterpolatedProbe interpolatedProbe) {
                interpolatedProbe.updateMeasurements(transportSystem);
            }
        }
    }

    /** Reconstructs a renderer-only passive observer without adding a project Identity Occupancy. */
    public PassiveObserver createProbe(ProbeDefinition definition, boolean preview) {
        PassiveObserver probe = buildProbe(definition, preview);
        if (probe == null) return null;
        if (preview) previewProbes.add(probe);
        else probes.add(probe);
        return probe;
    }

    private PassiveObserver buildProbe(ProbeDefinition definition, boolean preview) {
        if (lattice.getNodeArray() == null) return null;
        if (definition.getDetectorType() == ProbeDetectorType.RAW_NODE) {
            Node node = lattice.getNodeByIndex(definition.getNodeIndex());
            if (node == null) return null;
            Entity marker = createProbeMarker(definition, node.getPosition(), node.getScale(), preview);
            Probe probe = new Probe(definition, node, marker);
            probe.updateMarker();
            return probe;
        }
        List<Node> sourceNodes = new ArrayList<>();
        for (ProbeDefinition.InterpolationSource source : definition.getInterpolationSources()) {
            Node sourceNode = lattice.getNodeByIndex(source.nodeIndex());
            if (sourceNode == null) return null;
            sourceNodes.add(sourceNode);
        }
        PassiveObserver.Observation observation = ProbeInterpolation.interpolate(
                sourceNodes, definition.getInterpolationSources());
        double sourceScale = sourceNodes.getFirst().getScale();
        Entity marker = createProbeMarker(
                definition, observation.position(), sourceScale, preview);
        InterpolatedProbe probe = new InterpolatedProbe(definition, sourceNodes, marker);
        probe.updateMarker();
        return probe;
    }

    private Entity createProbeMarker(ProbeDefinition definition, Vector3d position,
                                     double sourceScale, boolean preview) {
        Entity marker = new Entity(lattice.getBaseEntity(), lattice.getModelSphere(),
                                   new Vector3d(position), new Vector3d(),
                                   sourceScale * (preview ? 2.0 : 2.75), EntityType.ENTITY);
        marker.setOverrideModelMaterial(true);
        // Material colours are mutated by Entity.setColor; never retain the definition's vector.
        marker.getMaterial().setDiffuseColor(new Vector4f(definition.getDisplayColor()));
        marker.getMaterial().setAmbientColor(new Vector4f(definition.getDisplayColor()));
        marker.getMaterial().setShininess(0.0f);
        return marker;
    }

    /** Replaces transient preview markers while leaving committed probe definitions untouched. */
    public void setPreviewDefinitions(List<ProbeDefinition> definitions) {
        setPreviewDefinitions(definitions, false);
    }

    /** Shows replacement previews without rendering duplicate committed generated markers. */
    public void setPreviewDefinitions(List<ProbeDefinition> definitions, boolean replacesGenerated) {
        clearPreviewProbes();
        replacementProbePreviewActive = replacesGenerated;
        for (ProbeDefinition definition : definitions) createProbe(definition, true);
    }

    /** Removes transient preview markers without changing committed probes or project data. */
    public void clearPreviewProbes() {
        if (neighborhoodInspectionActive) exitNeighborhoodInspection();
        previewProbes.clear();
        replacementProbePreviewActive = false;
    }

    public void setShowGradientArrows(boolean showGradientArrows) {
        this.showGradientArrows = showGradientArrows;
        if (!showGradientArrows) hideGradientArrows();
    }

    public void setGradientArrowScaleExponent(double exponent) {
        gradientArrowScaleExponent = Math.clamp(exponent, 0.0, 120.0);
    }

    public double getGradientArrowScale() {
        return Math.pow(10.0, gradientArrowScaleExponent / 10.0);
    }

    public enum CalibrationDirection {
        NEGATIVE_X(new Vector3d(-1, 0, 0)),
        POSITIVE_Y(new Vector3d(0, 1, 0)),
        POSITIVE_Z(new Vector3d(0, 0, 1));

        private final Vector3d vector;

        CalibrationDirection(Vector3d vector) {
            this.vector = vector;
        }

        public Vector3d vector() {
            return new Vector3d(vector);
        }
    }

    public void setShowCalibrationArrow(boolean showCalibrationArrow) {
        this.showCalibrationArrow = showCalibrationArrow;
        if (!showCalibrationArrow) clearCalibrationArrow();
    }

    public void setCalibrationDirection(CalibrationDirection calibrationDirection) {
        if (this.calibrationDirection == calibrationDirection) return;
        this.calibrationDirection = calibrationDirection;
        if (showCalibrationArrow) clearCalibrationArrow();
    }

    /** Updates cached renderer-only gradient arrows without recreating their entities. */
    public List<Entity> getGradientArrowRenderEntities() {
        if (showCalibrationArrow) {
            hideGradientArrows();
            Entity arrow = calibrationArrow == null ? createCalibrationArrow() : calibrationArrow;
            return arrow == null ? List.of() : List.of(arrow);
        }
        if (!showGradientArrows || probes.isEmpty()) {
            hideGradientArrows();
            return List.of();
        }
        if (gradientArrowModel == null) {
            gradientArrowModel = new OBJFormatLoader().loadOBJModel(Constants.MODEL_ARROW);
        }

        gradientArrowEntities.keySet().removeIf(probe -> {
            if (probes.contains(probe)
                    && (!replacementProbePreviewActive
                        || !probe.getId().startsWith(ProbeDefinition.GENERATED_ID_PREFIX))) return false;
            gradientArrowEntities.get(probe).setVisible(false);
            return true;
        });

        List<Entity> visible = new ArrayList<>();
        for (PassiveObserver probe : probes) {
            if (replacementProbePreviewActive
                    && probe.getId().startsWith(ProbeDefinition.GENERATED_ID_PREFIX)) continue;
            Entity arrow = gradientArrowEntities.computeIfAbsent(probe, this::createGradientArrow);
            Vector3d gradient = probe.getStableGradientImbalance();
            Vector3d anchor = probe.getMarker().getPosition();
            Vector3d localArrowVector = new Vector3d(probe.getStableGradientImbalance())
                    .mul(getGradientArrowScale());
            updateArrowFromLocalVector(arrow, anchor, localArrowVector,
                    localArrowVector.length(),
                    new Vector4f(probe.getMarker().getMaterial().getAmbientColor()));
            if (!arrow.isVisible()) continue;
            visible.add(arrow);
        }
        return visible;
    }

    private Entity createCalibrationArrow() {
        if (lattice.getBaseEntity() == null) return null;
        if (gradientArrowModel == null) {
            gradientArrowModel = new OBJFormatLoader().loadOBJModel(Constants.MODEL_ARROW);
        }
        Vector3d anchor = calibrationAnchor();
        Vector3d direction = calibrationDirection.vector();
        calibrationArrow = lattice.createEntity(lattice.getBaseEntity(), gradientArrowModel,
                anchor, new Vector3d(), 1.0);
        updateArrowFromLocalVector(calibrationArrow, anchor, direction,
                GradientArrowGeometry.CALIBRATION_LENGTH,
                new Vector4f(0.0f, 1.0f, 1.0f, 1.0f));
        return calibrationArrow;
    }

    /** Updates a renderer-only arrow from a vector already expressed in lattice-parent coordinates. */
    void updateArrowFromLocalVector(Entity arrow, Vector3d anchorLocal,
                                    Vector3d directionLocal, double visualLength,
                                    Vector4f color) {
        if (arrow == null || !Double.isFinite(visualLength) || visualLength <= 0.0
                || directionLocal.lengthSquared() <= 1.0e-24) {
            if (arrow != null) arrow.setVisible(false);
            return;
        }
        arrow.setPosition(anchorLocal);
        arrow.setRotation(GradientArrowGeometry.rotationForDirection(directionLocal));
        arrow.setScale(visualLength / (GradientArrowGeometry.MESH_LENGTH * settings.scale));
        arrow.setOverrideModelMaterial(true);
        arrow.getMaterial().setDiffuseColor(new Vector4f(color));
        arrow.getMaterial().setAmbientColor(new Vector4f(color));
        arrow.getMaterial().setShininess(0.0f);
        arrow.setCustomColor(new Vector4f(color));
        arrow.setVisible(true);
        arrow.updateMatrix();
    }

    private Vector3d calibrationAnchor() {
        Vector3i center = new Vector3i(project.getDimensionX() / 2,
                                      project.getDimensionY() / 2,
                                      project.getDimensionZ() / 2);
        Node node = lattice.getNodeByIndex(center);
        return node == null ? new Vector3d() : new Vector3d(node.getPosition());
    }

    private void clearCalibrationArrow() {
        if (calibrationArrow == null) return;
        calibrationArrow.setVisible(false);
        lattice.getEntities().remove(calibrationArrow);
        lattice.getNonNodeRenderEntities().remove(calibrationArrow);
        calibrationArrow = null;
    }

    /** Hides and forgets cached arrows when committed probe or lattice render state is rebuilt. */
    public void hideGradientArrows() {
        for (Entity arrow : gradientArrowEntities.values()) {
            arrow.setVisible(false);
            lattice.getEntities().remove(arrow);
            lattice.getNonNodeRenderEntities().remove(arrow);
        }
        gradientArrowEntities.clear();
    }

    private Entity createGradientArrow(PassiveObserver probe) {
        return lattice.createEntity(lattice.getBaseEntity(), gradientArrowModel,
                probe.getMarker().getPosition(), new Vector3d(), 1.0);
    }

    public List<Entity> getProbeRenderEntities() {
        List<Entity> entities = new ArrayList<>();
        for (PassiveObserver probe : probes) {
            if (!replacementProbePreviewActive
                    || !probe.getId().startsWith(ProbeDefinition.GENERATED_ID_PREFIX)) {
                entities.add(probe.getMarker());
            }
        }
        for (PassiveObserver probe : previewProbes) entities.add(probe.getMarker());
        return entities;
    }

    /** Replaces previews with committed renderer-only markers for the supplied definitions. */
    public void commitProbeDefinitions(List<ProbeDefinition> definitions) {
        clearPreviewProbes();
        for (ProbeDefinition definition : definitions) createProbe(definition, false);
    }

    /** Atomically replaces generated probe markers while preserving manually created receivers. */
    public boolean replaceGeneratedProbeDefinitions(List<ProbeDefinition> definitions) {
        if (neighborhoodInspectionActive) exitNeighborhoodInspection();
        hideGradientArrows();
        List<PassiveObserver> replacements = new ArrayList<>();
        for (ProbeDefinition definition : definitions) {
            PassiveObserver replacement = buildProbe(definition, false);
            if (replacement == null) return false;
            replacements.add(replacement);
        }
        if (selectedProbe != null && selectedProbe.getId().startsWith(ProbeDefinition.GENERATED_ID_PREFIX)) {
            selectedProbe.setSelected(false);
            selectedProbe = null;
        }
        if (hoveredProbe != null && hoveredProbe.getId().startsWith(ProbeDefinition.GENERATED_ID_PREFIX)) {
            hoveredProbe.setHovered(false);
            hoveredProbe = null;
        }
        clearComparison();
        probes.removeIf(probe -> probe.getId().startsWith(ProbeDefinition.GENERATED_ID_PREFIX));
        probes.addAll(replacements);
        clearPreviewProbes();
        return true;
    }

    /** Clears committed and transient probe interaction state before rebuilding the lattice. */
    public void clearProbeInteractionState() {
        if (selectedProbe != null) selectedProbe.setSelected(false);
        if (hoveredProbe != null) hoveredProbe.setHovered(false);
        selectedProbe = null;
        hoveredProbe = null;
        clearComparison();
    }

    /** Removes all probe markers and restores the ordinary lattice render state. */
    public void clearProbes() {
        if (neighborhoodInspectionActive) exitNeighborhoodInspection();
        clearCalibrationArrow();
        hideGradientArrows();
        clearProbeInteractionState();
        for (PassiveObserver probe : probes) resetProbeMeasurements(probe);
        for (PassiveObserver probe : previewProbes) resetProbeMeasurements(probe);
        probes.clear();
        previewProbes.clear();
        replacementProbePreviewActive = false;
    }

    private static void resetProbeMeasurements(PassiveObserver probe) {
        for (var occupancy : probe.getOccupancies()) occupancy.resetMeasurementState();
    }

    /** Makes the committed probe the live diagnostic selection and refreshes its marker immediately. */
    public void selectProbe(PassiveObserver probe) {
        boolean probeChanged = selectedProbe != probe;
        if (selectedProbe != null) selectedProbe.setSelected(false);
        selectedProbe = probe;
        if (selectedProbe != null) {
            selectedProbe.setSelected(true);
            selectedProbe.updateMarker();
        }
        if ((neighborhoodInspectionActive || inspectionTransitionActive) && probeChanged)
            retargetNeighborhoodInspection();
    }

    public boolean isNeighborhoodInspectionTransitionActive() { return inspectionTransitionActive; }


    /** Enters a renderer-only inspection session for the currently selected passive probe. */
    public boolean enterNeighborhoodInspection() {
        if (selectedProbe == null) return false;
        if ((neighborhoodInspectionActive || inspectionTransitionActive) && inspectedProbe == selectedProbe)
            return inspectionProbeMarker != null;
        if (!neighborhoodInspectionActive && !inspectionTransitionActive) {
            savedInspectionNavigation = InspectionNavigationSnapshot.capture(this);
            project.camera.setOrbitTarget(resolveNormalCameraTarget());
        }
        inspectedProbe = selectedProbe;
        retargetNeighborhoodInspection();
        return inspectionProbeMarker != null;
    }

    public void exitNeighborhoodInspection() {
        if (savedInspectionNavigation == null) return;
        // A second exit during the return flight is intentionally a no-op.
        if (inspectionTransitionActive && !inspectionTransitionEntering) return;
        // Cancel the entry/live-inspection update and reuse the same transition path
        // for the return. The snapshot remains valid until that flight completes.
        inspectionTransitionActive = false;
        inspectionTransitionEntering = false;
        neighborhoodInspectionActive = false;
        beginCameraTransition(false, savedInspectionNavigation.camera(),
                              savedInspectionNavigation.camera().orbitTarget());
    }

    public double getCameraDistance(ProbeDetectorType probeDetectorType) {
        switch (probeDetectorType) {
            case RAW_NODE -> {
                return 30;
            }
            case INTERPOLATED_2D -> {
                return 40;
            }
            case INTERPOLATED_3D -> {
                return 60;
            }
        }

        return 30;
    }

    /** Resolves the selected existing probe and starts the camera-only transition. */
    public void retargetNeighborhoodInspection() {
        if (inspectedProbe == null) inspectedProbe = selectedProbe;
        inspectionProbeMarker = inspectedProbe.getMarker();
        if (inspectionProbeMarker == null) return;
        inspectionFrameTarget.set(inspectionProbeMarker.toWorldPosition());
        inspectionFrameDistance = getCameraDistance(inspectedProbe.getDetectorType());
        previousInspectionMarkerPosition.set(inspectionProbeMarker.toWorldPosition());
        beginCameraTransition(true, null, null);
    }

    /** Keeps the orbit target attached to the existing live probe marker. */
    public void updateInspectionCameraTarget() {
        if (!neighborhoodInspectionActive || inspectionProbeMarker == null) return;
        Vector3d liveMarkerPosition = inspectionProbeMarker.toWorldPosition();
        Vector3d delta = new Vector3d(liveMarkerPosition).sub(previousInspectionMarkerPosition);
        if (delta.lengthSquared() != 0.0) {
            inspectionFrameTarget.add(delta);
            project.camera.followOrbitTarget(inspectionFrameTarget);
            previousInspectionMarkerPosition.set(liveMarkerPosition);
        }
    }

    /** Advances the camera-only inspection transition once per render frame. */
    public void updateCameraTransition(double deltaSeconds) {
        if (inspectionTransitionActive) {
            inspectionTransitionProgress = Math.min(1.0,
                    inspectionTransitionProgress + Math.max(0.0, deltaSeconds)
                            / INSPECTION_TRANSITION_SECONDS);
            double eased = smoothstep(inspectionTransitionProgress);
            Vector3d position = interpolate(transitionStartPosition, transitionEndPosition, eased);
            Vector3d target = interpolate(transitionStartTarget, transitionEndTarget, eased);
            if (inspectionTransitionEntering) {
                project.camera.setOrbitState(position, target);
            } else {
                double distance = lerp(transitionStartDistance, transitionEndDistance, eased);
                project.camera.setOrbitState(position, target, distance);
                Vector3d rotation = interpolateRotation(transitionStartRotation,
                                                         transitionEndRotation, eased);
                project.camera.setRotation((float) rotation.x, (float) rotation.y, (float) rotation.z);
                project.camera.setFieldOfView(lerp(transitionStartFov, transitionEndFov, eased));
                project.camera.setZ_near(lerp(transitionStartNear, transitionEndNear, eased));
                project.camera.setZ_far(lerp(transitionStartFar, transitionEndFar, eased));
            }
            if (inspectionTransitionProgress >= 1.0) finishCameraTransition();
            return;
        }
        updateInspectionCameraTarget();
    }

    private void beginCameraTransition(boolean entering, CameraState returnState, Vector3d returnTarget) {
        if (entering) {
            transitionStartPosition.set(project.camera.getPosition());
            transitionStartTarget.set(project.camera.getOrbitTarget());
            transitionEndTarget.set(inspectionFrameTarget);
            Vector3d offset = new Vector3d(transitionStartPosition).sub(transitionStartTarget);
            if (offset.lengthSquared() == 0.0) offset.set(0.0, 0.0, 1.0);
            offset.normalize().mul(inspectionFrameDistance);
            transitionEndPosition.set(transitionEndTarget).add(offset);
            transitionStartRotation.set(project.camera.getRotation().x,
                                        project.camera.getRotation().y,
                                        project.camera.getRotation().z);
            transitionEndRotation.set(transitionStartRotation);
            transitionStartDistance = project.camera.getOrbitDistance();
            transitionEndDistance = transitionStartDistance;
            transitionStartFov = transitionEndFov = project.camera.getFieldOfView();
            transitionStartNear = transitionEndNear = project.camera.getZ_near();
            transitionStartFar = transitionEndFar = project.camera.getZ_far();
        } else {
            transitionStartPosition.set(project.camera.getPosition());
            transitionStartTarget.set(project.camera.getOrbitTarget());
            transitionEndPosition.set(returnState.position());
            transitionEndTarget.set(returnTarget);
            transitionStartRotation.set(project.camera.getRotation().x,
                                        project.camera.getRotation().y,
                                        project.camera.getRotation().z);
            transitionEndRotation.set(returnState.rotation());
            transitionStartDistance = project.camera.getOrbitDistance();
            transitionEndDistance = returnState.orbitDistance();
            transitionStartFov = project.camera.getFieldOfView();
            transitionEndFov = returnState.fov();
            transitionStartNear = project.camera.getZ_near();
            transitionEndNear = returnState.near();
            transitionStartFar = project.camera.getZ_far();
            transitionEndFar = returnState.far();
        }
        inspectionTransitionEntering = entering;
        inspectionTransitionProgress = 0.0;
        inspectionTransitionActive = true;
        if (entering) neighborhoodInspectionActive = false;
    }

    private void finishCameraTransition() {
        inspectionTransitionActive = false;
        if (inspectionTransitionEntering) {
            neighborhoodInspectionActive = true;
            project.camera.lookAtOrbitTarget();
        } else {
            neighborhoodInspectionActive = false;
            inspectionProbeMarker = null;
            inspectedProbe = null;
            if (savedInspectionNavigation != null) {
                InspectionNavigationSnapshot snapshot = savedInspectionNavigation;
                savedInspectionNavigation = null;
                snapshot.camera().restore(project.camera);
            }
        }
    }

    private static double smoothstep(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static Vector3d interpolate(Vector3d start, Vector3d end, double amount) {
        return new Vector3d(start).lerp(end, amount);
    }

    private static Vector3d interpolateRotation(Vector3d start, Vector3d end, double amount) {
        return new Vector3d(
                interpolateAngle(start.x, end.x, amount),
                interpolateAngle(start.y, end.y, amount),
                interpolateAngle(start.z, end.z, amount));
    }

    private static double interpolateAngle(double start, double end, double amount) {
        double delta = end - start;
        delta = ((delta + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        return start + delta * amount;
    }

    private static double lerp(double start, double end, double amount) {
        return start + (end - start) * amount;
    }

    private static float lerp(float start, float end, double amount) {
        return (float) (start + (end - start) * amount);
    }

    /** Returns the single active orbit target: probe marker in inspection, base entity otherwise. */
    public Vector3d getActiveCameraTarget() {
        if (neighborhoodInspectionActive || (inspectionTransitionActive && inspectionTransitionEntering))
            return project.camera.getOrbitTarget();
        Vector3d target = resolveNormalCameraTarget();
        project.camera.setOrbitTarget(target);
        return target;
    }

    /** Orbits the camera around the active target without changing any entity transform. */
    public void orbitCameraAroundTarget(float rotationX, float rotationY, float rotationZ) {
        getActiveCameraTarget();
        project.camera.orbitAroundTarget(rotationX, rotationY, rotationZ);
    }

    public void zoomInspectionCamera(double scroll) {
        zoomCameraAroundTarget(scroll);
    }

    /** Shared normal/inspection zoom operation using the active camera target. */
    public void zoomCameraAroundTarget(double scroll) {
        getActiveCameraTarget();
        project.camera.zoomAroundTarget(scroll * 12.0, 2.0, 1000.0);
    }

    /** Resets orientation around the active target while preserving the current distance. */
    public void resetCameraOrbit() {
        getActiveCameraTarget();
        project.camera.resetOrbitTopView();
    }

    private void cameraFocusOnInspectionTarget() {
        inspectionCameraDistance = 24.0;
        project.camera.focusOrbit(inspectionCameraDistance);
    }

    private Vector3d resolveNormalCameraTarget() {
        Entity base = lattice == null ? null : lattice.getBaseEntity();
        return base == null ? new Vector3d() : base.toWorldPosition();
    }

    /** Changes hover state only; probe observations and their source nodes remain untouched. */
    public void setHoveredProbe(PassiveObserver probe) {
        if (hoveredProbe == probe) return;
        if (hoveredProbe != null) {
            hoveredProbe.setHovered(false);
            hoveredProbe.updateMarker();
        }
        hoveredProbe = probe;
        if (hoveredProbe != null) {
            hoveredProbe.setHovered(true);
            hoveredProbe.updateMarker();
        }
    }

    /** Assigns or removes an eligible generated probe from comparison A or B. */
    public boolean toggleComparisonProbe(PassiveObserver probe) {
        if (probe == null || !probes.contains(probe)
                || !probe.getId().startsWith(ProbeDefinition.GENERATED_ID_PREFIX)) return false;
        if (!comparison.toggle(probe)) return false;
        applyComparisonSlots();
        return true;
    }

    /** Clears both live comparison slots and restores their normal marker colours. */
    public void clearComparison() {
        comparison.clear();
        applyComparisonSlots();
    }

    private void applyComparisonSlots() {
        for (PassiveObserver probe : probes) probe.setComparisonSlot(-1);
        PassiveObserver a = comparison.get(ProbeComparison.Slot.A);
        PassiveObserver b = comparison.get(ProbeComparison.Slot.B);
        if (a != null) a.setComparisonSlot(0);
        if (b != null) b.setComparisonSlot(1);
        if (a != null) a.updateMarker();
        if (b != null && b != a) b.updateMarker();
    }

    public Probe getProbeForNode(Node node) {
        if (node == null) return null;
        for (PassiveObserver observer : probes) {
            if (observer instanceof Probe probe && probe.getNode() == node) return probe;
        }
        return null;
    }

    public PassiveObserver getPreviewProbeForNode(Node node) {
        if (node == null) return null;
        for (PassiveObserver observer : previewProbes) {
            if (observer instanceof Probe probe && probe.getNode() == node) return probe;
        }
        return null;
    }

    /** Returns the nearest interpolated marker after the unchanged node-picking path misses. */
    public InterpolatedProbe getInterpolatedProbeFromMousePos(double xPos, double yPos) {
        return getInterpolatedProbeFromMousePos(xPos, yPos, probes);
    }

    public InterpolatedProbe getPreviewInterpolatedProbeFromMousePos(double xPos, double yPos) {
        return getInterpolatedProbeFromMousePos(xPos, yPos, previewProbes);
    }

    private InterpolatedProbe getInterpolatedProbeFromMousePos(
            double xPos, double yPos, List<PassiveObserver> candidates) {
        Matrix4f projectionMatrix = project.camera.getProjectionMatrix();
        Matrix4f viewMatrix = project.camera.getViewMatrix();
        Vector3f cameraPos = project.camera.getPosition();
        float x = (float) (2 * xPos / appSettings.getWindowWidth() - 1);
        float y = (float) (2 * (appSettings.getWindowHeight() - yPos)
                / appSettings.getWindowHeight() - 1);
        Vector4f clipCoords = new Vector4f(x, y, 0, 1);
        Vector4f eyeCoords = new Matrix4f(projectionMatrix).invert().transform(clipCoords);
        eyeCoords.w = 0;
        Vector4f rayWorld = new Matrix4f(viewMatrix).invert().transform(eyeCoords);
        Vector3f ray = new Vector3f(rayWorld.x, rayWorld.y, rayWorld.z).normalize();

        InterpolatedProbe nearest = null;
        float nearestDistance = Float.POSITIVE_INFINITY;
        Vector4f markerPosition = new Vector4f();
        Vector2f intersection = new Vector2f();
        for (PassiveObserver observer : candidates) {
            if (!(observer instanceof InterpolatedProbe interpolatedProbe)) continue;
            Entity marker = observer.getMarker();
            marker.getTransformMatrix4f().transform(0, 0, 0, 1, markerPosition);
            double radius = settings.scale * marker.getScale() * 0.5;
            if (Intersectionf.intersectRaySphere(
                    cameraPos.x, cameraPos.y, cameraPos.z,
                    ray.x, ray.y, ray.z,
                    markerPosition.x, markerPosition.y, markerPosition.z,
                    (float) (radius * radius), intersection)
                    && intersection.x >= 0.0f && intersection.x < nearestDistance) {
                nearest = interpolatedProbe;
                nearestDistance = intersection.x;
            }
        }
        return nearest;
    }

    private Vector3i isWallNodeExists(Node node) {
        for (Vector3i nodeIndex : wallNodeIndexList) {
            if (node.isIndex(nodeIndex)) {
                return nodeIndex;
            }
        }
        return null;
    }

    private void addWallNode(Node node) {
        if (isWallNodeExists(node) == null)
            wallNodeIndexList.add(node.getIndex());
    }

    private void removeWallNode(Node node) {
        Vector3i nodeIndex;
        if ((nodeIndex = isWallNodeExists(node)) != null)
            wallNodeIndexList.remove(nodeIndex);
    }

    private void setNodeWall(Node node, boolean value) {
        if (value) {
            node.setType(EntityType.WALL);
            addWallNode(node);
        } else
            removeWallNode(node);
    }

    public void swapNodeWall(Node node) {
        setNodeWall(node, !node.isWall());
    }

    public void swapNodeWallZ(Node node) {
        int x = node.getIndex().x;
        int y = node.getIndex().y;

        for (int z = 0; z < project.getDimensionZ(); z++) {
            node = (Node) lattice.getNodeArray()[z * project.getDimensionX() * project.getDimensionY() +
                                                 y * project.getDimensionX() + x];
            swapNodeWall(node);
        }
    }

    public void eraseWall(Node node) {
        setNodeWall(node, false);
    }

    public void eraseWallZ(Node node) {
        Vector3i index = node.getIndex();
        for (int z = 0; z < project.getDimensionZ(); z++) {
            int i = z * project.getDimensionX() * project.getDimensionY() +
                    index.y * project.getDimensionX() + index.x;
            node = (Node) lattice.getNodeArray()[i];
            eraseWall(node);
        }
    }

    public void clearAllWalls() {
        for (Vector3i nodeIndex : wallNodeIndexList) {
            Node node = lattice.getNodeByIndex(nodeIndex);
            lattice.deleteEntity(node);
        }
        wallNodeIndexList.clear();
    }

    public void makeWallFromHiLightNodes() {
        for (Node node : hiLightedNodes) {
            node.setHiLighted(false);
            node.setVisible(false);
            setNodeWall(node, true);
        }
        hiLightedNodes.clear();
    }

    public void updateWalls() {
        for (Vector3i nodeIndex : wallNodeIndexList) {
            Node node = lattice.getNodeByIndex(nodeIndex);
            if (node != null) {
                node.setType(EntityType.WALL);
            }
        }
    }

    public void drawSlitWall() {
        clearHiLightNodes();

        int     p, d, iSize, jSize, ic, jc;
        boolean isSlit;
        int     numSlits = slitWall.getNumSlits();

        int jStart = 1;

        switch (slitWall.direction) {
            case x:
                p = (int) (project.slitWall.getPosition() * project.getDimensionX());
                if (p < 1) p = 1;
                if (p > project.getDimensionX() - 2) p = project.getDimensionX() - 2;
                iSize = project.getDimensionY();
                jSize = project.getDimensionZ();
                ic = iSize / 2;
                jc = jSize / 2;
                d = slitWall.getSlitDistance();

                double shw = slitWall.getSlitWidth() * 0.5;
                double shh = slitWall.getSlitHeight() * 0.5;

                if (jSize == 1) {
                    jStart = 0;
                    jSize  = 2;
                }

                for (int j = jStart; j < jSize - 1; j++) {
                    for (int i = 1; i < iSize - 1; i++) {
                        addNodeHiLight(p, i, j);
                    }
                }

                for (int j = jStart; j < jSize - 1; j++) {
                    for (int i = 1; i < iSize - 1; i++) {
                        for (int n = 1; n <= numSlits; n++) {
                            double c = (ic + ((n - numSlits * 0.5) - 0.5) * d);

                            isSlit = (i > c - shw && i < c + shw && j > jc - shh && j < jc + shh);
                            if (isSlit)
                                removeNodeHiLight(p, i, j);
                        }
                    }
                }

                break;
            case y:
                break;
            case z:
                break;
            default:
                break;

        }
    }

    public void drawLine(Node node) {
        clearHiLightNodes();

        int z  = settings.getVisibleIndexZ();
        int x1 = firstHiLightedNode.getIndex().x;
        int y1 = firstHiLightedNode.getIndex().y;
        int x2 = node.getIndex().x;
        int y2 = node.getIndex().y;
        int xD = x2 - x1;
        int yD = y2 - y1;
        if (xD == 0 && yD == 0) return;

        int xStart = x1, xEnd = x2, yStart = y1, yEnd = y2;
        if (x2 < x1) {
            xStart = x2;
            xEnd   = x1;
        }
        if (y2 < y1) {
            yStart = y2;
            yEnd   = y1;
        }

        if (Math.abs(xD) >= Math.abs(yD)) {
            yStart = x1 <= x2 ? y1 : y2;
            for (int x = xStart; x <= xEnd; x++) {
                int y = (int) Math.round(yStart + (x - xStart) / (double) xD * yD);
                addNodeHiLight(x, y, z);
            }
        } else {
            xStart = y1 <= y2 ? x1 : x2;
            for (int y = yStart; y <= yEnd; y++) {
                int x = (int) Math.round(xStart + (y - yStart) / (double) yD * xD);
                addNodeHiLight(x, y, z);
            }
        }
    }

    public void drawWall(Node node) {
        clearHiLightNodes();

        int x1 = firstHiLightedNode.getIndex().x;
        int y1 = firstHiLightedNode.getIndex().y;
        int x2 = node.getIndex().x;
        int y2 = node.getIndex().y;
        int xD = x2 - x1;
        int yD = y2 - y1;
        if (xD == 0 && yD == 0) return;

        int xStart = x1, xEnd = x2, yStart = y1, yEnd = y2;
        if (x2 < x1) {
            xStart = x2;
            xEnd   = x1;
        }
        if (y2 < y1) {
            yStart = y2;
            yEnd   = y1;
        }

        for (int z = 0; z < project.getDimensionZ(); z++) {
            if (Math.abs(xD) >= Math.abs(yD)) {
                yStart = x1 <= x2 ? y1 : y2;
                for (int x = xStart; x <= xEnd; x++) {
                    int y = (int) Math.round(yStart + (x - xStart) / (double) xD * yD);
                    addNodeHiLight(x, y, z);
                }
            } else {
                xStart = y1 <= y2 ? x1 : x2;
                for (int y = yStart; y <= yEnd; y++) {
                    int x = (int) Math.round(xStart + (y - yStart) / (double) yD * xD);
                    addNodeHiLight(x, y, z);
                }
            }
        }
    }

    public void setFirstHiLightNode(Node node) {
        firstHiLightedNode = node;
        firstHiLightedNode.setHiLighted(true);
    }

    public Node getNodeFromMousePos(double xPos, double yPos) {
        if (lattice.getNodeArray() == null) return null;
        Matrix4f projectionMatrix = project.camera.getProjectionMatrix();
        Matrix4f viewMatrix       = project.camera.getViewMatrix();
        Vector3f cameraPos        = project.camera.getPosition();

        float x = (float) ((2 * (xPos - 0)) / appSettings.getWindowWidth() - 1);
        float y = (float) (
                (2 * (appSettings.getWindowHeight() - (yPos + 0))) / appSettings.getWindowHeight() - 1);

        Vector4f clipCoords = new Vector4f(x, y, 0, 1);

        Matrix4f invProjection = new Matrix4f(projectionMatrix).invert();
        Vector4f eyeCoords     = invProjection.transform(clipCoords);
        eyeCoords.w = 0;
        Matrix4f invViewMatrix = new Matrix4f(viewMatrix).invert();
        Vector4f rayWorld      = invViewMatrix.transform(eyeCoords);
        Vector3f ray           = new Vector3f(rayWorld.x, rayWorld.y, rayWorld.z).normalize();

        Vector4f nodePos = new Vector4f();
        Vector2f result  = new Vector2f();
        Vector3f scale   = new Vector3f();

        int iMin = settings.getVisibleIndexZ() * project.getDimensionX() * project.getDimensionY();
        int iMax = (settings.getVisibleIndexZ() + 1) * project.getDimensionX() * project.getDimensionY();

        // FPS drops when using large number of nodes.
        // TODO: use BSP to improve performance.
        for (int i = iMin; i >= 0 && i < iMax && i < lattice.getNodeArray().length; i++) {
            Node node = (Node) lattice.getNodeArray()[i];
            if (node.isVisible() && (!node.isFixed() || node.isWall())) {
                node.getTransformMatrix4f().transform(0, 0, 0, 1, nodePos);
                node.getTransformMatrix4f().getScale(scale);

                double radius = settings.scale * node.getScale() * 0.5;

                if (Intersectionf.intersectRaySphere(
                        cameraPos.x, cameraPos.y, cameraPos.z,
                        ray.x, ray.y, ray.z,
                        nodePos.x, nodePos.y, nodePos.z,
                        (float) (radius * radius),
                        result)) {
                    return node;
                }
            }
        }
        return null;
    }

    private void threadUpdateMatrix(final int start, final int end) {
        threads.add(Thread.startVirtualThread(() -> {
            int i = start;

            while (i < end) {
                Entity entity = (Entity) lattice.getEntityArray()[i++];
                if (settings.getSliceType() == Settings.SliceType.identitiesOnly
                        && entity instanceof Node node) {
                    node.updatePosition();
                    node.setCustomScale(-1);
                    node.setCustomColor(null);
                }
                else {
                    entity.updateMatrix();
                }
            }
        }));
    }

    public void updateMatrix(int numThreads, int segmentSize) {
        for (int i = 0; i < numThreads; i++) {
            int start = i * segmentSize;
            int end   = start + segmentSize;
            end = Math.min(end, lattice.getEntityArray().length);
            threadUpdateMatrix(start, end);
        }
    }

    private void threadCalcRemField(final int start, final int end) {
        threads.add(Thread.startVirtualThread(() -> {
            int i = start;

            // If set to false then the node stops updating its position
            if (REMModel.isSimulationUpdateActive()) {
                while (i < end) {
                    ((Node) lattice.getNodeArray()[i++]).updateNodeProperties(
                            settings.getNodeFormulaVersion());
                }
            }
        }));
    }

    public void processRemFieldEngine(int numThreads, int segmentSize) {
        for (int i = 0; i < numThreads; i++) {
            int start = i * segmentSize;
            int end   = start + segmentSize;
            end = Math.min(end, lattice.getNodeArray().length);
            threadCalcRemField(start, end);
        }
    }

    public void waitForAllTasksReady() {
        for (Thread thread : threads) {
            try {
                thread.join();
            }
            catch (InterruptedException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        threads.clear();
    }

    public void startAnimation() {
        setAnimated(true);
    }

    public void stopAnimation() {
        setAnimated(false);
    }

    public void runAnimation() {
        if (animated) {
            double z = getLattice().getBaseEntity().getRotation().z;

            if (animateDirection == -1 && z <= -170) {
                animateDirection = 1;
            } else if (animateDirection == 1 && z >= -10) {
                animateDirection = -1;
            }

            getLattice().getBaseEntity().incRotation(0.0f, 0.0f, 0.5 * animateDirection);
        }
    }

    public void toggleAnimation() {
        setAnimated(!animated);
    }

}
