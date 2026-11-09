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
import org.lwjgl.system.MemoryUtil;

import java.lang.Math;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicReference;

@Getter
@Setter
public class Scene {

    private static final int INSTANCE_FLOATS = 30;

    private AppSettings appSettings = AppSettings.getInstance();

    private Project          project  = Project.getInstance();
    private Settings         settings = project.settings;
    private SlitWallSettings slitWall = project.slitWall;
    private RemModel         REMModel = project.getRemModel();

    private List<Terrain> terrains;

    private Lattice lattice;
    private List<Entity> surveyLineOverlays=List.of();
    public record SurveySegment(Vector3d start,Vector3d end,Node from,Node to) {
        public SurveySegment(Vector3d start,Vector3d end){this(start,end,null,null);}
        public SurveySegment(Node from,Node to){this(null,null,from,to);}
        @Override public Vector3d start(){return from==null?new Vector3d(start):new Vector3d(from.getPosition());}
        @Override public Vector3d end(){return to==null?new Vector3d(end):new Vector3d(to.getPosition());}
    }
    private List<SurveySegment> surveySegments=List.of();
    private Object[] surveyLineNodes;
    private final Map<Node,Boolean> surveyHighlightedNodes=new IdentityHashMap<>();

    /** Renderer-only marks: no lattice nodes, forces or occupancies are modified. */
    public void showSurveyLine(SurveyLineInspection.Selection selection,int shell,int selectedNode) {
        showSurveyLine(selection,shell,selectedNode,List.of(),List.of(),false);
    }

    public void showSurveyLine(SurveyLineInspection.Selection selection,int shell,int selectedNode,
                               List<TransverseIdentitySurvey.ReceiverDistance> receivers,List<Vector3i> path,boolean shortest) {
        if(neighborhoodInspectionActive)exitNeighborhoodInspection();
        clearSurveyNodeColors();
        var marks=new ArrayList<Entity>();var segments=new ArrayList<SurveySegment>();
        double scale=((Node)lattice.getNodeArray()[0]).getScale();
        var cyan=new Vector4f(.1f,.9f,1,(float)appSettings.getSurveyProbeOpacity());
        var magenta=new Vector4f(1,.1f,.8f,(float)appSettings.getSurveyHighlightOpacity());
        var end=selection.probes().getLast().getPlacement().targets().getFirst().requestedCoordinate();
        if(!shortest)segments.add(new SurveySegment(new Vector3d(selection.source()),end));
        for(var receiver:receivers) {
            var position=new Vector3d(receiver.nx(),receiver.ny(),receiver.nz()).mul(receiver.targetRadius()).add(selection.source());
            marks.add(surveyMark(position,scale,receiver.orientation()==selection.orientation()&&receiver.ray()==selection.ray()?magenta:cyan));
        }
        if(receivers.isEmpty())for(var probe:selection.probes())
            marks.add(surveyMark(probe.getPlacement().targets().getFirst().requestedCoordinate(),scale,magenta));
        if(shortest) {
            Node previous=null;
            for(var index:path) {
                var node=lattice.getNodeByIndex(index);
                if(previous!=null)segments.add(new SurveySegment(previous,node));
                markSurveyNode(node,magenta);previous=node;
            }
        } else {
            for(var source:selection.probes().get(shell).getInterpolationSources())if(source.weight()>0) {
                var node=lattice.getNodeByIndex(source.nodeIndex());
                markSurveyNode(node,magenta);
            }
        }
        // Draw the chosen sampling node and source last, retaining their original radius.
        var sources=selection.probes().get(shell).getInterpolationSources().stream().filter(s->s.weight()>0).toList();
        var selected=lattice.getNodeByIndex(sources.get(selectedNode).nodeIndex());
        markSurveyNode(selected,magenta);
        for(Object value:lattice.getNodeArray()) {
            var node=(Node)value;
            if(node.getEquilibriumPosition().distanceSquared(selection.source())<1e-16) {
                markSurveyNode(node,new Vector4f(1,1,.1f,(float)appSettings.getSurveySourceOpacity()));break;
            }
        }
        surveySegments=List.copyOf(segments);
        surveyLineNodes=lattice.getNodeArray();surveyLineOverlays=List.copyOf(marks);
        invalidateRenderInstanceDataLayout();settings.setChanged(true);
    }

    private void markSurveyNode(Node node,Vector4f color) {
        surveyHighlightedNodes.putIfAbsent(node,node.isVisible());
        node.setSurveyHighlightColor(new Vector4f(color.x*.5f,color.y*.5f,color.z*.5f,color.w));node.setVisible(true);
    }

    private void clearSurveyNodeColors() {
        if(surveyHighlightedNodes.isEmpty())return;
        for(var entry:surveyHighlightedNodes.entrySet()){entry.getKey().setSurveyHighlightColor(null);entry.getKey().setVisible(entry.getValue());}
        surveyHighlightedNodes.clear();invalidateRenderInstanceDataLayout();settings.setChanged(true);
    }

    private Entity surveyMark(Vector3d position,double scale,Vector4f color) {
        var marker=new Entity(lattice.getBaseEntity(),lattice.getModelSphere(),new Vector3d(position),new Vector3d(),scale,EntityType.ENTITY);
        marker.setOverrideModelMaterial(true);
        // customColor is consumed by updateMatrix; inspectionColor persists across camera refreshes.
        marker.setInspectionColor(new Vector4f(color.x*.5f,color.y*.5f,color.z*.5f,color.w));
        marker.getMaterial().setDiffuseColor(new Vector4f(color));
        marker.getMaterial().setAmbientColor(new Vector4f(color));marker.getMaterial().setShininess(0);
        marker.setVisible(true);marker.setInspectionVisible(true);marker.updateMatrix();marker.synchronizeTransformMatrixBuffers();return marker;
    }

    public List<Entity> getSurveyLineOverlays() {
        if(lattice==null||surveyLineNodes!=lattice.getNodeArray())clearSurveyLine();
        return surveyLineOverlays;
    }

    public List<SurveySegment> getSurveySegments(){getSurveyLineOverlays();return surveySegments;}
    public void clearSurveyLine(){clearSurveyNodeColors();surveyLineOverlays=List.of();surveySegments=List.of();surveyLineNodes=null;}

    private ArrayList<Vector3i> wallNodeIndexList = new ArrayList<>();
    private ArrayList<Node>     hiLightedNodes    = new ArrayList<>();
    private ArrayList<PassiveObserver> probes        = new ArrayList<>();
    private ArrayList<PassiveObserver> previewProbes = new ArrayList<>();
    private boolean             replacementProbePreviewActive;
    private PassiveObserver     selectedProbe;
    private PassiveObserver     hoveredProbe;
    private final ProbeComparison comparison = new ProbeComparison();
    private final ProbeArrivalMeasurement probeArrivalMeasurement =
            new ProbeArrivalMeasurement();
    private final ProbeShellArrivalReport probeShellArrivalReport=new ProbeShellArrivalReport();
    public void startDiamondProbeMeasurements(List<? extends PassiveObserver> selected,int shell,double position,double gradient) {
        probeArrivalMeasurement.start(selected,shell,position,gradient);
        probeShellArrivalReport.start(selected,position,gradient);
    }
    public void clearProbeArrivalMeasurements(){identityOrbitSpreadRun.invalidate(this);probeArrivalMeasurement.clear();probeShellArrivalReport.clear();}
    private final IdentityOrbitSpreadRun identityOrbitSpreadRun=new IdentityOrbitSpreadRun();
    public IdentityOrbitSpreadRun getIdentityOrbitSpreadRun(){return identityOrbitSpreadRun;}
    private final TransverseIdentitySurvey transverseIdentitySurvey=new TransverseIdentitySurvey();
    public TransverseIdentitySurvey getTransverseIdentitySurvey(){return transverseIdentitySurvey;}
    private record PendingIdentityOrbit(remframework.opengl.model.entity.Identity source,int warmup,int cycles,double position,double gradient) {}
    private final java.util.concurrent.atomic.AtomicReference<PendingIdentityOrbit> pendingIdentityOrbit=new java.util.concurrent.atomic.AtomicReference<>();
    private String identityOrbitSpreadError="";
    public String getIdentityOrbitSpreadError(){return identityOrbitSpreadError;}
    public void requestIdentityOrbitSpread(remframework.opengl.model.entity.Identity source,int warmup,int cycles,double position,double gradient) {
        project.getRemModel().stopSimulation();pendingIdentityOrbit.set(new PendingIdentityOrbit(source,warmup,cycles,position,gradient));
    }
    public void applyPendingIdentityOrbitSpread() {
        var request=pendingIdentityOrbit.getAndSet(null);if(request==null)return;
        try {identityOrbitSpreadRun.start(this,request.source(),request.warmup(),request.cycles(),request.position(),request.gradient());identityOrbitSpreadError="";}
        catch(IllegalArgumentException error){identityOrbitSpreadError=error.getMessage();project.getRemModel().stopSimulation();}
    }
    public void finishProbeArrivalMeasurements(){probeArrivalMeasurement.finish();probeShellArrivalReport.finish();}
    private final Map<PassiveObserver, Entity> gradientArrowEntities = new IdentityHashMap<>();
    private ObjFileModel gradientArrowModel;
    private boolean showGradientArrows = true;
    private double gradientArrowScaleExponent = 70.0;
    private ProbeArrowQuantity probeArrowQuantity = ProbeArrowQuantity.CYCLE_IMBALANCE;

    public void setProbeArrowQuantity(ProbeArrowQuantity quantity) {
        probeArrowQuantity = java.util.Objects.requireNonNull(quantity);
    }
    private boolean showCalibrationArrow;
    private CalibrationDirection calibrationDirection = CalibrationDirection.NEGATIVE_X;
    private Entity calibrationArrow;
    private Node                firstHiLightedNode;

    private boolean neighborhoodInspectionActive;

    private boolean inspectionMode;
    private long inspectionVisibilityVersion;
    private PassiveObserver inspectedProbe;
    private int inspectedSourceIndex = 0;
    private double inspectionSourceAlpha = 0.45;
    private boolean showInspectionProbe = true;
    private Entity inspectionProbeMarker;
    private final Set<Node> inspectionTemporaryNodes =
            Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * Packed render data written alongside the back transform matrix buffer.
     * Each entity owns one fixed 30-float record at its entity-array index.
     */
    private final Map<ObjFileModel, FloatBuffer[]> instanceDataBuffers = new IdentityHashMap<>();
    private final Map<ObjFileModel, Integer> instanceDataEntityCounts = new IdentityHashMap<>();
    private Object[] preparedInstanceEntities;
    private int preparedBaseEntityIndex = -1;
    private FloatBuffer[][] instanceDataBuffersByEntity;
    private int[] instanceDataSlotsByEntity;
    private volatile boolean instanceDataLayoutDirty = true;
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

    private static final Vector4f INSPECTION_SELECTED_SOURCE_COLOR =
            new Vector4f(0.0f, 1.0f, 0.0f, 1.0f);
    private static final Vector4f INSPECTION_SOURCE_COLOR =
            new Vector4f(1.0f, 0.5f, 0.0f, 1.0f);
    private static final Vector4f INSPECTION_NEIGHBOR_COLOR =
            new Vector4f(0.5f, 0.5f, 0.5f, 1.0f);

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

    }

    private DirectionalLight directionalLight;

    private boolean animated;

    protected double animateInc;

    private int animateDirection = -1;

    public String status = "";

    private final List<Thread> threads = new ArrayList<>();
    private final AtomicReference<Throwable> taskFailure = new AtomicReference<>();

    public Scene() {
        System.out.println("Scene.Scene()");
        terrains = new ArrayList<>();
        lattice  = Lattice.getInstance();
    }

    /** Removes scene bindings before releasing the lattice graph. */
    public synchronized void releaseLattice() {
        clearSurveyLine();
        pendingDiamondProbes.set(null);
        pendingDiamondArrival.set(null);
        diamondProbeStatus="Lattice replaced; generate diamond shells";
        clearProbes();
        clearPreviewProbes();
        hiLightedNodes.clear();
        firstHiLightedNode = null;
        inspectionProbeMarker = null;
        for (FloatBuffer[] buffers : instanceDataBuffers.values()) {
            MemoryUtil.memFree(buffers[0]);
            MemoryUtil.memFree(buffers[1]);
        }
        instanceDataBuffers.clear();
        instanceDataEntityCounts.clear();
        instanceDataBuffersByEntity = new FloatBuffer[0][];
        instanceDataSlotsByEntity = new int[0];
        preparedInstanceEntities = null;
        preparedBaseEntityIndex = -1;
        instanceDataLayoutDirty = true;
        lattice.releaseForReplacement();
    }

    public void build() {
        System.out.println("Scene.build()");
        clearProbes();
        clearPreviewProbes();
        Entity baseEntity = lattice.build();
        if (baseEntity == null) return;
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

    private record PendingDiamondProbes(Object[] nodes,List<ProbeDefinition> definitions) { }
    private final java.util.concurrent.atomic.AtomicReference<PendingDiamondProbes> pendingDiamondProbes=new java.util.concurrent.atomic.AtomicReference<>();
    private volatile String diamondProbeStatus="No diamond shells applied";
    private final java.util.concurrent.atomic.AtomicReference<java.nio.file.Path> pendingExperimentPreset=new java.util.concurrent.atomic.AtomicReference<>();
    private volatile String experimentPresetStatus="";
    private volatile long experimentPresetRevision;
    public String getExperimentPresetStatus(){return experimentPresetStatus;}
    public long getExperimentPresetRevision(){return experimentPresetRevision;}
    public void requestExperimentPreset(java.nio.file.Path path) {
        project.getRemModel().stopSimulation();pendingExperimentPreset.set(path);
        experimentPresetStatus="Waiting for completed frame to restore configuration";
    }
    public void applyPendingExperimentPreset() {
        var path=pendingExperimentPreset.getAndSet(null);if(path==null)return;
        try {
            remframework.settings.DiamondExperimentPreset.load(path,appSettings,project);
            experimentPresetRevision++;
            experimentPresetStatus="Restored configuration. Generate and apply the saved shell layout, then run.";
        } catch(java.io.IOException error){experimentPresetStatus="Load failed: "+error.getMessage();}
    }
    private record PendingDiamondArrival(int shell,double position,double gradient) { }
    private final java.util.concurrent.atomic.AtomicReference<PendingDiamondArrival> pendingDiamondArrival=new java.util.concurrent.atomic.AtomicReference<>();
    public String getDiamondProbeStatus() {return diamondProbeStatus;}
    public void requestDiamondProbeArrival(int shell,double position,double gradient) {
        project.getRemModel().stopSimulation();
        pendingDiamondArrival.set(new PendingDiamondArrival(shell,position,gradient));
    }
    /** Captures baselines only at the same completed-frame boundary as probe installation. */
    public void applyPendingDiamondProbeArrival() {
        var pending=pendingDiamondArrival.getAndSet(null);if(pending==null)return;
        if(lattice.isMechanicsOnly() && !lattice.isDiamondTransportEnabled())
            startDiamondProbeMeasurements(List.copyOf(probes),pending.shell(),pending.position(),pending.gradient());
    }
    public void requestDiamondProbes(Object[] nodes,List<ProbeDefinition> definitions) {
        project.getRemModel().stopSimulation();
        pendingDiamondProbes.set(new PendingDiamondProbes(nodes,List.copyOf(definitions)));
        diamondProbeStatus="Waiting for completed simulation frame";
    }
    /** Called on the display thread only after physics workers finish. */
    public void applyPendingDiamondProbes() {
        var pending=pendingDiamondProbes.getAndSet(null);if(pending==null)return;
        if(!lattice.isMechanicsOnly() || lattice.getNodeArray()!=pending.nodes()) {
            diamondProbeStatus="Lattice changed; regenerate diamond probes";return;
        }
        if(replaceGeneratedProbeDefinitions(pending.definitions())) {
            project.replaceDiamondProbeDefinitions(pending.definitions());
            diamondProbeStatus="Applied "+pending.definitions().size()+" diamond probes";
            if(!probes.isEmpty())selectProbe(probes.getFirst());
        } else diamondProbeStatus="Probe binding is no longer valid; regenerate shells";
    }

    /** Reconstructs committed probe measurements through TransportSystem after lattice creation. */
    public void updateProbeMeasurements(TransportSystem transportSystem) {
        for (PassiveObserver probe : probes) {
            if (probe instanceof Probe rawProbe) rawProbe.updateMeasurements(transportSystem);
            else if (probe instanceof InterpolatedProbe interpolatedProbe) {
                interpolatedProbe.updateMeasurements(transportSystem);
            }
        }
        if(lattice.isMechanicsOnly() && lattice.isDiamondTransportEnabled())clearProbeArrivalMeasurements();
        else {probeArrivalMeasurement.sample();probeShellArrivalReport.sample();}
        identityOrbitSpreadRun.sampled(this);
        transverseIdentitySurvey.sampled(this);
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
        boolean diamond=definition.getDetectorType()==ProbeDetectorType.DIAMOND_INTERPOLATED;
        if(diamond!=lattice.isMechanicsOnly())return null;
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
        if(diamond) {
            Vector3d represented=new Vector3d();
            for(int i=0;i<sourceNodes.size();i++) {
                Node n=sourceNodes.get(i);
                if(n.isWall() || (n.isFixed() && !definition.getPlacement().measurementSpace().equals("Diamond physical XY")))return null;
                represented.fma(definition.getInterpolationSources().get(i).weight(),n.getEquilibriumPosition());
            }
            if(represented.distance(definition.getPlacement().targets().getFirst().requestedCoordinate())>1e-8)return null;
        }
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

    /** Shows replacement previews without rendering duplicate committed generated markers. */
    public void setPreviewDefinitions(List<ProbeDefinition> definitions, boolean replacesGenerated) {
        clearPreviewProbes();
        replacementProbePreviewActive = replacesGenerated;
        for (ProbeDefinition definition : definitions) createProbe(definition, true);
    }

    /** Removes transient preview markers without changing committed probes or project data. */
    public void clearPreviewProbes() {
        clearInspectionState();
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
            Vector3d anchor = probe.getMarker().getPosition();
            Vector3d localArrowVector = probeArrowQuantity.read(probe, new Vector3d());
            double visualLength = GradientArrowGeometry.probeVisualLength(
                    localArrowVector.length(), getGradientArrowScale());
            // Normalize only the renderer's copy; preserve raw passive observations.
            if (visualLength > 0) localArrowVector.normalize();
            updateArrowFromLocalVector(arrow, anchor, localArrowVector,
                    visualLength,
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
        Vector3d rotation = GradientArrowGeometry.rotationForDirection(directionLocal);
        arrow.setRotation(rotation.x, rotation.y, rotation.z);
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

    /** Atomically replaces generated probe markers while preserving manually created receivers. */
    public boolean replaceGeneratedProbeDefinitions(List<ProbeDefinition> definitions) {
        clearProbeArrivalMeasurements();
        clearInspectionState();
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
        clearInspectionState();
        if (selectedProbe != null) selectedProbe.setSelected(false);
        if (hoveredProbe != null) hoveredProbe.setHovered(false);
        selectedProbe = null;
        hoveredProbe = null;
        clearComparison();
    }

    /** Removes all probe markers and restores the ordinary lattice render state. */
    public void clearProbes() {
        clearProbeArrivalMeasurements();
        clearInspectionState();
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
        if ((neighborhoodInspectionActive || inspectionTransitionActive) && probeChanged) {
            inspectedProbe = selectedProbe;
            inspectedSourceIndex = 0;
            retargetNeighborhoodInspection();
        }
    }

    public boolean isNeighborhoodInspectionTransitionActive() { return inspectionTransitionActive; }

    /** Clears only the nodes and colors temporarily touched by neighborhood inspection. */
    public void clearInspectionState() {
        inspectionVisibilityVersion++;
        for (Node node : inspectionTemporaryNodes) {
            node.setInspectionVisible(false);
            node.setInspectionColor(null);
            node.updateMatrix();
        }
        inspectionTemporaryNodes.clear();
        if (inspectionProbeMarker != null) {
            inspectionProbeMarker.setInspectionVisible(false);
            inspectionProbeMarker.setInspectionColor(null);
            inspectionProbeMarker.updateMatrix();
        }
    }

    /** Changes whenever renderer-only inspection visibility is rebuilt. */
    public long getInspectionVisibilityVersion() {
        return inspectionVisibilityVersion;
    }

    private void markInspectionNode(Node node, Vector4f color) {
        if (node == null) return;
        node.setInspectionVisible(true);
        node.setInspectionColor(color);
        node.updateMatrix();
        inspectionTemporaryNodes.add(node);
    }

    private static Vector4f withAlpha(Vector4f color, double alpha) {
        return new Vector4f(color.x, color.y, color.z, (float) alpha);
    }

    private Vector4f inspectionMarkerColor(PassiveObserver probe) {
        Vector4f color;
        int comparisonSlot;
        boolean selected;
        Vector4f displayColor;
        if (probe instanceof Probe rawProbe) {
            comparisonSlot = rawProbe.getComparisonSlot();
            selected = rawProbe.isSelected();
            displayColor = rawProbe.getDisplayColor();
        } else if (probe instanceof InterpolatedProbe interpolatedProbe) {
            comparisonSlot = interpolatedProbe.getComparisonSlot();
            selected = interpolatedProbe.isSelected();
            displayColor = interpolatedProbe.getDisplayColor();
        } else {
            comparisonSlot = -1;
            selected = false;
            displayColor = new Vector4f(1.0f);
        }
        if (comparisonSlot == 0) color = new Vector4f(0.0f, 1.0f, 0.0f, 1.0f);
        else if (comparisonSlot == 1) color = new Vector4f(1.0f, 0.0f, 1.0f, 1.0f);
        else if (selected) color = new Vector4f(1.0f, 0.35f, 0.0f, 1.0f);
        else color = new Vector4f(displayColor);
        color.w = (float) inspectionSourceAlpha;
        return color;
    }

    private void refreshInspectionVisuals() {
        if (inspectionMode || (inspectionTransitionActive && inspectionTransitionEntering))
            setInspectionProbeAndNeighborsVisible(inspectedProbe, true);
    }

    public double getInspectionSourceAlpha() { return inspectionSourceAlpha; }

    public void setInspectionSourceAlpha(double alpha) {
        double clamped = Math.max(0.0, Math.min(1.0, alpha));
        if (Double.compare(inspectionSourceAlpha, clamped) == 0) return;
        inspectionSourceAlpha = clamped;
        refreshInspectionVisuals();
    }

    public boolean isShowInspectionProbe() { return showInspectionProbe; }

    public void setShowInspectionProbe(boolean show) {
        if (showInspectionProbe == show) return;
        showInspectionProbe = show;
        refreshInspectionVisuals();
    }

    private void markInspectionNeighbors(Node node) {
        if (node == null) return;
        for (Node neighbor : node.getNeighbors()) {
            if (neighbor != null) markInspectionNode(neighbor, INSPECTION_NEIGHBOR_COLOR);
        }
    }

    /** Applies temporary visibility and color state without changing ordinary node visibility. */
    public void setInspectionProbeAndNeighborsVisible(PassiveObserver inspectedProbe, boolean isVisible) {
        clearInspectionState();
        if (!isVisible || inspectedProbe == null) return;

        if (inspectedProbe instanceof Probe probe) {
            markInspectionNode(probe.getNode(), INSPECTION_SELECTED_SOURCE_COLOR);
            markInspectionNeighbors(probe.getNode());
        } else if (inspectedProbe instanceof InterpolatedProbe probe) {
            List<Node> sources = probe.getSourceNodes();
            int selected = Math.clamp(inspectedSourceIndex, 0, sources.size() - 1);
            Set<Node> sourceSet = Collections.newSetFromMap(new IdentityHashMap<>());
            Set<Node> neighborUnion = Collections.newSetFromMap(new IdentityHashMap<>());
            for (int index = 0; index < sources.size(); index++) {
                Node source = sources.get(index);
                sourceSet.add(source);
                markInspectionNode(source,
                                   index == selected
                                           ? INSPECTION_SELECTED_SOURCE_COLOR
                                           : withAlpha(INSPECTION_SOURCE_COLOR, inspectionSourceAlpha));
                for (Node neighbor : source.getNeighbors()) {
                    if (neighbor != null) neighborUnion.add(neighbor);
                }
            }
            // Sources retain priority over neighbor coloring.
            for (Node neighbor : neighborUnion) {
                if (!sourceSet.contains(neighbor)) {
                    markInspectionNode(neighbor,
                            withAlpha(INSPECTION_NEIGHBOR_COLOR, inspectionSourceAlpha));
                } else {
                    neighbor.setInspectionVisible(true);
                }
            }
        }
        if (inspectionProbeMarker != null) {
            inspectionProbeMarker.setInspectionVisible(showInspectionProbe);
            inspectionProbeMarker.setInspectionColor(showInspectionProbe
                    ? inspectionMarkerColor(inspectedProbe) : null);
            inspectionProbeMarker.updateMatrix();
        }
    }

    public List<Node> getInspectionSourceNodes() {
        return inspectedProbe instanceof InterpolatedProbe probe
                ? probe.getSourceNodes() : List.of();
    }

    public int getInspectedSourceIndex() {
        return inspectedSourceIndex;
    }

    int getInspectionTemporaryNodeCount() {
        return inspectionTemporaryNodes.size();
    }

    /** Selects an interpolation source and retargets the active inspection camera to it. */
    public boolean selectInspectionSource(int sourceIndex) {
        if (!(selectedProbe instanceof InterpolatedProbe probe)
                || sourceIndex < 0 || sourceIndex >= probe.getSourceNodes().size()) return false;
        if (inspectedSourceIndex == sourceIndex && inspectedProbe == selectedProbe) return true;
        inspectedSourceIndex = sourceIndex;
        if (inspectionMode || (inspectionTransitionActive && inspectionTransitionEntering)) {
            inspectedProbe = selectedProbe;
            retargetNeighborhoodInspection();
        }
        return true;
    }

    public Node getInspectionFocusNode() {
        if (inspectedProbe instanceof InterpolatedProbe probe) {
            List<Node> sources = probe.getSourceNodes();
            if (!sources.isEmpty()) return sources.get(
                    Math.clamp(inspectedSourceIndex, 0, sources.size() - 1));
        }
        return null;
    }

    /** Enters a renderer-only inspection session for the currently selected passive probe. */
    public boolean enterNeighborhoodInspection() {
        if(lattice.isMechanicsOnly()) return false;
        if (selectedProbe == null) return false;
        if ((neighborhoodInspectionActive || inspectionTransitionActive) && inspectedProbe == selectedProbe)
            return inspectionProbeMarker != null;
        if (!neighborhoodInspectionActive && !inspectionTransitionActive) {
            savedInspectionNavigation = InspectionNavigationSnapshot.capture(this);
            project.camera.setOrbitTarget(resolveNormalCameraTarget());
        }
        if (inspectedProbe != selectedProbe) inspectedSourceIndex = 0;
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
            case INTERPOLATED_3D, DIAMOND_INTERPOLATED -> {
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
        Node focusNode = getInspectionFocusNode();
        inspectionFrameTarget.set(focusNode == null
                                          ? inspectionProbeMarker.toWorldPosition()
                                          : focusNode.toWorldPosition());
        inspectionFrameDistance = getCameraDistance(inspectedProbe.getDetectorType());
        previousInspectionMarkerPosition.set(inspectionFrameTarget);
        if (inspectionMode || (inspectionTransitionActive && inspectionTransitionEntering))
            setInspectionProbeAndNeighborsVisible(inspectedProbe, true);
        beginCameraTransition(true, null, null);
    }

    /** Keeps the orbit target attached to the existing live probe marker. */
    public void updateInspectionCameraTarget() {
        if (!neighborhoodInspectionActive || inspectionProbeMarker == null) return;
        Node focusNode = getInspectionFocusNode();
        Vector3d liveMarkerPosition = focusNode == null
                ? inspectionProbeMarker.toWorldPosition() : focusNode.toWorldPosition();
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
            inspectionMode = true;
            neighborhoodInspectionActive = true;
            project.camera.lookAtOrbitTarget();
            setInspectionProbeAndNeighborsVisible(inspectedProbe, true);
        } else {
            clearInspectionState();
            inspectionMode = false;
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
        if(lattice.isMechanicsOnly()) return;
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
        if(lattice.isMechanicsOnly()) return;
        for (Vector3i nodeIndex : wallNodeIndexList) {
            Node node = lattice.getNodeByIndex(nodeIndex);
            if (node != null) {
                node.setType(EntityType.WALL);
            }
        }
    }

    public void drawSlitWall() {
        if(lattice.isMechanicsOnly()) return;
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

    private void threadUpdateMatrix(final int start, final int end, final double renderScale,
                                    final boolean synchronizeBothBuffers) {
        startTask(() -> {
            int i = start;
            var contextMatrix=new Matrix4d();var contextFloat=new Matrix4f();var white=new Vector3d(1);

            while (i < end) {
                int entityIndex = i++;
                Entity entity = (Entity) lattice.getEntityArray()[entityIndex];
                if(entity instanceof Node node&&node.isVisible()&&node.isSlabContextNode()
                        &&!inspectionMode&&settings.getRenderingMethod()!=Settings.RenderingMethod.DEFAULT_RENDERING) {
                    // Pack the faint context directly, retaining no per-node matrix pairs for the full volume.
                    node.updatePosition();node.setColor(white);
                    contextMatrix.set(lattice.getBaseEntity().getTransformMatrix()).translate(node.getPosition());
                    var rotation=node.getRotationRad();
                    if(rotation.x!=0||rotation.y!=0||rotation.z!=0)contextMatrix.rotateXYZ(rotation.x,rotation.y,rotation.z);
                    contextMatrix.scale(renderScale*(node.getCustomScale()>0?node.getCustomScale():node.getScale()));
                    contextFloat.set(contextMatrix);
                    var buffers=instanceDataBuffersByEntity[entityIndex];
                    if(buffers!=null) {
                        int offset=instanceDataSlotsByEntity[entityIndex]*INSTANCE_FLOATS;
                        contextFloat.get(offset,buffers[Entity.getSwapBufferIndex()]);
                        writeInstanceAppearance(entity,buffers[Entity.getSwapBufferIndex()],offset);
                        if(synchronizeBothBuffers){contextFloat.get(offset,buffers[1-Entity.getSwapBufferIndex()]);writeInstanceAppearance(entity,buffers[1-Entity.getSwapBufferIndex()],offset);}
                    }
                    node.setCustomScale(-1);node.setCustomColor(null);
                    if(synchronizeBothBuffers)node.releaseRenderMatrices();
                    continue;
                }
                if (entity instanceof Node node && !node.isVisible()
                        && !(inspectionMode && node.isInspectionVisible())) {
                    node.updatePosition();
                    node.setCustomScale(-1);
                    node.setCustomColor(null);
                    if(synchronizeBothBuffers)node.releaseRenderMatrices();
                    continue;
                }
                entity.updateMatrix(renderScale);
                if (synchronizeBothBuffers) entity.synchronizeTransformMatrixBuffers();
                writeInstanceData(entity, entityIndex, synchronizeBothBuffers);
            }
        });
    }

    private void writeInstanceData(Entity entity, int entityIndex,
                                   boolean synchronizeBothBuffers) {
        FloatBuffer[] buffers = instanceDataBuffersByEntity[entityIndex];
        if (buffers == null) return;
        writeInstanceData(entity, buffers[Entity.getSwapBufferIndex()], entityIndex);
        if (synchronizeBothBuffers) {
            writeInstanceData(entity, buffers[1 - Entity.getSwapBufferIndex()], entityIndex);
        }
    }

    private void writeInstanceData(Entity entity, FloatBuffer data, int entityIndex) {
        int offset = instanceDataSlotsByEntity[entityIndex] * INSTANCE_FLOATS;
        entity.getCalculatedTransformMatrix4f().get(offset, data);
        writeInstanceAppearance(entity,data,offset);
    }

    private void writeInstanceAppearance(Entity entity,FloatBuffer data,int offset) {
        put(data, offset + 16, entity.getMaterial().getAmbientIntensityColor());
        put(data, offset + 20, entity.getMaterial().getDiffuseIntensityColor());
        put(data, offset + 24, entity.getMaterial().getSpecularIntensityColor());
        data.put(offset + 28, (float) entity.getMaterial().getShininess());
        data.put(offset + 29, entity.getModel() != null && entity.isVisible()
            ?(entity instanceof Node node&&node.isSlabContextNode()?2.0f:1.0f):0.0f);
    }

    private static void put(FloatBuffer data, int offset, Vector4f value) {
        data.put(offset, value.x);
        data.put(offset + 1, value.y);
        data.put(offset + 2, value.z);
        data.put(offset + 3, value.w);
    }

    private void ensureInstanceDataBuffers() {
        Object[] entityArray = lattice.getEntityArray();
        if (entityArray == null) return;
        if (!instanceDataLayoutDirty && entityArray == preparedInstanceEntities) return;

        for (FloatBuffer[] buffers : instanceDataBuffers.values()) {
            MemoryUtil.memFree(buffers[0]);
            MemoryUtil.memFree(buffers[1]);
        }
        instanceDataBuffers.clear();
        instanceDataEntityCounts.clear();
        instanceDataBuffersByEntity = new FloatBuffer[entityArray.length][];
        instanceDataSlotsByEntity = new int[entityArray.length];
        preparedBaseEntityIndex = -1;

        Map<ObjFileModel, Integer> counts = new IdentityHashMap<>();
        for (Object value : entityArray) {
            if (value instanceof Entity entity && entity.getModel() != null && entity.isVisible())
                counts.merge(entity.getModel(), 1, Integer::sum);
        }
        for (Map.Entry<ObjFileModel, Integer> entry : counts.entrySet()) {
            int floatCount = Math.multiplyExact(entry.getValue(), INSTANCE_FLOATS);
            instanceDataBuffers.put(entry.getKey(), new FloatBuffer[] {
                    MemoryUtil.memAllocFloat(floatCount),
                    MemoryUtil.memAllocFloat(floatCount)
            });
            instanceDataEntityCounts.put(entry.getKey(), entry.getValue());
        }

        Map<ObjFileModel, Integer> nextSlots = new IdentityHashMap<>();
        for (int i = 0; i < entityArray.length; i++) {
            if(entityArray[i] instanceof Node node)node.setRenderEntityIndex(i);
            if (entityArray[i] == lattice.getBaseEntity()) preparedBaseEntityIndex = i;
            if (entityArray[i] instanceof Entity entity && entity.getModel() != null
                    && entity.isVisible()) {
                instanceDataBuffersByEntity[i] = instanceDataBuffers.get(entity.getModel());
                instanceDataSlotsByEntity[i] =
                        nextSlots.merge(entity.getModel(), 1, Integer::sum) - 1;
            }
        }
        preparedInstanceEntities = entityArray;
        instanceDataLayoutDirty = false;
    }

    public void invalidateRenderInstanceDataLayout() {
        instanceDataLayoutDirty = true;
    }

    public boolean isRenderInstanceDataLayoutDirty() {
        return instanceDataLayoutDirty || lattice.getEntityArray() != preparedInstanceEntities;
    }

    public void updateMatrix(int numThreads, int segmentSize) {
        updateMatrix(numThreads, segmentSize, false);
    }

    /**
     * Updates entity matrices in parallel after publishing one stable lattice-root matrix.
     * <br><br>
     * When {@code synchronizeBothBuffers} is true, the caller has guaranteed that OpenGL
     * is not drawing. Both transform and packed-instance buffers are initialized so a
     * paused refresh or visibility-layout rebuild cannot expose an uninitialized frame.
     */
    public synchronized void updateMatrix(int numThreads, int segmentSize, boolean synchronizeBothBuffers) {
        ensureInstanceDataBuffers();
        double renderScale = settings.getScale();
        Entity baseEntity = lattice.getBaseEntity();
        if (baseEntity != null) {
            baseEntity.updateMatrix(renderScale);
            if (synchronizeBothBuffers) baseEntity.synchronizeTransformMatrixBuffers();
            if (preparedBaseEntityIndex >= 0) {
                writeInstanceData(baseEntity, preparedBaseEntityIndex, synchronizeBothBuffers);
            }
        }
        int entityCount = lattice.getEntityArray().length;
        for (int i = 0; i < numThreads; i++) {
            int start = i * segmentSize;
            if (start >= entityCount) break;
            int end   = start + segmentSize;
            end = Math.min(end, entityCount);
            if (preparedBaseEntityIndex >= start && preparedBaseEntityIndex < end) {
                if (start < preparedBaseEntityIndex) {
                    threadUpdateMatrix(start, preparedBaseEntityIndex, renderScale,
                            synchronizeBothBuffers);
                }
                if (preparedBaseEntityIndex + 1 < end) {
                    threadUpdateMatrix(preparedBaseEntityIndex + 1, end, renderScale,
                            synchronizeBothBuffers);
                }
            } else {
                threadUpdateMatrix(start, end, renderScale, synchronizeBothBuffers);
            }
        }
    }

    /** Returns the completed packed buffer corresponding to the render transform buffer. */
    public FloatBuffer getRenderInstanceData(ObjFileModel model) {
        FloatBuffer[] buffers = instanceDataBuffers.get(model);
        if (buffers == null) return null;
        FloatBuffer data = buffers[1 - Entity.getSwapBufferIndex()];
        data.position(0);
        data.limit(instanceDataEntityCounts.get(model) * INSTANCE_FLOATS);
        return data;
    }

    public int getRenderInstanceEntityCount(ObjFileModel model) {
        return instanceDataEntityCounts.getOrDefault(model, 0);
    }

    /**
     * Appends a bond endpoint as world XYZ and RGBA from the completed render frame.<br><br>
     * The published packed buffer also covers faint slab nodes that retain no individual matrices.
     * Hidden nodes produce no vertex. Opacity multiplies the node alpha, including slab transparency.
     */
    public boolean appendConnectionVertex(Node node,FloatBuffer vertices,double opacity) {
        int index=node.getRenderEntityIndex();
        if(!node.isVisible()||index<0||preparedInstanceEntities==null||index>=preparedInstanceEntities.length
                ||preparedInstanceEntities[index]!=node)return false;
        var buffers=instanceDataBuffersByEntity[index];if(buffers==null)return false;
        var data=buffers[1-Entity.getSwapBufferIndex()];int offset=instanceDataSlotsByEntity[index]*INSTANCE_FLOATS;
        if(data.get(offset+29)<.5f)return false;
        vertices.put(data.get(offset+12)).put(data.get(offset+13)).put(data.get(offset+14));
        for(int c=0;c<3;c++)vertices.put(Math.max(0,Math.min(1,data.get(offset+20+c))));
        vertices.put((float)(Math.max(0,Math.min(1,data.get(offset+23)))*Math.max(0,Math.min(1,opacity))));
        return true;
    }

    private void threadCalcRemField(final int start, final int end,
                                    final Node.FrameParameters parameters) {
        startTask(() -> {
            int i = start;

            while (i < end) {
                ((Node) lattice.getNodeArray()[i++]).updateNodeProperties(parameters);
            }
        });
    }

    public void processRemFieldEngine(int numThreads, int segmentSize) {
        if (!REMModel.isSimulationUpdateActive()) return;
        lattice.applyPendingMechanicalPulse();
        Node.FrameParameters parameters = lattice.frameParameters();
        int nodeCount = lattice.getNodeArray().length;
        for (int i = 0; i < numThreads; i++) {
            int start = i * segmentSize;
            if (start >= nodeCount) break;
            int end   = start + segmentSize;
            end = Math.min(end, nodeCount);
            threadCalcRemField(start, end, parameters);
        }
    }

    private void startTask(Runnable task) {
        threads.add(Thread.startVirtualThread(() -> {
            try {
                task.run();
            } catch (Throwable failure) {
                taskFailure.compareAndSet(null, failure);
            }
        }));
    }

    public void waitForAllTasksReady() {
        boolean interrupted = false;
        for (Thread thread : threads) {
            while (thread.isAlive()) {
                try {
                    thread.join();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
        }
        threads.clear();
        Throwable failure = taskFailure.getAndSet(null);
        if (interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for frame workers.");
        }
        if (failure instanceof RuntimeException runtimeException) throw runtimeException;
        if (failure instanceof Error error) throw error;
        if (failure != null) throw new IllegalStateException("Frame worker failed.", failure);
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
