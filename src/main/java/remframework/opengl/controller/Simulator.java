/*
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;
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
package remframework.opengl.controller;

import org.joml.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import remframework.opengl.model.*;
import remframework.helpers.Constants;
import remframework.helpers.IMouseAndKeyboardEvents;
import remframework.gui.model.NuklearModel.Frame;
import remframework.measurement.gravity.GravityExperimentController;
import remframework.measurement.gravitypressure.GravityPressureExperimentController;
import remframework.measurement.detector.DetectorExperimentController;
import remframework.measurement.detector.FeasibilityPilotTelemetry;
import remframework.opengl.model.RemModel.TranslationMode;
import remframework.opengl.model.RemModel.NodeSelectionMode;
import remframework.opengl.model.Probe;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.EntityType;
import remframework.opengl.model.entity.Node;
import remframework.opengl.model.entity.Occupancy;
import remframework.opengl.model.lights.DirectionalLight;
import remframework.opengl.model.lights.Light;
import remframework.opengl.helpers.OBJFormatLoader;
import remframework.opengl.helpers.Screenshot;
import remframework.opengl.view.GLWindow;
import remframework.opengl.view.renderer.Renderer;
import remframework.settings.AppSettings;
import remframework.settings.Project;
import remframework.settings.Settings;
import remframework.settings.Settings.SliceType;
import remframework.settings.SlitWallSettings;
import remframework.helpers.PerformanceMonitor;
import remframework.helpers.PerformanceMonitor.Measurement;
import remframework.helpers.Utils;
import remframework.geometry.discovery.viewport.Viewport;
import remframework.geometry.discovery.workflow.WorkspaceController;
import remframework.geometry.discovery.GeometryDiscoveryWorkspaceManager;

import java.lang.Math;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Simulator extends Engine implements IMouseAndKeyboardEvents {

    /// ///////////////////////////////////////////////////////////////////////////////
    /// Project Settings /////////////////////////////////////////////////////////////
    private final AppSettings      appSettings     = AppSettings.getInstance();
    private final Project  project  = Project.getInstance();
    private final RemModel remModel = project.getRemModel();
    private final Scene    scene    = project.scene;
    private final Settings         settings        = project.settings;
    private final SlitWallSettings slitWall        = project.slitWall;
    private final DetectorModel   detectorModel   = project.detectorModel;
    /// Project Settings /////////////////////////////////////////////////////////////
    /// ///////////////////////////////////////////////////////////////////////////////

    private final TransportSystem transportSystem = TransportSystem.getInstance();

    private final Renderer        renderer;
    private final OBJFormatLoader loader;
    private final Offscreen3DRenderer offscreen3DRenderer;
    private Renderer.RenderState renderState;
    private long renderStateVersion = Long.MIN_VALUE;

    private       Node     selectedNode;
    private final Vector2d lastMousePos = new Vector2d();
    private final Vector2d lastHoverPickMousePos = new Vector2d(Double.NaN, Double.NaN);
    private final Vector3f lastHoverPickCameraPosition = new Vector3f(Float.NaN);
    private final Vector3f lastHoverPickCameraRotation = new Vector3f(Float.NaN);
    private final Matrix4f lastHoverPickProjection = new Matrix4f();
    private boolean hoverPickPending;

    private              boolean         leftButtonPressed             = false;
    private              boolean         rightButtonPress              = false;
    private              boolean         isDrawing;
    private volatile     boolean         isRenderingReady;
    private              boolean         isLoadingStarted;
    private              boolean         isNodesUpdateRequired;
    private              boolean         renderMatricesDirty = true;
    private              boolean         pausedRenderMatricesCurrent;
    private              KeyEvents       keyEvents;
    private final        ArrayList<Node> customNodeFixed               = new ArrayList<>();
    private              int             waitAfterReset                = 0;
    private              Node            lastSelectedNode;
    protected            long            currentTime;
    private              int             numFrameCalculation;
    private final NonBlockingFrameTask frameCalculation = new NonBlockingFrameTask();
    private final        Long            updateLimit;
    private volatile     boolean         updateLimitReached;
    private final WorkspaceController discoveryWorkspace =
            GeometryDiscoveryWorkspaceManager.getInstance();
    private Viewport discoveryViewport;

    public Simulator(GLWindow glWindow, Long updateLimit) {
        super(glWindow);

        this.updateLimit = updateLimit;
        renderer = new Renderer();
        loader   = new OBJFormatLoader();
        offscreen3DRenderer = new Offscreen3DRenderer(glWindow);
    }

    @Override
    public void init() throws Exception {
        System.out.println("Simulator.init()");
        super.init();
        discoveryWorkspace.attach(remModel, nuklearModel);
        addMouseAndKeyboardListener(this);
        renderer.init();
        discoveryViewport = new Viewport(discoveryWorkspace);

        initDefaultLights();
        initDefaultTerrain();
        initDefaultScene();
        //updateNodesProperties();
        remModel.startSimulation();
        offscreen3DRenderer.start();
    }

    public void initDefaultLights() {
        System.out.println("Simulator.initDefaultLights()");
        ObjFileModel modelSphere    = loader.loadOBJModel(Constants.MODEL_LIGHT_BULB);
        ObjFileModel modelSpotLight = loader.loadOBJModel(Constants.MODEL_LIGHT_CAP);

        modelSpotLight.getMaterial().setAmbientColor(0.0f, 1.0f, 0.0f, 1.0f);
        modelSpotLight.getMaterial().setAmbientIntensity(6);
        modelSpotLight.getMaterial().setDiffuseColor(0.5f, 0.2f, 0.0f, 1.0f);
        modelSpotLight.getMaterial().setDiffuseIntensity(0.8);

        //Point light
        float    lightIntensity = 0.8f;
        Vector3d lightPosition  = new Vector3d(30, 200, 175);
        Vector3d lightRotation  = new Vector3d(0, 0, 0);
        Vector3f lightColor     = new Vector3f(0.67f, 0.8f, 1.0f);
        Vector3d lightDirection = new Vector3d(0, 0, 1);
        float    cutOff         = 90;
        Light pointLight = project.createLight(null, modelSphere, modelSpotLight, lightPosition,
                                               lightRotation, 1.0);
        pointLight.set(lightColor, lightIntensity, 0.3, 0.003, 0.0, cutOff);
        pointLight.setDirection(lightDirection);
        pointLight.setName("DefaultPointLight");
        pointLight.setSpotLight(false);
        pointLight.setScale(0);

        //Spot light
//        lightIntensity = 1.0f;
//        lightPosition  = new Vector3d(25.0f, 40.0f, -10.0f);
//        lightRotation  = new Vector3d(0, 0, 0);
//        lightDirection = new Vector3d(-0.3, -1, 0.1);
//        cutOff         = 25;
//        lightColor     = new Vector3f(0.5f, 1.0f, 0.5f);
//        Light spotLight = project.createLight(null, modelSphere, modelSpotLight, lightPosition, lightRotation,
//                                              1.0);
//        spotLight.set(lightColor, lightIntensity, 0.1, 0.03, 0.0007, cutOff);
//        spotLight.setDirection(lightDirection);
//        spotLight.setName("DefaultSpotLight");
//        spotLight.setSpotLight(true);

        // Directional light
        lightIntensity = 0.0f;
        lightColor     = new Vector3f(1.0f, 1.0f, 1.0f);
        DirectionalLight directionalLight = new DirectionalLight(lightColor, new Vector3f(0, 100, 0),
                                                                 lightIntensity);
        scene.setDirectionalLight(directionalLight);
    }

    public void initDefaultScene() {
        System.out.println("Simulator.initDefaultScene()");
        project.camera.movePosition(0, 0, 120);
        project.camera.updateProjectionMatrix();
        scene.orbitCameraAroundTarget(45.0f, 0.0f, 0.0f);

        // Create a default scene with xyz nodes.
        project.setDimension(appSettings.getDefaultDimensionX(), appSettings.getDefaultDimensionY(),
                             appSettings.getDefaultDimensionZ());
        // The scene is (re)build when settings has been changed and reset, see checksettingsChanged().
        project.reset();
    }

    public void initDefaultTerrain() throws Exception {
        System.out.println("Simulator.initDefaultTerrain()");
        project.terrain = new Terrain(
                new Vector3f(-Terrain.SIZE / 2, -150, -Terrain.SIZE / 2),
                loader,
                new Material());
        project.terrain.setTerrainTexturePath(Constants.TEXTURE_FILE_PATH);
        project.terrain.setTerrainTextureFile(Constants.TEXTURE_DEFAULT_FILE);
        Texture texture = new Texture(
                loader.loadTexture(Constants.BASE_PATH + project.terrain.getTerrainTextureFilePath()));
        project.terrain.getMaterial().setTexture(texture);
        scene.addTerrain(project.terrain);
    }

    private void mouseReleaseWall(Node node) {
        switch (remModel.getEditMode()) {
            case swapNode:
                scene.swapNodeWall(node);
                isNodesUpdateRequired = true;
                break;
            case swapNodesZ:
                scene.swapNodeWallZ(node);
                isNodesUpdateRequired = true;
                break;
            case drawLine:
            case drawWall:
                if (!isDrawing) {
                    if (node != null) {
                        scene.setFirstHiLightNode(node);
                        isDrawing = true;
                    }
                } else {
                    scene.makeWallFromHiLightNodes();
                    isNodesUpdateRequired = true;
                    isDrawing             = false;
                }
                break;
            default:
                break;

        }
    }

    public void mouseDrawWall(Node node) {
        switch (remModel.getEditMode()) {
            case drawLine:
                scene.drawLine(node);
                isNodesUpdateRequired = true;
                break;
            case drawWall:
                scene.drawWall(node);
                isNodesUpdateRequired = true;
                break;
            default:
                break;
        }
    }

    public void checkAndErase(Node node) {
        switch (remModel.getEditMode()) {
            case eraseNode:
                scene.eraseWall(node);
                isNodesUpdateRequired = true;
                break;
            case eraseWall:
                scene.eraseWallZ(node);
                isNodesUpdateRequired = true;
                break;
            default:
                break;
        }
    }

    @Override
    public boolean mouseCursorPosCallback(long handle, double xPos, double yPos) {
        if (discoveryWorkspace.state().active()) {
            discoveryViewport.cursor(xPos, yPos);
            return false;
        }
        remModel.getMousePos().x = xPos;
        remModel.getMousePos().y = yPos;
        hoverPickPending = true;

        float deltaX = 0;
        float deltaY = 0;

        if (lastMousePos.x > 0 && lastMousePos.y > 0) {
            double  x       = xPos - lastMousePos.x;
            double  y       = yPos - lastMousePos.y;
            boolean rotateX = x != 0;
            boolean rotateY = y != 0;

            if (rotateX)
                deltaY = (float) x;
            if (rotateY)
                deltaX = (float) y;
        }
        if (rightButtonPress) {
            scene.orbitCameraAroundTarget(deltaX * Constants.MOUSE_SENSITIVITY,
                                           deltaY * Constants.MOUSE_SENSITIVITY, 0.0f);
        }

        lastMousePos.x = xPos;
        lastMousePos.y = yPos;

        return false;
    }

    /** Defers expensive ray picking to one pass per rendered frame using the latest cursor position. */
    private void processDeferredHoverPick() {
        if (nuklearController.isLeftMouseCaptureActive()) return;
        Vector3f cameraPosition = project.camera.getPosition();
        Vector3f cameraRotation = project.camera.getRotation();
        boolean cameraChanged = !cameraPosition.equals(lastHoverPickCameraPosition)
                || !cameraRotation.equals(lastHoverPickCameraRotation)
                || !project.camera.getProjectionMatrix().equals(lastHoverPickProjection);
        Vector2d mousePosition = remModel.getMousePos();
        if (!hoverPickPending && !cameraChanged
                && mousePosition.equals(lastHoverPickMousePos)) return;

        Node node = scene.getNodeFromMousePos(mousePosition.x, mousePosition.y);
        if (node != selectedNode) {
            if (selectedNode != null) selectedNode.setSelected(false);
            selectedNode = node;
            if (selectedNode != null) {
                selectedNode.setSelected(true);
                remModel.getCurrentMouseNodeIndex().set(selectedNode.getIndex());
                remModel.setCurrentMouseNode(selectedNode);
            }
            renderMatricesDirty = true;
        }

        PassiveObserver hoveredProbe = scene.getProbeForNode(node);
        if (hoveredProbe == null) {
            hoveredProbe = scene.getInterpolatedProbeFromMousePos(mousePosition.x, mousePosition.y);
        }
        scene.setHoveredProbe(hoveredProbe);
        lastHoverPickMousePos.set(mousePosition);
        lastHoverPickCameraPosition.set(cameraPosition);
        lastHoverPickCameraRotation.set(cameraRotation);
        lastHoverPickProjection.set(project.camera.getProjectionMatrix());
        hoverPickPending = false;
    }

    @Override
    public boolean mouseButtonCallback(long handle, int button, int action, int mods) {
        leftButtonPressed           = button == GLFW.GLFW_MOUSE_BUTTON_1 && action == GLFW.GLFW_PRESS;
        boolean leftButtonReleased = button == GLFW.GLFW_MOUSE_BUTTON_1 && action == GLFW.GLFW_RELEASE;

        Node node = scene.getNodeFromMousePos(remModel.getMousePos().x,
                                              remModel.getMousePos().y);
        Probe probe = scene.getProbeForNode(node);
        boolean ctrlLeftClick = button == GLFW.GLFW_MOUSE_BUTTON_1
                && (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrlLeftClick) {
            if (leftButtonPressed) handleCtrlComparisonSelection(node, probe);
            return true;
        }
        if (probe != null && (leftButtonPressed || leftButtonReleased)) {
            scene.selectProbe(probe);
            remModel.setCurrentMouseNode(node);
            remModel.getCurrentMouseNodeIndex().set(node.getIndex());
            return true;
        }
        if (leftButtonPressed || leftButtonReleased) {
            InterpolatedProbe interpolatedProbe = scene.getInterpolatedProbeFromMousePos(
                    remModel.getMousePos().x, remModel.getMousePos().y);
            if (interpolatedProbe != null) {
                scene.selectProbe(interpolatedProbe);
                return true;
            }
        }

        if (discoveryWorkspace.state().active()) {
            if (nuklearModel.getFocusedFrame() == null)
                discoveryViewport.button(button, action, glWindow.getWidth(), glWindow.getHeight());
            return false;
        }
        if (nuklearModel.getFocusedFrame() == null || scene.isNeighborhoodInspectionActive()) {
            rightButtonPress   = button == GLFW.GLFW_MOUSE_BUTTON_2 && action == GLFW.GLFW_PRESS;

            if (node != null) {
                if (leftButtonPressed)
                    checkAndErase(node);

                if (leftButtonReleased) {
                    switch (remModel.getNodeSelectionMode()) {
                        case none:
                            if (remModel.isSimulating() && selectedNode != null)
                                selectedNode.incPosition(new Vector3d(0, 0, project.rainModel.getForce()));
                            break;
                        case occupancy:
                            remModel.setNodeSelectionMode(NodeSelectionMode.none);
                            remModel.getSelectedOccupancy().setNode(node);
                            nuklearModel.showFrame(Frame.identity);
                            break;
                        case ignitionNode:
                            remModel.setNodeSelectionMode(NodeSelectionMode.none);
                            project.detectorModel.setIgnitorNode(node.getIndex());
                            nuklearModel.showFrame(Frame.detector);
                            break;
                        case detectorNode:
                            remModel.setNodeSelectionMode(NodeSelectionMode.none);
                            project.detectorModel.setDetectorNode(node.getIndex());
                            nuklearModel.showFrame(Frame.detector);
                            break;
                        default:
                            break;
                    }

                    mouseReleaseWall(node);
                }
            }
        } else {
            leftButtonPressed  = false;
            rightButtonPress   = false;
        }
        return false;
    }

    /** Handles Ctrl-click comparison assignment without changing normal probe selection. */
    private void handleCtrlComparisonSelection(Node node, Probe rawProbe) {
        PassiveObserver candidate = rawProbe;
        if (candidate == null) {
            candidate = scene.getInterpolatedProbeFromMousePos(
                    remModel.getMousePos().x, remModel.getMousePos().y);
        }
        if (candidate != null) {
            if (!candidate.getId().startsWith(ProbeDefinition.GENERATED_ID_PREFIX)) {
                remModel.setCustomStatus("Only generated probes can be compared.");
            } else if (!scene.toggleComparisonProbe(candidate)) {
                remModel.setCustomStatus("Comparison pair is full.");
            }
            return;
        }
        if (scene.getPreviewProbeForNode(node) != null
                || scene.getPreviewInterpolatedProbeFromMousePos(
                        remModel.getMousePos().x, remModel.getMousePos().y) != null) {
            remModel.setCustomStatus("Preview probes cannot be compared.");
        }
    }

    @Override
    public boolean mouseScrollCallback(long handle, double xOffset, double yOffset) {
        if (nuklearModel.isMouseOverAnyFrame()) return false;
        if (discoveryWorkspace.state().active()) {
            discoveryViewport.scroll(-yOffset * Constants.CAMERA_MOVE_SPEED);
            return false;
        }
        scene.zoomCameraAroundTarget(-yOffset * Constants.CAMERA_MOVE_SPEED);
        float incCamera = keyEvents != null && keyEvents.isCtrlPressed ? 2 : 12;
        if (!scene.isNeighborhoodInspectionActive()) {
            project.camera.movePosition(
                    (float) (incCamera * -xOffset * Constants.CAMERA_MOVE_SPEED), 0, 0);
        }

        return false;
    }

    @Override
    public boolean mouseCursorEnterCallback(long handle, boolean entered) {
        return false;
    }

    @Override
    public boolean charCallback(long handle, int unicode) {
        return false;
    }

    @Override
    public boolean keyContinuousCallback(long handle, int key, KeyEvents keyEvents) {
        if (discoveryWorkspace.state().active()) return false;

        this.keyEvents = keyEvents;

        float incCamera = keyEvents.isShiftPressed ? 2 : 8;
        float inc       = keyEvents.isCtrlPressed ? (keyEvents.isShiftPressed ? 0.2f : 1.0f) : 0f;

        if (!(keyEvents.isCtrlPressed || keyEvents.isAltPressed || keyEvents.isShiftPressed)) {
            switch (key) {
                case GLFW.GLFW_KEY_W:
                    moveCamera(0, 0, -incCamera);
                    break;
                case GLFW.GLFW_KEY_S:
                    moveCamera(0, 0, incCamera);
                    break;
                case GLFW.GLFW_KEY_A:
                    moveCamera(-incCamera, 0, 0);
                    break;
                case GLFW.GLFW_KEY_D:
                    moveCamera(incCamera, 0, 0);
                    break;
                case GLFW.GLFW_KEY_Z:
                    moveCamera(0, -incCamera, 0);
                    break;
                case GLFW.GLFW_KEY_X:
                    moveCamera(0, incCamera, 0);
                    break;
            }
        }

        switch (key) {
            case GLFW.GLFW_KEY_LEFT:
                switch (remModel.getTranslationMode()) {
                    case light:
                        if (remModel.getSelectedLight() != null)
                            remModel.getSelectedLight().getPosBuff().x -= inc;
                        break;
                    case occupancy:
                        if (remModel.getSelectedOccupancy() != null)
                            remModel.getSelectedOccupancy().setAddAccX(-TransportSystem.getInstance().getAccelerationGain());
                        break;
                    default:
                        break;
                }
                break;
            case GLFW.GLFW_KEY_RIGHT:
                switch (remModel.getTranslationMode()) {
                    case light:
                        if (remModel.getSelectedLight() != null)
                            remModel.getSelectedLight().getPosBuff().x += inc;
                        break;
                    case occupancy:
                        if (remModel.getSelectedOccupancy() != null)
                            remModel.getSelectedOccupancy().setAddAccX(TransportSystem.getInstance().getImpulseGain());
                        break;
                    default:
                        break;
                }
                break;
            case GLFW.GLFW_KEY_UP:
                switch (remModel.getTranslationMode()) {
                    case normal:
                        if (keyEvents.isCtrlPressed) {
                            remModel.getCurrentMouseNodeIndex().z++;
                            if (remModel.getCurrentMouseNodeIndex().z >= project.getDimensionZ() - 2)
                                remModel.getCurrentMouseNodeIndex().z = project.getDimensionZ() - 2;
                            updateSelectedNode();

                            if (project.getDimensionZ() > 1) {
                                settings.setVisibleIndexZ(settings.getVisibleIndexZ() + 1);
                                if (settings.getVisibleIndexZ() >= project.getDimensionZ() - 2)
                                    settings.setVisibleIndexZ(project.getDimensionZ() - 2);
                                isNodesUpdateRequired = true;
                            }
                        }
                        break;
                    case alpha:
                        settings.incAlpha();
                        break;
                    case intensity:
                        settings.incIntensity();
                        break;
                    case light:
                        if (remModel.getSelectedLight() != null)
                            remModel.getSelectedLight().getPosBuff().z -= inc;
                        break;
                    case occupancy:
                        break;
                    default:
                        break;
                }
                break;
            case GLFW.GLFW_KEY_DOWN:
                switch (remModel.getTranslationMode()) {
                    case normal:
                        if (keyEvents.isCtrlPressed) {
                            remModel.getCurrentMouseNodeIndex().z--;
                            if (remModel.getCurrentMouseNodeIndex().z < 1)
                                remModel.getCurrentMouseNodeIndex().z = 1;
                            updateSelectedNode();

                            if (project.getDimensionZ() > 1) {
                                settings.setVisibleIndexZ(settings.getVisibleIndexZ() - 1);
                                if (settings.getVisibleIndexZ() < 1) settings.setVisibleIndexZ(1);
                                isNodesUpdateRequired = true;
                            }
                        }
                        break;
                    case alpha:
                        settings.decAlpha();
                        break;
                    case intensity:
                        settings.decIntensity();
                        break;
                    case light:
                        if (remModel.getSelectedLight() != null)
                            remModel.getSelectedLight().getPosBuff().z += inc;
                        break;
                    default:
                        break;
                }
                break;
            case GLFW.GLFW_KEY_PAGE_UP:
                if (Objects.requireNonNull(remModel.getTranslationMode()) == TranslationMode.light) {
                    if (remModel.getSelectedLight() != null)
                        remModel.getSelectedLight().getPosBuff().y += inc;
                }
                break;
            case GLFW.GLFW_KEY_PAGE_DOWN:
                if (Objects.requireNonNull(remModel.getTranslationMode()) == TranslationMode.light) {
                    if (remModel.getSelectedLight() != null)
                        remModel.getSelectedLight().getPosBuff().y -= inc;
                }
                break;
            case GLFW.GLFW_KEY_KP_4:
                if (keyEvents.isCtrlPressed) {
                    scene.orbitCameraAroundTarget(0.0f, inc, 0.0f);
                }
                break;
            case GLFW.GLFW_KEY_KP_6:
                if (keyEvents.isCtrlPressed) {
                    scene.orbitCameraAroundTarget(0.0f, -inc, 0.0f);
                }
                break;
            case GLFW.GLFW_KEY_KP_2:
                if (keyEvents.isCtrlPressed) {
                    scene.orbitCameraAroundTarget(-inc, 0.0f, 0.0f);
                }
                break;
            case GLFW.GLFW_KEY_KP_8:
                if (keyEvents.isCtrlPressed) {
                    scene.orbitCameraAroundTarget(inc, 0.0f, 0.0f);
                }
                break;
            case GLFW.GLFW_KEY_KP_3:
//                if (keyEvents.isCtrlPressed) {
//                    scene.orbitCameraAroundTarget(0.0f, 0.0f, inc);
//                }
                break;
            case GLFW.GLFW_KEY_KP_1:
//                if (keyEvents.isCtrlPressed) {
//                    scene.orbitCameraAroundTarget(0.0f, 0.0f, -inc);
//                }
                break;
            case GLFW.GLFW_KEY_KP_5:
                if (keyEvents.isCtrlPressed) {
                    scene.resetCameraOrbit();
                }
                break;
            case GLFW.GLFW_KEY_SPACE:
                if (remModel.isSimulating() && selectedNode != null)
                    selectedNode.incPosition(new Vector3d(0, 0, project.rainModel.getForce()));
                //                    selectedNode.addForce(new Vector3d(0, 0, qfsProject.rainModel
                //                    .getForce()));
                break;
        }


        return false;
    }

    @Override
    public boolean keyCallback(long handle, int key, int scanCode, int action, int mods) {
        if (discoveryWorkspace.state().active()) {
            if (action == GLFW.GLFW_RELEASE) {
                switch (key) {
                    case GLFW.GLFW_KEY_TAB -> {
                        var nodes = discoveryWorkspace.workspace().snapshot()
                                .geometry().model().sortedNodes();
                        if (!nodes.isEmpty()) {
                            Integer selected = discoveryWorkspace.viewState().selectedNodeId();
                            int index = 0;
                            for (int i = 0; i < nodes.size(); i++)
                                if (Objects.equals(nodes.get(i).id(), selected)) index = i;
                            int direction = (mods & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1;
                            discoveryWorkspace.viewState().setSelectedNodeId(
                                    nodes.get(Math.floorMod(index + direction, nodes.size())).id());
                        }
                    }
                    case GLFW.GLFW_KEY_F -> discoveryViewport.frameGeometry();
                    case GLFW.GLFW_KEY_P -> discoveryWorkspace.viewState().setOrthographic(
                            !discoveryWorkspace.viewState().orthographic());
                    case GLFW.GLFW_KEY_ESCAPE -> discoveryWorkspace.deactivateWorkspace();
                    default -> { }
                }
            }
            return false;
        }
        boolean isReleased = action == GLFW.GLFW_RELEASE;

        boolean isShiftPressed = (mods & GLFW.GLFW_MOD_SHIFT) == GLFW.GLFW_MOD_SHIFT;

        if (isReleased) {
            switch (key) {
                case GLFW.GLFW_KEY_ESCAPE:
                    if (scene.isNeighborhoodInspectionActive()) {
                        scene.exitNeighborhoodInspection();
                        return true;
                    }
                    break;
                case GLFW.GLFW_KEY_O:
                    if (keyEvents.isCtrlPressed) {
                        project.openFile();
                    }
                    break;
                case GLFW.GLFW_KEY_S:
                    if (keyEvents.isCtrlPressed) {
                        project.saveToFile();
                    }
                    break;
                case GLFW.GLFW_KEY_N:
                    remModel.setTranslationMode(TranslationMode.normal);
                    break;
                case GLFW.GLFW_KEY_R:
                    remModel.setTranslationMode(RemModel.TranslationMode.rain);
                    nuklearModel.toggleFrame(Frame.rain);
                    break;
                case GLFW.GLFW_KEY_G:
                    remModel.setTranslationMode(TranslationMode.alpha);
                    break;
                case GLFW.GLFW_KEY_I:
                    remModel.setTranslationMode(RemModel.TranslationMode.intensity);
                    break;
                case GLFW.GLFW_KEY_L:
                    remModel.setTranslationMode(RemModel.TranslationMode.light);
                    break;
                case GLFW.GLFW_KEY_F4:
                    scene.toggleAnimation();
                    renderMatricesDirty = true;
                    break;
                case GLFW.GLFW_KEY_F5:
                    scene.updateWalls();
                    break;
                case GLFW.GLFW_KEY_F6:
                    if (isShiftPressed) {
                        // Release all nodes that was fixed by the list of customNodeFixed.
                        for (Node node : customNodeFixed) {
                            //node.setFixed(false);
                            node.setType(EntityType.NODE);
                        }
                    } else {
                        if (!selectedNode.isFixed()) {
                            // Set the node position and make it fixed.
                            selectedNode.getPosBuff().z = project.rainModel.getForce();
                            //selectedNode.setFixed(true);
                            selectedNode.setType(EntityType.FIXED);
                            // Remember that this node is set fixed.
                            customNodeFixed.add(selectedNode);
                        } else {
                            // Release the node.
                            //selectedNode.setFixed(false);
                            selectedNode.setType(EntityType.NODE);
                        }
                    }
                    break;
                case GLFW.GLFW_KEY_F8:
                    remModel.toggleSimulation();
                    break;
                case GLFW.GLFW_KEY_F10:
                    project.reset();
                    slitWall.reset();
                    break;
                case GLFW.GLFW_KEY_F11:
                    Screenshot.prepareScreenshot(4, 4, 0xFF);
                    break;
                case GLFW.GLFW_KEY_F12:
                    settings.setSliceType(
                            settings.getSliceType() == SliceType.none ? SliceType.sliceZ : SliceType.none);
                    updateNodesProperties();
                    break;
                case GLFW.GLFW_KEY_F7:
                    if (lastSelectedNode != null) {
                        //lastSelectedNode.setFixed(false);
                        lastSelectedNode.setType(EntityType.NODE);
                        lastSelectedNode = null;
                    } else if (remModel.isSimulating() && selectedNode != null && !scene.getLattice().isMechanicsOnly()) {
                        Vector3d p = selectedNode.getPosition();
                        selectedNode.setPosition(new Vector3d(p.x, p.y, project.rainModel.getForce()));
                        //selectedNode.setFixed(true);
                        selectedNode.setType(EntityType.FIXED);
                        lastSelectedNode = selectedNode;
                    }
                    break;
                case GLFW.GLFW_KEY_P:
                    if (remModel.isSimulating() && selectedNode != null && !scene.getLattice().isMechanicsOnly()) {
                        if (selectedNode.isFixed()) {
                            ///selectedNode.setFixed(false);
                            selectedNode.setType(EntityType.NODE);
                        } else {
                            //selectedNode.setFixed(true);
                            selectedNode.setType(EntityType.FIXED);
                            selectedNode.setPosition(new Vector3d(0, 0, project.rainModel.getForce()));
                        }
                    }
                    break;
            }
        }

        return false;
    }

    private void moveCamera(float x, float y, float z) {
        project.camera.movePosition(
                x * Constants.CAMERA_MOVE_SPEED,
                y * Constants.CAMERA_MOVE_SPEED,
                z * Constants.CAMERA_MOVE_SPEED
                                   );
    }

    private void updateSelectedNode() {
        if (selectedNode != null) {
            selectedNode.setSelected(false);
        }

        selectedNode = scene.getLattice().getNode(remModel.getCurrentMouseNodeIndex());
        if (selectedNode != null) {
            selectedNode.setSelected(true);
        }
        renderMatricesDirty = true;
    }

    private void updateTerrain() {
        if (project.terrain.isVisible())
            project.terrain.updateTransformationMatrix();
    }

    private void waitForRenderingReady() {
        while (!isRenderingReady) {
            System.out.println("waitForRenderingReady()");
            Utils.sleepMS(1);
        }
    }

    /** Waits only for shutdown or another terminal transition. */
    private void waitForNextFrameIsReady() {
        frameCalculation.awaitComplete();
    }

    /** Publishes a completed back buffer without ever waiting for a running calculation. */
    private boolean publishNextFrameIfReady() {
        if (!frameCalculation.tryComplete()) return false;
        Node.swapBuffer();
        return true;
    }

    private void calcNextFrame(boolean singleStepUpdate) {
        numFrameCalculation++;
        FeasibilityPilotTelemetry.getInstance().beginUpdate();

        try {
            frameCalculation.start(() -> {
                try {
                    PerformanceMonitor.start(Measurement.totalCPURenderTime);

                    Object[] nodeArray = scene.getLattice().getNodeArray();
                    Object[] entityArray = scene.getLattice().getEntityArray();
                    if (nodeArray == null || entityArray == null) {
                        throw new IllegalStateException(
                                "Lattice arrays are not ready for calculation.");
                    }
                    int requestedThreads = appSettings.getNumThreads();

                    PerformanceMonitor.start(Measurement.physics);

                    if (waitAfterReset <= 0) {
                        simulateRain();
                        simulateOccupancies();
                    } else {
                        waitAfterReset--;
                    }
                    scene.updateProbeMeasurements(transportSystem);
                    simulateDetection();

                    int nodeWorkers = workerCount(requestedThreads, nodeArray.length);
                    if (nodeWorkers > 0) {
                        scene.processRemFieldEngine(nodeWorkers,
                                Math.ceilDiv(nodeArray.length, nodeWorkers));
                        scene.waitForAllTasksReady();
                    }

                    if(!scene.getLattice().validateMechanicalState()) return;
                    scene.getLattice().getDisturbance().completedStep();
                    if (remModel.isSimulating())
                        PerformanceMonitor.stop(Measurement.physics);
                    else
                        PerformanceMonitor.reset(Measurement.physics);

                    PerformanceMonitor.start(Measurement.updateMatrix);

                    int entityWorkers = workerCount(requestedThreads, entityArray.length);
                    if (entityWorkers > 0) {
                        scene.updateMatrix(entityWorkers,
                                Math.ceilDiv(entityArray.length, entityWorkers));
                        scene.waitForAllTasksReady();
                    }

                    samplePassiveDetectors();
                    FeasibilityPilotTelemetry telemetry =
                            FeasibilityPilotTelemetry.getInstance();
                    if (telemetry.isEnabled()) {
                        telemetry.endUpdate(DetectorExperimentController.getInstance()
                                .getUpdatesRecorded());
                    }
                    PerformanceMonitor.stop(Measurement.updateMatrix);
                    PerformanceMonitor.stop(Measurement.totalCPURenderTime);
                } finally {
                    if (singleStepUpdate) remModel.finishSingleStep();
                    numFrameCalculation--;
                    if (numFrameCalculation != 0) {
                        System.out.println("Error: numPhysicsFrameCalculation: "
                                + numFrameCalculation);
                    }
                }
            });
        } catch (Throwable failure) {
            numFrameCalculation--;
            throw failure;
        }
    }

    private static int workerCount(int requestedThreads, int itemCount) {
        if (itemCount <= 0) return 0;
        return Math.min(Math.max(1, requestedThreads), itemCount);
    }

    /** Keeps the complete finite lattice inside the camera far plane. */
    private void ensureCameraFarPlaneContainsLattice() {
        double scale = Math.abs(settings.getScale()) * Constants.EP_DEFAULT_DISTANCE;
        double halfX = Math.max(0, project.getDimensionX() - 1) * scale * 0.5;
        double halfY = Math.max(0, project.getDimensionY() - 1) * scale * 0.5;
        double halfZ = Math.max(0, project.getDimensionZ() - 1) * scale * 0.5;
        double radius = Math.sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ);
        Vector3f cameraPosition = project.camera.getPosition();
        double distanceToOrigin = Math.sqrt(cameraPosition.x * cameraPosition.x
                + cameraPosition.y * cameraPosition.y + cameraPosition.z * cameraPosition.z);
        double required = distanceToOrigin + radius + Math.max(1.0, scale);
        if (Double.isFinite(required) && required > project.camera.getZ_far() * 0.95) {
            project.camera.setZ_far((float) Math.min(Float.MAX_VALUE, required * 1.10));
        }
    }

    private void resetOccupancies() {
        if(scene.getLattice().isMechanicsOnly()) return;
        for (Occupancy occupancy : project.occupancies) {
            occupancy.reset();
        }
    }

    /** Keeps the render/picking matrix buffer current without advancing simulation state. */
    private void refreshRenderMatrices() {
        Object[] entityArray = scene.getLattice().getEntityArray();
        if (entityArray == null || entityArray.length == 0) return;
        int numThreads = workerCount(appSettings.getNumThreads(), entityArray.length);
        int segmentSize = Math.ceilDiv(entityArray.length, numThreads);
        Node.swapBuffer();
        scene.updateMatrix(numThreads, segmentSize, true);
        scene.waitForAllTasksReady();
    }

    private void simulateOccupancies() {
        if(scene.getLattice().isMechanicsOnly()) {
            if (!remModel.isSimulationUpdateActive() || updateLimitReached) return;
            for (Occupancy occupancy : project.occupancies) {
                if (!occupancy.isOccupied()) continue;
                Node node=scene.getLattice().getNodeByIndex(occupancy.getNodeIndex());
                if (node == null) continue;
                occupancy.setNode(node);
                transportSystem.update(occupancy);
                occupancy.update();
            }
            return;
        }
        if (remModel.isSimulationUpdateActive() && !updateLimitReached) {
            GravityExperimentController controller = GravityExperimentController.getInstance();
            controller.beginSimulationUpdate();
            GravityPressureExperimentController.getInstance().beginSimulationUpdate();

            for (Occupancy occupancy : project.occupancies) {
                if (occupancy.isOccupied()) {
                    Node node = Lattice.getInstance().getNodeByIndex(occupancy.getNodeIndex());
                    occupancy.setNode(node);

                    transportSystem.update(occupancy);
                    if (transportSystem.getLogType() == LogType.occupancy) {
                        transportSystem.printOccupancy(occupancy);
                    }
                    occupancy.update();
                }
            }
            if (transportSystem.getLogType() == LogType.distanceG && project.occupancies.size() == 2) {
                Occupancy occupancyA = project.occupancies.getFirst();
                Occupancy occupancyB = project.occupancies.get(1);
                transportSystem.printDistanceG(occupancyA, occupancyB);
            }
            if (updateLimit != null
                    && !DetectorExperimentController.getInstance().isEnabled()
                    && controller.getUpdatesRecorded() >= updateLimit) {
                updateLimitReached = true;
            }
        }
    }

    private void samplePassiveDetectors() {
        if(scene.getLattice().isMechanicsOnly()) return;
        DetectorExperimentController controller =
                DetectorExperimentController.getInstance();
        if (!remModel.isSimulationUpdateActive() || updateLimitReached
                || !controller.isEnabled()) {
            return;
        }
        double sourcePhase = project.occupancies.isEmpty()
                             ? Double.NaN
                             : project.occupancies.getFirst()
                                     .getIdentity().getPhase().z;
        controller.sample(scene.getLattice(), sourcePhase);
        if (updateLimit != null
                && controller.getUpdatesRecorded() >= updateLimit) {
            updateLimitReached = true;
        }
    }

    private void simulateDetection() {
        if(scene.getLattice().isMechanicsOnly()) return;
        if (nuklearModel.isFrameVisible(Frame.detector)) {
            Node node = scene.getLattice().getNodeByIndex(detectorModel.getIgnitorNode());
            if (node != null) {
                node.setCustomScale(2.0);
                node.setCustomColor(new Vector4f(0f, 1f, 0f, 1f));
                if (detectorModel.isIgnited())
                    detectorModel.ignite(node, project.rainModel.getForce());
            }

            node = scene.getLattice().getNodeByIndex(detectorModel.getDetectorNode());
            if (node != null) {
                node.setCustomScale(2.0);
                node.setCustomColor(new Vector4f(1f, 1f, 0f, 1f));
                detectorModel.detect(node);
            }
        }
    }

    private void simulateRain() {
        if(scene.getLattice().isMechanicsOnly()) return;
        if (remModel.isSimulationUpdateActive() && project.rainModel.isActive()) {
            Vector3i index = new Vector3i();

            for (int i = 0; i < project.rainModel.getIterations(); i++) {
                index.x = (int) (Math.clamp(Math.random() * project.getDimensionX(), 2,
                                            project.getDimensionX() - 2));
                index.y = (int) (Math.clamp(Math.random() * project.getDimensionY(), 2,
                                            project.getDimensionY() - 2));
                index.z = settings.getVisibleIndexZ();

                Node node = scene.getLattice().getNodeByIndex(index);
                if (node != null)
                    node.incPosition(new Vector3d(0, 0, Math.random() * project.rainModel.getForce()));
            }

        }
    }

    public void resetMonitoringDeadThreads() {
        currentTime = System.currentTimeMillis();
    }

    private void updateNodesProperties() {
        System.out.println("Simulator.updateNodesProperties()");
        renderMatricesDirty = true;
        renderer.cleanupNodes();

        if (settings.getSliceType() == SliceType.identitiesOnly) {
            for (Entity entity : scene.getLattice().getNonNodeRenderEntities()) {
                renderer.processEntity(entity);
            }
        }

        Performance.numVisibleNodes = 0;

        for (Entity entity : scene.getLattice().getEntities()) {
            if (entity.getClass() == Node.class) {
                Node node = (Node) entity;
                int  i    = node.getIndex().x;
                int  j    = node.getIndex().y;
                int  k    = node.getIndex().z;
                int  dimX = project.getDimensionX();
                int  dimY = project.getDimensionY();
                int  dimZ = project.getDimensionZ();

                if (i != -1 || j != -1 || k != -1) {
                    boolean isBoundingLine =
                            i == 0 && j == 0 ||
                            i == 0 && j == project.getDimensionY() - 1 ||
                            i == project.getDimensionX() - 1 && j == 0 ||
                            i == project.getDimensionX() - 1 && j == project.getDimensionY() - 1 ||

                            i == 0 && k == 0 ||
                            i == 0 && k == project.getDimensionZ() - 1 ||
                            i == project.getDimensionX() - 1 && k == 0 ||
                            i == project.getDimensionX() - 1 && k == project.getDimensionZ() - 1 ||

                            j == 0 && k == 0 ||
                            j == 0 && k == project.getDimensionZ() - 1 ||
                            j == project.getDimensionY() - 1 && k == 0 ||
                            j == project.getDimensionY() - 1 && k == project.getDimensionZ() - 1;

                    boolean isVisible = switch (settings.getSliceType()) {
                        case none -> isBoundingLine ||
                                     (i > 0 && i < dimX - 1 && j > 0 && j < dimY - 1 && k > 0 &&
                                      k < dimZ - 1);
                        case sliceX -> isBoundingLine || (i == settings.getVisibleIndexX());
                        case sliceY -> isBoundingLine || (j == settings.getVisibleIndexY());
                        case sliceZ -> isBoundingLine || (k == settings.getVisibleIndexZ());
                        case sliceXZ -> isBoundingLine || (i == settings.getVisibleIndexX() &&
                                                           k == settings.getVisibleIndexZ());
                        case sliceYZ -> isBoundingLine || (j == settings.getVisibleIndexY() &&
                                                           k == settings.getVisibleIndexZ());
                        case sliceXY -> isBoundingLine || (i == settings.getVisibleIndexX() &&
                                                           j == settings.getVisibleIndexY());
                        case physicalXY, physicalXZ, physicalYZ -> remframework.opengl.model.PhysicalCrossSection.contains(
                            node.getEquilibriumPosition(),scene.getLattice().getPhysicalSliceCenter(),settings.getSliceType(),settings.getPhysicalSliceThickness());
                        case identitiesOnly -> false;
                    };

                    node.setVisible(isVisible || node.isHiLighted());
                    if (node.isVisible()) Performance.numVisibleNodes++;
                }

            }
            renderer.processEntity(entity);
        }

        scene.invalidateRenderInstanceDataLayout();
        createIsotropyProbe();
    }

    private void createIsotropyProbe() {
        scene.clearProbes();
        if(scene.getLattice().isMechanicsOnly()) {
            for(var definition:project.getProbeDefinitions())
                if(definition.getDetectorType()==remframework.opengl.model.ProbeDetectorType.DIAMOND_INTERPOLATED)
                    scene.createProbe(definition,false);
            return;
        }
        Object[] nodeArray = scene.getLattice().getNodeArray();
        if (nodeArray == null || nodeArray.length == 0) return;
        String filename = project.getCurrentFilename();
        boolean detectGradient8 = !filename.isEmpty()
                && "detectGradient8.qfs".equals(Path.of(filename).getFileName().toString());
        if (detectGradient8 && project.getProbeDefinitions().stream()
                .noneMatch(definition -> "detectGradient8-receiver".equals(definition.getId()))) {
            project.getProbeDefinitions().add(new ProbeDefinition(
                    "detectGradient8-receiver", new Vector3i(130, 140, 0),
                    new Vector4f(0.2f, 0.8f, 1.0f, 1.0f)));
        }
        List<ProbeDefinition> generated = project.getProbeDefinitions().stream()
                .filter(d->d.isGenerated() && d.getDetectorType()!=remframework.opengl.model.ProbeDetectorType.DIAMOND_INTERPOLATED).toList();
        Vector3i centerIndex = new Vector3i(project.getDimensionX() / 2,
                                            project.getDimensionY() / 2,
                                            project.getDimensionZ() / 2);
        Node center = scene.getLattice().getNodeByIndex(centerIndex);
        if (!ProbePlacement.generatedDefinitionsCompatible(center, generated,
                project.getDimensionZ() > 1, ProbePlacement.DEFAULT_TOLERANCES)) {
            project.replaceGeneratedProbeDefinitions(List.of());
        }
        for (ProbeDefinition definition : project.getProbeDefinitions()) {
            scene.createProbe(definition, false);
        }
    }

    private void updateNodesArray() {
        System.out.println("simulator.updateNodesArray();");
        //resetOccupancies();
        updateNodesProperties();
        //resetOccupancies();
        scene.update();
        if(!scene.getLattice().isMechanicsOnly()) DetectorExperimentController.getInstance().resolveNodes(
                scene.getLattice());
        for (Occupancy occupancy : project.occupancies) {
            Node node = scene.getLattice().getNodeByIndex(occupancy.getNodeIndex());
            if (node != null) occupancy.setNode(node);
        }
        FeasibilityPilotTelemetry.getInstance().markLatticeInitialized();
//        resetOccupancies();
        nuklearModel.closeFrame(Frame.progress);

        settings.setVisibleIndexX(project.getDimensionX() / 2);
        settings.setVisibleIndexY(project.getDimensionY() / 2);
        settings.setVisibleIndexZ(project.getDimensionZ() / 2);

        remModel.getCurrentMouseNodeIndex().x = project.getDimensionX() / 2;
        remModel.getCurrentMouseNodeIndex().y = project.getDimensionY() / 2;
        remModel.getCurrentMouseNodeIndex().z = project.getDimensionZ() / 2;
        remModel.setCurrentMouseNode(scene.getLattice().getNodeByIndex(remModel.getCurrentMouseNodeIndex()));
    }

    private void checkSettingsChanged() {
        if (project.isChanged() || appSettings.isChanged()) {
            System.out.println("simulator.projectChanged: " + project.isChanged());
            System.out.println("simulator.appSettingsChanged: " + appSettings.isChanged());

            var selectedModel=remframework.lattice.LatticeManager.getInstance().selected();
            if(selectedModel.model()!=null && selectedModel.model().mechanicsOnly()) {
                var requestedSize=new org.joml.Vector3i();project.getDimension(requestedSize);
                var estimate=remframework.lattice.DiamondMemoryBudget.estimate(requestedSize,Long.MAX_VALUE);
                if(!estimate.allowed()) {
                    scene.getLattice().reportDiamondSizeFailure(estimate);
                    remModel.stopSimulation();remModel.setLoadingReady(true);
                    nuklearModel.closeFrame(Frame.progress);
                    nuklearModel.showErrorDialog("Cannot Build Diamond Lattice",estimate.error());
                    return;
                }
            }
            setCustomTitle(project.getCurrentFilename());
            nuklearModel.showFrame(Frame.progress);
            try {
                project.terrain.getMaterial().setTexture(new Texture(loader.loadTexture(
                        Constants.BASE_PATH + project.terrain.getTerrainTextureFilePath())));
            }
            catch (Exception e) {
                String message = String.format("Texture file '%s' not found!",
                                               project.terrain.getTerrainTextureFilePath());
                nuklearModel.showErrorDialog("Loading terrain texture", message);
            }
            isLoadingStarted = true;
            System.out.println("isLoadingStarted: " + isLoadingStarted);
            remModel.setLoadingReady(false);
            // This runs at a completed frame boundary, with no pending physics workers.
            remModel.stopSimulation();
            renderer.cleanupNodes();
            offscreen3DRenderer.stop();
            renderState = null;
            scene.releaseLattice();
            System.gc(); // Best effort; capacity is measured again below.
            offscreen3DRenderer.start();
            if(selectedModel.model()!=null && selectedModel.model().mechanicsOnly()) {
                var requestedSize=new org.joml.Vector3i();project.getDimension(requestedSize);
                var estimate=remframework.lattice.DiamondMemoryBudget.estimate(requestedSize);
                if(!estimate.allowed()) {
                    scene.getLattice().reportDiamondSizeFailure(estimate);
                    remModel.setLoadingReady(true);
                    isLoadingStarted=false;
                    nuklearModel.closeFrame(Frame.progress);
                    nuklearModel.showErrorDialog("Cannot Build Diamond Lattice",estimate.error()
                        +"\nPrevious lattice released. Reduce dimensions or choose another model and retry.");
                    return;
                }
            }
            scene.build();
            resetOccupancies();
            waitAfterReset = 0;
        }

        if (!isLoadingStarted) {
            if (project.isFileSaved()) {
                project.setFileSaved(false);
                setCustomTitle(project.getCurrentFilename());
            }

            if (remModel.isChanged()) {
                remModel.setChanged(false);
                isNodesUpdateRequired = true;
            }

            if (settings.isChanged()) {
                settings.setChanged(false);
                updateTerrain();
                isNodesUpdateRequired = true;
            }

            if (slitWall.isChanged()) {
                slitWall.setChanged(false);
                if (slitWall.isActive()) scene.drawSlitWall();
                else scene.clearHiLightNodes();
                isNodesUpdateRequired = true;
            }

            if (slitWall.isUpdated()) {
                slitWall.setUpdated(false);
                scene.makeWallFromHiLightNodes();
                isNodesUpdateRequired = true;
            }

            if (isNodesUpdateRequired) {
                updateNodesProperties();
                isNodesUpdateRequired = false;
            }
        }
    }

    @Override
    public void cleanUp() {
        System.out.println("simulator.cleanUp();");
        Throwable pendingFrameFailure = null;
        try {
            waitForNextFrameIsReady();
        } catch (Throwable failure) {
            pendingFrameFailure = failure;
        }
        GravityExperimentController controller = GravityExperimentController.getInstance();
        GravityPressureExperimentController pressureController =
                GravityPressureExperimentController.getInstance();
        DetectorExperimentController detectorController =
                DetectorExperimentController.getInstance();
        controller.close();
        pressureController.close();
        detectorController.close();
        transportSystem.closeReceiverStatusLogging();
        FeasibilityPilotTelemetry.getInstance().close();
        discoveryWorkspace.close();
        if (discoveryViewport != null) discoveryViewport.close();
        scene.stopAnimation();
        offscreen3DRenderer.stop();
        renderer.cleanup();
        loader.cleanup();
        super.cleanUp();
        if (updateLimit != null) {
            if (detectorController.getOutputFile() != null) {
                System.out.println("Passive detector experiment complete");
                System.out.println("Configured updates: " + updateLimit);
                System.out.println("Updates recorded: "
                        + detectorController.getUpdatesRecorded());
                System.out.println("Rows written: "
                        + detectorController.getRowsWritten());
                System.out.println("CSV: "
                        + detectorController.getOutputFile());
            } else if (pressureController.getOutputFile() != null) {
                System.out.println("Gravity Test 2 complete");
                System.out.println("Configured updates: " + updateLimit);
                System.out.println("Updates recorded: " + pressureController.getUpdatesRecorded());
                System.out.println("Rows written: " + pressureController.getRowsWritten());
                System.out.println("CSV: " + pressureController.getOutputFile());
            } else {
                System.out.println("Gravity Test 1 complete");
                System.out.println("Configured updates: " + updateLimit);
                System.out.println("Updates recorded: " + controller.getUpdatesRecorded());
                System.out.println("Measurement points: " + controller.getMeasurementPointCount());
                System.out.println("Rows written: " + controller.getRowsWritten());
                System.out.println("CSV: " + controller.getOutputFile());
            }
        }
        if (pendingFrameFailure instanceof RuntimeException runtimeException)
            throw runtimeException;
        if (pendingFrameFailure instanceof Error error) throw error;
        if (pendingFrameFailure != null)
            throw new IllegalStateException("Final calcNextFrame() failed.", pendingFrameFailure);
    }

    @Override
    public void render(boolean isAnimated) {
        if (Screenshot.isTakingScreenShot()) {
            Screenshot.updateScreenshotViewport();
        } else if (glWindow.isResize()) {
            GL11.glViewport(0, 0, glWindow.getWidth(), glWindow.getHeight());
        }

        // Input and Nuklear command generation must run before any 3D work. The GUI keeps
        // processing while a calculated back buffer is not ready for publication.
        beginGuiFrame(scene.isAnimated());
        publishNextFrameIfReady();
        boolean calculationPending = frameCalculation.isPending();

        if (discoveryWorkspace.state().active()) {
            if (!calculationPending
                    && scene.getLattice().applyPendingAbsorptionLayerChange()) {
                renderMatricesDirty = true;
            }
            PerformanceMonitor.start(Measurement.sendToGPU);
            discoveryWorkspace.phasedWorkflow().advanceGeometryTestFrame();
            discoveryViewport.render(glWindow.getWidth(), glWindow.getHeight());
            PerformanceMonitor.stop(Measurement.sendToGPU);
            finishGuiFrame();
            return;
        }

        // Scene rebuilds and other structural mutations are accepted only at a completed
        // frame boundary. GUI interaction itself is never delayed while one is pending.
        if (!calculationPending) {
            checkSettingsChanged();
            scene.applyPendingDiamondProbes();
            scene.applyPendingDiamondProbeArrival();
            if (scene.getLattice().applyPendingAbsorptionLayerChange()) {
                renderMatricesDirty = true;
            }
        }

        if (updateLimitReached) {
            glWindow.requestClose();
        }

        if (!calculationPending && isLoadingStarted) {
            // Make sure it is not doing any calculation while updating new scene.
            if (remModel.isLoadingReady()) {
                updateNodesArray();
                // Constructor-time child matrices predate the final root rotation. Initialize
                // both buffers before the newly loaded lattice is rendered for the first time.
                refreshRenderMatrices();
                renderMatricesDirty = false;
                boolean singleStepUpdate = remModel.consumeSingleStepRequest();
                if (remModel.isSimulating() || singleStepUpdate) {
                    pausedRenderMatricesCurrent = false;
                    calcNextFrame(singleStepUpdate);
                } else {
                    pausedRenderMatricesCurrent = true;
                }
                isLoadingStarted = false;
                System.out.println("isLoadingStarted: " + isLoadingStarted);
            }
        } else if (!calculationPending && !updateLimitReached) {
            waitForRenderingReady();
            resetMonitoringDeadThreads();

            // Animation changes frame input only at a completed calculation boundary.
            scene.runAnimation();
            if (scene.isAnimated()) renderMatricesDirty = true;
            ensureCameraFarPlaneContainsLattice();

            processDeferredHoverPick();
            for (PassiveObserver probe : scene.getProbes()) {
                probe.updateMarker();
            }
            for (PassiveObserver probe : scene.getPreviewProbes()) {
                probe.updateMarker();
            }
            // Ordinary probe/gradient render lists are filtered out during inspection.
            // Do not tear them down and rebuild them while the diagnostic view is active;
            // they are synchronized normally on the first frame after exit.
            if (!scene.isNeighborhoodInspectionActive()) {
                renderer.syncProbeEntities(scene.getProbeRenderEntities());
                renderer.syncGradientArrowEntities(scene.getGradientArrowRenderEntities());
            }

            // Meanwhile, start calculating the next frame while rendering.
            boolean singleStepUpdate = remModel.consumeSingleStepRequest();
            if (remModel.isSimulating() || singleStepUpdate) {
                if (scene.isRenderInstanceDataLayoutDirty()) refreshRenderMatrices();
                pausedRenderMatricesCurrent = false;
                renderMatricesDirty = false;
                calcNextFrame(singleStepUpdate);
            } else if (!pausedRenderMatricesCurrent || renderMatricesDirty || scene.isAnimated()) {
                refreshRenderMatrices();
                pausedRenderMatricesCurrent = true;
                renderMatricesDirty = false;
            } else {
                PerformanceMonitor.reset(Measurement.physics);
                PerformanceMonitor.reset(Measurement.updateMatrix);
                PerformanceMonitor.reset(Measurement.totalCPURenderTime);
            }
        }

        // Camera-only transitions remain responsive and read the stable published matrices.
        scene.updateCameraTransition(1.0 / 60.0);

        PerformanceMonitor.start(Measurement.sendToGPU);
        isRenderingReady = false;

        // Submit a complete membership/camera snapshot to the independent 3D context. The
        // display context presents its latest finished texture immediately and never waits for
        // this request to finish.
        long currentRenderStateVersion = renderer.stateVersion();
        if (renderState == null || renderStateVersion != currentRenderStateVersion) {
            renderState = renderer.snapshot();
            renderStateVersion = currentRenderStateVersion;
        }
        offscreen3DRenderer.request(renderState, project.camera, scene,
                glWindow.getWidth(), glWindow.getHeight());
        offscreen3DRenderer.presentLatest(glWindow.getWidth(), glWindow.getHeight());
        isRenderingReady = true;
        PerformanceMonitor.stop(Measurement.sendToGPU);

        finishGuiFrame();

        if (Screenshot.isTakingScreenShot()) {
            Screenshot.takeScreenShot();
        }
    }

}
