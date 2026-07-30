package remframework.geometry.discovery;

public record GeometryBatchAggregate(int totalRuns, int convergedRuns, double convergenceRate,
                                     double meanIterationCount, int bestRunId, int worstRunId,
                                     int fastestConvergedRunId,
                                     String highestConvergenceParameterCombination) {
}
