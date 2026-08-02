package remframework.geometry.discovery;

import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.experiment.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.reference.*;

/** Headless reproducible benchmark smoke experiment; no reference coordinates are supplied. */
public final class CubeBenchmarkExperimentsMain {
    private CubeBenchmarkExperimentsMain() { }
    public static void main(String[] args) {
        ReferenceTopologyRecognizer recognizer=new ReferenceTopologyRecognizer();
        Configuration cube=new ConfigurationGenerator().generate(ConfigurationRequest.exhaustive(
                8,3,GeometryEmbeddingDimension.THREE_D)).configurations().stream()
                .filter(c->recognizer.recognize(c).topology()==ReferenceTopology.CUBE_GRAPH)
                .findFirst().orElseThrow();
        BenchmarkPreset[] presets=args.length>0?new BenchmarkPreset[]{BenchmarkPreset.valueOf(args[0])}:
                new BenchmarkPreset[]{BenchmarkPreset.CUBE_EMERGENT_BENCHMARK,BenchmarkPreset.CUBE_CONSTRAINED_BENCHMARK};
        long seed=args.length>1?Long.parseLong(args[1]):42;
        for(BenchmarkPreset preset:presets){
            TestRequest request=new TestRequest(cube.id(),GeometryEmbeddingDimension.THREE_D,1,seed,1,
                    null,GeometrySymmetryPolicy.MEASURE_ONLY,100_000).withBenchmark(preset);
            IncrementalSession session=new IncrementalSession(cube,request);session.run();TestResult result=session.result();
            System.out.println(preset+" requestedSeed="+result.requestedSeed()+" realizedSeed="+result.realization().seed()+" configuration="+cube.id()+" reference="+result.referenceTopologyMatch().topology()
                    +" realization="+result.realization().status()+" boundary="+(result.boundary()==null?"NOT_RUN":result.boundary().status().code())
                    +" volume="+(result.volume().available()?result.volume().absoluteVolume():"UNAVAILABLE")
                    +" basicCandidate="+(result.threeDConditionReport()!=null&&result.threeDConditionReport().basicValidCandidate())
                    +" benchmarkPassed="+result.selectedBenchmarkPassed());
            System.out.println("iterations="+session.iteration()+" edgeError="+session.metrics().edges().stream().mapToDouble(e->Math.abs(e.distance()-e.targetDistance())).max().orElse(0)+" constraints="+session.threeDConstraintDiagnostics());
            if(result.threeDRegularity()!=null)System.out.println("faceAngleVariance="+result.threeDRegularity().faces().stream().map(f->f.interiorAngles().variance()).toList()
                    +" dihedralVariance="+result.threeDRegularity().interiorDihedralAngles().variance()
                    +" facePlaneRadiusVariance="+result.threeDRegularity().facePlaneRadii().variance()
                    +" vertexRadiusVariance="+result.threeDRegularity().vertexRadii().variance());
        }
    }
}
