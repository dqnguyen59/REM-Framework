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
import remframework.opengl.model.lights.DirectionalLight;
import remframework.opengl.helpers.OBJFormatLoader;
import remframework.settings.AppSettings;
import remframework.settings.Project;
import remframework.settings.Settings;
import remframework.settings.SlitWallSettings;

import java.lang.Math;
import java.util.ArrayList;
import java.util.List;

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
    private ArrayList<Probe>    probes            = new ArrayList<>();
    private ArrayList<Probe>    previewProbes     = new ArrayList<>();
    private boolean             replacementProbePreviewActive;
    private Probe               selectedProbe;
    private Node                firstHiLightedNode;

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

    public void addProbe(Probe probe) {
        probes.add(probe);
    }

    /** Creates a renderer-only marker that references a live node without creating an occupancy. */
    public Probe createProbe(ProbeDefinition definition, boolean preview) {
        Probe probe = buildProbe(definition, preview);
        if (probe == null) return null;
        if (preview) previewProbes.add(probe);
        else probes.add(probe);
        return probe;
    }

    private Probe buildProbe(ProbeDefinition definition, boolean preview) {
        Node node = lattice.getNodeByIndex(definition.getNodeIndex());
        if (node == null) return null;
        Entity marker = new Entity(lattice.getBaseEntity(), lattice.getModelSphere(),
                                   new Vector3d(node.getPosition()), new Vector3d(),
                                   node.getScale() * (preview ? 2.0 : 2.75), EntityType.ENTITY);
        marker.setOverrideModelMaterial(true);
        marker.getMaterial().setDiffuseColor(definition.getDisplayColor());
        marker.getMaterial().setAmbientColor(definition.getDisplayColor());
        marker.getMaterial().setShininess(0.0f);
        Probe probe = new Probe(definition.getId(), node, marker, definition.getDisplayColor(),
                                definition.getPlacement());
        probe.updateMarker();
        return probe;
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
        previewProbes.clear();
        replacementProbePreviewActive = false;
    }

    public List<Entity> getProbeRenderEntities() {
        List<Entity> entities = new ArrayList<>();
        for (Probe probe : probes) {
            if (!replacementProbePreviewActive
                    || !probe.getId().startsWith(ProbeDefinition.GENERATED_ID_PREFIX)) {
                entities.add(probe.getMarker());
            }
        }
        for (Probe probe : previewProbes) entities.add(probe.getMarker());
        return entities;
    }

    /** Replaces previews with committed renderer-only markers for the supplied definitions. */
    public void commitProbeDefinitions(List<ProbeDefinition> definitions) {
        clearPreviewProbes();
        for (ProbeDefinition definition : definitions) createProbe(definition, false);
    }

    /** Atomically replaces generated probe markers while preserving manually created receivers. */
    public boolean replaceGeneratedProbeDefinitions(List<ProbeDefinition> definitions) {
        List<Probe> replacements = new ArrayList<>();
        for (ProbeDefinition definition : definitions) {
            Probe replacement = buildProbe(definition, false);
            if (replacement == null) return false;
            replacements.add(replacement);
        }
        if (selectedProbe != null && selectedProbe.getId().startsWith(ProbeDefinition.GENERATED_ID_PREFIX)) {
            selectedProbe.setSelected(false);
            selectedProbe = null;
        }
        probes.removeIf(probe -> probe.getId().startsWith(ProbeDefinition.GENERATED_ID_PREFIX));
        probes.addAll(replacements);
        clearPreviewProbes();
        return true;
    }

    public void clearProbes() {
        if (selectedProbe != null) selectedProbe.setSelected(false);
        selectedProbe = null;
        probes.clear();
    }

    /** Makes the committed probe the live diagnostic selection and refreshes its marker immediately. */
    public void selectProbe(Probe probe) {
        if (selectedProbe != null) selectedProbe.setSelected(false);
        selectedProbe = probe;
        if (selectedProbe != null) {
            selectedProbe.setSelected(true);
            selectedProbe.updateMarker();
        }
    }

    public Probe getProbeForNode(Node node) {
        if (node == null) return null;
        for (Probe probe : probes) {
            if (probe.getNode() == node) return probe;
        }
        return null;
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
            if (REMModel.isSimulating()) {
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
