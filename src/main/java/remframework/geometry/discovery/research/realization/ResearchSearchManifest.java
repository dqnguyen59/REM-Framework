package remframework.geometry.discovery.research.realization;

import java.util.List;

/** Exact finite search declaration and explicit sphere-like experimental scope. */
public record ResearchSearchManifest(String searchMethod,long masterSeed,
        String surfaceAssumption,List<Integer> stagedIterationBudgets,List<Long> developmentSeeds,
        List<Long> heldOutSeeds,List<RealizationStrategySpecification> strategies,
        List<String> unavailableAlternatives) {
    public ResearchSearchManifest { stagedIterationBudgets=List.copyOf(stagedIterationBudgets);
        developmentSeeds=List.copyOf(developmentSeeds);heldOutSeeds=List.copyOf(heldOutSeeds);
        strategies=List.copyOf(strategies);unavailableAlternatives=List.copyOf(unavailableAlternatives); }
}
