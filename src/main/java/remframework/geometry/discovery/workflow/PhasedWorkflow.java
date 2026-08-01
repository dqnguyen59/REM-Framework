package remframework.geometry.discovery.workflow;
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
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.joml.Vector3d;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import remframework.geometry.discovery.eligibility.*;
import remframework.geometry.discovery.experiment.ExperimentProfile;
import remframework.geometry.discovery.measurement.regularity.*;
import remframework.geometry.discovery.reference.ReferenceTopologyRecognizer;
/** UI-facing model for the two explicit research stages. Selection never starts geometry. */
public final class PhasedWorkflow {
    public static final String TOPOLOGY_PREVIEW_LABEL="Graph visualization only - not a geometric realization.";
    private final ConfigurationGenerator generator=new ConfigurationGenerator();
    private final Registry registry=new Registry();
    private GenerationResult generation;
    private ConfigurationId selected;
    private IncrementalSession incrementalSession;
    private TestRequest activeRequest;
    private TestResult activeResult;
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
    public synchronized GenerationResult generateConfigurations(ConfigurationRequest request)throws IOException{
        beginGenerationTransaction();
        GenerationResult generated=generator.generate(request);
        generation=generated;
        registry.registerAll(generated.configurations());
        if(request.outputDirectory()!=null)new ConfigurationStore(request.outputDirectory()).save(generated);
        return generated;
    }
    public synchronized void beginGenerationTransaction(){
        if(incrementalSession!=null)incrementalSession.stop();
        generation=null;selected=null;incrementalSession=null;activeRequest=null;activeResult=null;
        persistedResultPath=null;reopenedCoordinates=List.of();registry.clear();renderRevision++;
    }
    public synchronized void reopen(Path setDirectory)throws IOException{List<Configuration> values=new ConfigurationStore(setDirectory.getParent()).reopen(setDirectory);registry.registerAll(values);selected=null;if(!values.isEmpty()){Configuration first=values.getFirst();ConfigurationRequest request=ConfigurationRequest.exhaustive(first.nodeCount(),first.exactDegree(),GeometryEmbeddingDimension.THREE_D);GeometryConfigurationDiagnostic diagnostic=GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.COMPLETE,GeometryConfigurationStatusCode.ENUMERATION_COMPLETE,"Configuration set reopened","Previously generated topology records were loaded without repeating enumeration.");generation=new GenerationResult(setDirectory.getFileName().toString(),request,values,new Metrics(0,values.size(),0,(int)values.stream().filter(v->v.automorphismGroupSize()>1).count(),(int)values.stream().filter(v->v.automorphismGroupSize()==1).count(),0,true),GeometryConfigurationStatus.of(diagnostic));}}
    public synchronized void selectForGeometryTest(ConfigurationId id){if(registry.find(id).isEmpty())throw new IllegalArgumentException("Unknown configuration ID.");if(incrementalSession!=null)incrementalSession.stop();selected=id;incrementalSession=null;activeRequest=null;activeResult=null;reopenedCoordinates=List.of();persistedResultPath=null;renderRevision++;}
    public synchronized Optional<ConfigurationId> selectedConfiguration(){return Optional.ofNullable(selected);}
    public synchronized Optional<Configuration> selectedConfigurationRecord(){return selected==null?Optional.empty():registry.find(selected);}
    public synchronized Optional<GenerationResult> generation(){return Optional.ofNullable(generation);}
    public synchronized TestResult runSelectedGeometryTest(TestRequest request){if(selected==null||!selected.equals(request.configurationId()))throw new IllegalStateException("The user must explicitly select this configuration before geometry testing.");TestResult result=new TestSession(registry.find(selected).orElseThrow(),request).run();registry.attach(result);activeResult=result;activeRequest=request;renderRevision++;return result;}
    public synchronized void startSelectedGeometryTest(TestRequest request) {
        requireSelected(request);
        if (incrementalSession == null || activeRequest == null
                || !activeRequest.equals(request)) {
            activeRequest = request;
            incrementalSession = new IncrementalSession(
                    registry.find(selected).orElseThrow(), request);
        }
        incrementalSession.resume();
        renderRevision++;
    }
    public synchronized void pauseSelectedGeometryTest() { if (incrementalSession != null) incrementalSession.pause(); }
    public synchronized void resumeSelectedGeometryTest() { if (incrementalSession != null) incrementalSession.resume(); }
    public synchronized void stepSelectedGeometryTest() { if (incrementalSession != null) { SessionState prior = incrementalSession.state(); incrementalSession.step(); if (prior == SessionState.READY || prior == SessionState.PAUSED) incrementalSession.pause(); publishIncrementalResult(); } }
    public synchronized void stopSelectedGeometryTest() { if (incrementalSession != null) incrementalSession.stop(); }
    public synchronized void resetSelectedGeometryTest() { if (selected == null) throw new IllegalStateException("Select one configuration before resetting geometry."); if (incrementalSession != null) incrementalSession.reset(); registry.clearTests(selected); activeResult=null; reopenedCoordinates=List.of(); persistedResultPath=null; renderRevision++; }
    public synchronized void advanceGeometryTestFrame() {
        if (incrementalSession == null || incrementalSession.executionState() != ExecutionState.RUNNING
                || !frameAdvanceScheduled.compareAndSet(false, true)) return;
        IncrementalSession session = incrementalSession;
        int iterations = visualizationIterationsPerFrame;
        incrementalWorker.submit(() -> {
            try {
                synchronized (PhasedWorkflow.this) {
                    if (incrementalSession == session && session.executionState() == ExecutionState.RUNNING)
                        session.step(iterations);
                    if (incrementalSession == session) publishIncrementalResult();
                }
            } finally { frameAdvanceScheduled.set(false); }
        });
    }
    public synchronized int visualizationIterationsPerFrame() { return visualizationIterationsPerFrame; }
    public synchronized void setVisualizationIterationsPerFrame(int value) { if (value < 1) throw new IllegalArgumentException("Visualization iterations per frame must be positive."); visualizationIterationsPerFrame = Math.min(value, 10000); }
    public synchronized SessionState geometryTestSessionState() { return incrementalSession == null ? SessionState.READY : incrementalSession.state(); }
    public synchronized ExecutionState geometryExecutionState() { return incrementalSession == null ? ExecutionState.IDLE : incrementalSession.executionState(); }
    public synchronized ScientificState geometryScientificState() { return incrementalSession == null ? ScientificState.INITIALIZED : incrementalSession.scientificState(); }
    public synchronized ExperimentConvergenceDiagnostics convergenceDiagnostics(){return incrementalSession==null?null:incrementalSession.convergenceDiagnostics();}
    public synchronized IncrementalSession incrementalSession() { return incrementalSession; }
    public synchronized RenderModel renderModel() {
        if (selected == null) return null;
        Configuration configuration = registry.find(selected).orElseThrow();
        if (incrementalSession == null) {
            if (activeResult != null && activeResult.realization() != null)
                return RenderModel.realization(configuration,
                        activeResult.realization().finalCoordinates(), renderRevision);
            if (!reopenedCoordinates.isEmpty())
                return RenderModel.realization(configuration,
                        reopenedCoordinates, renderRevision);
            return RenderModel.topologyPreview(configuration, renderRevision);
        }
        return RenderModel.realization(configuration, incrementalSession.currentCoordinates(), renderRevision);
    }
    public synchronized List<ConfigurationTableRow> tableRows(){ExperimentProfile profile=generation==null?ExperimentProfile.CLOSED_3D_VOLUME:generation.request().experimentProfile();ReferenceTopologyRecognizer recognizer=new ReferenceTopologyRecognizer();return registry.configurations().stream().map(c->{List<TestResult> tests=registry.tests(c.id());TestResult last=tests.isEmpty()?null:tests.get(tests.size()-1);TopologyEligibilityReport eligibility=new TopologyEligibilityAnalyzer().analyze(c,profile);return new ConfigurationTableRow(c.id(),c.nodeCount(),c.exactDegree(),c.edges().size(),c.graphSymmetry(),c.automorphismGroupSize(),eligibility.vertexTransitive(),profile,c.status().code(),eligibility.status(),c.status().code(),last!=null,last==null?GeometryConfigurationStatusCode.GEOMETRY_TEST_NOT_STARTED:last.status().code(),eligibility.conclusion(),recognizer.recognize(c).topology());}).toList();}
    public synchronized List<TestResult> geometryTests(ConfigurationId id){return registry.tests(id);}
    public synchronized TestResult latestGeometryTest(ConfigurationId id){List<TestResult> values=registry.tests(id);return values.isEmpty()?null:values.get(values.size()-1);}
    public synchronized Path saveLatestGeometryTest(Path configurationSetDirectory, ConfigurationId id) throws IOException {
        TestResult result = latestGeometryTest(id);
        if (result == null) throw new IllegalStateException("No geometry-test result exists for " + id + ".");
        return new TestResultStore().save(configurationSetDirectory, result);
    }
    public synchronized TestResult optimizeSelectedGeometryTest(double targetEdgeLength, int iterations) {
        if (selected == null) throw new IllegalStateException("Select one configuration before optimization.");
        TestResult current = latestGeometryTest(selected);
        if (current == null) throw new IllegalStateException("Run Geometry Test before optimization.");
        GeometryOptimizationResult optimization = current.boundary() == null || !current.boundary().closed()
                ? GeometryOptimizationResult.notAttempted()
                : current.experimentProfile() == ExperimentProfile.CLOSED_2D_AREA
                    ? new AreaOptimizer().maximize2d(registry.find(selected).orElseThrow(),current.realization(),current.boundary(),targetEdgeLength,iterations)
                    : new VolumeOptimizer().maximize3d(registry.find(selected).orElseThrow(),current.realization(),current.boundary(),targetEdgeLength,iterations);
        OptimizationComparison comparison=current.optimizationComparison();
        if(current.experimentProfile()!=ExperimentProfile.CLOSED_2D_AREA&&optimization.attempted()&&optimization.optimized()!=null&&current.threeDRegularity()!=null){
            Configuration graph=registry.find(selected).orElseThrow();FaceDerivationResult faces=new FaceDerivationPipeline().derive(graph,optimization.optimized());ClosedBoundaryResult boundary=new ClosedBoundaryValidator().validate(graph,optimization.optimized(),faces);
            ThreeDRegularityMetrics optimizedMetrics=new ThreeDRegularityCalculator().calculate(graph,optimization.optimized().finalCoordinates(),boundary,targetEdgeLength,current.threeDRegularity().centerDefinition(),current.threeDConditionReport()==null?1e-7:current.threeDConditionReport().conditions().facePlanarityTolerance());
            if(optimizedMetrics.applicable()){Map<String,MetricChange> changes=new LinkedHashMap<>();changes.put("volume",MetricChange.of(current.volume().absoluteVolume(),optimization.optimizedMeasure()));changes.put("facePlaneRadiusVariance",MetricChange.of(current.threeDRegularity().facePlaneRadii().variance(),optimizedMetrics.facePlaneRadii().variance()));changes.put("normalCenterAlignmentVariance",MetricChange.of(current.threeDRegularity().normalCenterAlignments().variance(),optimizedMetrics.normalCenterAlignments().variance()));changes.put("vertexRadiusVariance",MetricChange.of(current.threeDRegularity().vertexRadii().variance(),optimizedMetrics.vertexRadii().variance()));changes.put("faceCentroidRadiusVariance",MetricChange.of(current.threeDRegularity().faceCenterRadii().variance(),optimizedMetrics.faceCenterRadii().variance()));changes.put("edgeMidpointRadiusVariance",MetricChange.of(current.threeDRegularity().edgeMidpointRadii().variance(),optimizedMetrics.edgeMidpointRadii().variance()));changes.put("dihedralAngleVariance",MetricChange.of(current.threeDRegularity().interiorDihedralAngles().variance(),optimizedMetrics.interiorDihedralAngles().variance()));current.threeDRegularity().faceAreaByEdgeCount().forEach((edgeCount,statistics)->{Statistics after=optimizedMetrics.faceAreaByEdgeCount().get(edgeCount);if(after!=null)changes.put("faceAreaVariance.edgeCount."+edgeCount,MetricChange.of(statistics.variance(),after.variance()));});comparison=new OptimizationComparison(changes,"Measured comparison only; no regularity condition was added to the volume objective.");}
        }
        TestResult optimized = new TestResult(current.configurationId(),current.experimentProfile(),current.planarityMode(),current.angleConditionMode(),current.eligibility(),current.graphSymmetry(),
                current.realization(),current.faceDerivation(),current.boundary(),current.geometricSymmetry(),current.area(),current.volume(),
                current.twoDRegularity(),current.threeDRegularity(),current.threeDConditionReport(),optimization,comparison,current.status(),
                current.benchmarkPreset(),current.referenceTopologyMatch());
        registry.attach(optimized);
        return optimized;
    }
    private void requireSelected(TestRequest request) { if (selected == null || !selected.equals(request.configurationId())) throw new IllegalStateException("The user must explicitly select this configuration before geometry testing."); }
    private void publishIncrementalResult() { renderRevision++; if (incrementalSession != null && incrementalSession.result() != null && activeResult == null) { activeResult = incrementalSession.result(); registry.attach(activeResult); if (generation != null && generation.request().outputDirectory() != null) { try { persistedResultPath = new TestResultStore().save(generation.request().outputDirectory(), activeResult); } catch (IOException ignored) { persistedResultPath = null; } } } }
    public synchronized Optional<Path> persistedResultPath() { return Optional.ofNullable(persistedResultPath); }
    public synchronized void reopenStoredResult(StoredResult stored) {
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
    public synchronized Registry registry(){return registry;}
}
