package remframework.geometry.discovery.realization.session;
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

import org.joml.Vector3d;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import remframework.geometry.discovery.eligibility.*;
import remframework.geometry.discovery.measurement.regularity.*;
import remframework.geometry.discovery.reference.ReferenceTopologyRecognizer;
import remframework.geometry.discovery.experiment.*;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded, render-friendly facade over the existing DynamicElasticSession.
 * <br><br>
 * This class does not calculate forces itself; every update delegates to the
 * existing Dynamic Elastic step implementation.
 */
public final class IncrementalSession {
    private final Configuration configuration;
    private final TestRequest request;
    private final FixedDegreeGraph graph;
    private DynamicElasticSession elastic;
    private List<Vector3d> initialCoordinates;
    private int attempt;
    private volatile ExecutionState executionState = ExecutionState.IDLE;
    private volatile ScientificState scientificState = ScientificState.INITIALIZED;
    private volatile TestResult finalResult;
    private volatile String stopReason = "";
    private volatile int convergenceIteration = -1;
    private volatile List<Vector3d> publishedCoordinates=List.of();
    private volatile DynamicElasticMetrics publishedMetrics;
    private volatile Map<String,ConstraintMeasurement> publishedMeasurements=Map.of();
    private volatile ThreeDConstraintDiagnostics publishedThreeD=ThreeDConstraintDiagnostics.inactive();
    private volatile SelfIntersectionDiagnostics publishedSelfIntersection=SelfIntersectionDiagnostics.inactive();
    private volatile ConstraintObjectiveDiagnostics publishedObjectives;
    private volatile double publishedMaximumEdgeError;
    private volatile StagnationStatus publishedStagnation=StagnationStatus.progressing();
    private volatile int publishedIteration;
    private volatile ScientificTimingSnapshot timing=ScientificTimingSnapshot.initial(0);
    private long totalIterationNanos;
    private List<List<Integer>> topologyFaces=List.of();
    private int continuationStageIndex,continuationStageIteration,continuationStable;
    private double continuationRampFactor;
    private double continuationRampCeiling=1;
    private final List<ContinuationStageOutcome> continuationHistory=new ArrayList<>();
    private volatile ContinuationSnapshot continuationSnapshot=ContinuationSnapshot.simultaneous();
    private volatile OrientationConvexityDiagnostics orientationConvexity=OrientationConvexityDiagnostics.undetermined("Stage 2 has not published coordinates.");
    private List<Vector3d> randomStartCoordinates=List.of(),spectralTargetCoordinates=List.of(),scientificStartCoordinates=List.of();
    private volatile InitializationSnapshot initializationSnapshot=InitializationSnapshot.instant();
    private int initializationRevision,initializationStep;
    private List<List<Vector3d>> initializationTrails=List.of();
    private volatile GeometryConfigurationStatus initializationFailure;
    private final EnumSet<ManualConstraint> manualActiveForces=EnumSet.noneOf(ManualConstraint.class);
    private final EnumMap<ManualConstraint,Boolean> manualRequirements=new EnumMap<>(ManualConstraint.class);
    private final List<ManualAction> manualActions=new ArrayList<>();
    private final List<GeometrySnapshot> manualSnapshots=new ArrayList<>();
    private final EnumMap<ManualConstraint,ManualRegression> manualRegressions=new EnumMap<>(ManualConstraint.class);
    private ManualVelocityPolicy manualVelocityPolicy=ManualVelocityPolicy.RESET_VELOCITY_ON_FORCE_CHANGE;
    private ManualExecutionState manualExecutionState=ManualExecutionState.MANUAL_IDLE;
    private int manualSnapshotRevision;
    private long manualActionSequence;
    private final List<ManualExperimentEvent> manualEvents=new ArrayList<>();
    private boolean manualRecording;
    private boolean manualReplay;
    private long manualEventSequence;
    private ManualRecording lastManualRecording;

    public IncrementalSession(Configuration configuration,
            TestRequest request) {
        if (!configuration.id().equals(request.configurationId()))
            throw new IllegalArgumentException("Selected configuration ID does not match the test request.");
        this.configuration = configuration;
        this.request = request;
        this.graph = new FixedDegreeGraph(configuration.nodeCount(), configuration.exactDegree(),
                configuration.edges(), configuration.canonicalGraphLabel());
        long started=System.nanoTime();initializeAttempt();publishSnapshot();timing=ScientificTimingSnapshot.initial(System.nanoTime()-started);
    }

    public synchronized void initialize() {
        reset();
    }

    public synchronized boolean step() {
        if (executionState == ExecutionState.STOPPED) return false;
        if(request.continuation().strategy()==RealizationStrategy.MANUAL_RELAXATION){
            if(manualExecutionState!=ManualExecutionState.MANUAL_RELAXING)return false;
            executionState=ExecutionState.RUNNING;
        }
        if (executionState == ExecutionState.IDLE) executionState = ExecutionState.RUNNING;
        try{if(advanceSpectralInitialization()){publishSnapshot();return true;}}catch(RuntimeException failure){failInitialization(failure);publishSnapshot();return false;}
        if (scientificState == ScientificState.INITIALIZED) scientificState = request.dimension()==GeometryEmbeddingDimension.THREE_D?ScientificState.RELAXING_CONSTRAINTS:ScientificState.RELAXING;
        long started=System.nanoTime();elastic.step();totalIterationNanos+=System.nanoTime()-started;
        if(request.continuation().strategy()==RealizationStrategy.SEQUENTIAL_CONTINUATION)advanceContinuation();
        else if (elastic.finished()) finishAttempt();
        publishSnapshot();
        return true;
    }

    public synchronized int step(int iterations) {
        int performed = 0;
        for (int i = 0; i < Math.max(0, iterations); i++) {
            if (!step()) break;
            performed++;
            if (scientificState == ScientificState.SEARCH_LIMIT_REACHED
                    || scientificState == ScientificState.FAILED) break;
        }
        return performed;
    }

    public synchronized void run() {
        if(request.continuation().strategy()==RealizationStrategy.MANUAL_RELAXATION&&manualActiveForces.isEmpty()){manualExecutionState=ManualExecutionState.MANUAL_IDLE;executionState=ExecutionState.PAUSED;publishSnapshot();return;}
        resume();
        while (executionState == ExecutionState.RUNNING
                && scientificState != ScientificState.CONVERGED
                && scientificState != ScientificState.QUALIFICATION_FAILED
                && scientificState != ScientificState.SEARCH_LIMIT_REACHED
                && scientificState != ScientificState.FAILED) step();
    }

    public synchronized void resume() {
        if(request.continuation().strategy()==RealizationStrategy.MANUAL_RELAXATION){
            if(manualActiveForces.isEmpty()){manualExecutionState=ManualExecutionState.MANUAL_IDLE;executionState=ExecutionState.PAUSED;return;}
            manualExecutionState=ManualExecutionState.MANUAL_RELAXING;executionState=ExecutionState.RUNNING;return;
        }
        if (scientificState != ScientificState.CONVERGED&&scientificState!=ScientificState.FAILED
                &&scientificState!=ScientificState.SEARCH_LIMIT_REACHED) executionState = ExecutionState.RUNNING;
    }

    public void pause() {
        if(request.continuation().strategy()==RealizationStrategy.MANUAL_RELAXATION){manualExecutionState=ManualExecutionState.MANUAL_PAUSED;executionState=ExecutionState.PAUSED;return;}
        if (executionState == ExecutionState.RUNNING) executionState = ExecutionState.PAUSED;
    }

    public void stop() {
        if (executionState == ExecutionState.STOPPED) return;
        executionState = ExecutionState.STOPPED;
        stopReason = "Geometry realization stopped by the user.";
    }

    public synchronized void reset() {
        attempt = 0;
        finalResult = null;
        stopReason = "";
        convergenceIteration = -1;
        initializeAttempt();
        executionState = ExecutionState.IDLE;
        scientificState = ScientificState.INITIALIZED;
        totalIterationNanos=0;publishSnapshot();
    }

    public ManualExecutionState manualExecutionState(){return manualExecutionState;}
    public ManualVelocityPolicy manualVelocityPolicy(){return manualVelocityPolicy;}
    public Set<ManualConstraint> manualActiveForces(){return Set.copyOf(manualActiveForces);}
    public synchronized void setManualActiveForceSet(Set<ManualConstraint> forces){requireManual();Set<ManualConstraint> requested=forces==null?Set.of():Set.copyOf(forces);for(ManualConstraint c:ManualConstraint.values())if(manualActiveForces.contains(c)!=requested.contains(c))setManualRelaxation(c,requested.contains(c));}
    public List<ManualAction> manualActions(){return List.copyOf(manualActions);}
    public List<ManualExperimentEvent> manualEvents(){return List.copyOf(manualEvents);}
    public boolean manualRecording(){return manualRecording;}
    public ManualRecording lastManualRecording(){return lastManualRecording;}
    public synchronized void startManualRecording(String name){requireManual();manualActions.clear();manualEvents.clear();manualActionSequence=0;manualEventSequence=0;manualRecording=true;lastManualRecording=null;recordManualEvent(ManualExperimentEvent.Type.RECORDING_STARTED,"Recording started");}
    public synchronized ManualRecording stopManualRecording(String name){requireManual();recordManualEvent(ManualExperimentEvent.Type.RECORDING_STOPPED,"Recording stopped");manualRecording=false;ManualRecording recording=new ManualRecording(java.util.UUID.randomUUID().toString(),name,configuration.id().toString(),request.randomSeed(),manualActions,manualEvents,manualSnapshots,finalResult!=null&&finalResult.status().code()==GeometryConfigurationStatusCode.VALID_CANDIDATE,finalResult==null?"MANUAL_EXPERIMENT_STOPPED":finalResult.status().code().name(),finalResult==null?"Recording stopped before final qualification.":finalResult.status().explanation());lastManualRecording=recording;return recording;}
    public synchronized ManualRecording replayManualRecording(ManualRecording recording){requireManual();if(!configuration.id().toString().equals(recording.configurationId())||request.randomSeed()!=recording.seed())throw new IllegalArgumentException("Recording topology or seed does not match the selected experiment.");reset();manualActions.clear();manualActions.addAll(recording.actions());manualReplay=true;for(ManualAction action:recording.actions()){while(iteration()<action.scientificIteration())step();setManualRelaxation(action.constraint(),action.newRelaxationState());}manualReplay=false;lastManualRecording=new ManualRecording(java.util.UUID.randomUUID().toString(),recording.name()+" replay",recording.configurationId(),recording.seed(),recording.actions(),recording.events(),recording.snapshots(),finalResult!=null&&finalResult.status().code()==GeometryConfigurationStatusCode.VALID_CANDIDATE,finalResult==null?"MANUAL_EXPERIMENT_STOPPED":finalResult.status().code().name(),finalResult==null?"Replay stopped before final qualification.":finalResult.status().explanation());return lastManualRecording;}
    public synchronized ManualCandidateStrategy saveAsCandidateStrategy(String name){if(lastManualRecording==null)throw new IllegalStateException("Stop a recording before saving a candidate strategy.");return new ManualCandidateStrategy(name,1,lastManualRecording,"MANUAL_DISCOVERED_STRATEGY_CANDIDATE");}
    public List<GeometrySnapshot> manualSnapshots(){return List.copyOf(manualSnapshots);}
    public synchronized void deleteManualSnapshot(String id){if(manualSnapshots.removeIf(snapshot->snapshot.snapshotId().equals(id)))recordManualEvent(ManualExperimentEvent.Type.SNAPSHOT_DELETED,id);}
    public List<ManualRegression> manualRegressions(){return List.copyOf(manualRegressions.values());}
    public List<ManualConstraintState> manualConstraintStates(){
        List<ManualConstraintState> result=new ArrayList<>();for(ManualConstraint c:ManualConstraint.values()){ConstraintMeasurement measurement=publishedMeasurements.get(manualMeasurementId(c));double raw=measurement!=null&&measurement.rawValue()!=null?measurement.rawValue():Double.NaN;double tolerance=switch(c){case EDGE_LENGTH->request.edgeTolerance();case FACE_PLANARITY,CONVEXITY->request.threeDConditions().facePlanarityTolerance();case SELF_INTERSECTION_AVOIDANCE->request.targetEdgeLength()*.05;default->request.threeDConditions().absoluteRegularityTolerance();};boolean available=measurement!=null&&measurement.measured()&&measurement.rawValue()!=null&&Double.isFinite(measurement.rawValue());double ratio=available&&Double.isFinite(tolerance)&&tolerance>0?raw/tolerance:Double.NaN;boolean required=manualRequirements.getOrDefault(c,false);ManualConstraintState.ManualConstraintStatus status=!available?ManualConstraintState.ManualConstraintStatus.UNAVAILABLE:manualActiveForces.contains(c)?ManualConstraintState.ManualConstraintStatus.ACTIVE:ratio<=1?ManualConstraintState.ManualConstraintStatus.INACTIVE_PASS:ManualConstraintState.ManualConstraintStatus.INACTIVE_FAIL;Double strength=manualActiveForces.contains(c)?switch(c){case EDGE_LENGTH->request.relaxationSettings().springStrength();case FACE_PLANARITY->request.threeDConstraintStrengths().facePlanarity();case FACE_ANGLE->request.threeDConstraintStrengths().faceAngles();case FACE_DISTANCE->request.threeDConstraintStrengths().facePlaneRadii();case CONVEXITY,SELF_INTERSECTION_AVOIDANCE->.6;default->null;}:null;result.add(new ManualConstraintState(c,manualActiveForces.contains(c),available,required,raw,tolerance,ratio,status,strength,measurement));}return List.copyOf(result);
    }
    public synchronized void setManualVelocityPolicy(ManualVelocityPolicy policy){manualVelocityPolicy=policy==null?ManualVelocityPolicy.RESET_VELOCITY_ON_FORCE_CHANGE:policy;}
    public synchronized void setManualFinalRequirement(ManualConstraint constraint,boolean required){manualRequirements.put(constraint,required);}
    public synchronized void setManualRelaxation(ManualConstraint constraint,boolean enabled){if(request.continuation().strategy()!=RealizationStrategy.MANUAL_RELAXATION)throw new IllegalStateException("Manual relaxation is not selected.");boolean old=manualActiveForces.contains(constraint);if(old==enabled)return;Map<ManualConstraint,Double> before=ratios();if(enabled)manualActiveForces.add(constraint);else manualActiveForces.remove(constraint);reconfigureManualForces();if(manualVelocityPolicy==ManualVelocityPolicy.RESET_VELOCITY_ON_FORCE_CHANGE)elastic.resetVelocities();if(manualActiveForces.isEmpty()){manualExecutionState=ManualExecutionState.MANUAL_IDLE;executionState=ExecutionState.PAUSED;}else{manualExecutionState=ManualExecutionState.MANUAL_RELAXING;executionState=ExecutionState.RUNNING;}if(!manualReplay)manualActions.add(new ManualAction(++manualActionSequence,Instant.now(),iteration(),constraint,old,enabled,manualActiveForces,manualVelocityPolicy,realizedSeed(),manualSnapshotRevision,before,ratios()));if(manualRecording&&!manualReplay)recordManualEvent(ManualExperimentEvent.Type.FORCE_CHANGED,constraint.displayName()+" "+(enabled?"ON":"OFF"));manualRegressions.clear();for(var e:before.entrySet())manualRegressions.put(e.getKey(),new ManualRegression(e.getKey(),e.getValue(),ratios().getOrDefault(e.getKey(),Double.NaN),ratios().getOrDefault(e.getKey(),Double.NaN)-e.getValue(),"TRANSITION"));publishSnapshot();}
    public synchronized void turnAllManualRelaxationOff(){for(ManualConstraint c:ManualConstraint.values())if(manualActiveForces.contains(c))setManualRelaxation(c,false);}
    public synchronized void turnAllManualRelaxationOn(){for(ManualConstraint c:ManualConstraint.values())setManualRelaxation(c,true);}
    public synchronized GeometrySnapshot saveManualSnapshot(String label){GeometrySnapshot snapshot=new GeometrySnapshot(UUID.randomUUID().toString(),label,currentCoordinates(),elastic.nodes().stream().map(n->new Vector3d(n.velocity())).toList(),manualActiveForces,manualConstraintStates(),iteration(),realizedSeed(),request.threeDConditions().centerDefinition().name(),finalResult!=null&&finalResult.status().code()==GeometryConfigurationStatusCode.VALID_CANDIDATE,++manualSnapshotRevision);manualSnapshots.add(snapshot);if(manualRecording)recordManualEvent(ManualExperimentEvent.Type.SNAPSHOT_SAVED,snapshot.snapshotId());return snapshot;}
    public synchronized void restoreManualSnapshot(String id,boolean restoreForces){GeometrySnapshot snapshot=manualSnapshots.stream().filter(s->s.snapshotId().equals(id)).findFirst().orElseThrow();elastic.replacePositions(snapshot.coordinates());elastic.replaceVelocities(snapshot.velocities());if(restoreForces){manualActiveForces.clear();manualActiveForces.addAll(snapshot.activeForceSet());reconfigureManualForces();}manualExecutionState=manualActiveForces.isEmpty()?ManualExecutionState.MANUAL_IDLE:ManualExecutionState.MANUAL_PAUSED;if(manualRecording)recordManualEvent(ManualExperimentEvent.Type.SNAPSHOT_RESTORED,id);publishSnapshot();}
    public synchronized boolean evaluateManualFinalQualification(){if(request.continuation().strategy()!=RealizationStrategy.MANUAL_RELAXATION)throw new IllegalStateException("Manual qualification requires MANUAL_RELAXATION.");finalResult=finalizeResult(configuration,request,elastic.resultForQualification()).withContinuation(continuationSnapshot);scientificState=finalResult.status().code()==GeometryConfigurationStatusCode.VALID_CANDIDATE?ScientificState.CONVERGED:ScientificState.QUALIFICATION_FAILED;if(manualRecording)recordManualEvent(ManualExperimentEvent.Type.QUALIFICATION_EVALUATED,finalResult.status().code().name());return finalResult.status().code()==GeometryConfigurationStatusCode.VALID_CANDIDATE;}
    private void requireManual(){if(request.continuation().strategy()!=RealizationStrategy.MANUAL_RELAXATION)throw new IllegalStateException("Manual relaxation is not selected.");}
    private void recordManualEvent(ManualExperimentEvent.Type type,String detail){manualEvents.add(new ManualExperimentEvent(++manualEventSequence,Instant.now(),iteration(),type,detail,manualSnapshotRevision));}
    private Map<ManualConstraint,Double> ratios(){EnumMap<ManualConstraint,Double> values=new EnumMap<>(ManualConstraint.class);for(ManualConstraintState s:manualConstraintStates())values.put(s.constraint(),s.normalizedRatio());return values;}

    public List<Vector3d> currentCoordinates() {
        return publishedCoordinates.stream().map(Vector3d::new).toList();
    }

    public synchronized List<Vector3d> initialCoordinates() {
        return initialCoordinates.stream().map(Vector3d::new).toList();
    }

    public int iteration() { return publishedIteration; }
    public DynamicElasticMetrics metrics() { return publishedMetrics; }
    /** Immutable measurements from the same coordinate publication as the live diagnostics. */
    public Map<String,ConstraintMeasurement> constraintMeasurements(){return Map.copyOf(publishedMeasurements);}
    public ThreeDConstraintDiagnostics threeDConstraintDiagnostics(){return publishedThreeD;}
    public SelfIntersectionDiagnostics selfIntersectionDiagnostics(){return publishedSelfIntersection;}
    public ConstraintObjectiveDiagnostics objectiveDiagnostics(){return publishedObjectives;}
    public double maximumEdgeError(){return publishedMaximumEdgeError;}
    public StagnationStatus stagnationStatus(){return publishedStagnation;}
    public ConstraintPresentationSummary constraintPresentation(){return Stage2ConstraintPresentation.from(this);}
    public ContinuationSnapshot continuationSnapshot(){return continuationSnapshot;}
    public OrientationConvexityDiagnostics orientationConvexityDiagnostics(){return orientationConvexity;}
    public InitializationSnapshot initializationSnapshot(){return initializationSnapshot;}
    public GeometryConfigurationStatus initializationFailure(){return initializationFailure;}
    public List<Vector3d> randomStartCoordinates(){return copy(randomStartCoordinates);}
    public List<Vector3d> spectralTargetCoordinates(){return copy(spectralTargetCoordinates);}
    public List<Vector3d> scientificStartCoordinates(){return copy(scientificStartCoordinates);}
    public DisplacementSummary randomToSpectralDisplacement(){return displacement(randomStartCoordinates,spectralTargetCoordinates);}
    public DisplacementSummary scientificStartToCurrentDisplacement(){return displacement(scientificStartCoordinates,currentCoordinates());}
    public InitializationContribution initializationContribution(){List<InitializationMeasurement> values=new ArrayList<>();if(!randomStartCoordinates.isEmpty())values.add(measure("Random Start",randomStartCoordinates,false));if(!spectralTargetCoordinates.isEmpty())values.add(measure("Spectral Target",spectralTargetCoordinates,false));if(!scientificStartCoordinates.isEmpty())values.add(measure("Scientific Start",scientificStartCoordinates,false));values.add(measure("Final / Current Realization",currentCoordinates(),finalResult!=null&&finalResult.status().code()==GeometryConfigurationStatusCode.VALID_CANDIDATE));return new InitializationContribution(randomToSpectralDisplacement(),scientificStartToCurrentDisplacement(),values);}
    public List<List<Vector3d>> initializationTrails(){return initializationTrails.stream().map(IncrementalSession::copy).toList();}
    public synchronized void skipInitializationAnimation(){if(initializationSnapshot.phase()==InitializationPhase.DISPLAY_ANIMATION){initializationStep=request.continuation().spectralInitialization().animationFrames();completeDisplayAnimation();publishSnapshot();}}
    public synchronized void restartInitializationAnimation(){if(request.continuation().spectralInitialization().mode()!=SpectralPresentationMode.ANIMATED_DISPLAY)return;initializationStep=0;initializationRevision++;publishedCoordinates=copy(randomStartCoordinates);initializationSnapshot=new InitializationSnapshot(SpectralPresentationMode.ANIMATED_DISPLAY,InitializationPhase.DISPLAY_ANIMATION,initializationRevision,0,0,0,0,false,true);executionState=ExecutionState.PAUSED;scientificState=ScientificState.INITIALIZED;}
    public int realizationIndex(){return attempt+1;}
    public long realizedSeed(){return request.randomSeed()+attempt;}
    public List<ActiveConstraintState> activeThreeDConstraints(){ThreeDConstraintDiagnostics d=threeDConstraintDiagnostics();ThreeDExperimentConditions m=request.threeDConditions();ThreeDConstraintStrengths s=request.threeDConstraintStrengths();return List.of(
            state("Face planarity",m.facePlanarity()!=FacePlanarityMode.MEASURE_ONLY,m.facePlanarity()==FacePlanarityMode.REQUIRE_PLANAR_FACES,d.maximumFacePlanarityError(),m.facePlanarityTolerance(),s.facePlanarity()),
            state("Face angles",m.faceAngles()!=FaceAngleConditionMode.NONE,m.faceAngles()==FaceAngleConditionMode.REQUIRE_EQUAL_ANGLES_WITHIN_EACH_FACE,d.maximumFaceAngleDeviation(),m.absoluteRegularityTolerance(),s.faceAngles()),
            state("Dihedrals",m.dihedrals()!=DihedralConditionMode.NONE,m.dihedrals()==DihedralConditionMode.REQUIRE_EQUAL_DIHEDRAL_ANGLES,d.maximumDihedralDeviation(),m.absoluteRegularityTolerance(),s.dihedrals()),
            state("Face-plane radii",m.facePlaneRadii()!=FacePlaneRadiusMode.NONE,m.facePlaneRadii()==FacePlaneRadiusMode.REQUIRE_EQUAL_FACE_PLANE_RADII,d.maximumFacePlaneRadiusDeviation(),m.absoluteRegularityTolerance(),s.facePlaneRadii()));}
    private static ActiveConstraintState state(String name,boolean enabled,boolean active,double error,double tolerance,double strength){return new ActiveConstraintState(name,enabled,active,error,tolerance,active?strength:0,error<=tolerance);}
    /** Compatibility view for callers that used the former combined state. */
    public SessionState state() {
        if (scientificState == ScientificState.CONVERGED) return SessionState.CONVERGED;
        if (scientificState == ScientificState.SEARCH_LIMIT_REACHED) return SessionState.EXHAUSTED;
        if (scientificState == ScientificState.FAILED) return SessionState.FAILED;
        if (scientificState == ScientificState.QUALIFICATION_FAILED) return SessionState.FAILED;
        return switch (executionState) {
            case IDLE -> SessionState.READY;
            case RUNNING -> SessionState.RUNNING;
            case PAUSED -> SessionState.PAUSED;
            case STOPPED -> SessionState.STOPPED;
        };
    }
    public ExecutionState executionState() { return executionState; }
    public ScientificState scientificState() { return scientificState; }
    public ScientificTimingSnapshot timing(){return timing;}
    public int convergenceIteration() { return convergenceIteration; }
    public TestResult result() { return finalResult; }
    public String stopReason() { return stopReason; }
    public ExperimentConvergenceDiagnostics convergenceDiagnostics() {
        List<Vector3d> coordinates=currentCoordinates();
        double maximumEdgeError=elastic.metrics().edges().stream().mapToDouble(edge->Math.abs(edge.distance()-edge.targetDistance())).max().orElse(0);
        if(request.dimension()==GeometryEmbeddingDimension.THREE_D){ThreeDConstraintDiagnostics d=threeDConstraintDiagnostics();boolean edge=maximumEdgeError<=request.edgeTolerance();boolean planar=request.threeDConditions().facePlanarity()!=FacePlanarityMode.REQUIRE_PLANAR_FACES||d.maximumFacePlanarityError()<=request.threeDConditions().facePlanarityTolerance();boolean angles=request.threeDConditions().faceAngles()!=FaceAngleConditionMode.REQUIRE_EQUAL_ANGLES_WITHIN_EACH_FACE||d.maximumFaceAngleDeviation()<=request.threeDConditions().absoluteRegularityTolerance();return new ExperimentConvergenceDiagnostics(iteration(),maximumEdgeError,d.maximumFacePlanarityError(),d.maximumFacePlanarityError(),d.maximumFacePlanarityError(),request.threeDConditions().facePlanarityTolerance(),d.dihedralVariance(),d.maximumFaceAngleDeviation(),edge?"PASS":"FAIL",planar?"PASS":"FAIL",request.threeDConditions().faceAngles()==FaceAngleConditionMode.MEASURE_ONLY?"MEASURE_ONLY":angles?"PASS":"FAIL","TOPOLOGICAL_FACE_COMPLEX_AVAILABLE","PENDING_FINAL_BOUNDARY_QUALIFICATION",scientificState==ScientificState.CONVERGED);}
        List<Integer> cycle=orderedCycle(configuration);
        TwoDRegularityMetrics regularity=new TwoDRegularityCalculator().calculate(configuration,coordinates,cycle,
                request.targetEdgeLength(),request.edgeTolerance(),request.planarityTolerance(),
                request.degeneracyTolerance(),request.angleTolerance(),request.angleConditionMode());
        double mean=regularity.planeDistances().stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double rms=Math.sqrt(regularity.planeDistances().stream().mapToDouble(v->v*v).average().orElse(0));
        String angle=switch(request.angleConditionMode()){case NONE->"NOT_APPLICABLE";case MEASURE_ONLY->"MEASURE_ONLY";case REQUIRE_EQUAL_ANGLES->regularity.equalAngles()?"PASS":"FAIL";};
        return new ExperimentConvergenceDiagnostics(iteration(),maximumEdgeError,
                regularity.maximumPlaneDeviation(),mean,rms,request.planarityTolerance(),
                regularity.interiorAngles().variance(),regularity.maximumAngleTargetDeviation(),
                regularity.equalEdges()?"PASS":"FAIL",regularity.planar()?"PASS":"FAIL",angle,
                regularity.simpleBoundary()?"PASS":"FAIL",regularity.positiveArea()?"PASS":"FAIL",
                publishedMetrics.elasticEquilibrium()&&regularity.valid());
    }
    public Configuration configuration() { return configuration; }
    public TestRequest request(){return request;}

    private void initializeAttempt() {
        long seed = request.randomSeed() + attempt;
        DynamicElasticSettings settings = DynamicElasticSettings.defaults(
                request.targetEdgeLength(), seed)
                .withMaximumIterations(Math.min(request.searchLimit(),
                        request.relaxationSettings().maximumIterations()))
                .withCanonicalGeometry(DynamicElasticCanonicalGeometrySettings.disabled());
        if(request.benchmarkPreset()==BenchmarkPreset.CUBE_CONSTRAINED_BENCHMARK
                &&request.continuation().strategy()==RealizationStrategy.SIMULTANEOUS)
            settings=settings.withMaximumIterations(Math.min(settings.maximumIterations(),BenchmarkPresets.CONSTRAINED_ITERATIONS_PER_REALIZATION));
        GeometryEmbeddingDimension initializationDimension=request.experimentProfile()==ExperimentProfile.CLOSED_2D_AREA
                && request.planarityMode()!=PlanarityRealizationMode.STRICT_2D_COORDINATES
                ? GeometryEmbeddingDimension.THREE_D:request.dimension();
        boolean spectral=request.continuation().initialization()==TopologyInitialization.SPECTRAL_EMBEDDING;
        SpectralInitializationSettings spectralSettings=request.continuation().spectralInitialization();
        spectralTargetCoordinates=spectral?new SpectralTopologyInitializer().initialize(configuration,request.targetEdgeLength()):List.of();
        randomStartCoordinates=spectral?initializeCoordinates(configuration.nodeCount(),initializationDimension,request.targetEdgeLength(),seed):List.of();
        initialCoordinates=spectral&&spectralSettings.mode()!=SpectralPresentationMode.INSTANT?copy(randomStartCoordinates)
                :spectral?copy(spectralTargetCoordinates):initializeCoordinates(configuration.nodeCount(),initializationDimension,request.targetEdgeLength(),seed);
        int secondAnchor=configuration.adjacency().get(0).stream().mapToInt(Integer::intValue).min().orElse(1);
        gaugeCoordinates(initialCoordinates,secondAnchor);
        if(spectral){gaugeCoordinates(spectralTargetCoordinates,secondAnchor);gaugeCoordinates(randomStartCoordinates,secondAnchor);if(spectralSettings.mode()!=SpectralPresentationMode.INSTANT)initialCoordinates=copy(randomStartCoordinates);}
        if(request.experimentProfile()==ExperimentProfile.CLOSED_2D_AREA
                &&request.angleConditionMode()==AngleConditionMode.REQUIRE_EQUAL_ANGLES
                &&request.planarityMode()==PlanarityRealizationMode.STRICT_2D_COORDINATES)
            initialCoordinates=regularCycleCoordinates(configuration.nodeCount(),request.targetEdgeLength());
        List<DynamicElasticNode> nodes = new ArrayList<>();
        for (int id = 0; id < initialCoordinates.size(); id++)
            nodes.add(new DynamicElasticNode(id, initialCoordinates.get(id),
                    new Vector3d(), id==0||id==secondAnchor));
        elastic = new DynamicElasticSession(graph, settings, nodes);
        if(request.experimentProfile()==ExperimentProfile.CLOSED_2D_AREA){
            boolean strict=request.planarityMode()==PlanarityRealizationMode.STRICT_2D_COORDINATES;
            boolean constrained=request.planarityMode()==PlanarityRealizationMode.PLANE_CONSTRAINT;
            double targetDegrees=Math.toDegrees((configuration.nodeCount()-2.0)*Math.PI/configuration.nodeCount());
            elastic.configureTwoDimensionalProfile(strict,constrained,request.planeRestoringStrength(),
                    request.planarityTolerance(),request.angleConditionMode()==AngleConditionMode.REQUIRE_EQUAL_ANGLES,
                    targetDegrees,Math.toDegrees(request.angleTolerance()),request.angleRestoringStrength());
        }else{
            topologyFaces=new TopologyEligibilityAnalyzer().deriveTopologicalFaces(configuration);
            continuationStageIndex=0;continuationStageIteration=0;continuationStable=0;continuationRampFactor=0;continuationRampCeiling=1;if(attempt==0)continuationHistory.clear();
            if(request.continuation().strategy()==RealizationStrategy.MANUAL_RELAXATION){manualActiveForces.clear();manualActions.clear();manualRegressions.clear();manualExecutionState=ManualExecutionState.MANUAL_IDLE;reconfigureManualForces();}else configureContinuationStage();
        }
        initializationStep=0;initializationRevision++;
        initializationFailure=null;
        initializationTrails=new ArrayList<>();for(Vector3d coordinate:initialCoordinates){List<Vector3d> path=new ArrayList<>();path.add(new Vector3d(coordinate));initializationTrails.add(path);}
        if(!spectral||spectralSettings.mode()==SpectralPresentationMode.INSTANT){scientificStartCoordinates=copy(initialCoordinates);initializationSnapshot=new InitializationSnapshot(SpectralPresentationMode.INSTANT,InitializationPhase.DYNAMIC_ELASTIC_REALIZATION,initializationRevision,1,0,0,1,true,false);}
        else if(spectralSettings.mode()==SpectralPresentationMode.ANIMATED_DISPLAY){initializationSnapshot=new InitializationSnapshot(SpectralPresentationMode.ANIMATED_DISPLAY,InitializationPhase.DISPLAY_ANIMATION,initializationRevision,0,0,0,0,false,true);}
        else initializationSnapshot=new InitializationSnapshot(SpectralPresentationMode.GUIDED_RELAXATION,InitializationPhase.RANDOM_START,initializationRevision,0,0,1,0,false,false);
    }

    private boolean advanceSpectralInitialization(){
        SpectralInitializationSettings settings=request.continuation().spectralInitialization();InitializationPhase phase=initializationSnapshot.phase();
        if(phase==InitializationPhase.DISPLAY_ANIMATION){int frames=settings.animationFrames();initializationStep++;double alpha=Math.min(1,initializationStep/(double)frames),eased=alpha*alpha*(3-2*alpha);publishedCoordinates=interpolate(randomStartCoordinates,spectralTargetCoordinates,eased);appendTrails(publishedCoordinates);initializationSnapshot=new InitializationSnapshot(settings.mode(),phase,++initializationRevision,alpha,0,0,0,false,true);if(initializationStep>=frames)completeDisplayAnimation();return true;}
        if(settings.mode()!=SpectralPresentationMode.GUIDED_RELAXATION||phase==InitializationPhase.DYNAMIC_ELASTIC_REALIZATION||phase==InitializationPhase.QUALIFICATION||phase==InitializationPhase.INSTANT)return false;
        if(phase==InitializationPhase.RANDOM_START){initializationStep=0;initializationSnapshot=new InitializationSnapshot(settings.mode(),InitializationPhase.SPECTRAL_GUIDANCE,++initializationRevision,0,0,1,0,false,false);return true;}
        if(phase==InitializationPhase.SPECTRAL_GUIDANCE){initializationStep++;double progress=Math.min(1,initializationStep/(double)settings.guidanceIterations());applySpectralGuidance(settings.guidanceStrength());publishedCoordinates=elastic.nodes().stream().map(n->new Vector3d(n.position())).toList();appendTrails(publishedCoordinates);initializationSnapshot=new InitializationSnapshot(settings.mode(),phase,++initializationRevision,progress,initializationStep,1,0,false,false);if(initializationStep>=settings.guidanceIterations()){initializationStep=0;initializationSnapshot=new InitializationSnapshot(settings.mode(),InitializationPhase.GUIDANCE_HANDOVER,++initializationRevision,0,settings.guidanceIterations(),1,0,false,false);}return true;}
        if(phase==InitializationPhase.GUIDANCE_HANDOVER){initializationStep++;double progress=Math.min(1,initializationStep/(double)settings.handoverIterations()),spectralFactor=1-progress;applySpectralGuidance(settings.guidanceStrength()*spectralFactor);applyGeometricHandover(progress);publishedCoordinates=elastic.nodes().stream().map(n->new Vector3d(n.position())).toList();appendTrails(publishedCoordinates);initializationSnapshot=new InitializationSnapshot(settings.mode(),phase,++initializationRevision,progress,settings.guidanceIterations()+initializationStep,spectralFactor,progress,false,false);if(initializationStep>=settings.handoverIterations()){scientificStartCoordinates=copy(publishedCoordinates);rebuildElastic(scientificStartCoordinates);initializationSnapshot=new InitializationSnapshot(settings.mode(),InitializationPhase.DYNAMIC_ELASTIC_REALIZATION,++initializationRevision,1,settings.guidanceIterations()+settings.handoverIterations(),0,1,true,false);}return true;}
        return false;
    }

    private void applySpectralGuidance(double strength){List<DynamicElasticNode> nodes=elastic.nodes();for(int i=0;i<nodes.size();i++)elastic.setInitializationNodeState(i,new Vector3d(nodes.get(i).position()).lerp(spectralTargetCoordinates.get(i),Math.min(1,strength)));}
    private void applyGeometricHandover(double factor){List<DynamicElasticNode> current=elastic.nodes();DynamicElasticSettings defaults=DynamicElasticSettings.defaults(request.targetEdgeLength(),request.randomSeed()+attempt);DynamicElasticSettings scaled=defaults.withSpringStrength(defaults.springStrength()*factor).withMaximumIterations(1);DynamicElasticSession handover=new DynamicElasticSession(graph,scaled,current);ThreeDConstraintStrengths full=request.threeDConstraintStrengths();handover.configureThreeDimensionalProfile(topologyFaces,request.threeDConditions(),new ThreeDConstraintStrengths(full.facePlanarity()*factor,full.faceAngles()*factor,full.dihedrals()*factor,full.facePlaneRadii()*factor));handover.setSpatialConstraintsEnabled(false);handover.step();List<DynamicElasticNode> moved=handover.nodes();for(int i=0;i<moved.size();i++)elastic.setInitializationNodeState(i,moved.get(i).position());}
    private void completeDisplayAnimation(){publishedCoordinates=copy(spectralTargetCoordinates);scientificStartCoordinates=copy(spectralTargetCoordinates);rebuildElastic(scientificStartCoordinates);initializationSnapshot=new InitializationSnapshot(SpectralPresentationMode.ANIMATED_DISPLAY,InitializationPhase.DYNAMIC_ELASTIC_REALIZATION,++initializationRevision,1,0,0,1,true,false);}
    private void rebuildElastic(List<Vector3d> coordinates){DynamicElasticSettings settings=DynamicElasticSettings.defaults(request.targetEdgeLength(),request.randomSeed()+attempt).withMaximumIterations(Math.min(request.searchLimit(),request.relaxationSettings().maximumIterations())).withCanonicalGeometry(DynamicElasticCanonicalGeometrySettings.disabled());int secondAnchor=configuration.adjacency().get(0).stream().mapToInt(Integer::intValue).min().orElse(1);List<DynamicElasticNode> nodes=new ArrayList<>();for(int id=0;id<coordinates.size();id++)nodes.add(new DynamicElasticNode(id,coordinates.get(id),new Vector3d(),id==0||id==secondAnchor));elastic=new DynamicElasticSession(graph,settings,nodes);if(request.dimension()==GeometryEmbeddingDimension.THREE_D)configureContinuationStage();}
    private static List<Vector3d> interpolate(List<Vector3d> from,List<Vector3d> to,double alpha){List<Vector3d> result=new ArrayList<>(from.size());for(int i=0;i<from.size();i++)result.add(new Vector3d(from.get(i)).lerp(to.get(i),alpha));return List.copyOf(result);}
    private static List<Vector3d> copy(List<Vector3d> value){return value.stream().map(Vector3d::new).toList();}
    private void appendTrails(List<Vector3d> coordinates){for(int i=0;i<coordinates.size();i++)initializationTrails.get(i).add(new Vector3d(coordinates.get(i)));}
    private void failInitialization(RuntimeException failure){InitializationPhase phase=initializationSnapshot.phase();boolean spectral=request.continuation().initialization()==TopologyInitialization.SPECTRAL_EMBEDDING;GeometryConfigurationStatusCode code=phase==InitializationPhase.GUIDANCE_HANDOVER?GeometryConfigurationStatusCode.GUIDANCE_HANDOVER_FAILED:spectral?GeometryConfigurationStatusCode.SPECTRAL_GUIDANCE_FAILED:GeometryConfigurationStatusCode.STAGE2_INITIALIZATION_NUMERICALLY_INVALID;String subsystem=phase==InitializationPhase.GUIDANCE_HANDOVER?"geometry handover":spectral?"spectral initializer":"Stage 2 initializer";String reason=failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage();GeometryConfigurationDiagnostic diagnostic=GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GEOMETRY_REALIZATION,code,"Stage 2 initialization failed",subsystem+": "+reason);initializationFailure=GeometryConfigurationStatus.of(diagnostic);scientificState=ScientificState.FAILED;executionState=ExecutionState.STOPPED;stopReason=diagnostic.explanation();}
    private static DisplacementSummary displacement(List<Vector3d> from,List<Vector3d> to){if(from.isEmpty()||from.size()!=to.size())return new DisplacementSummary(Double.NaN,Double.NaN,Double.NaN);double maximum=0,sum=0;for(int i=0;i<from.size();i++){double d=from.get(i).distance(to.get(i));maximum=Math.max(maximum,d);sum+=d*d;}double mean=0;for(int i=0;i<from.size();i++)mean+=from.get(i).distance(to.get(i));return new DisplacementSummary(maximum,mean/from.size(),Math.sqrt(sum/from.size()));}
    private InitializationMeasurement measure(String name,List<Vector3d> coordinates,boolean qualified){DynamicElasticSettings settings=DynamicElasticSettings.defaults(request.targetEdgeLength(),request.randomSeed());List<DynamicElasticNode> nodes=new ArrayList<>();for(int i=0;i<coordinates.size();i++)nodes.add(new DynamicElasticNode(i,coordinates.get(i),new Vector3d(),false));DynamicElasticSession probe=new DynamicElasticSession(graph,settings,nodes);probe.configureThreeDimensionalProfile(topologyFaces,request.threeDConditions(),request.threeDConstraintStrengths());ThreeDConstraintDiagnostics diagnostics=probe.threeDConstraintDiagnostics();double edge=probe.metrics().edges().stream().mapToDouble(e->Math.abs(e.distance()-e.targetDistance())).max().orElse(0);OrientationConvexityDiagnostics convexity=new OrientationConvexityCalculator().calculate(topologyFaces,coordinates,request.threeDConditions().facePlanarityTolerance());int intersections=name.startsWith("Final")&&finalResult!=null&&finalResult.boundary()!=null?finalResult.boundary().selfIntersecting()?1:0:-1;return new InitializationMeasurement(name,edge,diagnostics.maximumFacePlanarityError(),diagnostics.maximumFaceAngleDeviation(),diagnostics.maximumFacePlaneRadiusDeviation(),convexity.violatingPairs(),intersections,qualified?"QUALIFIED":name.startsWith("Final")?"NOT QUALIFIED":"NOT EVALUATED FOR QUALIFICATION");}

    private void configureContinuationStage(){
        if(request.continuation().strategy()!=RealizationStrategy.SEQUENTIAL_CONTINUATION){elastic.configureThreeDimensionalProfile(topologyFaces,request.threeDConditions(),request.threeDConstraintStrengths());continuationSnapshot=ContinuationSnapshot.simultaneous(request.continuation().initialization(),request.continuation().spectralInitialization(),initializationSnapshot);return;}
        ContinuationStageDefinition stage=request.continuation().schedule().stages().get(continuationStageIndex);Set<ContinuationConstraint> active=new LinkedHashSet<>(stage.retained());active.addAll(stage.introduced());double factor=stage.rampIterations()==0?1:Math.min(1,stage.startingFactor()+continuationStageIteration*(stage.finalFactor()-stage.startingFactor())/stage.rampIterations());factor=Math.min(factor,continuationRampCeiling);continuationRampFactor=factor;
        ThreeDExperimentConditions original=request.threeDConditions();ThreeDExperimentConditions modes=new ThreeDExperimentConditions(active.contains(ContinuationConstraint.FACE_PLANARITY)?original.facePlanarity():FacePlanarityMode.MEASURE_ONLY,active.contains(ContinuationConstraint.FACE_ANGLE_EQUALITY)?original.faceAngles():FaceAngleConditionMode.MEASURE_ONLY,active.contains(ContinuationConstraint.JOINT_QUALIFICATION)?original.dihedrals():DihedralConditionMode.MEASURE_ONLY,original.vertexRadii(),original.faceCentroidRadii(),original.edgeMidpointRadii(),active.contains(ContinuationConstraint.FACE_PLANE_RADII)||active.contains(ContinuationConstraint.JOINT_QUALIFICATION)?original.facePlaneRadii():FacePlaneRadiusMode.MEASURE_ONLY,original.normalCenterAlignment(),original.centerDefinition(),original.facePlanarityTolerance(),original.absoluteRegularityTolerance(),original.relativeRegularityTolerance());ThreeDConstraintStrengths full=request.threeDConstraintStrengths();ThreeDConstraintStrengths strengths=new ThreeDConstraintStrengths(full.facePlanarity()*(stage.introduced().contains(ContinuationConstraint.FACE_PLANARITY)?factor:1),full.faceAngles()*(stage.introduced().contains(ContinuationConstraint.FACE_ANGLE_EQUALITY)?factor:1),full.dihedrals()*(stage.introduced().contains(ContinuationConstraint.JOINT_QUALIFICATION)?factor:1),full.facePlaneRadii()*(stage.introduced().contains(ContinuationConstraint.FACE_PLANE_RADII)?factor:1));boolean orientation=active.contains(ContinuationConstraint.OUTWARD_ORIENTATION);double orientationFactor=stage.introduced().contains(ContinuationConstraint.OUTWARD_ORIENTATION)?factor:1;elastic.configureThreeDimensionalProfile(topologyFaces,modes,strengths,orientation,request.continuation().outwardOrientationStrength()*orientationFactor);
        elastic.setSpatialConstraintsEnabled(active.contains(ContinuationConstraint.JOINT_QUALIFICATION));
    }

    private void reconfigureManualForces(){
        if(request.continuation().strategy()!=RealizationStrategy.MANUAL_RELAXATION)return;
        elastic.setSpringStrength(manualActiveForces.contains(ManualConstraint.EDGE_LENGTH)?request.relaxationSettings().springStrength():0);
        ThreeDExperimentConditions original=request.threeDConditions();
        ThreeDExperimentConditions modes=new ThreeDExperimentConditions(manualActiveForces.contains(ManualConstraint.FACE_PLANARITY)?FacePlanarityMode.REQUIRE_PLANAR_FACES:FacePlanarityMode.MEASURE_ONLY,manualActiveForces.contains(ManualConstraint.FACE_ANGLE)?FaceAngleConditionMode.REQUIRE_EQUAL_ANGLES_WITHIN_EACH_FACE:FaceAngleConditionMode.MEASURE_ONLY,manualActiveForces.contains(ManualConstraint.DIHEDRAL_ANGLE)?DihedralConditionMode.REQUIRE_EQUAL_DIHEDRAL_ANGLES:DihedralConditionMode.MEASURE_ONLY,original.vertexRadii(),original.faceCentroidRadii(),original.edgeMidpointRadii(),manualActiveForces.contains(ManualConstraint.FACE_DISTANCE)?FacePlaneRadiusMode.REQUIRE_EQUAL_FACE_PLANE_RADII:FacePlaneRadiusMode.MEASURE_ONLY,original.normalCenterAlignment(),original.centerDefinition(),original.facePlanarityTolerance(),original.absoluteRegularityTolerance(),original.relativeRegularityTolerance());
        elastic.configureThreeDimensionalProfile(topologyFaces,modes,request.threeDConstraintStrengths(),manualActiveForces.contains(ManualConstraint.FACE_ORIENTATION),request.continuation().outwardOrientationStrength(),manualActiveForces.contains(ManualConstraint.SELF_INTERSECTION_AVOIDANCE),.05,.6,manualActiveForces.contains(ManualConstraint.CONVEXITY),request.threeDConditions().facePlanarityTolerance(),.6);
        elastic.setSpatialConstraintsEnabled(false);
        elastic.refreshDiagnostics();
    }
    private ContinuationConstraint manualContinuationConstraint(ManualConstraint constraint){return switch(constraint){case EDGE_LENGTH->ContinuationConstraint.EDGE_EQUALITY;case FACE_ORIENTATION->ContinuationConstraint.OUTWARD_ORIENTATION;case FACE_PLANARITY->ContinuationConstraint.FACE_PLANARITY;case FACE_ANGLE->ContinuationConstraint.FACE_ANGLE_EQUALITY;case FACE_DISTANCE->ContinuationConstraint.FACE_PLANE_RADII;default->null;};}

    private void advanceContinuation(){ContinuationSchedule schedule=request.continuation().schedule();ContinuationStageDefinition stage=schedule.stages().get(continuationStageIndex);continuationStageIteration++;configureContinuationStage();orientationConvexity=new OrientationConvexityCalculator().calculate(topologyFaces,elastic.nodes().stream().map(n->new Vector3d(n.position())).toList(),request.threeDConditions().facePlanarityTolerance());Set<ContinuationConstraint> active=new LinkedHashSet<>(stage.retained());active.addAll(stage.introduced());ThreeDConstraintDiagnostics d=elastic.threeDConstraintDiagnostics();double worst=maximumEdgeErrorNow()/request.edgeTolerance();String worstName="Connected edge equality";if(active.contains(ContinuationConstraint.OUTWARD_ORIENTATION)){double ratio=orientationConvexity.outwardError()==0?0:Double.POSITIVE_INFINITY;if(ratio>worst){worst=ratio;worstName="Outward orientation";}}if(active.contains(ContinuationConstraint.FACE_PLANARITY)){double ratio=d.maximumFacePlanarityError()/request.threeDConditions().facePlanarityTolerance();if(ratio>worst){worst=ratio;worstName="Face planarity";}}if(active.contains(ContinuationConstraint.FACE_ANGLE_EQUALITY)){double ratio=d.maximumFaceAngleDeviation()/request.threeDConditions().absoluteRegularityTolerance();if(ratio>worst){worst=ratio;worstName="Face-angle equality";}}if(active.contains(ContinuationConstraint.FACE_PLANE_RADII)){double ratio=d.maximumFacePlaneRadiusDeviation()/request.threeDConditions().absoluteRegularityTolerance();if(ratio>worst){worst=ratio;worstName="Face-plane radii";}}boolean discrete=!active.contains(ContinuationConstraint.OUTWARD_ORIENTATION)||orientationConvexity.orientation()==OrientationState.COHERENT_AND_OUTWARD||orientationConvexity.orientation()==OrientationState.COHERENT_BUT_INWARD;boolean satisfied=continuationRampFactor>=1&&worst<=1&&discrete&&elastic.metrics().maximumNodeSpeed()<=request.relaxationSettings().maximumEquilibriumSpeed();continuationStable=satisfied?continuationStable+1:0;ContinuationStageState state=continuationRampFactor<1?ContinuationStageState.RAMPING:ContinuationStageState.RELAXING;String failure="";
        boolean retainedOrientationRegression=stage.retained().contains(ContinuationConstraint.OUTWARD_ORIENTATION)&&orientationConvexity.outwardError()>0;
        if(retainedOrientationRegression)continuationRampCeiling=Math.max(.05,continuationRampCeiling*.5);else if(continuationRampCeiling<1)continuationRampCeiling=Math.min(1,continuationRampCeiling+.002);
        if(continuationStable>=stage.stableIterations()){state=ContinuationStageState.SATISFIED;continuationHistory.add(new ContinuationStageOutcome(stage.id(),state,continuationStageIteration,continuationStable,continuationRampFactor,worstName,worst,"All retained and introduced transition conditions remained satisfied."));if(continuationStageIndex+1<schedule.stages().size()){continuationStageIndex++;continuationStageIteration=0;continuationStable=0;continuationRampCeiling=1;configureContinuationStage();elastic.continueRelaxation();}else{elastic.acceptContinuationEquilibrium();finishAttempt();}}
        else if(elastic.stagnationStatus().classification()==StagnationClass.PLATEAUED){state=ContinuationStageState.PLATEAUED;failure="Stage "+stage.id()+" plateaued outside transition criteria; worst constraint "+worstName+" ratio="+worst;continuationHistory.add(new ContinuationStageOutcome(stage.id(),state,continuationStageIteration,continuationStable,continuationRampFactor,worstName,worst,failure));stopReason=failure;if(++attempt<request.initialRealizations())initializeAttempt();else{finalResult=finalizeResult(configuration,request,elastic.result()).withContinuation(continuationSnapshot);scientificState=ScientificState.SEARCH_LIMIT_REACHED;}}
        else if(continuationStageIteration>=stage.maximumIterations()){state=ContinuationStageState.FAILED;failure="Stage "+stage.id()+" reached its iteration limit outside transition criteria; worst constraint "+worstName+" ratio="+worst;continuationHistory.add(new ContinuationStageOutcome(stage.id(),state,continuationStageIteration,continuationStable,continuationRampFactor,worstName,worst,failure));stopReason=failure;if(++attempt<request.initialRealizations())initializeAttempt();else{finalResult=finalizeResult(configuration,request,elastic.result()).withContinuation(continuationSnapshot);scientificState=ScientificState.SEARCH_LIMIT_REACHED;}}
        else if(elastic.finished())elastic.continueRelaxation();
        ContinuationStageDefinition current=schedule.stages().get(Math.min(continuationStageIndex,schedule.stages().size()-1));continuationSnapshot=new ContinuationSnapshot(RealizationStrategy.SEQUENTIAL_CONTINUATION,schedule.id(),schedule.version(),request.continuation().initialization(),continuationStageIndex+1,schedule.stages().size(),current.id(),current.displayName(),state,continuationStageIteration,elastic.iteration(),continuationRampFactor,continuationStable,current.stableIterations(),worstName,worst,List.copyOf(active),List.copyOf(stage.retained()),List.copyOf(continuationHistory),failure,request.continuation().spectralInitialization(),initializationSnapshot);if(finalResult!=null)finalResult=finalResult.withContinuation(continuationSnapshot);
    }
    private double maximumEdgeErrorNow(){return elastic.metrics().edges().stream().mapToDouble(e->Math.abs(e.distance()-e.targetDistance())).max().orElse(0);}

    private void publishSnapshot(){long started=System.nanoTime();if(!initializationSnapshot.displayPreview())publishedCoordinates=elastic.nodes().stream().map(node->new Vector3d(node.position())).toList();publishedMetrics=elastic.metrics();publishedMaximumEdgeError=publishedMetrics.edges().stream().mapToDouble(edge->Math.abs(edge.distance()-edge.targetDistance())).max().orElse(0);publishedThreeD=elastic.threeDConstraintDiagnostics();publishedSelfIntersection=elastic.selfIntersectionDiagnostics();if(!topologyFaces.isEmpty()&&!publishedCoordinates.isEmpty())orientationConvexity=new OrientationConvexityCalculator().calculate(topologyFaces,publishedCoordinates,request.threeDConditions().facePlanarityTolerance());publishedIteration=elastic.iteration();publishedMeasurements=request.dimension()==GeometryEmbeddingDimension.THREE_D?new Stage2MeasurementCalculator().calculate(graph,topologyFaces,publishedCoordinates,request.threeDConditions(),publishedIteration,request.targetEdgeLength()):Map.of();publishedObjectives=elastic.objectiveDiagnostics();publishedStagnation=elastic.stagnationStatus();if(request.continuation().strategy()==RealizationStrategy.SIMULTANEOUS)continuationSnapshot=ContinuationSnapshot.simultaneous(request.continuation().initialization(),request.continuation().spectralInitialization(),initializationSnapshot);else if(request.continuation().strategy()==RealizationStrategy.MANUAL_RELAXATION)continuationSnapshot=ContinuationSnapshot.manual(request.continuation().initialization(),request.continuation().spectralInitialization(),initializationSnapshot,manualActiveForces.stream().map(this::manualContinuationConstraint).filter(java.util.Objects::nonNull).toList(),publishedIteration);long publication=System.nanoTime()-started;DevelopmentTimingDiagnostics.record("stage2.snapshot-publication",publication);long count=Math.max(0,publishedIteration);double average=count==0?0:totalIterationNanos/1_000_000d/count;timing=new ScientificTimingSnapshot(timing.initializationNanos(),count,average,average==0?0:1000d/average,publication);}
    private static String manualMeasurementId(ManualConstraint c){return switch(c){case EDGE_LENGTH->"edge-equality";case FACE_ORIENTATION->"outward-face-orientation";case CONVEXITY->"convexity";case FACE_PLANARITY->"face-planarity";case FACE_ANGLE->"face-angle-equality";case FACE_DISTANCE->"face-plane-radius-equality";case NORMAL_ALIGNMENT->"normal-center-alignment";case FACE_AREA->"equal-face-area";case DIHEDRAL_ANGLE->"dihedral-equality";case VERTEX_DISTANCE->"vertex-radius-equality";case FACE_CENTER_DISTANCE->"face-centroid-radius-equality";case EDGE_CENTER_DISTANCE->"edge-midpoint-radius-equality";case SELF_INTERSECTION_AVOIDANCE->"self-intersection";};}

    private void finishAttempt() {
        if (finalResult != null) return;
        initializationSnapshot=new InitializationSnapshot(initializationSnapshot.mode(),InitializationPhase.QUALIFICATION,++initializationRevision,1,initializationSnapshot.initializationIteration(),0,1,false,false);
        if(request.continuation().strategy()==RealizationStrategy.SIMULTANEOUS)continuationSnapshot=ContinuationSnapshot.simultaneous(request.continuation().initialization(),request.continuation().spectralInitialization(),initializationSnapshot);
        DynamicElasticResult elasticResult = elastic.result();
        if (elasticResult.resultStatus() == DynamicElasticResultStatus.STABLE_CANDIDATE_FOUND) {
            finalResult = DevelopmentTimingDiagnostics.measure("stage2.final-qualification",()->finalizeResult(configuration, request, elasticResult)).withContinuation(continuationSnapshot);
            scientificState = finalResult.status().code()==GeometryConfigurationStatusCode.VALID_CANDIDATE
                    ? ScientificState.CONVERGED : ScientificState.QUALIFICATION_FAILED;
            convergenceIteration = iteration();
            return;
        }
        if (elasticResult.resultStatus() == DynamicElasticResultStatus.NUMERICAL_FAILURE) {
            finalResult = DevelopmentTimingDiagnostics.measure("stage2.final-qualification",()->finalizeResult(configuration, request, elasticResult)).withContinuation(continuationSnapshot);
            scientificState = ScientificState.FAILED;
            convergenceIteration = iteration();
            return;
        }
        if (++attempt < request.initialRealizations()) {
            initializeAttempt();
            scientificState = request.dimension()==GeometryEmbeddingDimension.THREE_D?ScientificState.RELAXING_CONSTRAINTS:ScientificState.RELAXING;
        } else {
            finalResult = DevelopmentTimingDiagnostics.measure("stage2.final-qualification",()->finalizeResult(configuration, request, elasticResult)).withContinuation(continuationSnapshot);
            scientificState = ScientificState.SEARCH_LIMIT_REACHED;
            convergenceIteration = iteration();
        }
    }

    static TestResult finalizeResult(Configuration configuration,
            TestRequest request, DynamicElasticResult result) {
        TopologyEligibilityReport eligibility = new TopologyEligibilityAnalyzer()
                .analyze(configuration, request.experimentProfile(),request.faceComposition());
        List<Vector3d> initial = result.initialNodes().stream()
                .map(node -> new Vector3d(node.position())).toList();
        List<Vector3d> coordinates = result.finalNodes().stream()
                .map(node -> new Vector3d(node.position())).toList();
        RealizationStatus realizationStatus = switch (result.resultStatus()) {
            case STABLE_CANDIDATE_FOUND -> RealizationStatus.REALIZED;
            case NUMERICAL_FAILURE -> RealizationStatus.NUMERICAL_FAILURE;
            default -> RealizationStatus.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS;
        };
        GeometryConfigurationStatusCode code = switch (realizationStatus) {
            case REALIZED -> GeometryConfigurationStatusCode.GEOMETRICALLY_REALIZED;
            case NUMERICAL_FAILURE -> GeometryConfigurationStatusCode.NUMERICAL_FAILURE;
            default -> GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS;
        };
        GeometryConfigurationDiagnostic diagnostic = realizationStatus
                == RealizationStatus.REALIZED
                ? GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.GEOMETRY_REALIZATION,
                code, "Geometrically realized",
                "Equal connected-edge lengths reached numerical equilibrium; topology remained fixed.")
                : GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GEOMETRY_REALIZATION,
                code, "No realization found within search limits",
                "The numerical search did not find a realization; this is not proof of non-embeddability.");
        Realization realization = new Realization(result.settings().randomSeed(),
                request.dimension(), initial, coordinates, result.metrics().meanDistanceAccuracy(),
                result.metrics().maximumNodeSpeed(), realizationStatus == RealizationStatus.REALIZED,
                result.metrics().minimumObservedNonNeighborDistance() > 0,
                result.metrics().edgeClearanceViolations() == 0, effectiveDimension(coordinates),
                realizationStatus, List.of(diagnostic));
        if (!realization.converged()) {
            GeometryConfigurationStatus status = GeometryConfigurationStatus.of(diagnostic);
            return new TestResult(configuration.id(), request.experimentProfile(),request.planarityMode(),request.angleConditionMode(), eligibility,
                    configuration.graphSymmetry(), realization, null, null, null,
                    AreaResult.unavailable(status), VolumeResult.unavailable(status), null, null, null,
                    GeometryOptimizationResult.notAttempted(), null, status,
                    request.benchmarkPreset(), new ReferenceTopologyRecognizer().recognize(configuration),request.threeDConstraintStrengths(),request.randomSeed(),request.faceComposition(),request.primaryParameterMode(),request.surfaceTopologyAssumption(),request.parameterDerivation(),null);
        }
        FaceDerivationResult faces = new FaceDerivationPipeline().derive(configuration, realization);
        ClosedBoundaryResult boundary = new ClosedBoundaryValidator().validate(configuration,
                realization, faces);
        GeometricSymmetryResult symmetry = new GeometricSymmetryAnalyzer().analyze(configuration, realization);
        GeometryMeasureCalculator measures = new GeometryMeasureCalculator();
        AreaResult area = measures.area(realization, boundary);
        VolumeResult volume = measures.volume(realization, boundary);
        TwoDRegularityMetrics twoD = null;
        ThreeDRegularityMetrics threeD = null;
        ThreeDConditionReport threeDConditions = null;
        boolean qualified = false;
        if (request.dimension() == GeometryEmbeddingDimension.TWO_D && !faces.faces().isEmpty()) {
            twoD = new TwoDRegularityCalculator().calculate(configuration, coordinates,
                    faces.faces().get(0).nodeIds(), request.targetEdgeLength(), request.edgeTolerance(),
                    request.planarityTolerance(), request.degeneracyTolerance(), request.angleTolerance(),
                    request.angleConditionMode());
            qualified = twoD.valid();
            if (!qualified) { area = AreaResult.unavailable(boundary.status()); }
        } else if (request.dimension() == GeometryEmbeddingDimension.THREE_D && boundary.closed()
                && volume.available() && volume.absoluteVolume() > request.degeneracyTolerance()) {
            threeD = new ThreeDRegularityCalculator().calculate(configuration, coordinates,
                    boundary, request.targetEdgeLength(),request.threeDConditions().centerDefinition(),
                    request.threeDConditions().facePlanarityTolerance());
            qualified = request.threeDConditions().facePlanarity()!=FacePlanarityMode.REQUIRE_PLANAR_FACES
                    ||threeD.facePlanes().stream().allMatch(FacePlaneDiagnostic::planar);
            threeDConditions=new ThreeDConditionEvaluator().evaluate(qualified,threeD,request.threeDConditions());
            qualified=threeDConditions.constrainedExperimentPassed();
        }
        if(request.faceComposition().mode()==FaceCompositionMode.UNIFORM_FACE_SIZE)
            qualified &= eligibility.faceComposition()!=null
                    &&eligibility.faceComposition().match()==FaceCompositionMatch.MATCH;
        GeometryConfigurationStatus status = qualified
                ? GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.info(
                GeometryConfigurationStage.COMPLETE, GeometryConfigurationStatusCode.VALID_CANDIDATE,
                "Valid candidate", "Realization, boundary, non-degeneracy, and profile-specific geometric qualification passed."))
                : boundary.status();
        List<ConstraintMeasurement> measurements=request.dimension()==GeometryEmbeddingDimension.THREE_D
                ? new Stage2MeasurementCalculator().calculate(new FixedDegreeGraph(configuration.nodeCount(),configuration.exactDegree(),configuration.edges(),configuration.canonicalGraphLabel()),
                faces.faces().stream().map(DerivedFace::nodeIds).toList(),coordinates,request.threeDConditions(),0,request.targetEdgeLength()).values().stream().toList():List.of();
        return new TestResult(configuration.id(), request.experimentProfile(),request.planarityMode(),request.angleConditionMode(), eligibility,
                configuration.graphSymmetry(), realization, faces, boundary, symmetry, area, volume,
                twoD, threeD,threeDConditions, GeometryOptimizationResult.notAttempted(), null, status,
                request.benchmarkPreset(), new ReferenceTopologyRecognizer().recognize(configuration),request.threeDConstraintStrengths(),request.randomSeed(),request.faceComposition(),request.primaryParameterMode(),request.surfaceTopologyAssumption(),request.parameterDerivation(),null).withConstraintMeasurements(measurements);
    }

    static List<Vector3d> initializeCoordinates(int count, GeometryEmbeddingDimension dimension,
            double length, long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        List<Vector3d> result = new ArrayList<>();
        for (int id = 0; id < count; id++) {
            Vector3d position;
            if (id == 0) position = new Vector3d();
            else if (id == 1) position = new Vector3d(length, 0, 0);
            else if (dimension == GeometryEmbeddingDimension.TWO_D) {
                double angle = 2 * Math.PI * id / count;
                position = new Vector3d(length * Math.cos(angle), length * Math.sin(angle), 0);
            } else {
                position = new Vector3d(random.nextDouble(-1,1),random.nextDouble(-1,1),random.nextDouble(-1,1));
                if(position.lengthSquared()<1e-6)position.set(0,0,1);
                position.normalize(length*random.nextDouble(.75,1.25));
            }
            result.add(position);
        }
        return result;
    }

    static void gaugeCoordinates(List<Vector3d> coordinates,int secondAnchor){Vector3d origin=new Vector3d(coordinates.get(0));coordinates.forEach(p->p.sub(origin));Vector3d direction=new Vector3d(coordinates.get(secondAnchor));if(direction.lengthSquared()<=1e-20)throw new IllegalArgumentException("Gauge anchor edge is degenerate.");Quaterniond rotation=new Quaterniond().rotationTo(direction.normalize(),new Vector3d(1,0,0));coordinates.forEach(rotation::transform);}

    private static List<Integer> orderedCycle(Configuration configuration){
        List<Integer> result=new ArrayList<>();int previous=-1,current=0;
        do{result.add(current);List<Integer> neighbors=configuration.adjacency().get(current);int next=neighbors.get(0)==previous?neighbors.get(1):neighbors.get(0);previous=current;current=next;}while(current!=0&&result.size()<=configuration.nodeCount());
        return result;
    }
    private static List<Vector3d> regularCycleCoordinates(int count,double edgeLength){List<Vector3d> result=new ArrayList<>();Vector3d current=new Vector3d();result.add(new Vector3d(current));for(int edge=0;edge<count-1;edge++){double direction=edge*2*Math.PI/count;current.add(edgeLength*Math.cos(direction),edgeLength*Math.sin(direction),0);result.add(new Vector3d(current));}return result;}

    private static int effectiveDimension(List<Vector3d> coordinates) {
        return coordinates.stream().mapToDouble(coordinate -> Math.abs(coordinate.z))
                .max().orElse(0) < 1e-7 ? 2 : 3;
    }
}
