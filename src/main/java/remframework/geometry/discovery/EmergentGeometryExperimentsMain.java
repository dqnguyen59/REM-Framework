package remframework.geometry.discovery;
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
import java.time.Duration;
/** Reproducible command-line runner for the Phase 2 initial manual experiments. */
public final class EmergentGeometryExperimentsMain {
    private EmergentGeometryExperimentsMain(){}
    public static void main(String[] args){run("A",4,2,GeometryEmbeddingDimension.TWO_D,true);run("B",4,3,GeometryEmbeddingDimension.THREE_D,true);run("C",8,3,GeometryEmbeddingDimension.THREE_D,false);}
    private static void run(String name,int n,int k,GeometryEmbeddingDimension dimension,boolean testFirst){ConfigurationRequest request=new ConfigurationRequest(n,k,dimension,GraphSymmetryFilter.BOTH,1000,Duration.ofSeconds(30),null,null);GenerationResult generated=new ConfigurationGenerator().generate(request);System.out.printf("Experiment %s: retained=%d complete=%s status=%s%n",name,generated.configurations().size(),generated.metrics().enumerationComplete(),generated.status().code());if(!testFirst||generated.configurations().isEmpty()){System.out.println("  no configuration selected; geometry tests not started");return;}Configuration selected=generated.configurations().get(0);TestRequest test=new TestRequest(selected.id(),dimension,1,20260731L,4,DynamicElasticSettings.defaults(1,20260731L),GeometrySymmetryPolicy.MEASURE_ONLY,100_000);TestResult result=new TestSession(selected,test).run();System.out.printf("  selected=%s graphSymmetry=%s realization=%s faces=%s boundary=%s area=%s volume=%s final=%s%n",selected.id(),selected.graphSymmetry(),result.realization().status(),result.faceDerivation()!=null&&result.faceDerivation().unique(),result.boundary()!=null&&result.boundary().closed(),result.area().available()?result.area().absoluteArea():"unavailable",result.volume().available()?result.volume().absoluteVolume():"unavailable",result.status().code());if(name.equals("A")&&result.boundary()!=null&&result.boundary().closed()){GeometryOptimizationResult optimization=new AreaOptimizer().maximize2d(selected,result.realization(),result.boundary(),1,10_000);System.out.printf("  area optimization: baseline=%.9f optimized=%.9f improvement=%.3f%% constraints=%s symmetryRequired=%s symmetricAfter=%s%n",optimization.baselineMeasure(),optimization.optimizedMeasure(),100*optimization.relativeImprovement(),optimization.constraintsPreserved(),optimization.symmetryPolicy(),optimization.symmetricAfterOptimization());}}
}
