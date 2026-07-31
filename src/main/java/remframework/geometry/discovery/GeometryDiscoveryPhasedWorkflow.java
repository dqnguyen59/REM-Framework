package remframework.geometry.discovery;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.joml.Vector3d;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
/** UI-facing model for the two explicit research stages. Selection never starts geometry. */
public final class GeometryDiscoveryPhasedWorkflow {
    public static final String TOPOLOGY_PREVIEW_LABEL="Graph visualization only - not a geometric realization.";
    private final GeometryConfigurationGenerator generator=new GeometryConfigurationGenerator();
    private final GeometryConfigurationRegistry registry=new GeometryConfigurationRegistry();
    private GeometryConfigurationGenerationResult generation;
    private GeometryConfigurationId selected;
    private GeometryTestIncrementalSession incrementalSession;
    private GeometryTestRequest activeRequest;
    private GeometryTestResult activeResult;
    private int visualizationIterationsPerFrame = 25;
    private long renderRevision;
    private Path persistedResultPath;
    private List<Vector3d> reopenedCoordinates = List.of();
    private final ExecutorService incrementalWorker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "geometry-phase2-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean frameAdvanceScheduled = new AtomicBoolean();
    public synchronized GeometryConfigurationGenerationResult generateConfigurations(GeometryConfigurationRequest request)throws IOException{generation=generator.generate(request);registry.registerAll(generation.configurations());if(request.outputDirectory()!=null)new GeometryConfigurationStore(request.outputDirectory()).save(generation);return generation;}
    public synchronized void reopen(Path setDirectory)throws IOException{List<GeometryConfiguration> values=new GeometryConfigurationStore(setDirectory.getParent()).reopen(setDirectory);registry.registerAll(values);selected=null;if(!values.isEmpty()){GeometryConfiguration first=values.getFirst();GeometryConfigurationRequest request=GeometryConfigurationRequest.exhaustive(first.nodeCount(),first.exactDegree(),GeometryEmbeddingDimension.THREE_D);GeometryConfigurationDiagnostic diagnostic=GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.COMPLETE,GeometryConfigurationStatusCode.ENUMERATION_COMPLETE,"Configuration set reopened","Previously generated topology records were loaded without repeating enumeration.");generation=new GeometryConfigurationGenerationResult(setDirectory.getFileName().toString(),request,values,new GeometryConfigurationMetrics(0,values.size(),0,(int)values.stream().filter(v->v.automorphismGroupSize()>1).count(),(int)values.stream().filter(v->v.automorphismGroupSize()==1).count(),0,true),GeometryConfigurationStatus.of(diagnostic));}}
    public synchronized void selectForGeometryTest(GeometryConfigurationId id){if(registry.find(id).isEmpty())throw new IllegalArgumentException("Unknown configuration ID.");if(incrementalSession!=null)incrementalSession.stop();selected=id;incrementalSession=null;activeRequest=null;activeResult=null;reopenedCoordinates=List.of();persistedResultPath=null;renderRevision++;}
    public synchronized Optional<GeometryConfigurationId> selectedConfiguration(){return Optional.ofNullable(selected);}
    public synchronized Optional<GeometryConfiguration> selectedConfigurationRecord(){return selected==null?Optional.empty():registry.find(selected);}
    public synchronized Optional<GeometryConfigurationGenerationResult> generation(){return Optional.ofNullable(generation);}
    public synchronized GeometryTestResult runSelectedGeometryTest(GeometryTestRequest request){if(selected==null||!selected.equals(request.configurationId()))throw new IllegalStateException("The user must explicitly select this configuration before geometry testing.");GeometryTestResult result=new GeometryTestSession(registry.find(selected).orElseThrow(),request).run();registry.attach(result);activeResult=result;activeRequest=request;renderRevision++;return result;}
    public synchronized void startSelectedGeometryTest(GeometryTestRequest request) {
        requireSelected(request);
        if (incrementalSession == null || activeRequest == null
                || !activeRequest.equals(request)) {
            activeRequest = request;
            incrementalSession = new GeometryTestIncrementalSession(
                    registry.find(selected).orElseThrow(), request);
        }
        incrementalSession.resume();
        renderRevision++;
    }
    public synchronized void pauseSelectedGeometryTest() { if (incrementalSession != null) incrementalSession.pause(); }
    public synchronized void resumeSelectedGeometryTest() { if (incrementalSession != null) incrementalSession.resume(); }
    public synchronized void stepSelectedGeometryTest() { if (incrementalSession != null) { GeometryTestSessionState prior = incrementalSession.state(); incrementalSession.step(); if (prior == GeometryTestSessionState.READY || prior == GeometryTestSessionState.PAUSED) incrementalSession.pause(); publishIncrementalResult(); } }
    public synchronized void stopSelectedGeometryTest() { if (incrementalSession != null) incrementalSession.stop(); }
    public synchronized void resetSelectedGeometryTest() { if (selected == null) throw new IllegalStateException("Select one configuration before resetting geometry."); if (incrementalSession != null) incrementalSession.reset(); registry.clearTests(selected); activeResult=null; reopenedCoordinates=List.of(); persistedResultPath=null; renderRevision++; }
    public synchronized void advanceGeometryTestFrame() {
        if (incrementalSession == null || incrementalSession.state() != GeometryTestSessionState.RUNNING
                || !frameAdvanceScheduled.compareAndSet(false, true)) return;
        GeometryTestIncrementalSession session = incrementalSession;
        int iterations = visualizationIterationsPerFrame;
        incrementalWorker.submit(() -> {
            try {
                synchronized (GeometryDiscoveryPhasedWorkflow.this) {
                    if (incrementalSession == session && session.state() == GeometryTestSessionState.RUNNING)
                        session.step(iterations);
                    if (incrementalSession == session) publishIncrementalResult();
                }
            } finally { frameAdvanceScheduled.set(false); }
        });
    }
    public synchronized int visualizationIterationsPerFrame() { return visualizationIterationsPerFrame; }
    public synchronized void setVisualizationIterationsPerFrame(int value) { if (value < 1) throw new IllegalArgumentException("Visualization iterations per frame must be positive."); visualizationIterationsPerFrame = Math.min(value, 10000); }
    public synchronized GeometryTestSessionState geometryTestSessionState() { return incrementalSession == null ? GeometryTestSessionState.READY : incrementalSession.state(); }
    public synchronized GeometryTestIncrementalSession incrementalSession() { return incrementalSession; }
    public synchronized GeometryDiscoveryRenderModel renderModel() {
        if (selected == null) return null;
        GeometryConfiguration configuration = registry.find(selected).orElseThrow();
        if (incrementalSession == null) {
            if (activeResult != null && activeResult.realization() != null)
                return GeometryDiscoveryRenderModel.realization(configuration,
                        activeResult.realization().finalCoordinates(), renderRevision);
            if (!reopenedCoordinates.isEmpty())
                return GeometryDiscoveryRenderModel.realization(configuration,
                        reopenedCoordinates, renderRevision);
            return GeometryDiscoveryRenderModel.topologyPreview(configuration, renderRevision);
        }
        return GeometryDiscoveryRenderModel.realization(configuration, incrementalSession.currentCoordinates(), renderRevision);
    }
    public synchronized List<GeometryConfigurationTableRow> tableRows(){return registry.configurations().stream().map(c->{List<GeometryTestResult> tests=registry.tests(c.id());GeometryTestResult last=tests.isEmpty()?null:tests.get(tests.size()-1);return new GeometryConfigurationTableRow(c.id(),c.nodeCount(),c.exactDegree(),c.edges().size(),c.graphSymmetry(),c.automorphismGroupSize(),c.graphSymmetry()==GraphSymmetryClassification.HIGHLY_SYMMETRIC,c.status().code(),last!=null,last==null?GeometryConfigurationStatusCode.GEOMETRY_TEST_NOT_STARTED:last.status().code(),c.status().summary());}).toList();}
    public synchronized List<GeometryTestResult> geometryTests(GeometryConfigurationId id){return registry.tests(id);}
    public synchronized GeometryTestResult latestGeometryTest(GeometryConfigurationId id){List<GeometryTestResult> values=registry.tests(id);return values.isEmpty()?null:values.get(values.size()-1);}
    public synchronized Path saveLatestGeometryTest(Path configurationSetDirectory, GeometryConfigurationId id) throws IOException {
        GeometryTestResult result = latestGeometryTest(id);
        if (result == null) throw new IllegalStateException("No geometry-test result exists for " + id + ".");
        return new GeometryTestResultStore().save(configurationSetDirectory, result);
    }
    public synchronized GeometryTestResult optimizeSelectedGeometryTest(double targetEdgeLength, int iterations) {
        if (selected == null) throw new IllegalStateException("Select one configuration before optimization.");
        GeometryTestResult current = latestGeometryTest(selected);
        if (current == null) throw new IllegalStateException("Run Geometry Test before optimization.");
        GeometryOptimizationResult optimization = current.boundary() != null && current.boundary().closed()
                ? new GeometryAreaOptimizer().maximize2d(registry.find(selected).orElseThrow(),
                current.realization(), current.boundary(), targetEdgeLength, iterations)
                : GeometryOptimizationResult.notAttempted();
        GeometryTestResult optimized = new GeometryTestResult(current.configurationId(), current.graphSymmetry(),
                current.realization(), current.faceDerivation(), current.boundary(), current.geometricSymmetry(),
                current.area(), current.volume(), optimization, current.status());
        registry.attach(optimized);
        return optimized;
    }
    private void requireSelected(GeometryTestRequest request) { if (selected == null || !selected.equals(request.configurationId())) throw new IllegalStateException("The user must explicitly select this configuration before geometry testing."); }
    private void publishIncrementalResult() { renderRevision++; if (incrementalSession != null && incrementalSession.result() != null && activeResult == null) { activeResult = incrementalSession.result(); registry.attach(activeResult); if (generation != null && generation.request().outputDirectory() != null) { try { persistedResultPath = new GeometryTestResultStore().save(generation.request().outputDirectory(), activeResult); } catch (IOException ignored) { persistedResultPath = null; } } } }
    public synchronized Optional<Path> persistedResultPath() { return Optional.ofNullable(persistedResultPath); }
    public synchronized void reopenStoredResult(GeometryTestStoredResult stored) {
        if (registry.find(stored.configurationId()).isEmpty())
            throw new IllegalArgumentException("Stored result references an unknown configuration.");
        if (incrementalSession != null) incrementalSession.stop();
        incrementalSession = null;
        activeRequest = null;
        activeResult = null;
        selected = stored.configurationId();
        reopenedCoordinates = stored.finalCoordinates();
        renderRevision++;
    }
    public void close() { incrementalWorker.shutdownNow(); }
    public synchronized GeometryConfigurationRegistry registry(){return registry;}
}
