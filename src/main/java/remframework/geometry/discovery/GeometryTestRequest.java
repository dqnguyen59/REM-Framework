package remframework.geometry.discovery;
public record GeometryTestRequest(GeometryConfigurationId configurationId,
        GeometryEmbeddingDimension dimension,double targetEdgeLength,long randomSeed,
        int initialRealizations,DynamicElasticSettings relaxationSettings,
        GeometrySymmetryPolicy symmetryPolicy,int searchLimit) {
    public GeometryTestRequest {
        if(configurationId==null||dimension==null)throw new IllegalArgumentException("A selected configuration and dimension are required.");
        if(!Double.isFinite(targetEdgeLength)||targetEdgeLength<=0||initialRealizations<1||searchLimit<1)throw new IllegalArgumentException("Geometry-test limits must be positive and finite.");
        symmetryPolicy=symmetryPolicy==null?GeometrySymmetryPolicy.MEASURE_ONLY:symmetryPolicy;
        relaxationSettings=relaxationSettings==null?DynamicElasticSettings.defaults(targetEdgeLength,randomSeed):relaxationSettings.withTargetDistance(targetEdgeLength).withCanonicalGeometry(DynamicElasticCanonicalGeometrySettings.disabled());
    }
}
