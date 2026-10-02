package remframework.geometry.discovery;

import java.util.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.experiment.*;
import remframework.geometry.discovery.realization.session.*;

/** Deterministic, headless diagnostic for isolated manual Stage 2 forces. */
public final class ManualForceIsolationDiagnostic {
    private ManualForceIsolationDiagnostic() { }
    public record Observation(String configurationId,long seed,ManualConstraint constraint,int iterations,
            double initialObjective,double finalObjective,double initialRatio,double finalRatio,
            double maximumDisplacement,ManualExecutionState state,String classification) { }
    public static List<Observation> run(String configurationId,int iterations,long... seeds){
        Configuration configuration=configuration(configurationId);List<Observation> result=new ArrayList<>();
        for(long seed:seeds)for(ManualConstraint constraint:List.of(ManualConstraint.EDGE_LENGTH,ManualConstraint.FACE_PLANARITY,ManualConstraint.FACE_ANGLE,ManualConstraint.FACE_DISTANCE,ManualConstraint.NORMAL_ALIGNMENT,ManualConstraint.DIHEDRAL_ANGLE)){
            TestRequest request=new TestRequest(configuration.id(),GeometryEmbeddingDimension.THREE_D,1,seed,1,null,GeometrySymmetryPolicy.MEASURE_ONLY,iterations,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME,null,null,null,1e-3,1e-7,1e-9,1e-6,null,0,0,null,BenchmarkPreset.NONE,ThreeDConstraintStrengths.defaults()).withContinuation(ContinuationSettings.manual());
            IncrementalSession session=new IncrementalSession(configuration,request);List<org.joml.Vector3d> before=session.currentCoordinates();session.setManualRelaxation(constraint,true);double initial=session.objectiveDiagnostics().totalObjective();double initialRatio=ratio(session,constraint);session.step(iterations);double finalObjective=session.objectiveDiagnostics().totalObjective();double finalRatio=ratio(session,constraint);double displacement=0;for(int i=0;i<before.size();i++)displacement=Math.max(displacement,before.get(i).distance(session.currentCoordinates().get(i)));result.add(new Observation(configurationId,seed,constraint,session.iteration(),initial,finalObjective,initialRatio,finalRatio,displacement,session.manualExecutionState(),classify(initial,finalObjective,initialRatio,finalRatio,displacement)));}
        return List.copyOf(result);
    }
    public static void main(String[] args){for(String id:List.of("cfg-b10320c7778b","cfg-c83f892b5931","cfg-8d94d51e4538"))for(Observation o:run(id,1_000,42,43,44,45,46))System.out.printf(Locale.ROOT,"%s seed=%d force=%s objective %.6g -> %.6g ratio %.6g -> %.6g displacement %.6g class=%s%n",o.configurationId(),o.seed(),o.constraint(),o.initialObjective(),o.finalObjective(),o.initialRatio(),o.finalRatio(),o.maximumDisplacement(),o.classification());}
    private static double ratio(IncrementalSession s,ManualConstraint c){return s.manualConstraintStates().stream().filter(x->x.constraint()==c).mapToDouble(ManualConstraintState::normalizedRatio).findFirst().orElse(Double.NaN);}
    private static String classify(double oi,double of,double ri,double rf,double displacement){if(!Double.isFinite(of)||!Double.isFinite(rf))return "NUMERICALLY_UNSTABLE";if(displacement<1e-12)return "GRADIENT_NEAR_ZERO_OR_UNDISPATCHED";if(of<oi&&rf<ri)return "EFFECTIVE";if(of>oi||rf>ri)return "DIVERGING_OR_CONFLICTING";return "PLATEAUED";}
    private static Configuration configuration(String id){ConfigurationRequest request=id.equals("cfg-b10320c7778b")?ConfigurationRequest.byFaceCount(12,3,GeometryEmbeddingDimension.THREE_D,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME).withFaceComposition(FaceComposition.uniform(5)):ConfigurationRequest.exhaustive(id.equals("cfg-c83f892b5931")?8:4,3,GeometryEmbeddingDimension.THREE_D);return new ConfigurationGenerator().generate(request).configurations().stream().filter(c->c.id().toString().equals(id)).findFirst().orElseThrow();}
}
