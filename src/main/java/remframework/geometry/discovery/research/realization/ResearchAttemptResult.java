package remframework.geometry.discovery.research.realization;

/** Completed solver-side attempt record. No reference data is present. */
public record ResearchAttemptResult(String strategyId,String configurationId,long seed,
        int budget,int iterations,boolean qualified,boolean numericFailure,
        double worstRequiredErrorRatio,int selfIntersections,int convexityViolations,
        double normalizedRequiredObjective,double edgeError,double edgeTolerance,
        double planarityError,double planarityTolerance,double angleError,double angleTolerance,
        double dihedralCoefficientOfVariation,double vertexRadiusCoefficientOfVariation,
        double facePlaneRadiusCoefficientOfVariation,double volume,
        long runtimeMillis,String stage,String stageHistory,String stagnation,
        String configurationStatus) {
    public String key(){return strategyId+"|"+configurationId+"|"+seed+"|"+budget;}
}
