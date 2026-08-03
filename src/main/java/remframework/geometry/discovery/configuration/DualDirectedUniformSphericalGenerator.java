package remframework.geometry.discovery.configuration;

import remframework.geometry.discovery.*;
import remframework.geometry.discovery.experiment.FaceComposition;
import remframework.geometry.discovery.graph.canonical.Canonicalizer;
import java.time.Instant;
import java.util.*;

/** Generates a uniform spherical surface through a complete supported dual class. */
public final class DualDirectedUniformSphericalGenerator {
    public Outcome generate(ConfigurationRequest request, NormalizedGraphRequest normalized, GenerationExecution execution) {
        ConfigurationRequest dualRequest=new ConfigurationRequest(
                Math.toIntExact(normalized.expectedFaceCount()),3,request.intendedDimension(),request.symmetryFilter(),
                request.maximumConfigurations(),request.enumerationTimeLimit(),
                GenerationStrategy.UNIFORM_SPHERICAL_FACE_GENERATION.name(),request.outputDirectory(),
                request.experimentProfile(),request.maximumDirectCycleNodes(),
                FaceComposition.uniform(normalized.exactDegree()),PrimaryParameterMode.NODE_COUNT,
                Math.toIntExact(normalized.expectedFaceCount()),null,null,request.surfaceTopologyAssumption(),null);
        NormalizedGraphRequest dualNormalized=dualRequest.normalizedGraphRequest();
        UniformSphericalFaceGenerator.Outcome dual= new UniformSphericalFaceGenerator().generate(dualRequest,dualNormalized,execution);
        List<Configuration> retained=new ArrayList<>();Set<String> canonicalIds=new HashSet<>();Map<String,Long> rejections=new TreeMap<>();
        CombinatorialSurfaceCanonicalizer canonicalizer=new CombinatorialSurfaceCanonicalizer();
        for(Configuration dualConfiguration:dual.configurations()) {
            String encoded=dualConfiguration.provenance().get("combinatorialFaces");
            if(encoded==null){rejections.merge("DUAL_FACE_COMPLEX_INVALID",1L,Long::sum);continue;}
            try {
                CombinatorialFaceComplex dualSurface=CombinatorialFaceComplex.decode(dualConfiguration.nodeCount(),encoded);
                CombinatorialFaceComplex primal=UniformSphericalFaceGenerator.dualizeSurface(dualSurface);
                CombinatorialSurfaceValidator.Validation validation=new CombinatorialSurfaceValidator().validate(primal,
                        Math.toIntExact(normalized.expectedEdgeCount()),Math.toIntExact(normalized.expectedFaceCount()),
                        normalized.exactDegree(),normalized.faceComposition().commonFaceEdgeCount(),normalized.surfaceTopologyAssumption().eulerCharacteristic());
                if(!validation.valid()){validation.failures().forEach(f->rejections.merge("PRIMAL_VALIDATION_FAILED:"+f,1L,Long::sum));continue;}
                CombinatorialSurfaceCanonicalizer.Result canonical=canonicalizer.canonicalize(primal);
                String graphLabel=canonical.code();if(!canonicalIds.add(graphLabel)){rejections.merge("DUPLICATE_ISOMORPH",1L,Long::sum);continue;}
                CombinatorialFaceComplex canonicalPrimal=canonicalRelabel(primal,canonical.vertexLabels());
                if(!request.symmetryFilter().accepts(canonical.automorphismCount())){rejections.merge("SYMMETRY_FILTERED",1L,Long::sum);continue;}
                GeometryConfigurationDiagnostic diagnostic=GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.COMPLETE,GeometryConfigurationStatusCode.GENERATED_VIA_DUALITY,"Generated via duality","A primal uniform spherical face complex passed independent validation after deterministic dualization.");
                Map<String,String> provenance=new LinkedHashMap<>();provenance.put("constructionMethod",ConstructionMethod.DUAL_TRIANGULATED_SURFACE.name());provenance.put("generationStrategy",GenerationStrategy.DUAL_DIRECTED_UNIFORM_SPHERICAL_GENERATION.name());provenance.put("dualGenerator",GenerationStrategy.UNIFORM_SPHERICAL_FACE_GENERATION.name());provenance.put("dualParameters","V="+dualNormalized.nodeCount()+",E="+dualNormalized.expectedEdgeCount()+",F="+dualNormalized.expectedFaceCount()+",K="+dualNormalized.exactDegree()+",m="+dualNormalized.faceComposition().commonFaceEdgeCount());provenance.put("dualSearchComplete",Boolean.toString(dual.diagnostics().completeness()==GenerationCompleteness.COMPLETE_FOR_SELECTED_PARAMETER_CLASS));provenance.put("combinatorialFaces",canonicalPrimal.encode());
                retained.add(new Configuration(new ConfigurationId("cfg-"+shortHash(graphLabel)),canonicalPrimal.vertexCount(),normalized.exactDegree(),canonicalPrimal.edges(),null,graphLabel,graphLabel,canonical.automorphismCount(),canonical.vertexTransitive()?GraphSymmetryClassification.HIGHLY_SYMMETRIC:canonical.automorphismCount()>1?GraphSymmetryClassification.SYMMETRIC:GraphSymmetryClassification.ASYMMETRIC,true,retained.size()+1,GenerationStrategy.DUAL_DIRECTED_UNIFORM_SPHERICAL_GENERATION.name(),dual.diagnostics().completeness()==GenerationCompleteness.COMPLETE_FOR_SELECTED_PARAMETER_CLASS,GeometryConfigurationStatus.of(diagnostic),List.of(diagnostic),Instant.now(),provenance));
            } catch(RuntimeException failure){rejections.merge("PRIMAL_DUALIZATION_FAILED",1L,Long::sum);}
        }
        boolean complete=dual.diagnostics().completeness()==GenerationCompleteness.COMPLETE_FOR_SELECTED_PARAMETER_CLASS;
        Map<String,Long> pruning=new TreeMap<>(dual.diagnostics().pruningCounts());rejections.forEach((k,v)->pruning.merge(k,v,Long::sum));
        GenerationDiagnostics diagnostics=new GenerationDiagnostics(GenerationStrategy.DUAL_DIRECTED_UNIFORM_SPHERICAL_GENERATION,"1",ConstructionMethod.DUAL_TRIANGULATED_SURFACE,complete?GenerationCompleteness.COMPLETE_FOR_SELECTED_PARAMETER_CLASS:GenerationCompleteness.INCOMPLETE_SEARCH_LIMIT_REACHED,dual.diagnostics().partialStatesExamined(),pruning.values().stream().mapToLong(Long::longValue).sum(),dual.diagnostics().completedCandidates(),dual.diagnostics().duplicateCandidates(),retained.size(),pruning,dual.diagnostics().searchLimits(),dual.diagnostics().workerCount(),complete?"Dual class complete; all retained surfaces passed primal validation.":"Dual generation incomplete; primal result remains incomplete.");
        return new Outcome(List.copyOf(retained),diagnostics,dual.configurations().size(),rejections);
    }
    private static CombinatorialFaceComplex canonicalRelabel(CombinatorialFaceComplex surface,int[] labels){List<List<Integer>> faces=surface.faces().stream().map(face->canonicalCycle(face.stream().map(vertex->labels[vertex]).toList())).toList();return new CombinatorialFaceComplex(surface.vertexCount(),faces.stream().sorted(DualDirectedUniformSphericalGenerator::compare).toList());}
    private static List<Integer> canonicalCycle(List<Integer> cycle){List<Integer> best=null;for(boolean reverse:new boolean[]{false,true})for(int start=0;start<cycle.size();start++){List<Integer> x=new ArrayList<>();for(int i=0;i<cycle.size();i++)x.add(cycle.get(reverse?Math.floorMod(start-i,cycle.size()):(start+i)%cycle.size()));if(best==null||compare(x,best)<0)best=x;}return List.copyOf(best);}
    private static int compare(List<Integer>a,List<Integer>b){for(int i=0;i<Math.min(a.size(),b.size());i++){int c=Integer.compare(a.get(i),b.get(i));if(c!=0)return c;}return Integer.compare(a.size(),b.size());}
    private static String shortHash(String value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0,12);}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public record Outcome(List<Configuration> configurations,GenerationDiagnostics diagnostics,int dualConfigurations,Map<String,Long> rejectionCounts){public Outcome{configurations=List.copyOf(configurations);rejectionCounts=Map.copyOf(rejectionCounts);}}
}
