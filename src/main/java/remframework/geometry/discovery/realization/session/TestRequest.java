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
        double planarityTolerance, double degeneracyTolerance, double angleTolerance,
        PlanarityRealizationMode planarityMode, double planeRestoringStrength,
        double angleRestoringStrength, ThreeDExperimentConditions threeDConditions,
        BenchmarkPreset benchmarkPreset, ThreeDConstraintStrengths threeDConstraintStrengths,
        FaceComposition faceComposition, PrimaryParameterMode primaryParameterMode,
        SurfaceTopologyAssumption surfaceTopologyAssumption,
        ParameterDerivationReport parameterDerivation) {
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
        planarityMode=planarityMode==null?PlanarityRealizationMode.STRICT_2D_COORDINATES:planarityMode;
        planeRestoringStrength=planeRestoringStrength>=0&&Double.isFinite(planeRestoringStrength)?planeRestoringStrength:.08;
        angleRestoringStrength=angleRestoringStrength>=0&&Double.isFinite(angleRestoringStrength)?angleRestoringStrength:.01;
        benchmarkPreset=benchmarkPreset==null?BenchmarkPreset.NONE:benchmarkPreset;
        if(benchmarkPreset!=BenchmarkPreset.NONE&&experimentProfile==ExperimentProfile.CLOSED_2D_AREA)
            throw new IllegalArgumentException("Cube benchmarks require a 3D experiment profile.");
        threeDConditions=threeDConditions==null?BenchmarkPresets.conditions(benchmarkPreset):threeDConditions;
        threeDConstraintStrengths=threeDConstraintStrengths==null?ThreeDConstraintStrengths.defaults():threeDConstraintStrengths;
        faceComposition=faceComposition==null?FaceComposition.discover():faceComposition;
        primaryParameterMode=primaryParameterMode==null?PrimaryParameterMode.NODE_COUNT:primaryParameterMode;
        surfaceTopologyAssumption=surfaceTopologyAssumption==null?SurfaceTopologyAssumption.SPHERE_LIKE:surfaceTopologyAssumption;
    }
    public TestRequest(ConfigurationId configurationId,
            GeometryEmbeddingDimension dimension,double targetEdgeLength,long randomSeed,
            int initialRealizations,DynamicElasticSettings relaxationSettings,
            GeometrySymmetryPolicy symmetryPolicy,int searchLimit, ExperimentProfile experimentProfile,
            AngleConditionMode angleConditionMode, RegularityMode regularityMode,
            OptimizationObjective optimizationObjective, double edgeTolerance,
            double planarityTolerance, double degeneracyTolerance, double angleTolerance,
            PlanarityRealizationMode planarityMode, double planeRestoringStrength,
            double angleRestoringStrength, ThreeDExperimentConditions threeDConditions,
            BenchmarkPreset benchmarkPreset, ThreeDConstraintStrengths threeDConstraintStrengths) {
        this(configurationId,dimension,targetEdgeLength,randomSeed,initialRealizations,
                relaxationSettings,symmetryPolicy,searchLimit,experimentProfile,angleConditionMode,
                regularityMode,optimizationObjective,edgeTolerance,planarityTolerance,
                degeneracyTolerance,angleTolerance,planarityMode,planeRestoringStrength,
                angleRestoringStrength,threeDConditions,benchmarkPreset,threeDConstraintStrengths,null,
                null,null,null);
    }
    public TestRequest(ConfigurationId configurationId,
            GeometryEmbeddingDimension dimension,double targetEdgeLength,long randomSeed,
            int initialRealizations,DynamicElasticSettings relaxationSettings,
            GeometrySymmetryPolicy symmetryPolicy,int searchLimit, ExperimentProfile experimentProfile,
            AngleConditionMode angleConditionMode, RegularityMode regularityMode,
            OptimizationObjective optimizationObjective, double edgeTolerance,
            double planarityTolerance, double degeneracyTolerance, double angleTolerance,
            PlanarityRealizationMode planarityMode, double planeRestoringStrength,
            double angleRestoringStrength, ThreeDExperimentConditions threeDConditions,
            BenchmarkPreset benchmarkPreset, ThreeDConstraintStrengths threeDConstraintStrengths,
            FaceComposition faceComposition) {
        this(configurationId,dimension,targetEdgeLength,randomSeed,initialRealizations,
                relaxationSettings,symmetryPolicy,searchLimit,experimentProfile,angleConditionMode,
                regularityMode,optimizationObjective,edgeTolerance,planarityTolerance,
                degeneracyTolerance,angleTolerance,planarityMode,planeRestoringStrength,
                angleRestoringStrength,threeDConditions,benchmarkPreset,threeDConstraintStrengths,
                faceComposition,null,null,null);
    }
    public TestRequest(ConfigurationId configurationId, GeometryEmbeddingDimension dimension,
            double targetEdgeLength,long randomSeed,int initialRealizations,
            DynamicElasticSettings relaxationSettings,GeometrySymmetryPolicy symmetryPolicy,int searchLimit){
        this(configurationId,dimension,targetEdgeLength,randomSeed,initialRealizations,
                relaxationSettings,symmetryPolicy,searchLimit,null,null,null,null,0,0,0,0);
    }
    public TestRequest(ConfigurationId configurationId, GeometryEmbeddingDimension dimension,
            double targetEdgeLength,long randomSeed,int initialRealizations,
            DynamicElasticSettings relaxationSettings,GeometrySymmetryPolicy symmetryPolicy,int searchLimit,
            ExperimentProfile experimentProfile,AngleConditionMode angleConditionMode,
            RegularityMode regularityMode,OptimizationObjective optimizationObjective,double edgeTolerance,
            double planarityTolerance,double degeneracyTolerance,double angleTolerance) {
        this(configurationId,dimension,targetEdgeLength,randomSeed,initialRealizations,relaxationSettings,
                symmetryPolicy,searchLimit,experimentProfile,angleConditionMode,regularityMode,
                optimizationObjective,edgeTolerance,planarityTolerance,degeneracyTolerance,
                angleTolerance,null,.08,.01,null,null,null);
    }
    public TestRequest(ConfigurationId configurationId, GeometryEmbeddingDimension dimension,
            double targetEdgeLength,long randomSeed,int initialRealizations,
            DynamicElasticSettings relaxationSettings,GeometrySymmetryPolicy symmetryPolicy,int searchLimit,
            ExperimentProfile experimentProfile,AngleConditionMode angleConditionMode,
            RegularityMode regularityMode,OptimizationObjective optimizationObjective,double edgeTolerance,
            double planarityTolerance,double degeneracyTolerance,double angleTolerance,
            PlanarityRealizationMode planarityMode,double planeRestoringStrength,double angleRestoringStrength){
        this(configurationId,dimension,targetEdgeLength,randomSeed,initialRealizations,relaxationSettings,
                symmetryPolicy,searchLimit,experimentProfile,angleConditionMode,regularityMode,
                optimizationObjective,edgeTolerance,planarityTolerance,degeneracyTolerance,angleTolerance,
                planarityMode,planeRestoringStrength,angleRestoringStrength,null,null,null);
    }

    public TestRequest withBenchmark(BenchmarkPreset preset) {
        int realizations=preset==BenchmarkPreset.CUBE_CONSTRAINED_BENCHMARK?Math.max(initialRealizations,BenchmarkPresets.CONSTRAINED_INITIAL_REALIZATIONS):initialRealizations;
        return new TestRequest(configurationId, dimension, targetEdgeLength, randomSeed,
                realizations, relaxationSettings, symmetryPolicy, searchLimit,
                experimentProfile, angleConditionMode, regularityMode, optimizationObjective,
                edgeTolerance, planarityTolerance, degeneracyTolerance, angleTolerance,
                planarityMode, planeRestoringStrength, angleRestoringStrength,
                BenchmarkPresets.conditions(preset), preset, ThreeDConstraintStrengths.defaults(),faceComposition,
                primaryParameterMode,surfaceTopologyAssumption,parameterDerivation);
    }
    public TestRequest withRandomSeed(long seed){return new TestRequest(configurationId,dimension,targetEdgeLength,seed,initialRealizations,relaxationSettings,symmetryPolicy,searchLimit,experimentProfile,angleConditionMode,regularityMode,optimizationObjective,edgeTolerance,planarityTolerance,degeneracyTolerance,angleTolerance,planarityMode,planeRestoringStrength,angleRestoringStrength,threeDConditions,benchmarkPreset,threeDConstraintStrengths,faceComposition,primaryParameterMode,surfaceTopologyAssumption,parameterDerivation);}
    public TestRequest withFaceComposition(FaceComposition value){return new TestRequest(configurationId,dimension,targetEdgeLength,randomSeed,initialRealizations,relaxationSettings,symmetryPolicy,searchLimit,experimentProfile,angleConditionMode,regularityMode,optimizationObjective,edgeTolerance,planarityTolerance,degeneracyTolerance,angleTolerance,planarityMode,planeRestoringStrength,angleRestoringStrength,threeDConditions,benchmarkPreset,threeDConstraintStrengths,value,primaryParameterMode,surfaceTopologyAssumption,parameterDerivation);}
    public TestRequest withParameterDerivation(ConfigurationRequest generation){return new TestRequest(configurationId,dimension,targetEdgeLength,randomSeed,initialRealizations,relaxationSettings,symmetryPolicy,searchLimit,experimentProfile,angleConditionMode,regularityMode,optimizationObjective,edgeTolerance,planarityTolerance,degeneracyTolerance,angleTolerance,planarityMode,planeRestoringStrength,angleRestoringStrength,threeDConditions,benchmarkPreset,threeDConstraintStrengths,faceComposition,generation.primaryParameterMode(),generation.surfaceTopologyAssumption(),generation.parameterDerivation());}
}
