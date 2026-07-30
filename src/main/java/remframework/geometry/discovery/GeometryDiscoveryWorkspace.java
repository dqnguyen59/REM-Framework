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
    private GeometryDiscoverySession geometrySession;
    private TopologySearchSession searchSession;
    private volatile boolean relaxationRequested;
    private volatile boolean searchRequested;
    private volatile boolean stopRequested;
    private volatile Future<?> activeTask;
    private GeometryDiscoveryModel inputModel;
    private volatile String lastWorkerThreadName = "";
    private volatile int relaxationIterationsPerUpdate = 25;
    private final Deque<DiscoveryExperimentEvent> eventLog = new ArrayDeque<>();
    private static final int EVENT_LOG_LIMIT = 200;
    private volatile long lastVisualPublishNanos;
    private volatile long searchVisualPublicationCount;
    private volatile long intentionalPacingWaitCount;

    public GeometryDiscoveryWorkspace(GeometryDiscoveryWorkspaceState state) {
        this.state = state;
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

    private synchronized void loadModel(GeometryDiscoveryModel model,
                                        GeometryDiscoverySettings settings, Path source) {
        stop();
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
            state.setExperimentLifecycle(DiscoveryExperimentLifecycle.PAUSED);
            recordEvent("pause", searchSession == null ? 0 : searchSession.iteration(),
                    "Discovery worker paused.");
            publish("Paused", "");
        });
    }

    public void singleRelaxationStep() {
        worker.submit(() -> {
            lastWorkerThreadName = Thread.currentThread().getName();
            if (geometrySession != null) {
                geometrySession.step();
                publish("Single relaxation step", "");
            }
        });
    }

    public void resetExperiment() {
        stop();
        worker.submit(() -> {
            lastWorkerThreadName = Thread.currentThread().getName();
            geometrySession.reset();
            searchSession = null;
            publish("Experiment reset", "");
        });
    }

    public void updateGeometrySettings(GeometryDiscoverySettings settings) {
        worker.submit(() -> {
            geometrySession.updateSettings(settings);
            publish("Relaxation settings updated", "");
        });
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
        GeometryDiscoveryResult geometry = geometrySession.result();
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

    @Override public void close() {
        stop();
        worker.shutdownNow();
    }
}
