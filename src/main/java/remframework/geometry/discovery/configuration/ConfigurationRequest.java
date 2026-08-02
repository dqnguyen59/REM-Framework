package remframework.geometry.discovery.configuration;
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

import java.nio.file.Path;
import java.time.Duration;
import remframework.geometry.discovery.experiment.ExperimentProfile;
import remframework.geometry.discovery.experiment.FaceComposition;

public record ConfigurationRequest(int nodeCount, int exactDegree,
        GeometryEmbeddingDimension intendedDimension, GraphSymmetryFilter symmetryFilter,
        int maximumConfigurations, Duration enumerationTimeLimit,
        String generationStrategy, Path outputDirectory, ExperimentProfile experimentProfile,
        int maximumDirectCycleNodes, FaceComposition faceComposition,
        PrimaryParameterMode primaryParameterMode, Integer authoritativeNodeCount,
        Long authoritativeFaceCount, ExactFaceCounts exactFaceCounts,
        SurfaceTopologyAssumption surfaceTopologyAssumption,
        ParameterDerivationReport parameterDerivation) {
    public ConfigurationRequest {
        intendedDimension = intendedDimension == null ? GeometryEmbeddingDimension.THREE_D : intendedDimension;
        symmetryFilter = symmetryFilter == null ? GraphSymmetryFilter.BOTH : symmetryFilter;
        generationStrategy = generationStrategy == null || generationStrategy.isBlank()
                ? "EXHAUSTIVE_BACKTRACKING" : generationStrategy;
        experimentProfile = experimentProfile == null
                ? (intendedDimension == GeometryEmbeddingDimension.TWO_D
                    ? ExperimentProfile.CLOSED_2D_AREA : ExperimentProfile.CLOSED_3D_VOLUME)
                : experimentProfile;
        maximumDirectCycleNodes=maximumDirectCycleNodes>0?maximumDirectCycleNodes:10_000;
        faceComposition=faceComposition==null?FaceComposition.discover():faceComposition;
        primaryParameterMode=primaryParameterMode==null?PrimaryParameterMode.NODE_COUNT:primaryParameterMode;
        surfaceTopologyAssumption=surfaceTopologyAssumption==null
                ?SurfaceTopologyAssumption.SPHERE_LIKE:surfaceTopologyAssumption;
        if(primaryParameterMode==PrimaryParameterMode.NODE_COUNT&&authoritativeNodeCount==null)
            authoritativeNodeCount=nodeCount;
        exactFaceCounts=exactFaceCounts==null?new ExactFaceCounts(java.util.Map.of()):exactFaceCounts;
        parameterDerivation=ParameterDerivation.derive(primaryParameterMode,authoritativeNodeCount,
                authoritativeFaceCount,exactFaceCounts,exactDegree,experimentProfile,faceComposition,
                surfaceTopologyAssumption);
        if(parameterDerivation.derivedVertexCount()!=null
                &&parameterDerivation.derivedVertexCount()>0
                &&parameterDerivation.derivedVertexCount()<=Integer.MAX_VALUE)
            nodeCount=Math.toIntExact(parameterDerivation.derivedVertexCount());
    }
    public ConfigurationRequest(int nodeCount, int exactDegree,
            GeometryEmbeddingDimension intendedDimension, GraphSymmetryFilter symmetryFilter,
            int maximumConfigurations, Duration enumerationTimeLimit,
            String generationStrategy, Path outputDirectory) {
        this(nodeCount, exactDegree, intendedDimension, symmetryFilter, maximumConfigurations,
                enumerationTimeLimit, generationStrategy, outputDirectory, null,10_000,null,
                null,null,null,null,null,null);
    }
    public ConfigurationRequest(int nodeCount, int exactDegree,
            GeometryEmbeddingDimension intendedDimension, GraphSymmetryFilter symmetryFilter,
            int maximumConfigurations, Duration enumerationTimeLimit,
            String generationStrategy, Path outputDirectory, ExperimentProfile experimentProfile) {
        this(nodeCount,exactDegree,intendedDimension,symmetryFilter,maximumConfigurations,
                enumerationTimeLimit,generationStrategy,outputDirectory,experimentProfile,10_000,null,
                null,null,null,null,null,null);
    }
    public ConfigurationRequest(int nodeCount, int exactDegree,
            GeometryEmbeddingDimension intendedDimension, GraphSymmetryFilter symmetryFilter,
            int maximumConfigurations, Duration enumerationTimeLimit,
            String generationStrategy, Path outputDirectory, ExperimentProfile experimentProfile,
            int maximumDirectCycleNodes) {
        this(nodeCount,exactDegree,intendedDimension,symmetryFilter,maximumConfigurations,
                enumerationTimeLimit,generationStrategy,outputDirectory,experimentProfile,
                maximumDirectCycleNodes,null,null,null,null,null,null,null);
    }
    public static ConfigurationRequest exhaustive(int n, int k,
            GeometryEmbeddingDimension dimension) {
        return new ConfigurationRequest(n, k, dimension, GraphSymmetryFilter.BOTH,
                10_000, Duration.ofSeconds(30), "EXHAUSTIVE_BACKTRACKING", null, null,10_000,null,
                null,null,null,null,null,null);
    }

    public ConfigurationRequest withFaceComposition(FaceComposition value) {
        return new ConfigurationRequest(nodeCount, exactDegree, intendedDimension, symmetryFilter,
                maximumConfigurations, enumerationTimeLimit, generationStrategy, outputDirectory,
                experimentProfile, maximumDirectCycleNodes, value,primaryParameterMode,
                authoritativeNodeCount,authoritativeFaceCount,exactFaceCounts,
                surfaceTopologyAssumption,null);
    }
    public ConfigurationRequest withGenerationStrategy(GenerationStrategy value){return new ConfigurationRequest(
            nodeCount,exactDegree,intendedDimension,symmetryFilter,maximumConfigurations,
            enumerationTimeLimit,value.name(),outputDirectory,experimentProfile,maximumDirectCycleNodes,
            faceComposition,primaryParameterMode,authoritativeNodeCount,authoritativeFaceCount,
            exactFaceCounts,surfaceTopologyAssumption,null);}
    public ConfigurationRequest withEnumerationTimeLimit(Duration value){return new ConfigurationRequest(
            nodeCount,exactDegree,intendedDimension,symmetryFilter,maximumConfigurations,value,
            generationStrategy,outputDirectory,experimentProfile,maximumDirectCycleNodes,
            faceComposition,primaryParameterMode,authoritativeNodeCount,authoritativeFaceCount,
            exactFaceCounts,surfaceTopologyAssumption,null);}

    public NormalizedGraphRequest normalizedGraphRequest() {
        if(!parameterDerivation.arithmeticFeasible())
            throw new IllegalStateException("Cannot normalize an arithmetically invalid request.");
        boolean explicitSpecialized=GenerationStrategy.UNIFORM_SPHERICAL_FACE_GENERATION.name().equals(generationStrategy);
        boolean specializedEligible=surfaceTopologyAssumption==SurfaceTopologyAssumption.SPHERE_LIKE
                &&faceComposition.mode()==remframework.geometry.discovery.experiment.FaceCompositionMode.UNIFORM_FACE_SIZE
                &&parameterDerivation.derivedFaceCount()!=null&&exactDegree==3;
        GenerationStrategy strategy=exactDegree==2?GenerationStrategy.DIRECT_CYCLE_CONSTRUCTION
                :(specializedEligible&&(nodeCount>ConfigurationGenerator.MAX_EXHAUSTIVE_N||explicitSpecialized)
                ?GenerationStrategy.UNIFORM_SPHERICAL_FACE_GENERATION:GenerationStrategy.EXHAUSTIVE_BACKTRACKING);
        UniformSphericalSearchLimits limits=new UniformSphericalSearchLimits(maximumConfigurations,
                Math.max(10_000L,(long)maximumConfigurations*100),
                (int)Math.min(Integer.MAX_VALUE,Math.max(10_000L,(long)maximumConfigurations*10)),enumerationTimeLimit);
        return new NormalizedGraphRequest(nodeCount,exactDegree,
                parameterDerivation.derivedEdgeCount(),parameterDerivation.derivedFaceCount(),
                faceComposition,surfaceTopologyAssumption,primaryParameterMode,parameterDerivation,strategy,limits);
    }

    public static ConfigurationRequest byFaceCount(long faceCount,int k,
            GeometryEmbeddingDimension dimension,ExperimentProfile profile) {
        return new ConfigurationRequest(0,k,dimension,GraphSymmetryFilter.BOTH,10_000,
                Duration.ofSeconds(30),"EXHAUSTIVE_BACKTRACKING",null,profile,10_000,
                null,PrimaryParameterMode.FACE_COUNT,null,faceCount,null,
                SurfaceTopologyAssumption.SPHERE_LIKE,null);
    }

    public static ConfigurationRequest byFaceComposition(ExactFaceCounts counts,int k,
            GeometryEmbeddingDimension dimension,ExperimentProfile profile) {
        return new ConfigurationRequest(0,k,dimension,GraphSymmetryFilter.BOTH,10_000,
                Duration.ofSeconds(30),"EXHAUSTIVE_BACKTRACKING",null,profile,10_000,
                null,PrimaryParameterMode.FACE_COMPOSITION,null,null,counts,
                SurfaceTopologyAssumption.SPHERE_LIKE,null);
    }
}
