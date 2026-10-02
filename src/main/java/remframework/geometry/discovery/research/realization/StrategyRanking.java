package remframework.geometry.discovery.research.realization;

import java.util.Comparator;
import java.util.List;

/** Scientific-validity-first ranking; reference resemblance is deliberately absent. */
public final class StrategyRanking {
    private StrategyRanking(){ }
    public static List<String> rank(List<ResearchAttemptResult> attempts){return attempts.stream()
            .collect(java.util.stream.Collectors.groupingBy(ResearchAttemptResult::strategyId))
            .entrySet().stream().sorted(Comparator
                    .<java.util.Map.Entry<String,List<ResearchAttemptResult>>>comparingLong(e->e.getValue().stream().filter(ResearchAttemptResult::qualified).count()).reversed()
                    .thenComparingLong(e->e.getValue().stream().filter(ResearchAttemptResult::numericFailure).count())
                    .thenComparingDouble(e->e.getValue().stream().mapToDouble(ResearchAttemptResult::worstRequiredErrorRatio).max().orElse(Double.POSITIVE_INFINITY))
                    .thenComparing(java.util.Map.Entry::getKey)).map(java.util.Map.Entry::getKey).toList();}
}
