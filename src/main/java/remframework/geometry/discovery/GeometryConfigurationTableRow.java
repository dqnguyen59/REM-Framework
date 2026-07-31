package remframework.geometry.discovery;
public record GeometryConfigurationTableRow(GeometryConfigurationId configurationId,int nodeCount,
        int exactDegree,int edgeCount,GraphSymmetryClassification symmetry,long automorphismCount,
        GeometryConfigurationStatusCode status,boolean geometryTested,
        GeometryConfigurationStatusCode geometryTestStatus,String diagnosticSummary) { }
