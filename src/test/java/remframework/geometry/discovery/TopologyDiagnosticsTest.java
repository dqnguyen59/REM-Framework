package remframework.geometry.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TopologyDiagnosticsTest {
    @TempDir Path temp;
    private final DirectionalBalanceCalculator calculator = new DirectionalBalanceCalculator();

    @Test void regularTetrahedronBiasIsNamedBiasRatherThanIsotropy() {
        GeometryDiscoveryResult relaxed = new GeometryDiscoveryEngine().relax(
                GeometryDiscoveryExamples.tetrahedron(1), GeometryDiscoverySettings.defaults(1));
        DirectionalBalanceMetrics metrics = calculator.calculate(relaxed.model());
        assertEquals(Math.sqrt(2.0 / 3.0), metrics.meanNormalizedDirectionalBias(), 1e-5);
        metrics.localDiagnostics().values().forEach(local ->
                assertEquals(Math.sqrt(6), local.unnormalizedDirectionalBias(), 1e-4));
        assertTrue(metrics.meanTensorIsotropyError() > 0);
    }

    @Test void tetrahedralCoordinationHasCorrectAngularReferenceAndTensor() {
        LocalDirectionalDiagnostics center = center(DirectionalDiagnosticExamples.tetrahedral3d());
        assertEquals(0, center.normalizedDirectionalBias(), 1e-12);
        assertEquals(0, center.tensorIsotropyError(), 1e-12);
        assertEquals(AngularReferenceType.TETRAHEDRAL_3D, center.angularReferenceType());
        assertTrue(center.angularReferenceApplicable());
        center.pairwiseDotProducts().forEach(dot -> assertEquals(-1.0 / 3, dot, 1e-12));
        assertEquals(0, center.knownReferenceAngularError(), 1e-12);
    }

    @Test void oppositeLineAndSixAxisNeighborhoodHaveExpectedBalance() {
        LocalDirectionalDiagnostics line = center(DirectionalDiagnosticExamples.lineCenter());
        assertEquals(0, line.normalizedDirectionalBias(), 1e-12);
        assertEquals(1, line.localDimensionality());
        assertEquals(AngularReferenceType.OPPOSITE_PAIR_1D, line.angularReferenceType());
        LocalDirectionalDiagnostics axes = center(DirectionalDiagnosticExamples.octahedral3d());
        assertEquals(0, axes.normalizedDirectionalBias(), 1e-12);
        assertEquals(0, axes.tensorIsotropyError(), 1e-12);
        assertEquals(0, axes.meanOppositeDirectionMismatch(), 1e-12);
        assertEquals(0, axes.missingOppositeDirectionCount());
    }

    @Test void asymmetryAndCoverageGapAreDetected() {
        LocalDirectionalDiagnostics symmetric =
                center(DirectionalDiagnosticExamples.tetrahedral3d());
        LocalDirectionalDiagnostics asymmetric =
                center(DirectionalDiagnosticExamples.asymmetric());
        assertTrue(asymmetric.tensorIsotropyError() > symmetric.tensorIsotropyError());
        LocalDirectionalDiagnostics gap = center(DirectionalDiagnosticExamples.directionalGap());
        LocalDirectionalDiagnostics axes = center(DirectionalDiagnosticExamples.octahedral3d());
        assertTrue(gap.maximumUncoveredAngle() > axes.maximumUncoveredAngle());
    }

    @Test void localDimensionalityDetectsOneTwoAndThreeDimensions() {
        assertEquals(1, center(DirectionalDiagnosticExamples.lineCenter()).localDimensionality());
        assertEquals(2, center(DirectionalDiagnosticExamples.equilateral2d()).localDimensionality());
        assertEquals(3, center(DirectionalDiagnosticExamples.tetrahedral3d()).localDimensionality());
        assertEquals(2, center(DirectionalDiagnosticExamples.nearlyPlanar()).localDimensionality());
    }

    @Test void unsupportedDegreeHasNoFalseReferencePenalty() {
        DirectionalBalanceMetrics metrics =
                calculator.calculate(DirectionalDiagnosticExamples.directionalGap());
        LocalDirectionalDiagnostics center = metrics.localDiagnostics().get(0);
        assertFalse(center.angularReferenceApplicable());
        assertEquals(AngularReferenceType.NONE, center.angularReferenceType());
        assertEquals(0, center.knownReferenceAngularError());
    }

    @Test void diagnosticsUseAuthoritativeConnectionsAfterTopologyMutation() {
        GeometryDiscoveryModel cube = GeometryDiscoveryExamples.smallCubic(1);
        TopologySearchSettings settings = TopologySearchSettings.defaults(
                1, GeometryDiscoverySettings.defaults(1));
        TopologyMutation mutation = new TopologyMutation(TopologyMutationType.ADD_EDGE,
                List.of(), List.of(new DiscoveryConnection(0, 7)), "add diagonal");
        GeometryDiscoveryModel changed = new TopologyMutator(1)
                .apply(cube, mutation, settings).model();
        DirectionalBalanceMetrics diagnostics = calculator.calculate(changed);
        assertEquals(4, diagnostics.localDiagnostics().get(0).degree());
        assertEquals(4, diagnostics.localDiagnostics().get(7).degree());
        assertEquals(3, diagnostics.localDiagnostics().get(1).degree());
    }

    @Test void profilesHaveDistinctDefaultsAndOverridesWin() {
        TopologyScoreWeights general =
                TopologyScoreWeights.forProfile(TopologyScoringProfile.GENERAL_GEOMETRY);
        TopologyScoreWeights wave =
                TopologyScoreWeights.forProfile(TopologyScoringProfile.WAVE_PROPAGATION);
        TopologyScoreWeights tetra =
                TopologyScoreWeights.forProfile(TopologyScoringProfile.TETRAHEDRAL);
        assertEquals(0, general.oppositeDirection());
        assertTrue(wave.oppositeDirection() > 0);
        assertTrue(tetra.knownReferenceAngular() > general.knownReferenceAngular());
        assertEquals(17, general.override("tensor-isotropy", 17).tensorIsotropy());
    }

    @Test void disabledAndInapplicableComponentsContributeExactlyZero() {
        TopologyCandidate candidate = diagnosticCandidate(
                DirectionalDiagnosticExamples.directionalGap(),
                TopologyScoringProfile.GENERAL_GEOMETRY,
                TopologyScoreWeights.forProfile(TopologyScoringProfile.GENERAL_GEOMETRY));
        assertEquals(0, candidate.score().details().get("directionalBias").weightedContribution());
        TopologyScoreComponent angular = candidate.score().details().get("knownReferenceAngular");
        assertFalse(angular.applicable());
        assertEquals(0, angular.weightedContribution());
    }

    @Test void normalizedScoreIsInvariantUnderUniformScale() {
        TopologyCandidate unit = diagnosticCandidate(
                GeometryDiscoveryExamples.smallCubic(1),
                TopologyScoringProfile.GENERAL_GEOMETRY,
                TopologyScoreWeights.defaults());
        TopologyCandidate ten = diagnosticCandidate(
                GeometryDiscoveryExamples.smallCubic(10),
                TopologyScoringProfile.GENERAL_GEOMETRY,
                TopologyScoreWeights.defaults());
        assertEquals(unit.score().total(), ten.score().total(), 1e-10);
        assertEquals(unit.score().details().get("edgeLengthError").normalizedValue(),
                ten.score().details().get("edgeLengthError").normalizedValue(), 1e-12);
    }

    @Test void totalEqualsWeightedContributions() {
        TopologyCandidate candidate = diagnosticCandidate(
                GeometryDiscoveryExamples.smallCubic(1),
                TopologyScoringProfile.WAVE_PROPAGATION,
                TopologyScoreWeights.forProfile(TopologyScoringProfile.WAVE_PROPAGATION));
        assertEquals(candidate.score().details().values().stream()
                        .mapToDouble(TopologyScoreComponent::weightedContribution).sum(),
                candidate.score().total(), 1e-12);
    }

    @Test void nonFiniteMetricsReceiveDeterministicFailureScore() {
        GeometryDiscoveryMetrics broken = new GeometryDiscoveryMetrics(0, false,
                Double.NaN, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, "3D");
        DirectionalBalanceMetrics directional = calculator.calculate(
                GeometryDiscoveryExamples.smallCubic(1));
        assertEquals(TopologyScore.NUMERICAL_FAILURE_SCORE,
                TopologyScore.calculate(broken,
                        new TopologyMetricsCalculator().calculate(
                                GeometryDiscoveryExamples.smallCubic(1)),
                        directional, TopologyScoreWeights.defaults(), 6).total());
    }

    @Test void oldMetadataLoadsAsUnavailableRatherThanZeroQuality() throws Exception {
        Path file = temp.resolve("old.toml");
        Files.writeString(file, """
                [topologySearch]
                totalScore = 2.0
                searchMode = "HILL_CLIMBING"
                searchSeed = 1
                [topologySearch.directionalBalance]
                meanLocalImbalance = 0.5
                meanTensorAnisotropy = 0.2
                """);
        TopologySearchMetadata metadata = new TopologySearchMetadataLoader().load(file);
        assertFalse(metadata.redesignedDiagnosticsAvailable());
        assertEquals("UNAVAILABLE", metadata.scoringProfile());
        assertFalse(metadata.angularReferenceAvailable());
    }

    @Test void visualizerExposesSelectedNodeDirectionalData() {
        GeometryDiscoveryModel model = DirectionalDiagnosticExamples.tetrahedral3d();
        GeometryDiscoveryVisualizer visualizer = new GeometryDiscoveryVisualizer(model,
                GeometryDiscoverySettings.defaults(1), "test");
        assertNotNull(visualizer.selectedNodeDirectionalDiagnostics());
        assertEquals(4, visualizer.selectedNodeDirectionalDiagnostics().degree());
    }

    @Test void diagnosticCliProducesFiniteReport() throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(bytes));
            GeometryDiagnosticMain.main(new String[]{
                    "--model", "tetrahedral-neighborhood", "--profile", "tetrahedral"});
        } finally {
            System.setOut(original);
        }
        String report = bytes.toString();
        assertTrue(report.contains("scoringProfile=TETRAHEDRAL"));
        assertFalse(report.contains("NaN"));
        assertFalse(report.contains("Infinity"));
    }

    private LocalDirectionalDiagnostics center(GeometryDiscoveryModel model) {
        return calculator.calculate(model).localDiagnostics().get(0);
    }

    private TopologyCandidate diagnosticCandidate(GeometryDiscoveryModel model,
                                                  TopologyScoringProfile profile,
                                                  TopologyScoreWeights weights) {
        double target = model.connections().isEmpty() ? 1
                : model.nodes().stream().filter(n -> n.id() == model.connections().getFirst()
                .firstNodeId()).findFirst().orElseThrow().position().distance(
                model.nodes().stream().filter(n -> n.id() == model.connections().getFirst()
                        .secondNodeId()).findFirst().orElseThrow().position());
        GeometryDiscoverySettings settings = GeometryDiscoverySettings.defaults(target);
        GeometryDiscoveryMetrics metrics = new GeometryDiscoveryMetricsCalculator()
                .calculate(model, settings, 0, true);
        GeometryDiscoveryResult result = new GeometryDiscoveryResult(model, settings, metrics,
                new DiscoveryValidationResult(),
                new GeometryRuntimeCompatibilityAnalyzer().analyze(model, 1e-6), List.of());
        TopologySearchSettings base = TopologySearchSettings.defaults(1, settings);
        TopologySearchSettings search = new TopologySearchSettings(base.searchMode(), 1,
                0, 1, 0, 32, -1, 1, 1, .99, 1e-6, 1e-9,
                settings, weights, profile, base.diagnosticSettings(), 1);
        TopologyMetrics topology = new TopologyMetricsCalculator().calculate(model);
        DirectionalBalanceMetrics directional = calculator.calculate(model);
        return new TopologyCandidate(0, null, result, topology, directional,
                TopologyScore.calculate(result, topology, directional, search), null,
                TopologySignature.of(model), "diagnostic");
    }
}
