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
import remframework.geometry.discovery.eligibility.*;
import remframework.geometry.discovery.experiment.*;
import remframework.geometry.discovery.measurement.regularity.*;
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
public record TestResult(ConfigurationId configurationId,
        ExperimentProfile experimentProfile, PlanarityRealizationMode planarityMode,
        AngleConditionMode angleConditionMode, TopologyEligibilityReport eligibility,
        GraphSymmetryClassification graphSymmetry,Realization realization,
        FaceDerivationResult faceDerivation,ClosedBoundaryResult boundary,
        GeometricSymmetryResult geometricSymmetry,AreaResult area,VolumeResult volume,
        TwoDRegularityMetrics twoDRegularity, ThreeDRegularityMetrics threeDRegularity,
        GeometryOptimizationResult optimization, OptimizationComparison optimizationComparison,
        GeometryConfigurationStatus status) {
    public TestResult(ConfigurationId configurationId, GraphSymmetryClassification graphSymmetry,
            Realization realization, FaceDerivationResult faceDerivation, ClosedBoundaryResult boundary,
            GeometricSymmetryResult geometricSymmetry, AreaResult area, VolumeResult volume,
            GeometryOptimizationResult optimization, GeometryConfigurationStatus status) {
        this(configurationId, null, null, null, null, graphSymmetry, realization, faceDerivation, boundary,
                geometricSymmetry, area, volume, null, null, optimization, null, status);
    }
}
