package remframework.geometry.discovery;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns discovery sessions and confines their mutation to one worker thread.
 * Published results are copies suitable for rendering without synchronization.
 */
public final class GeometryDiscoveryWorkspace implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "geometry-discovery-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<GeometryDiscoveryWorkspaceSnapshot> snapshot =
            new AtomicReference<>();
    private final AtomicLong version = new AtomicLong();
    private final GeometryDiscoveryWorkspaceState state;
    private final DynamicElasticResultStore dynamicElasticResultStore;
    private GeometryDiscoverySession geometrySession;
    private DynamicElasticSession dynamicElasticSession;
    private TopologySearchSession searchSession;
    private volatile boolean relaxationRequested;
    private volatile boolean searchRequested;
    private volatile boolean stopRequested;
    private volatile Future<?> activeTask;
    private GeometryDiscoveryModel inputModel;
    private volatile String lastWorkerThreadName = "";
    private volatile int relaxationIterationsPerUpdate = 25;
    private volatile boolean dynamicResultStored;
    private final AtomicReference<DynamicElasticMetrics> publishedDynamicMetrics =
            new AtomicReference<>();
    private final AtomicReference<DynamicElasticSettings> publishedDynamicSettings =
            new AtomicReference<>();
    private volatile DynamicElasticOperation dynamicElasticOperation =
            DynamicElasticOperation.READY;
    private volatile long dynamicElasticPublicationCount;
    private volatile long dynamicElasticIntentionalWaitCount;
    private final Deque<DiscoveryExperimentEvent> eventLog = new ArrayDeque<>();
    private static final int EVENT_LOG_LIMIT = 200;
    private volatile long lastVisualPublishNanos;
    private volatile long searchVisualPublicationCount;
    private volatile long intentionalPacingWaitCount;

    public GeometryDiscoveryWorkspace(GeometryDiscoveryWorkspaceState state) {
        this(state, DynamicElasticResultStore.defaults());
    }

    GeometryDiscoveryWorkspace(GeometryDiscoveryWorkspaceState state,
                               DynamicElasticResultStore dynamicElasticResultStore) {
        this.state = state;
        this.dynamicElasticResultStore = dynamicElasticResultStore;
        loadBuiltIn("tetrahedron");
    }

    public synchronized void loadBuiltIn(String name) {
        loadModel(GeometryDiagnosticMain.referenceOrBuiltIn(name),
                GeometryDiscoverySettings.defaults(1), null);
    }

    public synchronized void loadCandidate(Path path) {
        try {
            LoadedDiscoveryCandidate loaded = new GeometryDiscoveryCandidateLoader().load(path);
            loadModel(loaded.model(), loaded.settings(), path.toAbsolutePath().normalize());
        } catch (Exception exception) {
            publishError("Could not load candidate: " + exception.getMessage());
        }
    }

    public synchronized void loadDynamicElasticResult(Path path) {
        try {
            LoadedDynamicElasticResult loaded = new DynamicElasticResultLoader().load(path);
            loadModel(loaded.model(), GeometryDiscoverySettings
                    .defaults(loaded.settings().targetDistance()), loaded.source());
        } catch (Exception exception) {
            publishError("Could not load dynamic elastic result: " + exception.getMessage());
        }
    }

    private synchronized void loadModel(GeometryDiscoveryModel model,
                                        GeometryDiscoverySettings settings, Path source) {
        stop();
        dynamicElasticSession = null;
        publishedDynamicMetrics.set(null);
        publishedDynamicSettings.set(null);
        dynamicResultStored = false;
        inputModel = model.copy();
        geometrySession = new GeometryDiscoverySession(inputModel, settings);
        searchSession = null;
        state.setCurrentSource(source);
        state.setExperimentLifecycle(DiscoveryExperimentLifecycle.CREATED);
        publish("Candidate loaded", "");
    }

    public synchronized DiscoveryExperimentValidation createExperiment(
            DiscoveryExperimentConfiguration configuration) {
        DiscoveryExperimentValidation validation =
                new DiscoveryExperimentValidator().validate(configuration);
        if (!validation.valid()) return validation;
        try {
            GeometryDiscoveryModel model = new DiscoveryExperimentGenerator()
                    .generate(configuration);
            loadModel(model, configuration.geometrySettings(), null);
            state.setExperimentConfiguration(configuration);
            state.setScoringProfile(configuration.scoringProfile());
            state.setScoreWeights(configuration.scoreWeights());
            state.setWeightsOverridden(configuration.customWeights());
            state.setViewportSelection(DiscoveryViewportSelection.CURRENT_CANDIDATE);
            setRelaxationIterationsPerUpdate(configuration.workerIterationsPerUpdate());
            state.setExperimentLifecycle(DiscoveryExperimentLifecycle.CREATED);
            recordEvent("created", 0, "Experiment '" + configuration.name() + "' created.");
            publish("Experiment created", "");
            return validation;
        } catch (RuntimeException exception) {
            return new DiscoveryExperimentValidation(List.of(exception.getMessage()));
        }
    }

    public DiscoveryExperimentValidation createAndRelax(
            DiscoveryExperimentConfiguration configuration) {
        DiscoveryExperimentValidation validation = createExperiment(configuration);
        if (validation.valid()) startRelaxation();
        return validation;
    }

    public synchronized DiscoveryExperimentValidation startDiscovery(
            DiscoveryExperimentConfiguration configuration) {
        DiscoveryExperimentValidation validation = createExperiment(configuration);
        if (!validation.valid()) return validation;
        stopRequested = false;
        relaxationRequested = true;
        searchRequested = false;
        state.setExperimentLifecycle(DiscoveryExperimentLifecycle.INITIAL_RELAXATION);
        recordEvent("relaxation-started", 0, "Initial relaxation started.");
        GeometryDiscoverySession session = geometrySession;
        activeTask = worker.submit(() -> runDiscoveryPipeline(session, configuration));
        return validation;
    }

    private void runDiscoveryPipeline(GeometryDiscoverySession geometry,
                                      DiscoveryExperimentConfiguration configuration) {
        lastWorkerThreadName = Thread.currentThread().getName();
        try {
            geometry.start();
            while (!stopRequested && geometrySession == geometry && !geometry.isFinished()) {
                for (int i = 0; i < relaxationIterationsPerUpdate
                        && !stopRequested && !geometry.isFinished(); i++) geometry.step();
                publishThrottled("Initial relaxation");
            }
            relaxationRequested = false;
            if (stopRequested) {
                state.setExperimentLifecycle(DiscoveryExperimentLifecycle.STOPPED);
                recordEvent("stopped", 0, "Discovery stopped during initial relaxation.");
                publish("Discovery stopped", "");
                return;
            }
            recordEvent(geometry.result().converged()
                    ? "relaxation-completed" : "relaxation-failed",
                    geometry.result().metrics().iterationCount(),
                    geometry.result().converged() ? "Initial relaxation converged."
                            : "Initial relaxation reached its iteration limit.");
            state.setExperimentLifecycle(DiscoveryExperimentLifecycle.READY);
            searchSession = new TopologySearchSession(geometry.result().model(),
                    configuration.topologySearchSettings());
            searchRequested = true;
            state.setExperimentLifecycle(DiscoveryExperimentLifecycle.SEARCHING);
            recordEvent("search-started", 0, "Topology search started.");
            runSearchLoop(searchSession, configuration.topologySearchSettings());
        } catch (Throwable throwable) {
            state.setExperimentLifecycle(DiscoveryExperimentLifecycle.FAILED);
            recordEvent("error", 0, Objects.requireNonNullElse(
                    throwable.getMessage(), throwable.getClass().getSimpleName()));
            publishError("Discovery failed: " + throwable.getMessage());
        } finally {
            relaxationRequested = false;
            searchRequested = false;
        }
    }

    public synchronized void startRelaxation() {
        if (dynamicElasticSession != null) {
            startDynamicElastic();
            return;
        }
        if (geometrySession == null || relaxationRequested) return;
        stopRequested = false;
        relaxationRequested = true;
        geometrySession.start();
        state.setExperimentLifecycle(DiscoveryExperimentLifecycle.INITIAL_RELAXATION);
        recordEvent("relaxation-started", 0, "Geometry relaxation started.");
        GeometryDiscoverySession session = geometrySession;
        activeTask = worker.submit(() -> {
            lastWorkerThreadName = Thread.currentThread().getName();
            try {
                while (relaxationRequested && !stopRequested && geometrySession == session
                        && !session.isFinished()) {
                    for (int i = 0; i < relaxationIterationsPerUpdate && relaxationRequested
                            && geometrySession == session && !session.isFinished(); i++) session.step();
                    publishThrottled("Relaxing");
                    Thread.yield();
                }
                if (geometrySession == session) {
                    state.setExperimentLifecycle(session.isFinished()
                            ? DiscoveryExperimentLifecycle.READY
                            : DiscoveryExperimentLifecycle.PAUSED);
                    recordEvent(session.isFinished() ? "relaxation-completed" : "pause",
                            session.result().metrics().iterationCount(),
                            session.isFinished() ? "Geometry relaxation completed."
                                    : "Geometry relaxation paused.");
                    publish(session.isFinished() ? "Relaxation finished" : "Relaxation paused", "");
                }
            } catch (Throwable throwable) {
                publishError("Relaxation failed: " + throwable.getMessage());
            } finally {
                relaxationRequested = false;
            }
        });
    }

    public void pause() {
        relaxationRequested = false;
        searchRequested = false;
        worker.submit(() -> {
            lastWorkerThreadName = Thread.currentThread().getName();
            if (geometrySession != null) geometrySession.pause();
            if (dynamicElasticSession != null)
                dynamicElasticOperation = operationForState(
                        dynamicElasticSession.state(), DynamicElasticOperation.PAUSED);
            state.setExperimentLifecycle(DiscoveryExperimentLifecycle.PAUSED);
            recordEvent("pause", searchSession == null ? 0 : searchSession.iteration(),
                    "Discovery worker paused.");
            publish("Paused", "");
        });
    }

    public void singleRelaxationStep() {
        if (relaxationRequested || searchRequested) return;
        worker.submit(() -> {
            lastWorkerThreadName = Thread.currentThread().getName();
            if (dynamicElasticSession != null) {
                applyPersistedDynamicMaximum(dynamicElasticSession);
                dynamicElasticSession.step();
                dynamicElasticOperation = operationForState(dynamicElasticSession.state(),
                        DynamicElasticOperation.PAUSED);
                publish("Single elastic update", "");
                storeDynamicResultIfFinished();
            } else if (geometrySession != null) {
                geometrySession.step();
                publish("Single relaxation step", "");
            }
        });
    }

    public void resetExperiment() {
        stop();
        worker.submit(() -> {
            lastWorkerThreadName = Thread.currentThread().getName();
            if (dynamicElasticSession != null) dynamicElasticSession.reset();
            else geometrySession.reset();
            searchSession = null;
            dynamicResultStored = false;
            dynamicElasticOperation = DynamicElasticOperation.READY;
            publish("Experiment reset", "");
        });
    }

    public void updateGeometrySettings(GeometryDiscoverySettings settings) {
        worker.submit(() -> {
            geometrySession.updateSettings(settings);
            publish("Relaxation settings updated", "");
        });
    }

    public synchronized FixedDegreeValidation createDynamicElastic(
            int nodeCount, int exactNeighbors, DynamicElasticSettings settings) {
        return createDynamicElastic(nodeCount, exactNeighbors, settings,
                DynamicElasticTopologySource.GENERATED_FIXED_DEGREE_TOPOLOGY, null);
    }

    public synchronized FixedDegreeValidation createDynamicElastic(
            int nodeCount, int exactNeighbors, DynamicElasticSettings settings,
            DynamicElasticTopologySource topologySource,
            CanonicalDynamicElasticTopology canonicalTopology) {
        if (topologySource == DynamicElasticTopologySource.CANONICAL_TOPOLOGY) {
            if (canonicalTopology == null)
                return new FixedDegreeValidation(false,
                        List.of("A canonical topology must be selected."), null, null);
            nodeCount = canonicalTopology == CanonicalDynamicElasticTopology.CYCLE
                    ? nodeCount : canonicalTopology.fixedNodeCount();
            exactNeighbors = canonicalTopology.exactDegree();
        }
        FixedDegreeValidation validation =
                new FixedDegreeValidator().validate(nodeCount, exactNeighbors);
        if (!validation.valid()) return validation;
        try {
            stop();
            FixedDegreeGraph graph = topologySource
                    == DynamicElasticTopologySource.CANONICAL_TOPOLOGY
                    ? canonicalTopology.create(nodeCount)
                    : new FixedDegreeGraphGenerator().generate(
                            nodeCount, exactNeighbors, settings.randomSeed(), 0);
            DynamicElasticSettings configured = settings.withMaximumIterations(
                    state.dynamicElasticMaximumIterations());
            dynamicElasticSession = new DynamicElasticSession(graph, configured);
            dynamicResultStored = false;
            dynamicElasticOperation = DynamicElasticOperation.READY;
            dynamicElasticPublicationCount = 0;
            dynamicElasticIntentionalWaitCount = 0;
            geometrySession = renderSession(dynamicElasticSession);
            searchSession = null;
            inputModel = dynamicElasticSession.geometryModel();
            state.setCurrentSource(null);
            state.setExperimentLifecycle(DiscoveryExperimentLifecycle.CREATED);
            recordEvent("created", 0, "Fixed-degree Dynamic Elastic Discovery created: K="
                    + exactNeighbors + ", N=" + nodeCount + ".");
            publish("Dynamic elastic candidate ready", "");
            return validation;
        } catch (RuntimeException exception) {
            return new FixedDegreeValidation(false, List.of(exception.getMessage()), null, null);
        }
    }

    private synchronized void startDynamicElastic() {
        startDynamicElastic(DynamicElasticOperation.RUNNING_CONTINUOUSLY);
    }

    public synchronized void runDynamicElasticUntilEquilibrium() {
        startDynamicElastic(DynamicElasticOperation.RUNNING_UNTIL_EQUILIBRIUM);
    }

    private synchronized void startDynamicElastic(DynamicElasticOperation operation) {
        if (dynamicElasticSession == null || relaxationRequested || searchRequested) return;
        if (dynamicElasticSession.state() == DynamicElasticState.EQUILIBRIUM_REACHED
                || dynamicElasticSession.state() == DynamicElasticState.NUMERICAL_FAILURE)
            return;
        stopRequested = false;
        relaxationRequested = true;
        dynamicElasticOperation = operation;
        state.setExperimentLifecycle(DiscoveryExperimentLifecycle.INITIAL_RELAXATION);
        recordEvent("relaxation-started", dynamicElasticSession.iteration(),
                "Dynamic elastic relaxation started.");
        DynamicElasticSession session = dynamicElasticSession;
        activeTask = worker.submit(() -> {
            lastWorkerThreadName = Thread.currentThread().getName();
            try {
                applyPersistedDynamicMaximum(session);
                if (session.finished()) {
                    dynamicElasticOperation = operationForState(session.state(),
                            DynamicElasticOperation.PAUSED);
                    publish(dynamicElasticOperation.label(), "");
                    return;
                }
                while (relaxationRequested && !stopRequested
                        && dynamicElasticSession == session && !session.finished()) {
                    session.step();
                    boolean mandatory = session.finished();
                    boolean intervalReached = session.iteration()
                            % state.dynamicElasticPublicationInterval() == 0;
                    if (!mandatory && intervalReached) {
                        publish(operation.label(), "");
                        dynamicElasticPublicationCount++;
                        waitForDynamicPlayback();
                    }
                }
                if (dynamicElasticSession == session) {
                    dynamicElasticOperation = operationForState(session.state(),
                            DynamicElasticOperation.PAUSED);
                    state.setExperimentLifecycle(session.finished()
                            ? DiscoveryExperimentLifecycle.READY
                            : DiscoveryExperimentLifecycle.PAUSED);
                    // Mandatory final or cooperative-pause publication, even off interval.
                    publish(dynamicElasticOperation.label(), "");
                    dynamicElasticPublicationCount++;
                    storeDynamicResultIfFinished();
                }
            } catch (Throwable throwable) {
                publishError("Dynamic elastic relaxation failed: " + throwable.getMessage());
            } finally {
                relaxationRequested = false;
            }
        });
    }

    public void updateDynamicElasticSettings(DynamicElasticSettings settings) {
        worker.submit(() -> {
            if (dynamicElasticSession != null) {
                dynamicElasticSession.updateSettings(settings);
                publishedDynamicSettings.set(settings);
                publish("Dynamic elastic settings updated", "");
            }
        });
    }

    public boolean updateDynamicElasticMaximumIterations(int maximumIterations) {
        DynamicElasticMetrics metrics = publishedDynamicMetrics.get();
        int current = metrics == null ? 0 : metrics.iteration();
        if (maximumIterations < 1 || maximumIterations < current) return false;
        state.setDynamicElasticMaximumIterations(maximumIterations);
        worker.submit(() -> {
            if (dynamicElasticSession != null) {
                DynamicElasticSettings changed = dynamicElasticSession.settings()
                        .withMaximumIterations(maximumIterations);
                boolean continuing = dynamicElasticSession.state()
                        == DynamicElasticState.MAX_ITERATIONS_REACHED
                        && maximumIterations > dynamicElasticSession.iteration();
                dynamicElasticSession.updateSettings(changed);
                if (continuing) {
                    dynamicResultStored = false;
                    dynamicElasticOperation = DynamicElasticOperation.PAUSED;
                }
                publish("Maximum iterations updated", "");
            }
        });
        return true;
    }

    private void applyPersistedDynamicMaximum(DynamicElasticSession session) {
        int maximum = state.dynamicElasticMaximumIterations();
        if (maximum >= session.iteration()
                && maximum != session.settings().maximumIterations()) {
            boolean continuing = session.state() == DynamicElasticState.MAX_ITERATIONS_REACHED
                    && maximum > session.iteration();
            session.updateSettings(session.settings().withMaximumIterations(maximum));
            if (continuing) dynamicResultStored = false;
        }
    }

    private void waitForDynamicPlayback() {
        long delay = DynamicElasticPlaybackPacing.delayNanos(
                state.dynamicElasticUpdatesPerSecond(),
                state.dynamicElasticMaximumSpeed());
        if (delay == 0) return;
        long deadline = System.nanoTime() + delay;
        while (relaxationRequested && !stopRequested) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return;
            java.util.concurrent.locks.LockSupport.parkNanos(
                    Math.min(remaining, 10_000_000L));
            dynamicElasticIntentionalWaitCount++;
            if (Thread.interrupted()) return;
        }
    }

    private DynamicElasticOperation operationForState(
            DynamicElasticState dynamicState, DynamicElasticOperation fallback) {
        return switch (dynamicState) {
            case EQUILIBRIUM_REACHED -> DynamicElasticOperation.EQUILIBRIUM_REACHED;
            case MAX_ITERATIONS_REACHED ->
                    DynamicElasticOperation.MAXIMUM_ITERATIONS_REACHED;
            case NUMERICAL_FAILURE -> DynamicElasticOperation.NUMERICAL_FAILURE;
            default -> fallback;
        };
    }

    private void storeDynamicResultIfFinished() {
        if (dynamicResultStored || dynamicElasticSession == null
                || !dynamicElasticSession.finished()) return;
        try {
            DynamicElasticResultRow row = dynamicElasticResultStore
                    .append(dynamicElasticSession.result());
            dynamicResultStored = true;
            recordEvent("export", dynamicElasticSession.iteration(),
                    "Dynamic elastic result stored as " + row.runId() + ".");
        } catch (IOException exception) {
            publishError("Could not store dynamic elastic result: " + exception.getMessage());
        }
    }

    public synchronized void startSearch(TopologySearchSettings settings) {
        if (searchRequested) return;
        stopRequested = false;
        searchRequested = true;
        state.setExperimentLifecycle(DiscoveryExperimentLifecycle.SEARCHING);
        recordEvent(searchSession == null ? "search-started" : "resume",
                searchSession == null ? 0 : searchSession.iteration(),
                searchSession == null ? "Topology search started." : "Topology search resumed.");
        activeTask = worker.submit(() -> {
            lastWorkerThreadName = Thread.currentThread().getName();
            try {
                if (searchSession == null)
                    searchSession = new TopologySearchSession(
                            geometrySession.result().model(), settings);
                runSearchLoop(searchSession, settings);
            } catch (Throwable throwable) {
                publishError("Topology search failed: " + throwable.getMessage());
            } finally {
                searchRequested = false;
            }
        });
    }

    private void runSearchLoop(TopologySearchSession session, TopologySearchSettings settings) {
        double priorBest = session.bestCandidate().score().total();
        int priorAccepted = session.metrics().acceptedMutationCount();
        searchVisualPublicationCount = 0;
        intentionalPacingWaitCount = 0;
        lastVisualPublishNanos = 0;
        while (searchRequested && !stopRequested && searchSession == session
                && session.iteration() < settings.maximumSearchIterations()) {
            double previousScore = session.currentCandidate().score().total();
            session.step();
            TopologySearchMetrics metrics = session.metrics();
            boolean acceptedMutation = metrics.acceptedMutationCount() > priorAccepted;
            if (acceptedMutation) {
                TopologyMutation mutation = session.currentCandidate().mutation();
                String mutationDetails = mutation == null ? "unknown mutation"
                        : mutation.type() + "; removed " + mutation.removedEdges()
                        + "; added " + mutation.addedEdges();
                recordEvent("accepted-mutation", session.iteration(),
                        mutationDetails + "; score " + previousScore + " -> "
                                + session.currentCandidate().score().total());
                priorAccepted = metrics.acceptedMutationCount();
            }
            double best = session.bestCandidate().score().total();
            boolean newBest = best < priorBest;
            if (newBest) {
                TopologyMutation mutation = session.bestCandidate().mutation();
                recordEvent("new-best", session.iteration(),
                        (mutation == null ? "" : mutation.type() + "; removed "
                                + mutation.removedEdges() + "; added "
                                + mutation.addedEdges() + "; ")
                                + "best score " + priorBest + " -> " + best);
                priorBest = best;
                if (state.followBestCandidate())
                    state.setViewportSelection(DiscoveryViewportSelection.BEST_CANDIDATE);
            }
            if (shouldPublishSearch(acceptedMutation, newBest)) {
                publish("Searching", "");
                searchVisualPublicationCount++;
                paceAfterVisualPublication();
            }
            if (new DiscoveryVisualizationPublicationPolicy().shouldPauseOnNewBest(
                    state.pauseOnNewBest(), newBest)) {
                searchRequested = false;
                state.setExperimentLifecycle(DiscoveryExperimentLifecycle.PAUSED);
                recordEvent("pause", session.iteration(), "Paused on a new best candidate.");
                publish("Paused on new best candidate", "");
                break;
            }
            Thread.yield();
        }
        if (searchSession == session) {
            boolean complete = session.iteration() >= settings.maximumSearchIterations();
            searchRequested = false;
            state.setExperimentLifecycle(complete ? DiscoveryExperimentLifecycle.COMPLETED
                    : stopRequested ? DiscoveryExperimentLifecycle.STOPPED
                    : DiscoveryExperimentLifecycle.PAUSED);
            recordEvent(complete ? "completed" : stopRequested ? "stop" : "pause",
                    session.iteration(), complete ? "Topology search completed."
                            : stopRequested ? "Topology search stopped."
                            : "Topology search paused.");
            publish(complete ? "Search finished" : stopRequested
                    ? "Search stopped" : "Search paused", "");
            searchVisualPublicationCount++;
        }
    }

    private boolean shouldPublishSearch(boolean accepted, boolean newBest) {
        long now = System.nanoTime();
        boolean timedDue = state.maximumVisualizationSpeed()
                || now - lastVisualPublishNanos >= visualizationIntervalNanos();
        return new DiscoveryVisualizationPublicationPolicy().shouldPublish(
                state.visualizationUpdateTrigger(), accepted, newBest, timedDue);
    }

    private void paceAfterVisualPublication() {
        lastVisualPublishNanos = System.nanoTime();
        if (state.maximumVisualizationSpeed()
                || state.visualizationUpdateTrigger()
                == DiscoveryVisualizationUpdateTrigger.TIMED_SNAPSHOT) return;
        long deadline = lastVisualPublishNanos + visualizationIntervalNanos();
        while (searchRequested && !stopRequested) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return;
            try {
                Thread.sleep(Math.min(25,
                        Math.max(1, remaining / 1_000_000)));
                intentionalPacingWaitCount++;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private long visualizationIntervalNanos() {
        return 1_000_000_000L / Math.max(1, state.snapshotUpdatesPerSecond());
    }

    public void singleSearchStep(TopologySearchSettings settings) {
        worker.submit(() -> {
            lastWorkerThreadName = Thread.currentThread().getName();
            if (searchSession == null)
                searchSession = new TopologySearchSession(geometrySession.result().model(), settings);
            searchSession.step();
            publish("Single search iteration", "");
        });
    }

    public void resetSearch() {
        stop();
        worker.submit(() -> {
            lastWorkerThreadName = Thread.currentThread().getName();
            searchSession = null;
            publish("Topology search reset", "");
        });
    }

    public void loadBestCandidate() {
        worker.submit(() -> {
            if (searchSession != null) loadSearchCandidate(searchSession.bestCandidate());
        });
    }

    public void loadCurrentCandidate() {
        worker.submit(() -> {
            if (searchSession != null) loadSearchCandidate(searchSession.currentCandidate());
        });
    }

    private void loadSearchCandidate(TopologyCandidate candidate) {
        if (candidate == null) return;
        geometrySession = new GeometryDiscoverySession(candidate.model(),
                candidate.geometryResult().settings());
        publish("Search candidate loaded", "");
    }

    public void loadCandidate(TopologyCandidate candidate) {
        worker.submit(() -> loadSearchCandidate(candidate));
    }

    public Path exportCandidate(TopologyCandidate candidate, Path requested) throws IOException {
        return new GeometryDiscoveryExporter().export(candidate.geometryResult(),
                state.experimentConfiguration(), requested);
    }

    public void preview(TopologyCandidate candidate) {
        GeometryDiscoveryWorkspaceSnapshot old = snapshot.get();
        snapshot.set(new GeometryDiscoveryWorkspaceSnapshot(old.geometry(), old.search(),
                old.archive(), candidate, old.comparison(), "Previewing candidate", old.error(),
                old.relaxationRunning(), old.searchRunning(), version.incrementAndGet()));
    }

    public void compare(TopologyCandidate first, TopologyCandidate second) {
        try {
            GeometryComparisonReport report = new GeometryDiscoveryComparator().compare(
                    first.geometryResult(), second.geometryResult());
            GeometryDiscoveryWorkspaceSnapshot old = snapshot.get();
            snapshot.set(new GeometryDiscoveryWorkspaceSnapshot(old.geometry(), old.search(),
                    old.archive(), old.preview(), report,
                    "Comparison ready; score difference "
                            + (first.score().total() - second.score().total()), old.error(),
                    old.relaxationRunning(), old.searchRunning(), version.incrementAndGet()));
        } catch (Exception exception) {
            publishError("Comparison failed: " + exception.getMessage());
        }
    }

    public void compareDynamicResult(Path path) {
        try {
            LoadedDynamicElasticResult loaded = new DynamicElasticResultLoader().load(path);
            GeometryDiscoveryResult stored = new GeometryDiscoverySession(loaded.model(),
                    GeometryDiscoverySettings.defaults(
                            loaded.settings().targetDistance())).result();
            GeometryComparisonReport report = new GeometryDiscoveryComparator().compare(
                    snapshot().geometry(), stored);
            GeometryDiscoveryWorkspaceSnapshot old = snapshot.get();
            snapshot.set(new GeometryDiscoveryWorkspaceSnapshot(old.geometry(), old.search(),
                    old.archive(), old.preview(), report,
                    "Dynamic elastic comparison ready", old.error(),
                    old.relaxationRunning(), old.searchRunning(), version.incrementAndGet()));
        } catch (Exception exception) {
            publishError("Dynamic elastic comparison failed: " + exception.getMessage());
        }
    }

    public Path exportCurrent(Path requested) throws IOException {
        return new GeometryDiscoveryExporter().export(snapshot().geometry(),
                state.experimentConfiguration(), requested);
    }

    public Path exportBest(Path outputDirectory) throws IOException {
        TopologySearchResult result = snapshot().search();
        if (result == null) throw new IllegalStateException("No topology search result is available.");
        return new TopologySearchExporter().export(result, outputDirectory);
    }

    public Path saveMetrics(Path requested) throws IOException {
        return new GeometryDiscoveryMetricsReportWriter().write(snapshot().geometry(), requested);
    }

    public Path saveExperimentConfiguration(Path requested) throws IOException {
        if (state.experimentConfiguration() == null)
            throw new IllegalStateException("No New Discovery configuration is active.");
        Path result = new DiscoveryExperimentConfigurationStore().save(
                state.experimentConfiguration(), requested);
        recordEvent("export", 0, "Experiment configuration saved to " + result);
        return result;
    }

    public DiscoveryExperimentValidation loadExperimentConfiguration(Path path)
            throws IOException {
        DiscoveryExperimentConfiguration configuration =
                new DiscoveryExperimentConfigurationStore().load(path);
        // Creation is intentional, but no relaxation/search worker is resumed.
        return createExperiment(configuration);
    }

    public synchronized void stop() {
        stopRequested = true;
        relaxationRequested = false;
        searchRequested = false;
        if (geometrySession != null) geometrySession.pause();
        if (state.experimentLifecycle() == DiscoveryExperimentLifecycle.SEARCHING
                || state.experimentLifecycle() == DiscoveryExperimentLifecycle.INITIAL_RELAXATION)
            state.setExperimentLifecycle(DiscoveryExperimentLifecycle.STOPPED);
    }

    private void publishThrottled(String status) {
        long now = System.nanoTime();
        long interval = 1_000_000_000L / state.snapshotUpdatesPerSecond();
        if (now - lastVisualPublishNanos >= interval) {
            lastVisualPublishNanos = now;
            publish(status, "");
        }
    }

    private synchronized void recordEvent(String type, int iteration, String details) {
        eventLog.addLast(new DiscoveryExperimentEvent(
                java.time.Instant.now(), type, iteration, details));
        while (eventLog.size() > EVENT_LOG_LIMIT) eventLog.removeFirst();
    }

    public synchronized List<DiscoveryExperimentEvent> eventLog() {
        return List.copyOf(eventLog);
    }

    private void publish(String status, String error) {
        if (dynamicElasticSession != null) {
            publishedDynamicMetrics.set(dynamicElasticSession.metrics());
            publishedDynamicSettings.set(dynamicElasticSession.settings());
        }
        GeometryDiscoveryResult geometry = dynamicElasticSession == null
                ? geometrySession.result() : renderSession(dynamicElasticSession).result();
        TopologySearchResult search = searchSession == null ? null : searchSession.result();
        List<TopologyCandidate> archive = search == null ? List.of() : search.topCandidates();
        GeometryDiscoveryWorkspaceSnapshot previous = snapshot.get();
        snapshot.set(new GeometryDiscoveryWorkspaceSnapshot(geometry, search, archive,
                previous == null ? null : previous.preview(),
                previous == null ? null : previous.comparison(), status, error,
                relaxationRequested, searchRequested, version.incrementAndGet()));
    }

    private void publishError(String message) {
        System.err.println("Geometry Discovery workspace: " + message);
        if (geometrySession == null) return;
        publish("Error", Objects.requireNonNullElse(message, "Unknown error"));
    }

    public GeometryDiscoveryWorkspaceSnapshot snapshot() { return snapshot.get(); }
    public boolean dynamicElasticActive() { return dynamicElasticSession != null; }
    public DynamicElasticMetrics dynamicElasticMetrics() {
        return publishedDynamicMetrics.get();
    }
    public DynamicElasticSettings dynamicElasticSettings() {
        return publishedDynamicSettings.get();
    }
    public DynamicElasticResult dynamicElasticResult() {
        return dynamicElasticSession == null ? null : dynamicElasticSession.result();
    }
    public DynamicElasticOperation dynamicElasticOperation() {
        return dynamicElasticOperation;
    }
    public long dynamicElasticPublicationCount() {
        return dynamicElasticPublicationCount;
    }
    public long dynamicElasticIntentionalWaitCount() {
        return dynamicElasticIntentionalWaitCount;
    }
    public GeometryDiscoveryWorkspaceState state() { return state; }
    public boolean workerStopped() {
        return worker.isShutdown() || activeTask == null || activeTask.isDone();
    }
    public String lastWorkerThreadName() { return lastWorkerThreadName; }
    public int relaxationIterationsPerUpdate() { return relaxationIterationsPerUpdate; }
    public long searchVisualPublicationCount() { return searchVisualPublicationCount; }
    public long intentionalPacingWaitCount() { return intentionalPacingWaitCount; }
    public void setRelaxationIterationsPerUpdate(int value) {
        relaxationIterationsPerUpdate = Math.max(1, Math.min(10_000, value));
    }

    private GeometryDiscoverySession renderSession(DynamicElasticSession session) {
        DynamicElasticSettings dynamic = session.settings();
        GeometryDiscoverySettings settings = GeometryDiscoverySettings
                .defaults(dynamic.targetDistance())
                .withMaximumIterations(dynamic.maximumIterations())
                .withRandomSeed(dynamic.randomSeed())
                .withConvergenceTolerance(1 - dynamic.requiredAccuracy());
        return new GeometryDiscoverySession(session.geometryModel(), settings);
    }

    @Override public void close() {
        stop();
        worker.shutdownNow();
    }
}
