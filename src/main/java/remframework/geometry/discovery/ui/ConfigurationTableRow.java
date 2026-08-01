package remframework.geometry.discovery.ui;
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
public record ConfigurationTableRow(ConfigurationId configurationId,int nodeCount,
        int exactDegree,int edgeCount,GraphSymmetryClassification symmetry,long automorphismCount,
        boolean vertexTransitive, GeometryConfigurationStatusCode status,boolean geometryTested,
        GeometryConfigurationStatusCode geometryTestStatus,String diagnosticSummary) {
    public ConfigurationTableRow(ConfigurationId configurationId, int nodeCount,
            int exactDegree, int edgeCount, GraphSymmetryClassification symmetry,
            long automorphismCount, GeometryConfigurationStatusCode status,
            boolean geometryTested, GeometryConfigurationStatusCode geometryTestStatus,
            String diagnosticSummary) {
        this(configurationId, nodeCount, exactDegree, edgeCount, symmetry, automorphismCount,
                symmetry == GraphSymmetryClassification.HIGHLY_SYMMETRIC, status,
                geometryTested, geometryTestStatus, diagnosticSummary);
    }
}
