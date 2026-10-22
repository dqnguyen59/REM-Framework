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

    /**
     * Number of boundary layers that absorb outgoing waves.
     * <br><br>
     * 0 = perfectly reflecting boundaries.
     * Larger values create a wider absorbing region.
     */
    private volatile int numEdgeAbsorptionLayers = 30;
    private volatile boolean absorptionFactorsDirty;

    private volatile LatticeModel activeModel = SystemCubicLatticeModel.create(false);
    private final java.util.concurrent.atomic.AtomicBoolean mechanicalPulseRequested = new java.util.concurrent.atomic.AtomicBoolean();
    private remframework.geometry.discovery.GeometryConfigurationStatus mechanicsStatus;
    private volatile boolean diamondIdentityExcitationEnabled;
    public boolean isMechanicsOnly() {return activeModel.mechanicsOnly();}
    public boolean isDiamondIdentityExcitationEnabled() {return diamondIdentityExcitationEnabled;}
    public boolean allowsIdentityExcitation() {return !isMechanicsOnly() || diamondIdentityExcitationEnabled;}
    /** Explicit excitation-only bridge; never enables the cubic pressure/transport path. */
    public void setDiamondIdentityExcitationEnabled(boolean enabled) {
        if(enabled && !isMechanicsOnly())throw new IllegalStateException("Select experimental diamond mechanics first");
        diamondIdentityExcitationEnabled=enabled;
        remModel.setCustomStatus("Experimental diamond: Identity excitation "+(enabled?"on":"off")+"; pressure and transport off");
        disturbance.cancel();remModel.stopSimulation();
    }
    public remframework.geometry.discovery.GeometryConfigurationStatus diamondRemIntegrationStatus() {
        return remframework.geometry.discovery.GeometryConfigurationStatus.of(remframework.geometry.discovery.GeometryConfigurationDiagnostic.info(
            remframework.geometry.discovery.GeometryConfigurationStage.GEOMETRY_REALIZATION,
            remframework.geometry.discovery.GeometryConfigurationStatusCode.GEOMETRY_TEST_NOT_STARTED,
            "Diamond pressure and transport",
            "Pressure updates and transport disabled: opposite cubic axis slots have no physical tetrahedral mapping. Identity excitation is "+(diamondIdentityExcitationEnabled?"enabled.":"disabled.")));
    }
    public Node.FrameParameters frameParameters() {
        boolean experimental=isMechanicsOnly();
        return new Node.FrameParameters(experimental ? remframework.settings.Settings.NodeFormulaVersion.v_0_1 : project.settings.getNodeFormulaVersion(),
            project.getConstantWaveSpeed()/activeModel.mechanicalDivisor(),project.getConstantElectricFactor(),
            experimental ? 0 : project.getConstantMagneticFactor(),experimental ? 0 : appSettings.getAbsorption(),
            experimental || appSettings.isUseFixedNodes());
    }
    /** Reports experimental numerical failure before publishing a bad physics frame. */
    public boolean validateMechanicalState() {
        if(!isMechanicsOnly() || nodeArray==null) return true;
        for(Object value:nodeArray) {Node node=(Node)value;
            if(!node.getPosition().isFinite() || !node.getVelocity().isFinite() || !node.getAcceleration().isFinite()) {
                mechanicsStatus=remframework.geometry.discovery.GeometryConfigurationStatus.of(remframework.geometry.discovery.GeometryConfigurationDiagnostic.stop(
                    remframework.geometry.discovery.GeometryConfigurationStage.GEOMETRY_REALIZATION,
                    remframework.geometry.discovery.GeometryConfigurationStatusCode.NUMERICAL_FAILURE,"Experimental runtime mechanics","Nonfinite state at storage index "+node.getIndex()+"; simulation stopped."));
                remModel.stopSimulation();remModel.setCustomStatus("NUMERICAL_FAILURE: experimental diamond stopped");return false;
            }
        }
        return true;
    }
    private final remframework.geometry.discovery.research.diamond.DiamondRuntimeDisturbance disturbance = new remframework.geometry.discovery.research.diamond.DiamondRuntimeDisturbance();
    public remframework.geometry.discovery.research.diamond.DiamondRuntimeDisturbance getDisturbance() {return disturbance;}
    public void requestMechanicalPulse() {disturbance.cancel();mechanicalPulseRequested.set(true);}
    /** Called by the physics calculation thread before starting any Node workers. */
    public void applyPendingMechanicalPulse() {
        disturbance.initializePending(this);
        if(!isMechanicsOnly() || !mechanicalPulseRequested.getAndSet(false) || nodeArray==null) return;
        Node source=null;double closest=Double.POSITIVE_INFINITY;
        for(Object value:nodeArray) {Node n=(Node)value;if(n.getType()==EntityType.FIXED) continue;
            double distance=n.getPosition().lengthSquared();if(distance<closest) {closest=distance;source=n;}}
        if(source!=null) {source.setPosition(new Vector3d(source.getPosition()).add(.05,0,0));source.updatePosition();}
    }
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

    private synchronized Entity addEntity(Entity entity) {
        entities.add(entity);
        if (!(entity instanceof Node)) {
            nonNodeRenderEntities.add(entity);
        }
        return entity;
    }

    /** Called at an idle frame boundary before replacement allocation. */
    public synchronized void releaseForReplacement() {
        disturbance.releaseLattice();
        mechanicalPulseRequested.set(false);
        diamondIdentityExcitationEnabled = false;
        project.occupancies.forEach(remframework.opengl.model.entity.Occupancy::detachLattice);
        remModel.setCurrentMouseNode(null);
        clearEntities();
        nodeMatrix = null;
        nodeArray = new Object[0];
        entityArray = new Object[0];
        baseEntity = null;
        modelSphere = null;
    }

    public synchronized void clearEntities() {
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

    public synchronized void deleteEntity(Node node) {
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

    /**
     * Requests a new edge-absorption width for the active lattice.
     * <br><br>
     * The node factors are reapplied by {@link #applyPendingAbsorptionLayerChange()}
     * at a simulation-frame boundary. Negative values are treated as 0.
     */
    public synchronized void setNumEdgeAbsorptionLayers(int numEdgeAbsorptionLayers) {
        int normalizedLayers = Math.max(0, numEdgeAbsorptionLayers);
        if (this.numEdgeAbsorptionLayers == normalizedLayers) return;
        this.numEdgeAbsorptionLayers = normalizedLayers;
        absorptionFactorsDirty = true;
    }

    /**
     * Recomputes the persisted absorption factor of every existing node after
     * a runtime edge-layer change.
     * <br><br>
     * This method is intended to run after the preceding simulation update has
     * completed and before the next update starts.
     * <br><br>
     * @return true when existing node factors were updated
     */
    public synchronized boolean applyPendingAbsorptionLayerChange() {
        if (!absorptionFactorsDirty || nodeMatrix == null) return false;

        int sizeK = nodeMatrix.length;
        int sizeJ = sizeK == 0 ? 0 : nodeMatrix[0].length;
        int sizeI = sizeJ == 0 ? 0 : nodeMatrix[0][0].length;
        for (int k = 0; k < sizeK; k++) {
            for (int j = 0; j < sizeJ; j++) {
                for (int i = 0; i < sizeI; i++) {
                    Node node = nodeMatrix[k][j][i];
                    if (node != null) {
                        node.setAbsorptionFactor(
                                computeAbsorptionFactor(i, j, k, sizeI, sizeJ, sizeK));
                    }
                }
            }
        }
        absorptionFactorsDirty = false;
        return true;
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

    public void reportDiamondSizeFailure(remframework.lattice.DiamondMemoryBudget.Estimate estimate) {
        System.err.println("Diamond build rejected ["+estimate.code()+"]: "+estimate.error());
        mechanicsStatus=remframework.geometry.discovery.GeometryConfigurationStatus.of(remframework.geometry.discovery.GeometryConfigurationDiagnostic.stop(
            remframework.geometry.discovery.GeometryConfigurationStage.GEOMETRY_REALIZATION, estimate.code(), "Diamond dimension request", estimate.error()));
        remModel.setCustomStatus(estimate.error());
    }
    private final Vector3d physicalSliceCenter = new Vector3d();
    public Vector3d getPhysicalSliceCenter() {return physicalSliceCenter;}
    private Entity build(boolean headless) {
        disturbance.cancel();diamondIdentityExcitationEnabled=false;
        System.out.println("Lattice.build()");
        Vector3i projectDimension = new Vector3i();
        project.getDimension(projectDimension);
        LatticeDescriptor descriptor = LatticeManager.getInstance().selected();
        LatticeModel latticeModel = LatticeManager.SYSTEM_CUBIC_ID.equals(descriptor.identifier())
                ? SystemCubicLatticeModel.create(appSettings.isUseLoop()) : descriptor.model();
        if(latticeModel.mechanicsOnly()) {
            var estimate=remframework.lattice.DiamondMemoryBudget.estimate(projectDimension);
            if(!estimate.allowed()) {reportDiamondSizeFailure(estimate);remModel.stopSimulation();remModel.setLoadingReady(true);return baseEntity;}
        }
        activeModel=latticeModel;
        if(!isMechanicsOnly()) mechanicalPulseRequested.set(false);
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
        physicalSliceCenter.zero();
        for(var definition:definitions) physicalSliceCenter.add(definition.position());
        if(!definitions.isEmpty()) physicalSliceCenter.div(definitions.size());
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

                    if (latticeModel.fixedBoundary(definition,dimension)) n.setType(EntityType.FIXED);
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
                if(isMechanicsOnly()) {
                    mechanicsStatus=remframework.geometry.discovery.GeometryConfigurationStatus.of(remframework.geometry.discovery.GeometryConfigurationDiagnostic.info(
                        remframework.geometry.discovery.GeometryConfigurationStage.GEOMETRY_REALIZATION,
                        remframework.geometry.discovery.GeometryConfigurationStatusCode.GEOMETRIC_SYMMETRY_UNKNOWN,
                        "Experimental diamond mechanics","Node v0.1 /4; fixed incomplete boundary; occupancy and transport gated. Full REM isotropy unproved."));
                    remModel.setCustomStatus("Experimental diamond: Node v0.1 /4; occupancy and transport off");
                }
                System.out.println("Built Lattice Model: " + descriptor.uiLabel());
            } catch (RuntimeException exception) {
                mechanicsStatus=remframework.geometry.discovery.GeometryConfigurationStatus.of(remframework.geometry.discovery.GeometryConfigurationDiagnostic.stop(
                    remframework.geometry.discovery.GeometryConfigurationStage.GEOMETRY_REALIZATION,
                    remframework.geometry.discovery.GeometryConfigurationStatusCode.NUMERICAL_FAILURE,"Runtime lattice construction",exception.getMessage()));
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
