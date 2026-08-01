package remframework.geometry.discovery.realization.session;
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
public record TestRequest(ConfigurationId configurationId,
        GeometryEmbeddingDimension dimension,double targetEdgeLength,long randomSeed,
        int initialRealizations,DynamicElasticSettings relaxationSettings,
        GeometrySymmetryPolicy symmetryPolicy,int searchLimit) {
    public TestRequest {
        if(configurationId==null||dimension==null)throw new IllegalArgumentException("A selected configuration and dimension are required.");
        if(!Double.isFinite(targetEdgeLength)||targetEdgeLength<=0||initialRealizations<1||searchLimit<1)throw new IllegalArgumentException("Geometry-test limits must be positive and finite.");
        symmetryPolicy=symmetryPolicy==null?GeometrySymmetryPolicy.MEASURE_ONLY:symmetryPolicy;
        relaxationSettings=relaxationSettings==null?DynamicElasticSettings.defaults(targetEdgeLength,randomSeed):relaxationSettings.withTargetDistance(targetEdgeLength).withCanonicalGeometry(DynamicElasticCanonicalGeometrySettings.disabled());
    }
}
