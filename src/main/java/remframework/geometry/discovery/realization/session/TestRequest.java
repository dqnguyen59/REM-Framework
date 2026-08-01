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
import remframework.geometry.discovery.experiment.*;
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
        GeometrySymmetryPolicy symmetryPolicy,int searchLimit, ExperimentProfile experimentProfile,
        AngleConditionMode angleConditionMode, RegularityMode regularityMode,
        OptimizationObjective optimizationObjective, double edgeTolerance,
        double planarityTolerance, double degeneracyTolerance, double angleTolerance) {
    public TestRequest {
        if(configurationId==null||dimension==null)throw new IllegalArgumentException("A selected configuration and dimension are required.");
        if(!Double.isFinite(targetEdgeLength)||targetEdgeLength<=0||initialRealizations<1||searchLimit<1)throw new IllegalArgumentException("Geometry-test limits must be positive and finite.");
        symmetryPolicy=symmetryPolicy==null?GeometrySymmetryPolicy.MEASURE_ONLY:symmetryPolicy;
        relaxationSettings=relaxationSettings==null?DynamicElasticSettings.defaults(targetEdgeLength,randomSeed):relaxationSettings.withTargetDistance(targetEdgeLength).withCanonicalGeometry(DynamicElasticCanonicalGeometrySettings.disabled());
        experimentProfile=experimentProfile==null?(dimension==GeometryEmbeddingDimension.TWO_D?ExperimentProfile.CLOSED_2D_AREA:ExperimentProfile.CLOSED_3D_VOLUME):experimentProfile;
        if(experimentProfile.intendedDimension()!=dimension)throw new IllegalArgumentException("Experiment profile dimension must match realization dimension.");
        angleConditionMode=angleConditionMode==null?AngleConditionMode.MEASURE_ONLY:angleConditionMode;
        regularityMode=regularityMode==null?RegularityMode.MEASURE_ONLY:regularityMode;
        optimizationObjective=optimizationObjective==null?OptimizationObjective.NONE:optimizationObjective;
        if(!experimentProfile.permittedOptimizationObjectives().contains(optimizationObjective))throw new IllegalArgumentException("Optimization objective is not permitted by the experiment profile.");
        edgeTolerance=edgeTolerance>0?edgeTolerance:Math.max(1e-7,targetEdgeLength*1e-3);
        planarityTolerance=planarityTolerance>0?planarityTolerance:1e-7;
        degeneracyTolerance=degeneracyTolerance>0?degeneracyTolerance:1e-9;
        angleTolerance=angleTolerance>0?angleTolerance:1e-6;
    }
    public TestRequest(ConfigurationId configurationId, GeometryEmbeddingDimension dimension,
            double targetEdgeLength,long randomSeed,int initialRealizations,
            DynamicElasticSettings relaxationSettings,GeometrySymmetryPolicy symmetryPolicy,int searchLimit){
        this(configurationId,dimension,targetEdgeLength,randomSeed,initialRealizations,
                relaxationSettings,symmetryPolicy,searchLimit,null,null,null,null,0,0,0,0);
    }
}
