package remframework.geometry.discovery;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
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
        publish("Candidate loaded", "");
    }

    public synchronized void startRelaxation() {
        if (geometrySession == null || relaxationRequested) return;
        stopRequested = false;
        relaxationRequested = true;
        geometrySession.start();
        GeometryDiscoverySession session = geometrySession;
        activeTask = worker.submit(() -> {
            lastWorkerThreadName = Thread.currentThread().getName();
            try {
                while (relaxationRequested && !stopRequested && geometrySession == session
                        && !session.isFinished()) {
                    for (int i = 0; i < relaxationIterationsPerUpdate && relaxationRequested
                            && geometrySession == session && !session.isFinished(); i++) session.step();
                    publish("Relaxing", "");
                    Thread.yield();
                }
                if (geometrySession == session)
                    publish(session.isFinished() ? "Relaxation finished" : "Relaxation paused", "");
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
        activeTask = worker.submit(() -> {
            lastWorkerThreadName = Thread.currentThread().getName();
            try {
                if (searchSession == null)
                    searchSession = new TopologySearchSession(
                            geometrySession.result().model(), settings);
                TopologySearchSession session = searchSession;
                while (searchRequested && !stopRequested
                        && searchSession == session
                        && session.iteration() < settings.maximumSearchIterations()) {
                    session.step();
                    if (session.iteration() % 5 == 0) publish("Searching", "");
                    Thread.yield();
                }
                if (searchSession == session)
                    publish(session.iteration() >= settings.maximumSearchIterations()
                            ? "Search finished" : "Search paused", "");
            } catch (Throwable throwable) {
                publishError("Topology search failed: " + throwable.getMessage());
            } finally {
                searchRequested = false;
            }
        });
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
        return new GeometryDiscoveryExporter().export(candidate.geometryResult(), requested);
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
        return new GeometryDiscoveryExporter().export(snapshot().geometry(), requested);
    }

    public Path exportBest(Path outputDirectory) throws IOException {
        TopologySearchResult result = snapshot().search();
        if (result == null) throw new IllegalStateException("No topology search result is available.");
        return new TopologySearchExporter().export(result, outputDirectory);
    }

    public Path saveMetrics(Path requested) throws IOException {
        return new GeometryDiscoveryMetricsReportWriter().write(snapshot().geometry(), requested);
    }

    public synchronized void stop() {
        stopRequested = true;
        relaxationRequested = false;
        searchRequested = false;
        if (geometrySession != null) geometrySession.pause();
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
    public void setRelaxationIterationsPerUpdate(int value) {
        relaxationIterationsPerUpdate = Math.max(1, Math.min(10_000, value));
    }

    @Override public void close() {
        stop();
        worker.shutdownNow();
    }
}
