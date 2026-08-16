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
 * File created on 09/07/2026
 */
package remframework.opengl.model;

import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;
import remframework.helpers.Constants;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.EntityType;
import remframework.opengl.model.entity.Node;
import remframework.opengl.helpers.Neighbor;
import remframework.opengl.helpers.OBJFormatLoader;
import remframework.settings.AppSettings;
import remframework.settings.Project;
import remframework.lattice.LatticeDescriptor;
import remframework.lattice.LatticeManager;
import remframework.lattice.LatticeModel;
import remframework.lattice.SystemCubicLatticeModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class Lattice {

    private final AppSettings appSettings = AppSettings.getInstance();
    private final Project     project     = Project.getInstance();
    private final RemModel    remModel    = project.getRemModel();

    private Entity       baseEntity;
    private ObjFileModel modelSphere;

    private Node[][][]   nodeMatrix;
    private List<Entity> entities;
    private List<Entity> nonNodeRenderEntities;

    private Object[] nodeArray;
    private Object[] entityArray;

    private int numEdgeAbsorptionLayers = 30;

    /**
     * Number of boundary layers that absorb outgoing waves.
     * <br><br>
     * 0 = perfectly reflecting boundaries.
     * Larger values create a wider absorbing region.
     */
    private static Lattice latticeInstance;

    public static Lattice getInstance() {
        if (latticeInstance == null) {
            latticeInstance = new Lattice();
        }
        return latticeInstance;
    }

    public Lattice() {
        System.out.println("Lattice.Lattice()");
        entities = new ArrayList<>();
        nonNodeRenderEntities = new ArrayList<>();
    }

    public Node getNode(int x, int y, int z) {
        return nodeMatrix[z][y][x];
    }

    public Node getNode(Vector3i node) {
        return getNode(node.x, node.y, node.z);
    }

    private Entity addEntity(Entity entity) {
        entities.add(entity);
        if (!(entity instanceof Node)) {
            nonNodeRenderEntities.add(entity);
        }
        return entity;
    }

    public void clearEntities() {
        entities.clear();
        nonNodeRenderEntities.clear();
    }

    public Entity createEntity(
            Entity parent, ObjFileModel model, Vector3d position, Vector3d rotation, double scale) {
        return addEntity(new Entity(parent, model, position, rotation, scale, EntityType.ENTITY));
    }

    /** Registers a renderer-only gradient arrow in the same non-node render path as other entities. */
    public GradientArrowEntity createGradientArrowEntity(
            Entity parent, ObjFileModel model, Vector3d position, double scale) {
        return (GradientArrowEntity) addEntity(
                new GradientArrowEntity(parent, model, position, scale));
    }

    public Entity createEntity(Vector3d position, Vector3d rotation, double scale) {
        if (baseEntity == null) {
            System.out.println("Warning, baseEntity is null!");
            return null;
        }
        if (modelSphere == null) {
            System.out.println("Warning, modelSphere is null!");
            return null;
        }
        return addEntity(new Entity(baseEntity, modelSphere, position, rotation, scale, EntityType.ENTITY));
    }

    public Node createNode(Entity parent, ObjFileModel model, Vector3d position, Vector3d rotation, double scale) {
        return (Node) addEntity(new Node(parent, model, position, rotation, scale));
    }

    public void deleteEntity(Node node) {
        if (node != null) {
            entities.remove(node);
        }
    }

    public List<Entity> getNodes() {
        List<Entity> nodes = new ArrayList<>();
        for (Entity entity : getEntities()) {
            if (Node.class.isAssignableFrom(entity.getClass())) {
                nodes.add(entity);
            }
        }
        return nodes;
    }

    public Node getNodeByIndex(Vector3i nodeIndex) {
        int sizeY = nodeMatrix == null ? 0 : nodeMatrix[0].length;
        int sizeX = sizeY == 0 ? 0 : nodeMatrix[0][0].length;
        int index = nodeIndex.z * sizeY * sizeX + nodeIndex.y * sizeX + nodeIndex.x;
        if (index >= 0 && index < getNodeArray().length)
            return (Node) getNodeArray()[index];
        else
            return null;

    }

    /**
     * Computes the boundary absorption factor for a node.
     * <br><br>
     * Interior nodes return 1.0.
     * Nodes inside the absorption region smoothly decrease toward 0.0
     * at the outer boundary.
     */
    private float computeAbsorptionFactor(
            int i, int j, int k,
            int sizeI, int sizeJ, int sizeK) {

        if (numEdgeAbsorptionLayers <= 0)
            return 1.0f;

        int distanceX = numEdgeAbsorptionLayers * 2 >= sizeI ? Integer.MAX_VALUE : Math.min(i, sizeI - 1 - i);
        int distanceY = numEdgeAbsorptionLayers * 2 >= sizeJ ? Integer.MAX_VALUE : Math.min(j, sizeJ - 1 - j);
        int distanceZ = numEdgeAbsorptionLayers * 2 >= sizeK ? Integer.MAX_VALUE : Math.min(k, sizeK - 1 - k);

        int edgeDistance = Math.min(distanceX, Math.min(distanceY, distanceZ));

        //System.out.println(String.format("I,J,K: (%d, %d, %d) = %d", i, j, k, edgeDistance));

        if (edgeDistance >= numEdgeAbsorptionLayers)
            return 1.0f;

        // first free layer = 1
        float t = (edgeDistance + 1.0f) / (numEdgeAbsorptionLayers + 1.0f);

        return (float) (0.5 - 0.5 * Math.cos(Math.PI * t));
    }

    public Entity build() {
        return build(false);
    }

    /**
     * Builds the mechanical lattice without allocating OpenGL model resources.
     *
     * <br><br>
     * Node construction, absorption factors, fixed boundaries, indexing, and
     * neighbor wiring are identical to {@link #build()}.
     */
    public Entity buildHeadless() {
        return build(true);
    }

    private Entity build(boolean headless) {
        System.out.println("Lattice.build()");
        Vector3i projectDimension = new Vector3i();
        project.getDimension(projectDimension);
        LatticeDescriptor descriptor = LatticeManager.getInstance().selected();
        LatticeModel latticeModel = descriptor.sourceType() == LatticeDescriptor.SourceType.SYSTEM
                ? SystemCubicLatticeModel.create(appSettings.isUseLoop()) : descriptor.model();
        Vector3i dimension = latticeModel.dimensions(projectDimension);
        if (!dimension.equals(projectDimension))
            project.setDimension(dimension.x, dimension.y, dimension.z, false);

        int sizeI = dimension.x;
        int sizeJ = dimension.y;
        int sizeK = dimension.z;

        Performance.numNodes  = sizeI * sizeJ * sizeK;
        Performance.numNodesX = sizeI;
        Performance.numNodesY = sizeJ;
        Performance.numNodesZ = sizeK;

        modelSphere = headless
                      ? null
                      : new OBJFormatLoader().loadOBJModel(
                              Constants.MODEL_NODE);

        baseEntity = createEntity(null, null, new Vector3d(), new Vector3d(), 1.0f);
        baseEntity.setPosition(0, 0, 0);
        baseEntity.setRotation(project.baseRotation.x, project.baseRotation.y, project.baseRotation.z);

        nodeMatrix = new Node[sizeK][sizeJ][sizeI];
        List<LatticeModel.NodeDefinition> definitions = latticeModel.nodes(projectDimension);
        int NumNodes = definitions.size();

        remModel.setLoadingReady(false);
        remModel.setProgress(0.0);

        new Thread(() -> {
            int countNode = 0;
            Map<String, Node> nodesById = new HashMap<>();
            try {
                for (LatticeModel.NodeDefinition definition : definitions) {
                    int i = definition.index().x;
                    int j = definition.index().y;
                    int k = definition.index().z;
                    Node n = createNode(baseEntity, modelSphere, new Vector3d(definition.position()),
                                            new Vector3d(0, 0, 0), Constants.EP_DEFAULT_DISTANCE);
                    n.setIndex(i, j, k);

                    n.setAbsorptionFactor(computeAbsorptionFactor(i, j, k, sizeI, sizeJ, sizeK));

                    n.setOverrideModelMaterial(true);
                    n.getMaterial().setDiffuseColor(new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));
                    n.getMaterial().setAmbientColor(new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));
                    nodeMatrix[k][j][i] = n;
                    nodesById.put(definition.id(), n);

                    boolean isEdgeX = (sizeI > 1 && (i == 0 || i == sizeI - 1));
                    boolean isEdgeY = (sizeJ > 1 && (j == 0 || j == sizeJ - 1));
                    boolean isEdgeZ = (sizeK > 1 && (k == 0 || k == sizeK - 1));

                    if (isEdgeX || isEdgeY || isEdgeZ) n.setType(EntityType.FIXED);
                    n.setXFixed(isEdgeX);
                    n.setYFixed(isEdgeY);
                    n.setZFixed(isEdgeZ);
                    countNode++;
                    remModel.setProgress((double) countNode / (double) NumNodes * 100);
                }

                for (LatticeModel.NodeDefinition definition : definitions) {
                    Node node = nodesById.get(definition.id());
                    for (Map.Entry<Integer, String> neighbor : definition.neighbors().entrySet()) {
                        node.setNeighbor(Neighbor.values()[neighbor.getKey()],
                                nodesById.get(neighbor.getValue()));
                    }
                }
                System.out.println("Built Lattice Model: " + descriptor.uiLabel());
            } catch (RuntimeException exception) {
                System.err.println("Lattice construction failed for " + descriptor.uiLabel()
                        + ": " + exception.getMessage());
                remModel.setCustomStatus("Lattice construction failed: " + exception.getMessage());
                if (!LatticeManager.SYSTEM_CUBIC_ID.equals(descriptor.identifier()))
                    LatticeManager.getInstance().constructionFailed(descriptor,
                            exception.getMessage());
            }
            remModel.setLoadingReady(true);
        }).start();

        return baseEntity;
    }

    public void update() {
        System.out.println("Lattice.update()");
        List<Node> orderedNodes = new ArrayList<>();
        if (nodeMatrix != null) {
            for (Node[][] plane : nodeMatrix)
                for (Node[] row : plane)
                    for (Node node : row)
                        if (node != null) orderedNodes.add(node);
        }
        setNodeArray(orderedNodes.toArray());
        project.occupancies.forEach(remframework.opengl.model.entity.Occupancy::invalidateOccupiedRegion);
        setEntityArray(entities.toArray());
    }

}
