package remframework.geometry.discovery.configuration;

import remframework.geometry.discovery.*;
import remframework.geometry.discovery.graph.canonical.Canonicalizer;
import remframework.geometry.discovery.graph.symmetry.AutomorphismAnalyzer;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deterministic exhaustive generation of connected simple regular graphs.
 * The implementation is intentionally bounded to N &lt;= 8; larger requests are
 * reported incomplete instead of being sampled.
 */
public final class ConfigurationGenerator {
    public static final int MAX_EXHAUSTIVE_N = 8;
    private final GraphIsomorphismAnalyzer isomorphism = new GraphIsomorphismAnalyzer();
    private final Canonicalizer canonicalizer = new Canonicalizer();
    private final AutomorphismAnalyzer automorphisms = new AutomorphismAnalyzer();

    public GenerationResult generate(ConfigurationRequest request) {
        return generate(request,GenerationExecution.defaults());
    }

    public GenerationResult generate(ConfigurationRequest request,GenerationExecution execution) {
        long started=System.nanoTime();
        if(request==null)return result(null,List.of(),0,0,started,false,validate(null));
        if(request!=null&&request.primaryParameterMode()==PrimaryParameterMode.NODE_COUNT) {
            GeometryConfigurationStatus invalid=validate(request);
            if(invalid!=null)return result(request,List.of(),0,0,started,false,invalid);
        }
        if(request!=null&&!request.parameterDerivation().arithmeticFeasible()) {
            var report=request.parameterDerivation();
            var d=GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.REQUEST_VALIDATION,
                    report.primaryStatus(),"Primary-parameter preflight failed",report.explanation());
            return result(request,List.of(),0,0,started,false,GeometryConfigurationStatus.of(d));
        }
        NormalizedGraphRequest normalized=request.normalizedGraphRequest();
        GeometryConfigurationStatus invalid=request!=null&&request.primaryParameterMode()==PrimaryParameterMode.NODE_COUNT
                ?null:validate(request);
        if(invalid!=null) return result(request,List.of(),0,0,started,false,invalid);
        if(normalized.exactDegree()==2&&normalized.nodeCount()>=3)
            return directCycle(request,started);
        if(normalized.selectedStrategy()==GenerationStrategy.UNIFORM_SPHERICAL_FACE_GENERATION)
            return uniformSpherical(request,normalized,started,execution);
        if(normalized.selectedStrategy()==GenerationStrategy.DUAL_DIRECTED_UNIFORM_SPHERICAL_GENERATION)
            return dualDirectedUniformSpherical(request,normalized,started,execution);
        if(normalized.nodeCount()>MAX_EXHAUSTIVE_N) {
            var d=GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GRAPH_GENERATION,
                    GeometryConfigurationStatusCode.ENUMERATION_LIMIT_REACHED,
                    "Exhaustive range exceeded", "Exact enumeration is supported through N = "
                            +MAX_EXHAUSTIVE_N+"; no sampling was performed.");
            return result(request,List.of(),0,0,started,false,GeometryConfigurationStatus.of(d));
        }
        long deadline=System.nanoTime()+(long)(request.enumerationTimeLimit().toNanos());
        List<List<DiscoveryConnection>> representatives=new ArrayList<>();
        long[] examined={0}, duplicates={0}; AtomicBoolean limited=new AtomicBoolean();
        enumerate(normalized.nodeCount(),normalized.exactDegree(),new boolean[normalized.nodeCount()][normalized.nodeCount()],
                new int[normalized.nodeCount()],representatives,examined,duplicates,deadline,
                request.maximumConfigurations(),limited);
        List<Configuration> configurations=new ArrayList<>();
        record Classified(String label,List<DiscoveryConnection> edges,
                          AutomorphismAnalyzer.Result symmetry) {}
        List<Classified> classified=new ArrayList<>();
        for(List<DiscoveryConnection> edges:representatives) {
            var symmetry=automorphisms.analyze(normalized.nodeCount(),edges);
            if(request.symmetryFilter().accepts(symmetry.groupSize()))
                classified.add(new Classified(canonicalizer.canonicalLabel(normalized.nodeCount(),edges),edges,symmetry));
        }
        classified.sort(Comparator.comparing(Classified::label));
        boolean complete=!limited.get(); Instant created=Instant.now();
        for(int i=0;i<classified.size();i++) {
            Classified c=classified.get(i); String hash=shortHash(c.label());
            var diagnostic=GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.COMPLETE,
                    GeometryConfigurationStatusCode.GENERATED,"Topologically generated",
                    "Connected simple "+request.exactDegree()+"-regular graph retained as one isomorphism class.");
            var status=GeometryConfigurationStatus.of(diagnostic);
            configurations.add(new Configuration(new ConfigurationId("cfg-"+hash),
                    normalized.nodeCount(),normalized.exactDegree(),c.edges(),null,c.label(),hash,
                    c.symmetry().groupSize(),c.symmetry().classification(),true,i+1,
                    request.generationStrategy(),complete,status,List.of(diagnostic),created,
                    Map.of("embeddingDimension",request.intendedDimension().name())));
        }
        var code=complete?GeometryConfigurationStatusCode.ENUMERATION_COMPLETE
                :GeometryConfigurationStatusCode.RESOURCE_LIMIT_REACHED;
        var d=complete?GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.COMPLETE,code,
                "Enumeration complete","All supported labeled candidates were examined and collapsed by exact isomorphism.")
                :GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GRAPH_GENERATION,code,
                "Enumeration incomplete","The configured time or retained-configuration limit was reached.");
        return result(request,configurations,examined[0],duplicates[0],started,complete,
                GeometryConfigurationStatus.of(d));
    }

    private GenerationResult uniformSpherical(ConfigurationRequest request,
            NormalizedGraphRequest normalized,long started,GenerationExecution execution){
        UniformSphericalFaceGenerator.Outcome generated=new UniformSphericalFaceGenerator().generate(request,normalized,execution);
        GenerationDiagnostics diagnostics=generated.diagnostics();boolean complete=diagnostics.completeness()==GenerationCompleteness.COMPLETE_FOR_SELECTED_PARAMETER_CLASS;
        GeometryConfigurationStatusCode code=complete?GeometryConfigurationStatusCode.COMPLETE_FOR_SELECTED_PARAMETER_CLASS
                :diagnostics.completeness()==GenerationCompleteness.INCOMPLETE_SEARCH_LIMIT_REACHED
                ?GeometryConfigurationStatusCode.INCOMPLETE_SEARCH_LIMIT_REACHED:GeometryConfigurationStatusCode.GENERATION_FAILED;
        GeometryConfigurationDiagnostic diagnostic=complete
                ?GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.COMPLETE,code,"Uniform spherical generation complete",diagnostics.explanation())
                :GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GRAPH_GENERATION,code,"Uniform spherical generation incomplete",diagnostics.explanation());
        List<Configuration> configurations=generated.configurations();int symmetric=(int)configurations.stream().filter(c->c.automorphismGroupSize()>1).count();
        Metrics metrics=new Metrics(diagnostics.partialStatesExamined(),configurations.size(),diagnostics.duplicateCandidates(),symmetric,configurations.size()-symmetric,generated.elapsedMilliseconds(),complete);
        String setId="set-"+shortHash(normalized.nodeCount()+":"+normalized.exactDegree()+":"+request.intendedDimension()+":"+request.symmetryFilter()+":"+normalized.faceComposition());
        return new GenerationResult(setId,request,configurations,metrics,GeometryConfigurationStatus.of(diagnostic),diagnostics);
    }

    private GenerationResult dualDirectedUniformSpherical(ConfigurationRequest request,NormalizedGraphRequest normalized,long started,GenerationExecution execution){
        DualDirectedUniformSphericalGenerator.Outcome generated=new DualDirectedUniformSphericalGenerator().generate(request,normalized,execution);
        GenerationDiagnostics diagnostics=generated.diagnostics();boolean complete=diagnostics.completeness()==GenerationCompleteness.COMPLETE_FOR_SELECTED_PARAMETER_CLASS;
        GeometryConfigurationStatusCode code=generated.configurations().isEmpty()?GeometryConfigurationStatusCode.PRIMAL_VALIDATION_FAILED:complete?GeometryConfigurationStatusCode.ELIGIBLE_FOR_GENERATION_VIA_DUALITY:GeometryConfigurationStatusCode.DUAL_GENERATION_INCOMPLETE;
        String explanation=diagnostics.explanation()+" Dual configurations="+generated.dualConfigurations()+", retained primal configurations="+generated.configurations().size()+", rejections="+generated.rejectionCounts();
        GeometryConfigurationDiagnostic diagnostic=complete&& !generated.configurations().isEmpty()?GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.COMPLETE,code,"Dual-directed spherical generation",explanation):GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GRAPH_GENERATION,code,"Dual-directed spherical generation",explanation);
        Metrics metrics=new Metrics(diagnostics.partialStatesExamined(),generated.configurations().size(),diagnostics.duplicateCandidates(),(int)generated.configurations().stream().filter(c->c.automorphismGroupSize()>1).count(),(int)generated.configurations().stream().filter(c->c.automorphismGroupSize()<=1).count(),generated.diagnostics().searchLimits()==null?0:0,complete);
        String setId="set-"+shortHash(normalized.nodeCount()+":"+normalized.exactDegree()+":"+request.intendedDimension()+":"+request.symmetryFilter()+":"+normalized.faceComposition()+":dual");
        return new GenerationResult(setId,request,generated.configurations(),metrics,GeometryConfigurationStatus.of(diagnostic),diagnostics);
    }

    private GenerationResult directCycle(ConfigurationRequest request,long started){
        if(request.nodeCount()>request.maximumDirectCycleNodes()){
            var d=GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GRAPH_GENERATION,
                    GeometryConfigurationStatusCode.RESOURCE_LIMIT_REACHED,"Direct cycle safety limit exceeded",
                    "Direct cycle construction is configured through N = "+request.maximumDirectCycleNodes()+".");
            return result(request,List.of(),0,0,started,false,GeometryConfigurationStatus.of(d));
        }
        ConfigurationRequest effectiveRequest=new ConfigurationRequest(request.nodeCount(),request.exactDegree(),
                request.intendedDimension(),request.symmetryFilter(),request.maximumConfigurations(),
                request.enumerationTimeLimit(),"DIRECT_CYCLE_CONSTRUCTION",request.outputDirectory(),
                request.experimentProfile(),request.maximumDirectCycleNodes(),request.faceComposition(),
                request.primaryParameterMode(),request.authoritativeNodeCount(),
                request.authoritativeFaceCount(),request.exactFaceCounts(),
                request.surfaceTopologyAssumption(),null);
        List<DiscoveryConnection> edges=new ArrayList<>();
        for(int node=0;node<request.nodeCount()-1;node++)edges.add(new DiscoveryConnection(node,node+1));
        edges.add(new DiscoveryConnection(request.nodeCount()-1,0));
        String label=request.nodeCount()<=MAX_EXHAUSTIVE_N
                ?canonicalizer.canonicalLabel(request.nodeCount(),edges)
                :CanonicalGraphSignature.of(request.nodeCount(),edges);
        long groupSize=2L*request.nodeCount();
        List<Configuration> configurations=new ArrayList<>();
        if(request.symmetryFilter().accepts(groupSize)){
            String hash=shortHash(label);
            var diagnostic=GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.COMPLETE,
                    GeometryConfigurationStatusCode.GENERATED,"Cycle constructed exactly",
                    "The unique connected simple 2-regular topology C_"+request.nodeCount()+" was constructed directly.");
            configurations.add(new Configuration(new ConfigurationId("cfg-"+hash),request.nodeCount(),2,
                    edges,null,label,hash,groupSize,GraphSymmetryClassification.HIGHLY_SYMMETRIC,true,1,
                    "DIRECT_CYCLE_CONSTRUCTION",true,GeometryConfigurationStatus.of(diagnostic),List.of(diagnostic),
                    Instant.now(),Map.of("embeddingDimension",request.intendedDimension().name(),
                            "vertexTransitive","true","generationStrategy","DIRECT_CYCLE_CONSTRUCTION")));
        }
        var complete=GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.COMPLETE,
                GeometryConfigurationStatusCode.ENUMERATION_COMPLETE,"Direct cycle construction complete",
                "Exactly one non-isomorphic connected simple 2-regular graph exists for N >= 3.");
        return result(effectiveRequest,configurations,1,0,started,true,GeometryConfigurationStatus.of(complete));
    }

    private void enumerate(int n,int k,boolean[][] adjacency,int[] degree,
            List<List<DiscoveryConnection>> representatives,long[] examined,long[] duplicates,
            long deadline,int maximum,AtomicBoolean limited) {
        if(limited.get()) return;
        if(System.nanoTime()>=deadline || representatives.size()>=maximum){limited.set(true);return;}
        int vertex=-1; for(int i=0;i<n;i++) if(degree[i]<k){vertex=i;break;}
        if(vertex<0) {
            List<DiscoveryConnection> edges=edges(adjacency); examined[0]++;
            if(!connected(n,edges)) return;
            for(List<DiscoveryConnection> known:representatives)
                if(isomorphism.areIsomorphic(n,known,edges)){duplicates[0]++;return;}
            representatives.add(edges); return;
        }
        int needed=k-degree[vertex]; List<Integer> available=new ArrayList<>();
        for(int j=vertex+1;j<n;j++) if(!adjacency[vertex][j]&&degree[j]<k) available.add(j);
        if(available.size()<needed) return;
        final int selectedVertex=vertex;
        choose(available,0,needed,new int[needed],0,choice->{
            for(int target:choice){adjacency[selectedVertex][target]=adjacency[target][selectedVertex]=true;degree[selectedVertex]++;degree[target]++;}
            enumerate(n,k,adjacency,degree,representatives,examined,duplicates,deadline,maximum,limited);
            for(int target:choice){adjacency[selectedVertex][target]=adjacency[target][selectedVertex]=false;degree[selectedVertex]--;degree[target]--;}
        });
    }
    private interface ChoiceConsumer { void accept(int[] values); }
    private void choose(List<Integer> values,int from,int needed,int[] selected,int depth,ChoiceConsumer consumer){
        if(depth==needed){consumer.accept(selected.clone());return;}
        for(int i=from;i<=values.size()-(needed-depth);i++){selected[depth]=values.get(i);choose(values,i+1,needed,selected,depth+1,consumer);}
    }
    private List<DiscoveryConnection> edges(boolean[][] graph){List<DiscoveryConnection> r=new ArrayList<>();for(int i=0;i<graph.length;i++)for(int j=i+1;j<graph.length;j++)if(graph[i][j])r.add(new DiscoveryConnection(i,j));return r;}
    private boolean connected(int n,List<DiscoveryConnection> edges){if(n==1)return true;List<List<Integer>> a=Configuration.adjacency(n,edges);boolean[] seen=new boolean[n];ArrayDeque<Integer> q=new ArrayDeque<>();seen[0]=true;q.add(0);int count=1;while(!q.isEmpty())for(int v:a.get(q.remove()))if(!seen[v]){seen[v]=true;count++;q.add(v);}return count==n;}
    private GeometryConfigurationStatus validate(ConfigurationRequest r){
        GeometryConfigurationDiagnostic d=null;
        if(r==null)d=GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.REQUEST_VALIDATION,GeometryConfigurationStatusCode.INVALID_PARAMETERS,"Request missing","A configuration request is required.");
        else if(r.nodeCount()<1)d=GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.REQUEST_VALIDATION,GeometryConfigurationStatusCode.INVALID_NODE_COUNT,"Invalid node count","N must be at least 1.");
        else if(r.exactDegree()<0||r.exactDegree()>=r.nodeCount())d=GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.REQUEST_VALIDATION,GeometryConfigurationStatusCode.INVALID_DEGREE,"Invalid exact degree","K must satisfy 0 <= K < N.");
        else if((long)r.nodeCount()*r.exactDegree()%2!=0)d=GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.REQUEST_VALIDATION,GeometryConfigurationStatusCode.DEGREE_SUM_ODD,"Odd degree sum","N * K must be even for an undirected graph.");
        else if(r.nodeCount()>1&&(r.exactDegree()==0||r.exactDegree()==1&&r.nodeCount()!=2))d=GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.REQUEST_VALIDATION,GeometryConfigurationStatusCode.CONNECTED_REGULAR_GRAPH_IMPOSSIBLE,"Connected regular graph impossible","The requested N and K cannot form a connected K-regular graph.");
        else if(r.maximumConfigurations()<1||r.enumerationTimeLimit()==null||r.enumerationTimeLimit().isZero()||r.enumerationTimeLimit().isNegative())d=GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.REQUEST_VALIDATION,GeometryConfigurationStatusCode.INVALID_PARAMETERS,"Invalid resource limits","Maximum configurations and enumeration time must be positive and finite.");
        return d==null?null:GeometryConfigurationStatus.of(d);
    }
    private GenerationResult result(ConfigurationRequest r,List<Configuration> c,long examined,long dup,long started,boolean complete,GeometryConfigurationStatus status){int sym=(int)c.stream().filter(x->x.automorphismGroupSize()>1).count();int asym=c.size()-sym;long elapsed=(System.nanoTime()-started)/1_000_000;String setId="set-"+shortHash(r==null?"invalid":r.nodeCount()+":"+r.exactDegree()+":"+r.intendedDimension()+":"+r.symmetryFilter()+":"+r.faceComposition());return new GenerationResult(setId,r,c,new Metrics(examined,c.size(),dup,sym,asym,elapsed,complete),status);}
    private static String shortHash(String value){
        try {
            byte[] digest=java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest).substring(0,12);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
