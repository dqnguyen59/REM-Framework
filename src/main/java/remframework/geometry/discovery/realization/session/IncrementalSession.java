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

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import remframework.geometry.discovery.eligibility.*;
import remframework.geometry.discovery.measurement.regularity.*;
import remframework.geometry.discovery.reference.ReferenceTopologyRecognizer;
import remframework.geometry.discovery.experiment.*;

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
    private volatile ThreeDConstraintDiagnostics publishedThreeD=ThreeDConstraintDiagnostics.inactive();
    private volatile int publishedIteration;
    private volatile ScientificTimingSnapshot timing=ScientificTimingSnapshot.initial(0);
    private long totalIterationNanos;

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
        if (executionState == ExecutionState.IDLE) executionState = ExecutionState.RUNNING;
        if (scientificState == ScientificState.INITIALIZED) scientificState = request.dimension()==GeometryEmbeddingDimension.THREE_D?ScientificState.RELAXING_CONSTRAINTS:ScientificState.RELAXING;
        long started=System.nanoTime();elastic.step();totalIterationNanos+=System.nanoTime()-started;
        if (elastic.finished()) finishAttempt();
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
        resume();
        while (executionState == ExecutionState.RUNNING
                && scientificState != ScientificState.CONVERGED
                && scientificState != ScientificState.SEARCH_LIMIT_REACHED
                && scientificState != ScientificState.FAILED) step();
    }

    public synchronized void resume() {
        if (scientificState != ScientificState.CONVERGED&&scientificState!=ScientificState.FAILED
                &&scientificState!=ScientificState.SEARCH_LIMIT_REACHED) executionState = ExecutionState.RUNNING;
    }

    public void pause() {
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

    public List<Vector3d> currentCoordinates() {
        return publishedCoordinates.stream().map(Vector3d::new).toList();
    }

    public synchronized List<Vector3d> initialCoordinates() {
        return initialCoordinates.stream().map(Vector3d::new).toList();
    }

    public int iteration() { return publishedIteration; }
    public DynamicElasticMetrics metrics() { return publishedMetrics; }
    public ThreeDConstraintDiagnostics threeDConstraintDiagnostics(){return publishedThreeD;}
    public ConstraintObjectiveDiagnostics objectiveDiagnostics(){return elastic.objectiveDiagnostics();}
    public StagnationStatus stagnationStatus(){return elastic.stagnationStatus();}
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
        if(request.benchmarkPreset()==BenchmarkPreset.CUBE_CONSTRAINED_BENCHMARK)
            settings=settings.withMaximumIterations(Math.min(settings.maximumIterations(),BenchmarkPresets.CONSTRAINED_ITERATIONS_PER_REALIZATION));
        GeometryEmbeddingDimension initializationDimension=request.experimentProfile()==ExperimentProfile.CLOSED_2D_AREA
                && request.planarityMode()!=PlanarityRealizationMode.STRICT_2D_COORDINATES
                ? GeometryEmbeddingDimension.THREE_D:request.dimension();
        initialCoordinates = initializeCoordinates(configuration.nodeCount(),
                initializationDimension, request.targetEdgeLength(), seed);
        int secondAnchor=configuration.adjacency().get(0).stream().mapToInt(Integer::intValue).min().orElse(1);
        initialCoordinates.get(secondAnchor).set(request.targetEdgeLength(),0,0);
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
            List<List<Integer>> topologyFaces=new TopologyEligibilityAnalyzer().deriveTopologicalFaces(configuration);
            elastic.configureThreeDimensionalProfile(topologyFaces,request.threeDConditions(),request.threeDConstraintStrengths());
        }
    }

    private void publishSnapshot(){long started=System.nanoTime();publishedCoordinates=elastic.nodes().stream().map(node->new Vector3d(node.position())).toList();publishedMetrics=elastic.metrics();publishedThreeD=elastic.threeDConstraintDiagnostics();publishedIteration=elastic.iteration();long publication=System.nanoTime()-started;DevelopmentTimingDiagnostics.record("stage2.snapshot-publication",publication);long count=Math.max(0,publishedIteration);double average=count==0?0:totalIterationNanos/1_000_000d/count;timing=new ScientificTimingSnapshot(timing.initializationNanos(),count,average,average==0?0:1000d/average,publication);}

    private void finishAttempt() {
        if (finalResult != null) return;
        DynamicElasticResult elasticResult = elastic.result();
        if (elasticResult.resultStatus() == DynamicElasticResultStatus.STABLE_CANDIDATE_FOUND) {
            finalResult = DevelopmentTimingDiagnostics.measure("stage2.final-qualification",()->finalizeResult(configuration, request, elasticResult));
            scientificState = finalResult.status().code()==GeometryConfigurationStatusCode.VALID_CANDIDATE
                    ? ScientificState.CONVERGED : ScientificState.QUALIFICATION_FAILED;
            convergenceIteration = iteration();
            return;
        }
        if (elasticResult.resultStatus() == DynamicElasticResultStatus.NUMERICAL_FAILURE) {
            finalResult = DevelopmentTimingDiagnostics.measure("stage2.final-qualification",()->finalizeResult(configuration, request, elasticResult));
            scientificState = ScientificState.FAILED;
            convergenceIteration = iteration();
            return;
        }
        if (++attempt < request.initialRealizations()) {
            initializeAttempt();
            scientificState = request.dimension()==GeometryEmbeddingDimension.THREE_D?ScientificState.RELAXING_CONSTRAINTS:ScientificState.RELAXING;
        } else {
            finalResult = DevelopmentTimingDiagnostics.measure("stage2.final-qualification",()->finalizeResult(configuration, request, elasticResult));
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
                    request.benchmarkPreset(), new ReferenceTopologyRecognizer().recognize(configuration),request.threeDConstraintStrengths(),request.randomSeed(),request.faceComposition(),request.primaryParameterMode(),request.surfaceTopologyAssumption(),request.parameterDerivation());
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
        return new TestResult(configuration.id(), request.experimentProfile(),request.planarityMode(),request.angleConditionMode(), eligibility,
                configuration.graphSymmetry(), realization, faces, boundary, symmetry, area, volume,
                twoD, threeD,threeDConditions, GeometryOptimizationResult.notAttempted(), null, status,
                request.benchmarkPreset(), new ReferenceTopologyRecognizer().recognize(configuration),request.threeDConstraintStrengths(),request.randomSeed(),request.faceComposition(),request.primaryParameterMode(),request.surfaceTopologyAssumption(),request.parameterDerivation());
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
