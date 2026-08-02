package remframework.geometry.discovery.configuration;

import remframework.geometry.discovery.GeometryConfigurationStatusCode;
import remframework.geometry.discovery.experiment.*;

import java.util.*;

/** Normalizes authoritative surface parameters using exact integer arithmetic. */
public final class ParameterDerivation {
    private ParameterDerivation() { }

    public static ParameterDerivationReport derive(PrimaryParameterMode mode,Integer nodeCount,
            Long faceCount,ExactFaceCounts exactFaces,int degree,ExperimentProfile profile,
            FaceComposition faceConstraint,SurfaceTopologyAssumption surface) {
        mode=mode==null?PrimaryParameterMode.NODE_COUNT:mode;
        surface=surface==null?SurfaceTopologyAssumption.SPHERE_LIKE:surface;
        faceConstraint=faceConstraint==null?FaceComposition.discover():faceConstraint;
        Map<String,String> inputs=new LinkedHashMap<>();List<String> formulas=new ArrayList<>();
        List<String> checks=new ArrayList<>();List<GeometryConfigurationStatusCode> reasons=new ArrayList<>();
        formulas.add(profile==ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME
                ?"CONVEX_POLYHEDRAL_VOLUME implies the explicit SPHERE_LIKE boundary assumption (chi=2)."
                :"Surface topology is explicitly selected as "+surface+" (chi="+surface.eulerCharacteristic()+").");
        Long v=null,e=null,f=null,twiceE=null;
        try {
            if(degree<0) reasons.add(GeometryConfigurationStatusCode.INVALID_PRIMARY_PARAMETERS);
            switch(mode) {
                case NODE_COUNT -> {
                    inputs.put("V",String.valueOf(nodeCount));inputs.put("K",String.valueOf(degree));
                    if(nodeCount==null||nodeCount<=0) reasons.add(GeometryConfigurationStatusCode.INVALID_PRIMARY_PARAMETERS);
                    else {v=nodeCount.longValue();twiceE=Math.multiplyExact(v,(long)degree);
                        formulas.add("2E = V*K = "+v+"*"+degree+" = "+twiceE);
                        if((twiceE&1)!=0)reasons.add(GeometryConfigurationStatusCode.NON_INTEGER_DERIVED_EDGE_COUNT);
                        else e=twiceE/2;
                        if(profile!=ExperimentProfile.CLOSED_2D_AREA){f=e==null?null:Math.addExact(Math.subtractExact(2L,v),e);formulas.add("F = 2 - V + E = "+f);}
                    }
                }
                case FACE_COUNT -> {
                    inputs.put("F",String.valueOf(faceCount));inputs.put("K",String.valueOf(degree));f=faceCount;
                    if(faceCount==null||faceCount<=0)reasons.add(GeometryConfigurationStatusCode.INVALID_PRIMARY_PARAMETERS);
                    else if(degree==2)reasons.add(GeometryConfigurationStatusCode.INVALID_PRIMARY_PARAMETERS);
                    else if(degree<3)reasons.add(GeometryConfigurationStatusCode.DEGREE_GRAPH_IMPOSSIBLE);
                    else {long numerator=Math.multiplyExact(2L,Math.subtractExact(f,2L));long denominator=degree-2L;
                        formulas.add("V = 2(F-2)/(K-2) = "+numerator+"/"+denominator);
                        checks.add(numerator+" divisible by "+denominator+": "+(numerator%denominator==0?"PASS":"FAIL"));
                        if(numerator%denominator!=0)reasons.add(GeometryConfigurationStatusCode.NON_INTEGER_DERIVED_VERTEX_COUNT);
                        else {v=numerator/denominator;twiceE=Math.multiplyExact(v,(long)degree);
                            if((twiceE&1)!=0)reasons.add(GeometryConfigurationStatusCode.NON_INTEGER_DERIVED_EDGE_COUNT);else e=twiceE/2;}
                    }
                }
                case FACE_COMPOSITION -> {
                    inputs.put("F_m",String.valueOf(exactFaces==null?Map.of():exactFaces.countsByEdgeCount()));
                    inputs.put("K",String.valueOf(degree));ExactFaceCounts values=exactFaces==null?new ExactFaceCounts(Map.of()):exactFaces;
                    long totalFaces=0,totalIncidences=0;boolean positive=false;
                    for(var entry:values.countsByEdgeCount().entrySet()){
                        if(entry.getKey()<3||entry.getValue()<0)reasons.add(GeometryConfigurationStatusCode.INVALID_PRIMARY_PARAMETERS);
                        totalFaces=Math.addExact(totalFaces,entry.getValue());
                        totalIncidences=Math.addExact(totalIncidences,Math.multiplyExact((long)entry.getKey(),entry.getValue()));
                        positive|=entry.getValue()>0;
                    }
                    f=totalFaces;twiceE=totalIncidences;formulas.add("F = sum(F_m) = "+f);
                    formulas.add("2E = sum(m*F_m) = "+twiceE);
                    if(!positive)reasons.add(GeometryConfigurationStatusCode.INVALID_PRIMARY_PARAMETERS);
                    if((twiceE&1)!=0)reasons.add(GeometryConfigurationStatusCode.NON_INTEGER_DERIVED_EDGE_COUNT);else e=twiceE/2;
                    if(degree<=0)reasons.add(GeometryConfigurationStatusCode.DEGREE_GRAPH_IMPOSSIBLE);
                    else {checks.add(twiceE+" divisible by K="+degree+": "+(twiceE%degree==0?"PASS":"FAIL"));
                        if(twiceE%degree!=0)reasons.add(GeometryConfigurationStatusCode.NON_INTEGER_DERIVED_VERTEX_COUNT);else v=twiceE/degree;}
                }
            }
        } catch(ArithmeticException overflow) { reasons.add(GeometryConfigurationStatusCode.INVALID_PRIMARY_PARAMETERS);formulas.add("Integer overflow detected during exact arithmetic."); }
        if(v!=null&&(v<=0||v>Integer.MAX_VALUE))reasons.add(GeometryConfigurationStatusCode.INVALID_PRIMARY_PARAMETERS);
        if(e!=null&&e<=0)reasons.add(GeometryConfigurationStatusCode.NON_INTEGER_DERIVED_EDGE_COUNT);
        if(f!=null&&f<=0&&profile!=ExperimentProfile.CLOSED_2D_AREA)reasons.add(GeometryConfigurationStatusCode.INVALID_PRIMARY_PARAMETERS);
        if(v!=null&&(degree>=v||degree<0))reasons.add(GeometryConfigurationStatusCode.DEGREE_GRAPH_IMPOSSIBLE);
        if(v!=null&&e!=null&&f!=null){long euler=Math.addExact(Math.subtractExact(v,e),f);
            formulas.add("V - E + F = "+v+" - "+e+" + "+f+" = "+euler);
            if(euler!=surface.eulerCharacteristic())reasons.add(GeometryConfigurationStatusCode.EULER_CHARACTERISTIC_MISMATCH);}
        String faceIncidence="NOT_APPLICABLE";
        if(faceConstraint.mode()==FaceCompositionMode.UNIFORM_FACE_SIZE&&e!=null&&f!=null){long mF;
            try{mF=Math.multiplyExact((long)faceConstraint.commonFaceEdgeCount(),f);
                long required=Math.multiplyExact(2L,e);faceIncidence="mF="+mF+", 2E="+required+": "+(mF==required?"PASS":"FAIL");
                if(mF!=required){reasons.add(GeometryConfigurationStatusCode.FACE_COMPOSITION_ARITHMETICALLY_IMPOSSIBLE);reasons.add(GeometryConfigurationStatusCode.FACE_INCIDENCE_MISMATCH);}
            }catch(ArithmeticException overflow){reasons.add(GeometryConfigurationStatusCode.INVALID_PRIMARY_PARAMETERS);faceIncidence="Integer overflow: FAIL";}
            int requiredFaceSize=faceConstraint.commonFaceEdgeCount();
            if(mode==PrimaryParameterMode.FACE_COMPOSITION&&exactFaces!=null&&exactFaces.countsByEdgeCount().entrySet().stream()
                    .anyMatch(entry->entry.getValue()>0&&entry.getKey()!=requiredFaceSize)){
                reasons.add(GeometryConfigurationStatusCode.FACE_COMPOSITION_ARITHMETICALLY_IMPOSSIBLE);
                reasons.add(GeometryConfigurationStatusCode.FACE_INCIDENCE_MISMATCH);
                faceIncidence += "; exact face counts contain a size other than the uniform constraint: FAIL";
            }
        } else if(mode==PrimaryParameterMode.FACE_COMPOSITION&&twiceE!=null) faceIncidence="sum(m*F_m) = 2E = "+twiceE+": PASS";
        reasons=deduplicate(reasons);boolean feasible=reasons.isEmpty();
        if(feasible)reasons.add(GeometryConfigurationStatusCode.ARITHMETICALLY_FEASIBLE);
        boolean specialized=feasible&&v!=null&&f!=null&&surface==SurfaceTopologyAssumption.SPHERE_LIKE
                &&faceConstraint.mode()==FaceCompositionMode.UNIFORM_FACE_SIZE&&degree==3;
        boolean generation=feasible&&v!=null&&(degree==2?v<=10_000:v<=ConfigurationGenerator.MAX_EXHAUSTIVE_N||specialized);
        reasons.add(generation?GeometryConfigurationStatusCode.ELIGIBLE_FOR_GENERATION:feasible
                ?GeometryConfigurationStatusCode.GENERATION_STRATEGY_UNAVAILABLE:reasons.getFirst());
        reasons=deduplicate(reasons);
        String strategy=degree==2?"DIRECT_CYCLE_CONSTRUCTION":v!=null&&v>ConfigurationGenerator.MAX_EXHAUSTIVE_N&&specialized
                ?"UNIFORM_SPHERICAL_FACE_GENERATION":"EXHAUSTIVE_BACKTRACKING";
        String explanation=(feasible?"Arithmetic feasibility: PASS":"Arithmetic feasibility: FAIL")
                +". Generation capability: "+(generation?"AVAILABLE":"UNAVAILABLE")+". Reasons: "+reasons;
        return new ParameterDerivationReport(mode,surface,inputs,v,e,f,surface.eulerCharacteristic(),
                twiceE==null?"UNAVAILABLE":"2E = V*K = "+twiceE,faceIncidence,checks,feasible,
                generation,strategy,reasons,formulas,explanation);
    }

    private static List<GeometryConfigurationStatusCode> deduplicate(List<GeometryConfigurationStatusCode> values){return new ArrayList<>(new LinkedHashSet<>(values));}
}
