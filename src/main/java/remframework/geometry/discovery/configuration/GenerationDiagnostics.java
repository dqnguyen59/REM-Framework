package remframework.geometry.discovery.configuration;

import java.util.Map;

public record GenerationDiagnostics(GenerationStrategy strategy,String strategyVersion,
        ConstructionMethod constructionMethod,GenerationCompleteness completeness,
        long partialStatesExamined,long partialStatesPruned,long completedCandidates,
        long duplicateCandidates,int retainedConfigurations,Map<String,Long> pruningCounts,
        UniformSphericalSearchLimits searchLimits,int workerCount,String explanation) {
    public GenerationDiagnostics { pruningCounts=Map.copyOf(pruningCounts); }
    public static GenerationDiagnostics legacy(GenerationStrategy strategy,boolean complete,String explanation){return new GenerationDiagnostics(strategy,"1",ConstructionMethod.PRIMAL_GRAPH,complete?GenerationCompleteness.COMPLETE_WITHIN_CONFIGURED_SEARCH_LIMITS:GenerationCompleteness.INCOMPLETE_SEARCH_LIMIT_REACHED,0,0,0,0,0,Map.of(),null,1,explanation);}
}
