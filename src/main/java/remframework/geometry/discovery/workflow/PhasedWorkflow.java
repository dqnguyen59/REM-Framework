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
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
    private int scientificUpdatesPerSecond=10;
    private long nextScientificUpdateNanos;
    private long renderRevision;
    private Path persistedResultPath;
    private List<Vector3d> reopenedCoordinates = List.of();
    private final ExecutorService incrementalWorker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "geometry-phase2-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean frameAdvanceScheduled = new AtomicBoolean();
    private final ExecutorService generationCoordinator = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "geometry-stage1-coordinator");thread.setDaemon(true);return thread;
    });
    private final AtomicLong operationToken = new AtomicLong();
    private final AtomicReference<GenerationProgress> generationProgress = new AtomicReference<>();
    private volatile Future<?> generationTask;
    private volatile boolean generationRunning;
    private volatile String asynchronousFailure = "";
    private volatile Stage2InitializationState stage2InitializationState=Stage2InitializationState.NOT_STARTED;
    private volatile long stage2InitializationMillis;
    private volatile RenderModel publishedRenderModel;
    private volatile List<ConfigurationTableRow> cachedTableRows;
    private volatile String stage1ThreadName="",stage2ThreadName="";
    public synchronized GenerationResult generateConfigurations(ConfigurationRequest request)throws IOException{
        beginGenerationTransaction();
        GenerationResult generated=generator.generate(request);
        generation=generated;
        registry.registerAll(generated.configurations());
        cachedTableRows=buildTableRows();
        if(request.outputDirectory()!=null)new ConfigurationStore(request.outputDirectory()).save(generated);
        return generated;
    }
    public void generateConfigurationsAsync(ConfigurationRequest request,GenerationWorkerCount workerCount){
        beginGenerationTransaction();long token=operationToken.incrementAndGet();generationRunning=true;asynchronousFailure="";
        generationTask=generationCoordinator.submit(()->{stage1ThreadName=Thread.currentThread().getName();try{
            GenerationExecution execution=new GenerationExecution(workerCount,()->token!=operationToken.get()||Thread.currentThread().isInterrupted(),progress->{if(token==operationToken.get())generationProgress.set(progress);});
            GenerationResult generated=generator.generate(request,execution);if(token!=operationToken.get())return;
            if(request.outputDirectory()!=null)new ConfigurationStore(request.outputDirectory()).save(generated);
            long publicationStarted=System.nanoTime();synchronized(this){if(token!=operationToken.get())return;generation=generated;registry.registerAll(generated.configurations());cachedTableRows=buildTableRows();renderRevision++;}DevelopmentTimingDiagnostics.record("stage1.completed-result-publication",System.nanoTime()-publicationStarted);
        }catch(Exception failure){if(token==operationToken.get())asynchronousFailure=failure.getMessage();}finally{if(token==operationToken.get()){generationRunning=false;generationTask=null;}}});
    }
    public void cancelGeneration(){operationToken.incrementAndGet();Future<?> task=generationTask;generationTask=null;if(task!=null)task.cancel(true);generationRunning=false;GenerationProgress old=generationProgress.get();if(old!=null)generationProgress.set(new GenerationProgress(old.strategy(),old.workerCount(),0,old.elapsed(),old.statesExamined(),old.statesPerSecond(),old.statesPruned(),0,old.completedCandidates(),old.duplicateCandidates(),old.retainedConfigurations(),old.pruningCounts(),old.limits(),false,true));}
    public boolean generationRunning(){return generationRunning;}
    public boolean generationTaskRetired(){return generationTask==null;}
    public GenerationProgress generationProgress(){return generationProgress.get();}
    public String asynchronousFailure(){return asynchronousFailure;}
    public synchronized void beginGenerationTransaction(){
        cancelGeneration();generationProgress.set(null);
        if(incrementalSession!=null)incrementalSession.stop();
        generation=null;selected=null;incrementalSession=null;activeRequest=null;activeResult=null;publishedRenderModel=null;cachedTableRows=null;
        persistedResultPath=null;reopenedCoordinates=List.of();registry.clear();renderRevision++;
    }
    public synchronized void reopen(Path setDirectory)throws IOException{ConfigurationStore store=new ConfigurationStore(setDirectory.getParent());List<Configuration> values=store.reopen(setDirectory);registry.registerAll(values);selected=null;if(!values.isEmpty()){ConfigurationRequest request=store.reopenRequest(setDirectory);GeometryConfigurationDiagnostic diagnostic=GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.COMPLETE,GeometryConfigurationStatusCode.ENUMERATION_COMPLETE,"Configuration set reopened","Previously generated topology records and their recomputed parameter derivation were loaded without repeating enumeration.");generation=new GenerationResult(setDirectory.getFileName().toString(),request,values,new Metrics(0,values.size(),0,(int)values.stream().filter(v->v.automorphismGroupSize()>1).count(),(int)values.stream().filter(v->v.automorphismGroupSize()==1).count(),0,true),GeometryConfigurationStatus.of(diagnostic));}}
    public synchronized void selectForGeometryTest(ConfigurationId id){Configuration configuration=registry.find(id).orElseThrow(()->new IllegalArgumentException("Unknown configuration ID."));operationToken.incrementAndGet();if(incrementalSession!=null)incrementalSession.stop();selected=id;incrementalSession=null;activeRequest=null;activeResult=null;stage2InitializationState=Stage2InitializationState.NOT_STARTED;reopenedCoordinates=List.of();persistedResultPath=null;renderRevision++;publishedRenderModel=RenderModel.topologyPreview(configuration,renderRevision);}
    public synchronized Optional<ConfigurationId> selectedConfiguration(){return Optional.ofNullable(selected);}
    public synchronized Optional<Configuration> selectedConfigurationRecord(){return selected==null?Optional.empty():registry.find(selected);}
    public synchronized Optional<GenerationResult> generation(){return Optional.ofNullable(generation);}
    public synchronized TestResult runSelectedGeometryTest(TestRequest request){if(selected==null||!selected.equals(request.configurationId()))throw new IllegalStateException("The user must explicitly select this configuration before geometry testing.");TestResult result=new TestSession(registry.find(selected).orElseThrow(),request).run();registry.attach(result);cachedTableRows=null;activeResult=result;activeRequest=request;renderRevision++;return result;}
    public void startSelectedGeometryTest(TestRequest request) {
        Configuration configuration;long token;
        synchronized(this){requireSelected(request);activeRequest=request;incrementalSession=null;activeResult=null;stage2InitializationState=Stage2InitializationState.INITIALIZING;token=operationToken.incrementAndGet();configuration=registry.find(selected).orElseThrow();renderRevision++;}
        incrementalWorker.submit(()->{stage2ThreadName=Thread.currentThread().getName();long started=System.nanoTime();try{IncrementalSession created=new IncrementalSession(configuration,request);if(token!=operationToken.get())return;created.resume();RenderModel initial=RenderModel.realization(configuration,created.currentCoordinates(),renderRevision+1,created.initializationTrails());synchronized(this){if(token==operationToken.get()){incrementalSession=created;publishedRenderModel=initial;stage2InitializationMillis=(System.nanoTime()-started)/1_000_000;stage2InitializationState=Stage2InitializationState.READY;renderRevision++;}}}catch(Exception failure){if(token==operationToken.get()){asynchronousFailure=failure.getMessage();stage2InitializationState=Stage2InitializationState.FAILED;}}});
    }
    public synchronized void pauseSelectedGeometryTest() { if (incrementalSession != null) incrementalSession.pause(); }
    public synchronized void resumeSelectedGeometryTest() { if (incrementalSession != null) incrementalSession.resume(); }
    public void stepSelectedGeometryTest() { IncrementalSession session; synchronized(this){session=incrementalSession;if(session==null||frameAdvanceScheduled.getAndSet(true))return;publishedRenderModel=RenderModel.realization(session.configuration(),session.currentCoordinates(),++renderRevision,session.initializationTrails());}incrementalWorker.submit(()->{try{SessionState prior=session.state();session.step();if(prior==SessionState.READY||prior==SessionState.PAUSED)session.pause();RenderModel next=RenderModel.realization(session.configuration(),session.currentCoordinates(),renderRevision+1,session.initializationTrails());synchronized(this){if(incrementalSession==session){publishedRenderModel=next;publishIncrementalResult();}}}finally{frameAdvanceScheduled.set(false);}}); }
    public synchronized void stopSelectedGeometryTest() { if (incrementalSession != null) incrementalSession.stop(); }
    public synchronized void skipInitializationAnimation(){if(incrementalSession!=null){incrementalSession.skipInitializationAnimation();publishedRenderModel=RenderModel.realization(incrementalSession.configuration(),incrementalSession.currentCoordinates(),++renderRevision,incrementalSession.initializationTrails());}}
    public synchronized void restartInitializationAnimation(){if(incrementalSession!=null){incrementalSession.restartInitializationAnimation();publishedRenderModel=RenderModel.realization(incrementalSession.configuration(),incrementalSession.currentCoordinates(),++renderRevision,incrementalSession.initializationTrails());}}
    public void resetSelectedGeometryTest() {IncrementalSession session;ConfigurationId id; synchronized(this){if(selected==null)throw new IllegalStateException("Select one configuration before resetting geometry.");session=incrementalSession;id=selected;stage2InitializationState=Stage2InitializationState.INITIALIZING;registry.clearTests(id);activeResult=null;reopenedCoordinates=List.of();persistedResultPath=null;renderRevision++;}if(session!=null)incrementalWorker.submit(()->{session.reset();RenderModel next=RenderModel.realization(session.configuration(),session.currentCoordinates(),renderRevision+1,session.initializationTrails());synchronized(this){if(incrementalSession==session){publishedRenderModel=next;stage2InitializationState=Stage2InitializationState.READY;renderRevision++;}}});}
    public void resetSelectedGeometryTestNewSeed(){TestRequest request; synchronized(this){if(selected==null)throw new IllegalStateException("Select one configuration before resetting geometry.");if(activeRequest==null)throw new IllegalStateException("Start a geometry test before requesting a new seed.");request=activeRequest.withRandomSeed(activeRequest.randomSeed()+1);}startSelectedGeometryTest(request);}
    public synchronized void advanceGeometryTestFrame() {
        if (incrementalSession == null || incrementalSession.executionState() != ExecutionState.RUNNING
                || !frameAdvanceScheduled.compareAndSet(false, true)) return;
        long now=System.nanoTime();int rate=incrementalSession.initializationSnapshot().phase()==InitializationPhase.DISPLAY_ANIMATION?incrementalSession.request().continuation().spectralInitialization().animationFramesPerSecond():scientificUpdatesPerSecond;if(now<nextScientificUpdateNanos){frameAdvanceScheduled.set(false);return;}nextScientificUpdateNanos=now+1_000_000_000L/rate;
        IncrementalSession session = incrementalSession;
        incrementalWorker.submit(() -> {
            try {
                if (incrementalSession == session && session.executionState() == ExecutionState.RUNNING)session.step();RenderModel next=RenderModel.realization(session.configuration(),session.currentCoordinates(),renderRevision+1,session.initializationTrails());
                synchronized (PhasedWorkflow.this) {if (incrementalSession == session){publishedRenderModel=next;publishIncrementalResult();}}
            } finally { frameAdvanceScheduled.set(false); }
        });
    }
    public synchronized int visualizationIterationsPerFrame() { return visualizationIterationsPerFrame; }
    public synchronized void setVisualizationIterationsPerFrame(int value) { if (value < 1) throw new IllegalArgumentException("Visualization iterations per frame must be positive."); visualizationIterationsPerFrame = Math.min(value, 10000); }
    public synchronized int scientificUpdatesPerSecond(){return scientificUpdatesPerSecond;}
    public synchronized void setScientificUpdatesPerSecond(int value){scientificUpdatesPerSecond=Math.max(1,Math.min(120,value));}
    public synchronized SessionState geometryTestSessionState() { return incrementalSession == null ? SessionState.READY : incrementalSession.state(); }
    public synchronized ExecutionState geometryExecutionState() { return incrementalSession == null ? ExecutionState.IDLE : incrementalSession.executionState(); }
    public synchronized ScientificState geometryScientificState() { return incrementalSession == null ? ScientificState.INITIALIZED : incrementalSession.scientificState(); }
    public Stage2InitializationState stage2InitializationState(){return stage2InitializationState;}
    public long stage2InitializationMillis(){return stage2InitializationMillis;}
    public String stage1ThreadName(){return stage1ThreadName;}
    public String stage2ThreadName(){return stage2ThreadName;}
    public synchronized ExperimentConvergenceDiagnostics convergenceDiagnostics(){return incrementalSession==null?null:incrementalSession.convergenceDiagnostics();}
    public synchronized IncrementalSession incrementalSession() { return incrementalSession; }
    public RenderModel renderModel() {return publishedRenderModel;}
    public List<ConfigurationTableRow> tableRows(){List<ConfigurationTableRow> rows=cachedTableRows;if(rows!=null)return rows;long waiting=System.nanoTime();synchronized(this){DevelopmentTimingDiagnostics.record("ui.workflow-lock-wait",System.nanoTime()-waiting);if(cachedTableRows==null)cachedTableRows=buildTableRows();return cachedTableRows;}}
    private List<ConfigurationTableRow> buildTableRows(){long started=System.nanoTime();try{ExperimentProfile profile=generation==null?ExperimentProfile.CLOSED_3D_VOLUME:generation.request().experimentProfile();var composition=generation==null?remframework.geometry.discovery.experiment.FaceComposition.discover():generation.request().faceComposition();ReferenceTopologyRecognizer recognizer=new ReferenceTopologyRecognizer();return registry.configurations().stream().map(c->{List<TestResult> tests=registry.tests(c.id());TestResult last=tests.isEmpty()?null:tests.get(tests.size()-1);TopologyEligibilityReport eligibility=new TopologyEligibilityAnalyzer().analyze(c,profile,composition);return new ConfigurationTableRow(c.id(),c.nodeCount(),c.exactDegree(),c.edges().size(),c.graphSymmetry(),c.automorphismGroupSize(),eligibility.vertexTransitive(),profile,c.status().code(),eligibility.status(),c.status().code(),last!=null,last==null?GeometryConfigurationStatusCode.GEOMETRY_TEST_NOT_STARTED:last.status().code(),eligibility.conclusion(),recognizer.recognize(c).topology(),eligibility.faceComposition()==null?remframework.geometry.discovery.experiment.FaceCompositionMatch.UNDETERMINED:eligibility.faceComposition().match());}).toList();}finally{DevelopmentTimingDiagnostics.record("ui.configuration-table-build",System.nanoTime()-started);}}
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
                current.benchmarkPreset(),current.referenceTopologyMatch(),current.threeDConstraintStrengths(),current.requestedSeed());
        registry.attach(optimized);
        return optimized;
    }
    private void requireSelected(TestRequest request) { if (selected == null || !selected.equals(request.configurationId())) throw new IllegalStateException("The user must explicitly select this configuration before geometry testing."); }
    private void publishIncrementalResult() { renderRevision++; if (incrementalSession != null && incrementalSession.result() != null && activeResult == null) { activeResult = incrementalSession.result(); registry.attach(activeResult);cachedTableRows=null; if (generation != null && generation.request().outputDirectory() != null) { try { persistedResultPath = new TestResultStore().save(generation.request().outputDirectory(), activeResult); } catch (IOException ignored) { persistedResultPath = null; } } } }
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
        renderRevision++;publishedRenderModel=RenderModel.realization(registry.find(selected).orElseThrow(),reopenedCoordinates,renderRevision);
    }
    public void close() { cancelGeneration();generationCoordinator.shutdownNow();incrementalWorker.shutdownNow(); }
    public synchronized Registry registry(){return registry;}
}
