package remframework.geometry.discovery;

import java.nio.file.Path;
import java.util.Map;

public final class GeometryDiagnosticMain {
    private GeometryDiagnosticMain() { }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = TopologySearchMain.parse(args);
        TopologyScoringProfile profile = TopologyScoringProfile.parse(
                options.getOrDefault("profile", options.getOrDefault("scoring-profile", "general")));
        GeometryDiscoveryModel model;
        GeometryDiscoverySettings geometrySettings;
        if (options.containsKey("file")) {
            LoadedDiscoveryCandidate loaded = new GeometryDiscoveryCandidateLoader()
                    .load(Path.of(options.get("file")));
            model = loaded.model();
            geometrySettings = loaded.settings();
        } else {
            String name = options.getOrDefault("model", "tetrahedron");
            model = referenceOrBuiltIn(name);
            geometrySettings = GeometryDiscoverySettings.defaults(
                    Double.parseDouble(options.getOrDefault("target-distance", "1")));
        }
        GeometryDiscoveryMetrics metrics = new GeometryDiscoveryMetricsCalculator()
                .calculate(model, geometrySettings, 0, true);
        GeometryDiscoveryResult geometry = new GeometryDiscoveryResult(model, geometrySettings,
                metrics, new GeometryDiscoveryValidator().validate(model, geometrySettings),
                new GeometryRuntimeCompatibilityAnalyzer().analyze(model,
                        geometrySettings.dimensionalityTolerance()), java.util.List.of());
        TopologySearchSettings base = TopologySearchSettings.defaults(1, geometrySettings);
        TopologyScoreWeights weights = TopologySearchMain.applyWeightOverrides(
                TopologyScoreWeights.forProfile(profile), options);
        TopologySearchSettings searchSettings = new TopologySearchSettings(
                base.searchMode(), base.randomSeed(), 0, 1, 0, 32, -1, 1,
                base.initialTemperature(), base.coolingRate(), base.minimumTemperature(),
                base.scoreImprovementTolerance(), geometrySettings, weights, profile,
                base.diagnosticSettings(), 1);
        TopologyMetrics topology = new TopologyMetricsCalculator().calculate(model);
        DirectionalBalanceMetrics directional = new DirectionalBalanceCalculator()
                .calculate(model, searchSettings.diagnosticSettings());
        TopologyCandidate candidate = new TopologyCandidate(0, null, geometry, topology,
                directional, TopologyScore.calculate(geometry, topology, directional, searchSettings),
                null, TopologySignature.of(model), "diagnostic");
        System.out.print(new GeometryDiagnosticReport().format(candidate, profile));
    }

    static GeometryDiscoveryModel referenceOrBuiltIn(String name) {
        return switch (name) {
            case "tetrahedron" -> DirectionalDiagnosticExamples.regularTetrahedronGraph();
            case "line-center" -> DirectionalDiagnosticExamples.lineCenter();
            case "equilateral" -> DirectionalDiagnosticExamples.equilateral2d();
            case "tetrahedral-neighborhood" -> DirectionalDiagnosticExamples.tetrahedral3d();
            case "octahedral-neighborhood" -> DirectionalDiagnosticExamples.octahedral3d();
            case "asymmetric" -> DirectionalDiagnosticExamples.asymmetric();
            case "nearly-planar" -> DirectionalDiagnosticExamples.nearlyPlanar();
            case "directional-gap" -> DirectionalDiagnosticExamples.directionalGap();
            default -> GeometryDiscoveryExamples.create(name, 1);
        };
    }
}
