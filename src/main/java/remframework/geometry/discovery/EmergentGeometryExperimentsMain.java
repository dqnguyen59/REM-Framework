package remframework.geometry.discovery;

import remframework.geometry.discovery.configuration.Configuration;
import remframework.geometry.discovery.configuration.ConfigurationGenerator;
import remframework.geometry.discovery.configuration.ConfigurationRequest;
import remframework.geometry.discovery.configuration.GenerationResult;
import remframework.geometry.discovery.eligibility.TopologyEligibilityAnalyzer;
import remframework.geometry.discovery.experiment.ExperimentProfile;
import remframework.geometry.discovery.experiment.AngleConditionMode;
import remframework.geometry.discovery.experiment.PlanarityRealizationMode;
import remframework.geometry.discovery.measurement.regularity.TwoDRegularityCalculator;
import remframework.geometry.discovery.optimization.AreaOptimizer;
import remframework.geometry.discovery.realization.session.TestRequest;
import remframework.geometry.discovery.realization.session.TestResult;
import remframework.geometry.discovery.realization.session.TestSession;

import java.time.Duration;

/** Reproducible headless representative scientific experiments. */
public final class EmergentGeometryExperimentsMain {
    private static final long SEED = 20260731L;
    private EmergentGeometryExperimentsMain() { }

    public static void main(String[] args) {
        areaExperiment("A", 4);
        areaExperiment("B", 5);
        volumeExperimentC();
        cubicGraphExperimentD();
    }

    private static void areaExperiment(String name, int n) {
        Configuration selected = generate(n, 2, ExperimentProfile.CLOSED_2D_AREA).configurations().get(0);
        TestResult baseline = run(selected, ExperimentProfile.CLOSED_2D_AREA);
        double maximumEdgeError=baseline.twoDRegularity().edgeLengths().values().stream().mapToDouble(value->Math.abs(value-1)).max().orElse(0);
        System.out.printf("Experiment %s N=%d K=2 mode=STRICT_2D_COORDINATES angle=MEASURE_ONLY iterationsPerRenderedFrame=1 eligibility=%s realization=%s maxEdgeError=%.9g area=%.9f maxPlaneDeviation=%.9g angleVariance=%.9g maxAngleDeviation=%.9g centroidRadiusVariance=%.9g%n",
                name, n, baseline.eligibility().status(), baseline.realization().status(),
                maximumEdgeError,baseline.twoDRegularity().area(),baseline.twoDRegularity().maximumPlaneDeviation(),baseline.twoDRegularity().interiorAngles().variance(),baseline.twoDRegularity().maximumAngleTargetDeviation(),
                baseline.twoDRegularity().centroidRadii().variance());
        TestResult equalAngles=run(selected,ExperimentProfile.CLOSED_2D_AREA,AngleConditionMode.REQUIRE_EQUAL_ANGLES);
        System.out.printf("  mode=STRICT_2D_COORDINATES angle=REQUIRE_EQUAL_ANGLES realization=%s candidate=%s angleVariance=%s maxAngleDeviation=%s regularPolygon=%s%n",
                equalAngles.realization().status(),equalAngles.status().code(),equalAngles.twoDRegularity()==null?"unavailable":equalAngles.twoDRegularity().interiorAngles().variance(),
                equalAngles.twoDRegularity()==null?"unavailable":equalAngles.twoDRegularity().maximumAngleTargetDeviation(),equalAngles.twoDRegularity()!=null&&equalAngles.twoDRegularity().equalEdges()&&equalAngles.twoDRegularity().equalAngles());
        GeometryOptimizationResult optimization = new AreaOptimizer().maximize2d(selected,
                baseline.realization(), baseline.boundary(), 1, 10_000);
        var optimized = new TwoDRegularityCalculator().calculate(selected,
                optimization.optimized().finalCoordinates(), baseline.boundary().orientedFaces().get(0).nodeIds(),
                1, .02, 1e-7, 1e-9, 1e-6,
                remframework.geometry.discovery.experiment.AngleConditionMode.MEASURE_ONLY);
        System.out.printf("  %s area=%.9f angleVariance=%.9g centroidRadiusVariance=%.9g revalidated=%s deltaAngleVariance=%.9g deltaCentroidRadiusVariance=%.9g%n",
                optimization.constraintsPreserved()?"BEST_AREA_FOUND_WITHIN_OPTIMIZATION_LIMITS":"OPTIMIZED_GEOMETRY_REJECTED",
                optimized.area(), optimized.interiorAngles().variance(), optimized.centroidRadii().variance(),
                optimized.valid(), optimized.interiorAngles().variance()-baseline.twoDRegularity().interiorAngles().variance(),
                optimized.centroidRadii().variance()-baseline.twoDRegularity().centroidRadii().variance());
    }

    private static void volumeExperimentC() {
        Configuration selected = generate(4, 3, ExperimentProfile.CLOSED_3D_VOLUME).configurations().get(0);
        TestResult result = run(selected, ExperimentProfile.CLOSED_3D_VOLUME);
        var m = result.threeDRegularity();
        System.out.printf("Experiment C N=4 K=3 eligibility=%s realization=%s faces=%s boundary=%s volume=%s candidate=%s%n",
                result.eligibility().status(), result.realization().status(), result.faceDerivation().faces().size(),
                result.boundary().status().code(), result.volume().available()?result.volume().absoluteVolume():"unavailable", result.status().code());
        if (m != null) System.out.printf("  edgeVar=%.9g dihedralVar=%.9g vertexRadiusVar=%.9g faceCenterRadiusVar=%.9g edgeMidpointRadiusVar=%.9g faceGroups=%s%n",
                m.edgeLengths().variance(),m.interiorDihedralAngles().variance(),m.vertexRadii().variance(),
                m.faceCenterRadii().variance(),m.edgeMidpointRadii().variance(),m.faceAreaByEdgeCount().keySet());
    }

    private static void cubicGraphExperimentD() {
        GenerationResult generated = generate(8, 3, ExperimentProfile.CLOSED_3D_VOLUME);
        TopologyEligibilityAnalyzer analyzer = new TopologyEligibilityAnalyzer();
        System.out.printf("Experiment D N=8 K=3 connected non-isomorphic configurations=%d%n", generated.configurations().size());
        for (Configuration configuration : generated.configurations())
            System.out.printf("  %s CLOSED_2D_AREA=%s CLOSED_3D_VOLUME=%s CONVEX_POLYHEDRAL_VOLUME=%s%n",
                    configuration.id(), analyzer.analyze(configuration,ExperimentProfile.CLOSED_2D_AREA).status(),
                    analyzer.analyze(configuration,ExperimentProfile.CLOSED_3D_VOLUME).status(),
                    analyzer.analyze(configuration,ExperimentProfile.CONVEX_POLYHEDRAL_VOLUME).status());
        Configuration selected = generated.configurations().get(0); TestResult result = run(selected,ExperimentProfile.CLOSED_3D_VOLUME);
        System.out.printf("  selected=%s realization=%s faces=%s boundary=%s volume=%s candidate=%s%n",
                selected.id(),result.realization().status(),result.faceDerivation()==null?"not-derived":result.faceDerivation().faces().size(),
                result.boundary()==null?"not-tested":result.boundary().status().code(),
                result.volume().available()?result.volume().absoluteVolume():"unavailable",result.status().code());
    }

    private static GenerationResult generate(int n,int k,ExperimentProfile profile) {
        return new ConfigurationGenerator().generate(new ConfigurationRequest(n,k,profile.intendedDimension(),
                GraphSymmetryFilter.BOTH,1000,Duration.ofSeconds(30),null,null,profile));
    }
    private static TestResult run(Configuration configuration,ExperimentProfile profile) {
        return run(configuration,profile,AngleConditionMode.MEASURE_ONLY);
    }
    private static TestResult run(Configuration configuration,ExperimentProfile profile,AngleConditionMode angleMode) {
        return new TestSession(configuration,new TestRequest(configuration.id(),profile.intendedDimension(),1,
                SEED,4,DynamicElasticSettings.defaults(1,SEED),GeometrySymmetryPolicy.MEASURE_ONLY,100_000,
                profile,angleMode,null,null,0,0,0,angleMode==AngleConditionMode.REQUIRE_EQUAL_ANGLES?Math.toRadians(2):0,
                PlanarityRealizationMode.STRICT_2D_COORDINATES,.08,.05)).run();
    }
}
