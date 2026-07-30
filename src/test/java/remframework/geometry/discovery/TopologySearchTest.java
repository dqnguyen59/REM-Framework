package remframework.geometry.discovery;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TopologySearchTest {
    @TempDir Path temp;

    @Test void canonicalSignatureIgnoresEdgeOrdering() {
        GeometryDiscoveryModel cube = GeometryDiscoveryExamples.smallCubic(1);
        List<DiscoveryConnection> reversed = new ArrayList<>(cube.connections());
        Collections.reverse(reversed);
        GeometryDiscoveryModel copy = new GeometryDiscoveryModel("copy", "", cube.nodes(), reversed);
        assertEquals(TopologySignature.of(cube), TopologySignature.of(copy));
    }

    @Test void addEdgePreservesValidity() {
        TopologySearchSettings settings = settings(1, 0);
        TopologyMutationResult result = new TopologyMutator(2).propose(
                GeometryDiscoveryExamples.squareCycle(1), TopologyMutationType.ADD_EDGE, settings);
        assertTrue(result.valid(), result.rejectionReason());
        assertEquals(5, result.model().connections().size());
    }

    @Test void removingBridgeIsRejected() {
        TopologyMutation mutation = new TopologyMutation(TopologyMutationType.REMOVE_EDGE,
                List.of(new DiscoveryConnection(1, 2)), List.of(), "remove bridge");
        TopologyMutationResult result = new TopologyMutator(1).apply(
                GeometryDiscoveryExamples.line(1), mutation, settings(1, 0));
        assertFalse(result.valid());
        assertTrue(result.rejectionReason().contains("connected"));
    }

    @Test void duplicateRewireAndDegreeLimitsAreRejected() {
        GeometryDiscoveryModel square = GeometryDiscoveryExamples.squareCycle(1);
        TopologyMutation duplicate = new TopologyMutation(TopologyMutationType.REWIRE_EDGE,
                List.of(new DiscoveryConnection(0, 1)),
                List.of(new DiscoveryConnection(0, 3)), "duplicate");
        assertFalse(new TopologyMutator(1).apply(square, duplicate, settings(1, 0)).valid());
        TopologySearchSettings degreeTwo = replaceDegree(settings(1, 0), 1, 2);
        TopologyMutation add = new TopologyMutation(TopologyMutationType.ADD_EDGE, List.of(),
                List.of(new DiscoveryConnection(0, 2)), "excess");
        assertFalse(new TopologyMutator(1).apply(square, add, degreeTwo).valid());
    }

    @Test void mutationGenerationIsDeterministic() {
        TopologyMutator first = new TopologyMutator(55);
        TopologyMutator second = new TopologyMutator(55);
        GeometryDiscoveryModel cube = GeometryDiscoveryExamples.smallCubic(1);
        for (int i = 0; i < 10; i++)
            assertEquals(first.next(cube, settings(55, 0)).mutation(),
                    second.next(cube, settings(55, 0)).mutation());
    }

    @Test void topologyMetricsMatchKnownGraphs() {
        TopologyMetricsCalculator calculator = new TopologyMetricsCalculator();
        TopologyMetrics line = calculator.calculate(GeometryDiscoveryExamples.line(1));
        assertEquals(3, line.graphDiameter());
        assertEquals(3, line.bridgeEdgeCount());
        assertEquals(2, line.articulationPointCount());
        TopologyMetrics cycle = calculator.calculate(GeometryDiscoveryExamples.squareCycle(1));
        assertEquals(2, cycle.graphDiameter());
        assertEquals(0, cycle.bridgeEdgeCount());
        TopologyMetrics tetra = calculator.calculate(GeometryDiscoveryExamples.tetrahedron(1));
        assertEquals(4, tetra.triangleCount());
        assertEquals(1, tetra.graphDiameter());
        TopologyMetrics cube = calculator.calculate(GeometryDiscoveryExamples.smallCubic(1));
        assertEquals(3, cube.graphDiameter());
        assertEquals(3, cube.minimumDegree());
        assertEquals(3, cube.maximumDegree());
    }

    @Test void directionalDiagnosticsRecognizeSymmetryAndAsymmetry() {
        DirectionalBalanceCalculator calculator = new DirectionalBalanceCalculator();
        GeometryDiscoveryResult tetra = new GeometryDiscoveryEngine().relax(
                GeometryDiscoveryExamples.tetrahedron(1), GeometryDiscoverySettings.defaults(1));
        DirectionalBalanceMetrics symmetric = calculator.calculate(tetra.model());
        // Three edge directions at a tetrahedron vertex have mutual angle 60 degrees;
        // their normalized vector-sum magnitude is sqrt(2/3), not zero.
        assertEquals(Math.sqrt(2.0 / 3.0), symmetric.meanLocalImbalance(), 1e-4);
        GeometryDiscoveryModel asymmetric = new GeometryDiscoveryModel("asymmetric", "",
                List.of(node(0, 0, 0, 0), node(1, 1, 0, 0), node(2, 2, .1, 0), node(3, 3, 0, 0)),
                List.of(new DiscoveryConnection(0, 1), new DiscoveryConnection(0, 2),
                        new DiscoveryConnection(0, 3), new DiscoveryConnection(1, 2),
                        new DiscoveryConnection(2, 3)));
        DirectionalBalanceMetrics unbalanced = calculator.calculate(asymmetric);
        assertTrue(unbalanced.maximumLocalImbalance() > symmetric.maximumLocalImbalance());
        assertTrue(symmetric.meanTensorAnisotropy() < unbalanced.meanTensorAnisotropy());
    }

    @Test void scoreComponentsSumAndLowerIsBetter() {
        GeometryDiscoveryResult geometry = new GeometryDiscoveryEngine().relax(
                GeometryDiscoveryExamples.smallCubic(1), GeometryDiscoverySettings.defaults(1));
        TopologyMetrics topology = new TopologyMetricsCalculator().calculate(geometry.model());
        DirectionalBalanceMetrics directional =
                new DirectionalBalanceCalculator().calculate(geometry.model());
        TopologyScore score = TopologyScore.calculate(geometry.metrics(), topology, directional,
                TopologyScoreWeights.defaults(), 6);
        assertEquals(score.components().values().stream().mapToDouble(Double::doubleValue).sum(),
                score.total(), 1e-12);
        assertTrue(score.total() < score.total() + 1);
    }

    @Test void hillClimbingRejectsWorseAndAnnealingCanAcceptIt() {
        TopologySearchSession hill = new TopologySearchSession(
                GeometryDiscoveryExamples.smallCubic(1), settings(1, 0));
        assertFalse(hill.accept(11, 10));
        TopologySearchSettings annealing = withMode(settings(1, 0),
                TopologySearchMode.SIMULATED_ANNEALING, 1e20);
        assertTrue(new TopologySearchSession(GeometryDiscoveryExamples.smallCubic(1), annealing)
                .accept(11, 10));
    }

    @Test void duplicateTopologiesAreNotReevaluatedAndBestIsRetained() {
        TopologySearchResult result = new TopologySearchEngine().search(
                GeometryDiscoveryExamples.tetrahedron(1), settings(8, 30));
        assertTrue(result.metrics().duplicateTopologyCount() > 0);
        assertTrue(result.bestCandidate().score().total()
                <= result.initialCandidate().score().total() + 1e-12);
        for (int i = 1; i < result.topCandidates().size(); i++)
            assertTrue(result.topCandidates().get(i - 1).score().total()
                    <= result.topCandidates().get(i).score().total());
    }

    @Test void sameSeedProducesIdenticalSearchResult() {
        TopologySearchResult first = new TopologySearchEngine().search(
                GeometryDiscoveryExamples.smallCubic(1), settings(77, 15));
        TopologySearchResult second = new TopologySearchEngine().search(
                GeometryDiscoveryExamples.smallCubic(1), settings(77, 15));
        assertEquals(first.bestCandidate().topologySignature(),
                second.bestCandidate().topologySignature());
        assertEquals(first.bestCandidate().score().total(),
                second.bestCandidate().score().total());
        assertEquals(first.metrics().acceptedMutationCount(), second.metrics().acceptedMutationCount());
    }

    @Test void searchOutputsHistoryMetadataAndNeverOverwriteTemplate() throws Exception {
        TopologySearchResult result = new TopologySearchEngine().search(
                GeometryDiscoveryExamples.smallCubic(1), settings(3, 5));
        Path output = temp.resolve("search");
        Path best = new TopologySearchExporter().export(result, output);
        assertTrue(Files.readString(output.resolve("search-history.csv")).startsWith(
                "iteration,currentScore,bestScore"));
        assertTrue(Files.readString(best).contains("[topologySearch.directionalBalance]"));
        Path template = temp.resolve("cubic-lattice.toml");
        Files.writeString(template, "sentinel");
        assertThrows(IllegalArgumentException.class,
                () -> new TopologySearchExporter().export(result, template));
        assertEquals("sentinel", Files.readString(template));
    }

    @Test void exportedTopologyMetadataLoadsForVisualizer() throws Exception {
        TopologySearchResult result = new TopologySearchEngine().search(
                GeometryDiscoveryExamples.smallCubic(1), settings(4, 1));
        Path best = new TopologySearchExporter().export(result, temp.resolve("load"));
        assertNotNull(new GeometryDiscoveryCandidateLoader().load(best));
        TopologySearchMetadata metadata = new TopologySearchMetadataLoader().load(best);
        assertEquals(result.bestCandidate().score().total(), metadata.totalScore(), 1e-12);
    }

    @Test void batchSeedOrderingIsDeterministic() {
        assertEquals(List.of(2L, 3L, 4L), TopologySearchBatchMain.seeds("2:4"));
        assertEquals(List.of(4L, 1L), TopologySearchBatchMain.seeds("4,1"));
    }

    private static TopologySearchSettings settings(long seed, int iterations) {
        GeometryDiscoverySettings geometry = GeometryDiscoverySettings.defaults(1)
                .withMaximumIterations(300).withRandomSeed(seed);
        TopologySearchSettings base = TopologySearchSettings.defaults(seed, geometry);
        return new TopologySearchSettings(TopologySearchMode.HILL_CLIMBING, seed, iterations,
                1, 1, 6, -1, 10, 1, .99, 1e-6, 1e-9, geometry,
                base.scoreWeights(), 10);
    }

    private static TopologySearchSettings replaceDegree(TopologySearchSettings s, int min, int max) {
        return new TopologySearchSettings(s.searchMode(), s.randomSeed(), s.maximumSearchIterations(),
                s.mutationsAttemptedPerIteration(), min, max, s.requiredEdgeCount(), s.archiveSize(),
                s.initialTemperature(), s.coolingRate(), s.minimumTemperature(),
                s.scoreImprovementTolerance(), s.geometrySettings(), s.scoreWeights(),
                s.checkpointInterval());
    }

    private static TopologySearchSettings withMode(TopologySearchSettings s,
                                                   TopologySearchMode mode, double temperature) {
        return new TopologySearchSettings(mode, s.randomSeed(), s.maximumSearchIterations(),
                s.mutationsAttemptedPerIteration(), s.minimumDegree(), s.maximumDegree(),
                s.requiredEdgeCount(), s.archiveSize(), temperature, s.coolingRate(),
                s.minimumTemperature(), s.scoreImprovementTolerance(), s.geometrySettings(),
                s.scoreWeights(), s.checkpointInterval());
    }

    private static DiscoveryNode node(int id, double x, double y, double z) {
        return new DiscoveryNode(id, new Vector3d(x, y, z), id == 0);
    }
}
