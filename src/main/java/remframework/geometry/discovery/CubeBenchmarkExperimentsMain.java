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
        for(BenchmarkPreset preset:new BenchmarkPreset[]{BenchmarkPreset.CUBE_EMERGENT_BENCHMARK,
                BenchmarkPreset.CUBE_CONSTRAINED_BENCHMARK}){
            TestRequest request=new TestRequest(cube.id(),GeometryEmbeddingDimension.THREE_D,1,42,1,
                    null,GeometrySymmetryPolicy.MEASURE_ONLY,100_000).withBenchmark(preset);
            TestResult result=new TestSession(cube,request).run();
            System.out.println(preset+" configuration="+cube.id()+" reference="+result.referenceTopologyMatch().topology()
                    +" realization="+result.realization().status()+" boundary="+(result.boundary()==null?"NOT_RUN":result.boundary().status().code())
                    +" volume="+(result.volume().available()?result.volume().absoluteVolume():"UNAVAILABLE")
                    +" basicCandidate="+(result.threeDConditionReport()!=null&&result.threeDConditionReport().basicValidCandidate())
                    +" benchmarkPassed="+result.selectedBenchmarkPassed());
            if(result.threeDRegularity()!=null)System.out.println("faceAngleVariance="+result.threeDRegularity().faces().stream().map(f->f.interiorAngles().variance()).toList()
                    +" dihedralVariance="+result.threeDRegularity().interiorDihedralAngles().variance()
                    +" facePlaneRadiusVariance="+result.threeDRegularity().facePlaneRadii().variance());
        }
    }
}
