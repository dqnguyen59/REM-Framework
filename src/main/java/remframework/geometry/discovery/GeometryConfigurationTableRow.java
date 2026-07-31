package remframework.geometry.discovery;
public record GeometryConfigurationTableRow(GeometryConfigurationId configurationId,int nodeCount,
        int exactDegree,int edgeCount,GraphSymmetryClassification symmetry,long automorphismCount,
        boolean vertexTransitive, GeometryConfigurationStatusCode status,boolean geometryTested,
        GeometryConfigurationStatusCode geometryTestStatus,String diagnosticSummary) {
    public GeometryConfigurationTableRow(GeometryConfigurationId configurationId, int nodeCount,
            int exactDegree, int edgeCount, GraphSymmetryClassification symmetry,
            long automorphismCount, GeometryConfigurationStatusCode status,
            boolean geometryTested, GeometryConfigurationStatusCode geometryTestStatus,
            String diagnosticSummary) {
        this(configurationId, nodeCount, exactDegree, edgeCount, symmetry, automorphismCount,
                symmetry == GraphSymmetryClassification.HIGHLY_SYMMETRIC, status,
                geometryTested, geometryTestStatus, diagnosticSummary);
    }
}
