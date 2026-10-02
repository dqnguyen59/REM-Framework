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

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeometryDiscoveryExtensionTest {
    @TempDir Path temp;
    private final GeometryDiscoverySettings settings = GeometryDiscoverySettings.defaults(10);

    @Test
    void exportedCandidateLoadsIntoVisualizerDataModel() throws Exception {
        GeometryDiscoveryResult original = new GeometryDiscoveryEngine().relax(
                GeometryDiscoveryExamples.tetrahedron(10), settings);
        Path file = new GeometryDiscoveryExporter().export(original, temp.resolve("candidate.toml"));
        LoadedDiscoveryCandidate loaded = new GeometryDiscoveryCandidateLoader().load(file);
        GeometryDiscoveryVisualizer visualizer = new GeometryDiscoveryVisualizer(
                loaded.model(), loaded.settings(), file.toString());
        assertEquals(4, visualizer.session().model().nodes().size());
        assertEquals(6, visualizer.session().model().connections().size());
        assertEquals(file.toAbsolutePath(), loaded.sourcePath());
    }

    @Test
    void incrementalSteppingMatchesBatchExecution() {
        GeometryDiscoveryModel model = GeometryDiscoveryExamples.tetrahedron(10);
        GeometryDiscoveryResult batch = new GeometryDiscoveryEngine().relax(model, settings);
        GeometryDiscoverySession session = new GeometryDiscoverySession(model, settings);
        session.start();
        while (!session.isFinished()) session.step();
        assertModelsEqual(batch.model(), session.result().model());
        assertEquals(batch.metrics(), session.result().metrics());
    }

    @Test
    void pauseResumeDoesNotChangeDeterministicResult() {
        GeometryDiscoveryModel model = GeometryDiscoveryExamples.tetrahedron(10);
        GeometryDiscoverySession session = new GeometryDiscoverySession(model, settings);
        session.start();
        for (int i = 0; i < 20; i++) session.step();
        session.pause();
        GeometryDiscoveryModel paused = session.model().copy();
        for (int i = 0; i < 20; i++)
            if (session.isRunning()) session.step();
        assertModelsEqual(paused, session.model());
        session.start();
        GeometryDiscoveryResult resumed = session.runUntilConverged();
        GeometryDiscoveryResult uninterrupted = new GeometryDiscoveryEngine().relax(model, settings);
        assertModelsEqual(uninterrupted.model(), resumed.model());
    }

    @Test
    void resetRestoresInitialPositions() {
        GeometryDiscoverySession session = new GeometryDiscoverySession(
                GeometryDiscoveryExamples.tetrahedron(10), settings);
        GeometryDiscoveryModel initial = session.model().copy();
        for (int i = 0; i < 10; i++) session.step();
        session.reset();
        assertModelsEqual(initial, session.model());
        assertEquals(0, session.state().iteration());
    }

    @Test
    void fitToViewHandlesOneTwoAndThreeDimensionalCandidates() {
        ViewState view = new ViewState();
        for (GeometryDiscoveryModel model : List.of(GeometryDiscoveryExamples.line(10),
                GeometryDiscoveryExamples.squareCycle(10),
                GeometryDiscoveryExamples.smallCubic(10))) {
            ViewFit fit = view.fitToView(model);
            assertTrue(fit.center().isFinite());
            assertTrue(fit.radius() > 0);
            assertTrue(fit.cameraDistance() > fit.radius());
            assertTrue(fit.farPlane() > fit.nearPlane());
        }
    }

    @Test
    void emptyAndSingleNodeViewModelsAreSafe() {
        ViewState view = new ViewState();
        GeometryDiscoveryModel empty = new GeometryDiscoveryModel("empty", "empty",
                List.of(), List.of());
        GeometryDiscoveryModel single = new GeometryDiscoveryModel("single", "single",
                List.of(new DiscoveryNode(0, new Vector3d(), true)), List.of());
        assertTrue(view.fitToView(empty).center().isFinite());
        assertTrue(view.fitToView(single).center().isFinite());
        GeometryDiscoveryVisualizer visualizer =
                new GeometryDiscoveryVisualizer(empty, settings, "test");
        visualizer.selectNextNode(1);
        assertNull(visualizer.viewState().selectedNodeId());
    }

    @Test
    void selectedNodeLocalMetricsAreCorrect() {
        GeometryDiscoveryModel line = GeometryDiscoveryExamples.line(10);
        SelectedNodeMetrics local = new SelectedNodeMetricsCalculator().calculate(line, 1, 10);
        assertEquals(2, local.neighborDistances().size());
        double expectedMean = (line.nodes().get(1).position().distance(line.nodes().get(0).position())
                + line.nodes().get(1).position().distance(line.nodes().get(2).position())) / 2;
        assertEquals(expectedMean, local.meanLocalEdgeDistance(), 1e-12);
    }

    @Test
    void batchCombinationsAndCsvAreDeterministicAndComplete() throws Exception {
        GeometryBatchParameters parameters = parameters(temp.resolve("batch"));
        GeometryDiscoveryBatchRunner runner = new GeometryDiscoveryBatchRunner();
        List<GeometryBatchCombination> combinations = runner.combinations(parameters);
        assertEquals(8, combinations.size());
        assertEquals(new GeometryBatchCombination(1, 1, 0.04, 0.1, 2.0),
                combinations.getFirst());
        assertEquals(new GeometryBatchCombination(8, 2, 0.08, 0.25, 2.0),
                combinations.getLast());
        GeometryDiscoveryBatchRunner.Output output = runner.run(parameters);
        String header = Files.readAllLines(output.csvPath()).getFirst();
        for (String column : List.of("runId", "model", "seed", "learningRate",
                "repulsionStrength", "maximumCorrection", "converged", "iterations",
                "totalEdgeError", "meanEdgeDistance", "minEdgeDistance", "maxEdgeDistance",
                "edgeStandardDeviation", "minimumNodeSeparation", "separationViolations",
                "extentX", "extentY", "extentZ", "dimensionality", "runtimeCompatible",
                "elapsedNanoseconds", "exportedCandidatePath"))
            assertTrue(header.contains(column), column);
    }

    @Test
    void aggregateStatisticsAreCorrect() throws Exception {
        GeometryDiscoveryBatchRunner.Output output =
                new GeometryDiscoveryBatchRunner().run(parameters(temp.resolve("aggregate")));
        GeometryBatchAggregate aggregate = output.aggregate();
        assertEquals(8, aggregate.totalRuns());
        assertEquals((double) aggregate.convergedRuns() / 8, aggregate.convergenceRate());
        assertTrue(aggregate.meanIterationCount() > 0);
        assertTrue(aggregate.bestRunId() >= 1);
        assertTrue(aggregate.worstRunId() >= 1);
        assertFalse(aggregate.highestConvergenceParameterCombination().isBlank());
        assertTrue(Files.readString(output.aggregatePath()).contains("convergenceRate="));
    }

    @Test
    void exportNumbersFilesAndMetricsReportIsFinite() throws Exception {
        GeometryDiscoveryResult result = new GeometryDiscoveryEngine().relax(
                GeometryDiscoveryExamples.tetrahedron(10), settings);
        GeometryDiscoveryExporter exporter = new GeometryDiscoveryExporter();
        Path first = exporter.export(result, temp.resolve("candidate.toml"));
        Path second = exporter.export(result, temp.resolve("candidate.toml"));
        assertEquals("candidate.toml", first.getFileName().toString());
        assertEquals("candidate-2.toml", second.getFileName().toString());
        Path report = new GeometryDiscoveryMetricsReportWriter().write(
                result, temp.resolve("metrics.txt"));
        String contents = Files.readString(report);
        assertFalse(contents.contains("NaN"));
        assertFalse(contents.contains("Infinity"));
        assertTrue(contents.contains("totalEdgeLengthError="));
    }

    @Test
    void comparisonDetectsIdenticalAndDifferentModels() {
        GeometryDiscoveryResult tetrahedron = new GeometryDiscoveryEngine().relax(
                GeometryDiscoveryExamples.tetrahedron(10), settings);
        GeometryDiscoveryComparator comparator = new GeometryDiscoveryComparator();
        GeometryComparisonReport identical = comparator.compare(tetrahedron, tetrahedron);
        assertTrue(identical.geometricallyIdentical(1e-12));
        assertEquals(0, identical.normalizedGeometryDifference(), 1e-15);

        GeometryDiscoveryResult cube = new GeometryDiscoveryEngine().relax(
                GeometryDiscoveryExamples.smallCubic(10), settings);
        GeometryComparisonReport different = comparator.compare(tetrahedron, cube);
        assertNotEquals(0, different.nodeCountDifference());
        assertFalse(different.runtimeCompatibilityEqual());
    }

    @Test
    void visualizerAndBatchNeverOverwriteCubicTemplate() throws Exception {
        Path template = temp.resolve("lattice/cubic-lattice.toml");
        Files.createDirectories(template.getParent());
        Files.writeString(template, "template");
        GeometryDiscoveryVisualizer visualizer = new GeometryDiscoveryVisualizer(
                GeometryDiscoveryExamples.tetrahedron(10), settings, "test");
        assertThrows(IllegalArgumentException.class,
                () -> visualizer.exportCandidate(template));
        assertThrows(IllegalArgumentException.class,
                () -> visualizer.saveMetricsReport(template));
        GeometryBatchParameters bad = new GeometryBatchParameters("tetrahedron", 1, 1, 10,
                List.of(0.08), List.of(0.25), List.of(2.0), 100, 1e-7,
                template, false);
        assertThrows(IllegalArgumentException.class,
                () -> new GeometryDiscoveryBatchRunner().run(bad));
        assertEquals("template", Files.readString(template));
    }

    private GeometryBatchParameters parameters(Path output) {
        return new GeometryBatchParameters("tetrahedron", 1, 2, 10,
                List.of(0.04, 0.08), List.of(0.1, 0.25), List.of(2.0),
                20_000, 1e-7, output, false);
    }

    private void assertModelsEqual(GeometryDiscoveryModel expected,
                                   GeometryDiscoveryModel actual) {
        assertEquals(expected.nodes().size(), actual.nodes().size());
        for (int i = 0; i < expected.sortedNodes().size(); i++) {
            assertEquals(expected.sortedNodes().get(i).id(), actual.sortedNodes().get(i).id());
            assertEquals(expected.sortedNodes().get(i).position(),
                    actual.sortedNodes().get(i).position());
        }
    }
}
