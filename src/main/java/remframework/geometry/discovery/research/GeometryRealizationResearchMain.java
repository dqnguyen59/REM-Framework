package remframework.geometry.discovery.research;

import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.experiment.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.research.benchmark.*;
import remframework.geometry.discovery.research.realization.*;

import java.nio.file.*;
import java.util.*;

/** Resumable CLI for bounded, topology-independent Stage 2 strategy research. */
public final class GeometryRealizationResearchMain {
    private GeometryRealizationResearchMain(){ }
    public static void main(String[] args)throws Exception{
        Path output=Path.of(option(args,"--output","target/geometry-realization-research"));
        long master=Long.parseLong(option(args,"--master-seed","20260802"));
        ResearchSearchManifest manifest=GeneralStrategySearchSpace.initial(master);
        if(Arrays.asList(args).contains("--quick"))manifest=new ResearchSearchManifest(
                "GRID_SEARCH_QUICK_VALIDATION",manifest.masterSeed(),manifest.surfaceAssumption(),
                List.of(25),List.of(42L),List.of(142L),manifest.strategies().stream()
                .filter(RealizationStrategySpecification::executableByCurrentSolver).limit(2).toList(),
                manifest.unavailableAlternatives());
        ResearchStore store=new ResearchStore(output);store.writeManifest(manifest);
        if(Arrays.asList(args).contains("--manifest-only")){System.out.println(output.toAbsolutePath());return;}
        new GeometryRealizationResearchMain().run(manifest,store,new BenchmarkEvaluationStore(output));
        System.out.println(output.toAbsolutePath());
    }
    public void run(ResearchSearchManifest manifest,ResearchStore store,BenchmarkEvaluationStore benchmarkStore)throws Exception{
        List<BenchmarkProblem> problems=problems();BenchmarkProblem development=problems.getLast();
        List<RealizationStrategySpecification> survivors=manifest.strategies().stream().filter(RealizationStrategySpecification::executableByCurrentSolver).toList();
        Set<String> completed=store.completedKeys();List<ResearchAttemptResult> all=new ArrayList<>(store.readAttempts());
        for(int round=0;round<manifest.stagedIterationBudgets().size();round++){
            int budget=manifest.stagedIterationBudgets().get(round);List<BenchmarkProblem> roundProblems=round==manifest.stagedIterationBudgets().size()-1?problems:List.of(development);List<Long> seeds=round==0?List.of(manifest.developmentSeeds().getFirst()):round==1?manifest.developmentSeeds():manifest.heldOutSeeds();
            for(RealizationStrategySpecification strategy:survivors)for(BenchmarkProblem problem:roundProblems)for(long seed:seeds){String key=strategy.id()+"|"+problem.configuration.id()+"|"+seed+"|"+budget;if(completed.contains(key))continue;ResearchAttemptResult attempt=execute(strategy,problem,seed,budget);store.append(attempt);all.add(attempt);completed.add(key);benchmarkStore.append(new PostRunBenchmarkEvaluator().evaluate(attempt,problem.configuration,problem.faceSize));}
            List<String> ranked=StrategyRanking.rank(all.stream().filter(a->a.budget()==budget).toList());int keep=Math.max(1,(ranked.size()+1)/2);Set<String> retained=new LinkedHashSet<>(ranked.subList(0,Math.min(keep,ranked.size())));survivors=survivors.stream().filter(s->retained.contains(s.id())).toList();
        }
        store.writeRanking(StrategyRanking.rank(all));
    }
    private ResearchAttemptResult execute(RealizationStrategySpecification strategy,BenchmarkProblem problem,long seed,int budget){long started=System.nanoTime();TestRequest request=request(strategy,problem,seed,budget);IncrementalSession session=new IncrementalSession(problem.configuration,request);session.run();ConstraintPresentationSummary summary=session.constraintPresentation();OrientationConvexityDiagnostics convexity=session.orientationConvexityDiagnostics();ConstraintObjectiveDiagnostics objective=session.objectiveDiagnostics();ThreeDConstraintDiagnostics diagnostics=session.threeDConstraintDiagnostics();TestResult result=session.result();String status=result==null?session.scientificState().name():result.status().code().name();int intersections=result==null||result.boundary()==null?-1:result.boundary().selfIntersecting()?1:0;String history=session.continuationSnapshot().history().stream().map(x->x.stageId()+":"+x.state()+":"+x.iterations()+":"+x.worstConstraint()+":"+x.worstRatio()).collect(java.util.stream.Collectors.joining(";"));var regularity=result==null?null:result.threeDRegularity();double dihedral=regularity==null?Double.NaN:coefficientOfVariation(regularity.interiorDihedralAngles()),vertex=regularity==null?Double.NaN:coefficientOfVariation(regularity.vertexRadii()),plane=regularity==null?Double.NaN:coefficientOfVariation(regularity.facePlaneRadii()),volume=result==null||!result.volume().available()?Double.NaN:result.volume().absoluteVolume();return new ResearchAttemptResult(strategy.id(),problem.configuration.id().toString(),seed,budget,session.iteration(),summary.qualified(),session.stagnationStatus().classification()==StagnationClass.NUMERICALLY_UNSTABLE,summary.worstRequiredRatio(),intersections,convexity.violatingPairs(),objective==null?Double.NaN:objective.totalObjective(),session.maximumEdgeError(),request.edgeTolerance(),diagnostics.maximumFacePlanarityError(),conditions(request).facePlanarityTolerance(),diagnostics.maximumFaceAngleDeviation(),conditions(request).absoluteRegularityTolerance(),dihedral,vertex,plane,volume,(System.nanoTime()-started)/1_000_000,session.continuationSnapshot().stageId(),history,session.stagnationStatus().classification().name(),status);}
    private static double coefficientOfVariation(remframework.geometry.discovery.measurement.regularity.Statistics values){return values==null||!Double.isFinite(values.mean())||Math.abs(values.mean())<1e-15?Double.NaN:values.standardDeviation()/Math.abs(values.mean());}
    private static ThreeDExperimentConditions conditions(TestRequest request){return request.threeDConditions();}
    private TestRequest request(RealizationStrategySpecification strategy,BenchmarkProblem p,long seed,int budget){ThreeDExperimentConditions conditions=new ThreeDExperimentConditions(FacePlanarityMode.REQUIRE_PLANAR_FACES,FaceAngleConditionMode.REQUIRE_EQUAL_ANGLES_WITHIN_EACH_FACE,DihedralConditionMode.MEASURE_ONLY,RegularityMode.MEASURE_ONLY,RegularityMode.MEASURE_ONLY,RegularityMode.MEASURE_ONLY,FacePlaneRadiusMode.REQUIRE_EQUAL_FACE_PLANE_RADII,RegularityMode.MEASURE_ONLY,GeometryCenterDefinition.VERTEX_CENTROID,1e-4,1e-3,1e-3);TestRequest request=new TestRequest(p.configuration.id(),GeometryEmbeddingDimension.THREE_D,1,seed,strategy.multistartCount(),null,GeometrySymmetryPolicy.MEASURE_ONLY,budget,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME,null,null,null,1e-3,1e-7,1e-9,1e-6,null,0,0,conditions,BenchmarkPreset.NONE,ThreeDConstraintStrengths.defaults()).withFaceComposition(FaceComposition.uniform(p.faceSize)).withParameterDerivation(p.request);ContinuationSettings continuation=strategy.activation()==RealizationStrategySpecification.Activation.SIMULTANEOUS?ContinuationSettings.simultaneous():ContinuationSettings.sequential(conditions,strategy.stableIterations(),Math.min(strategy.maximumIterationsPerStage(),budget),strategy.rampDuration());TopologyInitialization initialization=strategy.initialization()==RealizationStrategySpecification.Initialization.SPECTRAL_EMBEDDING?TopologyInitialization.SPECTRAL_EMBEDDING:TopologyInitialization.RANDOM_SPHERICAL;return request.withContinuation(continuation.withInitialization(initialization));}
    private List<BenchmarkProblem> problems(){return List.of(problem(4,3,3),problem(6,3,4),problem(12,3,5));}
    private BenchmarkProblem problem(int faces,int degree,int faceSize){ConfigurationRequest request=ConfigurationRequest.byFaceCount(faces,degree,GeometryEmbeddingDimension.THREE_D,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME).withFaceComposition(FaceComposition.uniform(faceSize)).withGenerationStrategy(GenerationStrategy.UNIFORM_SPHERICAL_FACE_GENERATION);Configuration configuration=new ConfigurationGenerator().generate(request).configurations().getFirst();return new BenchmarkProblem(request,configuration,faceSize);}
    private record BenchmarkProblem(ConfigurationRequest request,Configuration configuration,int faceSize){ }
    private static String option(String[] args,String name,String fallback){for(int i=0;i+1<args.length;i++)if(args[i].equals(name))return args[i+1];return fallback;}
}
