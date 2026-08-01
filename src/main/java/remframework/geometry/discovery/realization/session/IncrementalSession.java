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
    private ExecutionState executionState = ExecutionState.IDLE;
    private ScientificState scientificState = ScientificState.INITIALIZED;
    private TestResult finalResult;
    private String stopReason = "";
    private int convergenceIteration = -1;

    public IncrementalSession(Configuration configuration,
            TestRequest request) {
        if (!configuration.id().equals(request.configurationId()))
            throw new IllegalArgumentException("Selected configuration ID does not match the test request.");
        this.configuration = configuration;
        this.request = request;
        this.graph = new FixedDegreeGraph(configuration.nodeCount(), configuration.exactDegree(),
                configuration.edges(), configuration.canonicalGraphLabel());
        initializeAttempt();
    }

    public synchronized void initialize() {
        reset();
    }

    public synchronized boolean step() {
        if (executionState == ExecutionState.STOPPED) return false;
        if (executionState == ExecutionState.IDLE) executionState = ExecutionState.RUNNING;
        if (scientificState == ScientificState.INITIALIZED) scientificState = ScientificState.RELAXING;
        elastic.step();
        if (elastic.finished()) finishAttempt();
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
        if (executionState != ExecutionState.STOPPED) executionState = ExecutionState.RUNNING;
    }

    public synchronized void pause() {
        if (executionState == ExecutionState.RUNNING) executionState = ExecutionState.PAUSED;
    }

    public synchronized void stop() {
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
    }

    public synchronized List<Vector3d> currentCoordinates() {
        return elastic.nodes().stream().map(node -> new Vector3d(node.position())).toList();
    }

    public synchronized List<Vector3d> initialCoordinates() {
        return initialCoordinates.stream().map(Vector3d::new).toList();
    }

    public synchronized int iteration() { return elastic.iteration(); }
    public synchronized DynamicElasticMetrics metrics() { return elastic.metrics(); }
    /** Compatibility view for callers that used the former combined state. */
    public synchronized SessionState state() {
        if (scientificState == ScientificState.CONVERGED) return SessionState.CONVERGED;
        if (scientificState == ScientificState.SEARCH_LIMIT_REACHED) return SessionState.EXHAUSTED;
        if (scientificState == ScientificState.FAILED) return SessionState.FAILED;
        return switch (executionState) {
            case IDLE -> SessionState.READY;
            case RUNNING -> SessionState.RUNNING;
            case PAUSED -> SessionState.PAUSED;
            case STOPPED -> SessionState.STOPPED;
        };
    }
    public synchronized ExecutionState executionState() { return executionState; }
    public synchronized ScientificState scientificState() { return scientificState; }
    public synchronized int convergenceIteration() { return convergenceIteration; }
    public synchronized TestResult result() { return finalResult; }
    public synchronized String stopReason() { return stopReason; }
    public Configuration configuration() { return configuration; }

    private void initializeAttempt() {
        long seed = request.randomSeed() + attempt;
        DynamicElasticSettings settings = DynamicElasticSettings.defaults(
                request.targetEdgeLength(), seed)
                .withMaximumIterations(Math.min(request.searchLimit(),
                        request.relaxationSettings().maximumIterations()))
                .withCanonicalGeometry(DynamicElasticCanonicalGeometrySettings.disabled());
        initialCoordinates = initializeCoordinates(configuration.nodeCount(),
                request.dimension(), request.targetEdgeLength(), seed);
        List<DynamicElasticNode> nodes = new ArrayList<>();
        for (int id = 0; id < initialCoordinates.size(); id++)
            nodes.add(new DynamicElasticNode(id, initialCoordinates.get(id),
                    new Vector3d(), id < 2));
        elastic = new DynamicElasticSession(graph, settings, nodes);
    }

    private void finishAttempt() {
        if (finalResult != null) return;
        DynamicElasticResult elasticResult = elastic.result();
        if (elasticResult.resultStatus() == DynamicElasticResultStatus.STABLE_CANDIDATE_FOUND) {
            finalResult = finalizeResult(configuration, request, elasticResult);
            scientificState = ScientificState.CONVERGED;
            convergenceIteration = iteration();
            return;
        }
        if (elasticResult.resultStatus() == DynamicElasticResultStatus.NUMERICAL_FAILURE) {
            finalResult = finalizeResult(configuration, request, elasticResult);
            scientificState = ScientificState.FAILED;
            convergenceIteration = iteration();
            return;
        }
        if (++attempt < request.initialRealizations()) {
            initializeAttempt();
            scientificState = ScientificState.RELAXING;
        } else {
            finalResult = finalizeResult(configuration, request, elasticResult);
            scientificState = ScientificState.SEARCH_LIMIT_REACHED;
            convergenceIteration = iteration();
        }
    }

    static TestResult finalizeResult(Configuration configuration,
            TestRequest request, DynamicElasticResult result) {
        TopologyEligibilityReport eligibility = new TopologyEligibilityAnalyzer()
                .analyze(configuration, request.experimentProfile());
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
            return new TestResult(configuration.id(), request.experimentProfile(), eligibility,
                    configuration.graphSymmetry(), realization, null, null, null,
                    AreaResult.unavailable(status), VolumeResult.unavailable(status), null, null,
                    GeometryOptimizationResult.notAttempted(), null, status);
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
                    boundary, request.targetEdgeLength());
            qualified = true;
        }
        GeometryConfigurationStatus status = qualified
                ? GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.info(
                GeometryConfigurationStage.COMPLETE, GeometryConfigurationStatusCode.VALID_CANDIDATE,
                "Valid candidate", "Realization, boundary, non-degeneracy, and profile-specific geometric qualification passed."))
                : boundary.status();
        return new TestResult(configuration.id(), request.experimentProfile(), eligibility,
                configuration.graphSymmetry(), realization, faces, boundary, symmetry, area, volume,
                twoD, threeD, GeometryOptimizationResult.notAttempted(), null, status);
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
                position = new Vector3d(random.nextDouble(-length, length),
                        random.nextDouble(-length, length), random.nextDouble(-length, length));
            }
            result.add(position);
        }
        return result;
    }

    private static int effectiveDimension(List<Vector3d> coordinates) {
        return coordinates.stream().mapToDouble(coordinate -> Math.abs(coordinate.z))
                .max().orElse(0) < 1e-7 ? 2 : 3;
    }
}
