package remframework.geometry.discovery;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Bounded, render-friendly facade over the existing DynamicElasticSession.
 * <br><br>
 * This class does not calculate forces itself; every update delegates to the
 * existing Dynamic Elastic step implementation.
 */
public final class GeometryTestIncrementalSession {
    private final GeometryConfiguration configuration;
    private final GeometryTestRequest request;
    private final FixedDegreeGraph graph;
    private DynamicElasticSession elastic;
    private List<Vector3d> initialCoordinates;
    private int attempt;
    private GeometryTestSessionState state = GeometryTestSessionState.READY;
    private GeometryTestResult finalResult;
    private String stopReason = "";

    public GeometryTestIncrementalSession(GeometryConfiguration configuration,
            GeometryTestRequest request) {
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
        if (state == GeometryTestSessionState.CONVERGED
                || state == GeometryTestSessionState.EXHAUSTED
                || state == GeometryTestSessionState.FAILED
                || state == GeometryTestSessionState.STOPPED) return false;
        state = GeometryTestSessionState.RUNNING;
        elastic.step();
        if (elastic.finished()) finishAttempt();
        return true;
    }

    public synchronized int step(int iterations) {
        int performed = 0;
        for (int i = 0; i < Math.max(0, iterations); i++) {
            if (!step()) break;
            performed++;
            if (state == GeometryTestSessionState.CONVERGED
                    || state == GeometryTestSessionState.EXHAUSTED
                    || state == GeometryTestSessionState.FAILED) break;
        }
        return performed;
    }

    public synchronized void run() {
        resume();
        while (state == GeometryTestSessionState.RUNNING
                || state == GeometryTestSessionState.READY) step();
    }

    public synchronized void resume() {
        if (state == GeometryTestSessionState.STOPPED) return;
        state = GeometryTestSessionState.RUNNING;
    }

    public synchronized void pause() {
        if (state == GeometryTestSessionState.RUNNING) state = GeometryTestSessionState.PAUSED;
    }

    public synchronized void stop() {
        if (state == GeometryTestSessionState.CONVERGED
                || state == GeometryTestSessionState.EXHAUSTED
                || state == GeometryTestSessionState.FAILED) return;
        state = GeometryTestSessionState.STOPPED;
        stopReason = "Geometry realization stopped by the user.";
    }

    public synchronized void reset() {
        attempt = 0;
        finalResult = null;
        stopReason = "";
        initializeAttempt();
        state = GeometryTestSessionState.READY;
    }

    public synchronized List<Vector3d> currentCoordinates() {
        return elastic.nodes().stream().map(node -> new Vector3d(node.position())).toList();
    }

    public synchronized List<Vector3d> initialCoordinates() {
        return initialCoordinates.stream().map(Vector3d::new).toList();
    }

    public synchronized int iteration() { return elastic.iteration(); }
    public synchronized DynamicElasticMetrics metrics() { return elastic.metrics(); }
    public synchronized GeometryTestSessionState state() { return state; }
    public synchronized GeometryTestResult result() { return finalResult; }
    public synchronized String stopReason() { return stopReason; }
    public GeometryConfiguration configuration() { return configuration; }

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
        DynamicElasticResult elasticResult = elastic.result();
        if (elasticResult.resultStatus() == DynamicElasticResultStatus.STABLE_CANDIDATE_FOUND) {
            finalResult = finalizeResult(configuration, request, elasticResult);
            state = GeometryTestSessionState.CONVERGED;
            return;
        }
        if (elasticResult.resultStatus() == DynamicElasticResultStatus.NUMERICAL_FAILURE) {
            finalResult = finalizeResult(configuration, request, elasticResult);
            state = GeometryTestSessionState.FAILED;
            return;
        }
        if (++attempt < request.initialRealizations()) {
            initializeAttempt();
            state = GeometryTestSessionState.RUNNING;
        } else {
            finalResult = finalizeResult(configuration, request, elasticResult);
            state = GeometryTestSessionState.EXHAUSTED;
        }
    }

    static GeometryTestResult finalizeResult(GeometryConfiguration configuration,
            GeometryTestRequest request, DynamicElasticResult result) {
        List<Vector3d> initial = result.initialNodes().stream()
                .map(node -> new Vector3d(node.position())).toList();
        List<Vector3d> coordinates = result.finalNodes().stream()
                .map(node -> new Vector3d(node.position())).toList();
        GeometryRealizationStatus realizationStatus = switch (result.resultStatus()) {
            case STABLE_CANDIDATE_FOUND -> GeometryRealizationStatus.REALIZED;
            case NUMERICAL_FAILURE -> GeometryRealizationStatus.NUMERICAL_FAILURE;
            default -> GeometryRealizationStatus.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS;
        };
        GeometryConfigurationStatusCode code = switch (realizationStatus) {
            case REALIZED -> GeometryConfigurationStatusCode.GEOMETRICALLY_REALIZED;
            case NUMERICAL_FAILURE -> GeometryConfigurationStatusCode.NUMERICAL_FAILURE;
            default -> GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS;
        };
        GeometryConfigurationDiagnostic diagnostic = realizationStatus
                == GeometryRealizationStatus.REALIZED
                ? GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.GEOMETRY_REALIZATION,
                code, "Geometrically realized",
                "Equal connected-edge lengths reached numerical equilibrium; topology remained fixed.")
                : GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GEOMETRY_REALIZATION,
                code, "No realization found within search limits",
                "The numerical search did not find a realization; this is not proof of non-embeddability.");
        GeometryRealization realization = new GeometryRealization(result.settings().randomSeed(),
                request.dimension(), initial, coordinates, result.metrics().meanDistanceAccuracy(),
                result.metrics().maximumNodeSpeed(), realizationStatus == GeometryRealizationStatus.REALIZED,
                result.metrics().minimumObservedNonNeighborDistance() > 0,
                result.metrics().edgeClearanceViolations() == 0, effectiveDimension(coordinates),
                realizationStatus, List.of(diagnostic));
        if (!realization.converged()) {
            GeometryConfigurationStatus status = GeometryConfigurationStatus.of(diagnostic);
            return new GeometryTestResult(configuration.id(), configuration.graphSymmetry(), realization,
                    null, null, null, AreaResult.unavailable(status), VolumeResult.unavailable(status),
                    GeometryOptimizationResult.notAttempted(), status);
        }
        FaceDerivationResult faces = new FaceDerivationPipeline().derive(configuration, realization);
        ClosedBoundaryResult boundary = new ClosedBoundaryValidator().validate(configuration,
                realization, faces);
        GeometricSymmetryResult symmetry = new GeometricSymmetryAnalyzer().analyze(configuration, realization);
        GeometryMeasureCalculator measures = new GeometryMeasureCalculator();
        AreaResult area = measures.area(realization, boundary);
        VolumeResult volume = measures.volume(realization, boundary);
        GeometryConfigurationStatus status = boundary.closed()
                ? GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.info(
                GeometryConfigurationStage.COMPLETE, GeometryConfigurationStatusCode.VALID_CANDIDATE,
                "Valid candidate", "A realization and unique closed boundary were established; eligible measures were calculated."))
                : boundary.status();
        return new GeometryTestResult(configuration.id(), configuration.graphSymmetry(), realization,
                faces, boundary, symmetry, area, volume, GeometryOptimizationResult.notAttempted(), status);
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
