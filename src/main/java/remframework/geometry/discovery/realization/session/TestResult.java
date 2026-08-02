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
import remframework.geometry.discovery.reference.ReferenceTopologyMatch;
import remframework.geometry.discovery.reference.ReferenceTopology;
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
        ThreeDConditionReport threeDConditionReport,
        GeometryOptimizationResult optimization, OptimizationComparison optimizationComparison,
        GeometryConfigurationStatus status, BenchmarkPreset benchmarkPreset,
        ReferenceTopologyMatch referenceTopologyMatch,
        ThreeDConstraintStrengths threeDConstraintStrengths,long requestedSeed,
        FaceComposition faceComposition) {
    public TestResult(ConfigurationId configurationId,
            ExperimentProfile experimentProfile, PlanarityRealizationMode planarityMode,
            AngleConditionMode angleConditionMode, TopologyEligibilityReport eligibility,
            GraphSymmetryClassification graphSymmetry,Realization realization,
            FaceDerivationResult faceDerivation,ClosedBoundaryResult boundary,
            GeometricSymmetryResult geometricSymmetry,AreaResult area,VolumeResult volume,
            TwoDRegularityMetrics twoDRegularity, ThreeDRegularityMetrics threeDRegularity,
            ThreeDConditionReport threeDConditionReport,
            GeometryOptimizationResult optimization, OptimizationComparison optimizationComparison,
            GeometryConfigurationStatus status, BenchmarkPreset benchmarkPreset,
            ReferenceTopologyMatch referenceTopologyMatch,
            ThreeDConstraintStrengths threeDConstraintStrengths,long requestedSeed) {
        this(configurationId,experimentProfile,planarityMode,angleConditionMode,eligibility,
                graphSymmetry,realization,faceDerivation,boundary,geometricSymmetry,area,volume,
                twoDRegularity,threeDRegularity,threeDConditionReport,optimization,
                optimizationComparison,status,benchmarkPreset,referenceTopologyMatch,
                threeDConstraintStrengths,requestedSeed,FaceComposition.discover());
    }
    public TestResult(ConfigurationId configurationId, GraphSymmetryClassification graphSymmetry,
            Realization realization, FaceDerivationResult faceDerivation, ClosedBoundaryResult boundary,
            GeometricSymmetryResult geometricSymmetry, AreaResult area, VolumeResult volume,
            GeometryOptimizationResult optimization, GeometryConfigurationStatus status) {
        this(configurationId, null, null, null, null, graphSymmetry, realization, faceDerivation, boundary,
                geometricSymmetry, area, volume, null, null, null, optimization, null, status,
                BenchmarkPreset.NONE, null,null,realization==null?0:realization.seed(),FaceComposition.discover());
    }

    public boolean benchmarkTopologyRequirementPassed() {
        return benchmarkPreset == null || benchmarkPreset == BenchmarkPreset.NONE
                || referenceTopologyMatch != null
                && referenceTopologyMatch.topology() == ReferenceTopology.CUBE_GRAPH;
    }

    public boolean selectedBenchmarkPassed() {
        if (!benchmarkTopologyRequirementPassed()) return false;
        if (benchmarkPreset == null || benchmarkPreset == BenchmarkPreset.NONE) return true;
        boolean sixQuadrilateralFaces=faceDerivation!=null&&faceDerivation.unique()
                &&faceDerivation.faces().size()==6&&faceDerivation.faces().stream().allMatch(face->face.nodeIds().size()==4);
        return sixQuadrilateralFaces&&boundary!=null&&boundary.closed()&&boundary.manifold()&&!boundary.selfIntersecting()
                &&volume.available()&&volume.absoluteVolume()>0
                &&status.code() == GeometryConfigurationStatusCode.VALID_CANDIDATE
                && threeDConditionReport != null && threeDConditionReport.constrainedExperimentPassed();
    }
}
