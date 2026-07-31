package remframework.geometry.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeometryDiscoveryPhase2UiTest {
    private static GeometryConfigurationRequest request() {
        return new GeometryConfigurationRequest(4, 2, GeometryEmbeddingDimension.TWO_D,
                GraphSymmetryFilter.BOTH, 20, Duration.ofSeconds(5), null, null);
    }

    @Test void generationActionExposesStatusAndRows() {
        GeometryDiscoveryPhasedWorkflow workflow = new GeometryDiscoveryPhasedWorkflow();
        GeometryConfigurationGenerationResult result = assertDoesNotThrow(() -> workflow.generateConfigurations(request()));
        assertEquals(GeometryConfigurationStatusCode.ENUMERATION_COMPLETE, result.status().code());
        assertEquals(1, workflow.tableRows().size());
        assertEquals(GeometryConfigurationStatusCode.GEOMETRY_TEST_NOT_STARTED,
                workflow.tableRows().getFirst().geometryTestStatus());
    }

    @Test void geometryTestCannotStartWithoutExactlyOneSelection() throws Exception {
        GeometryDiscoveryPhasedWorkflow workflow = new GeometryDiscoveryPhasedWorkflow();
        workflow.generateConfigurations(request());
        GeometryConfigurationId id = workflow.generation().orElseThrow().configurations().getFirst().id();
        GeometryTestRequest test = new GeometryTestRequest(id, GeometryEmbeddingDimension.TWO_D,
                1, 42, 1, null, GeometrySymmetryPolicy.MEASURE_ONLY, 1000);
        assertThrows(IllegalStateException.class, () -> workflow.runSelectedGeometryTest(test));
        workflow.selectForGeometryTest(id);
        assertDoesNotThrow(() -> workflow.runSelectedGeometryTest(test));
    }

    @Test void selectedTopologyRemainsImmutableAfterTest() throws Exception {
        GeometryDiscoveryPhasedWorkflow workflow = new GeometryDiscoveryPhasedWorkflow();
        workflow.generateConfigurations(request());
        GeometryConfiguration configuration = workflow.generation().orElseThrow().configurations().getFirst();
        List<DiscoveryConnection> edges = configuration.edges();
        workflow.selectForGeometryTest(configuration.id());
        workflow.runSelectedGeometryTest(new GeometryTestRequest(configuration.id(),
                GeometryEmbeddingDimension.TWO_D, 1, 7, 1, null,
                GeometrySymmetryPolicy.MEASURE_ONLY, 500));
        assertEquals(edges, workflow.selectedConfigurationRecord().orElseThrow().edges());
    }

    @Test void persistedResultCanBeReopened(@TempDir Path temp) throws Exception {
        GeometryDiscoveryPhasedWorkflow workflow = new GeometryDiscoveryPhasedWorkflow();
        workflow.generateConfigurations(request());
        GeometryConfigurationId id = workflow.generation().orElseThrow().configurations().getFirst().id();
        workflow.selectForGeometryTest(id);
        GeometryTestResult result = workflow.runSelectedGeometryTest(new GeometryTestRequest(id,
                GeometryEmbeddingDimension.TWO_D, 1, 8, 1, null,
                GeometrySymmetryPolicy.MEASURE_ONLY, 500));
        Path saved = new GeometryTestResultStore().save(temp, result);
        GeometryTestStoredResult reopened = new GeometryTestResultStore().load(saved);
        assertEquals(id, reopened.configurationId());
        assertEquals(result.status().code(), reopened.status());
        assertEquals(result.realization().finalCoordinates(), reopened.finalCoordinates());
    }

    @Test void areaAndOptimizationAreGatedByClosedBoundary() {
        GeometryConfiguration configuration = new GeometryConfigurationGenerator().generate(request())
                .configurations().getFirst();
        var diagnostic = GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.BOUNDARY_VALIDATION,
                GeometryConfigurationStatusCode.OPEN_BOUNDARY, "Open", "Boundary is open.");
        GeometryConfigurationStatus status = GeometryConfigurationStatus.of(diagnostic);
        GeometryRealization realization = new GeometryRealization(1, GeometryEmbeddingDimension.TWO_D,
                List.of(), List.of(), 0, 0, false, false, false, 2,
                GeometryRealizationStatus.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS, List.of(diagnostic));
        ClosedBoundaryResult open = new ClosedBoundaryResult(false, false, false, false, false,
                0, List.of(), status, List.of(diagnostic));
        assertFalse(new GeometryMeasureCalculator().area(realization, open).available());
        assertFalse(new GeometryAreaOptimizer().maximize2d(configuration, realization, open, 1, 10).attempted());
    }

    @Test void failureStatusRetainsDetailedReason() {
        GeometryConfigurationRequest invalid = new GeometryConfigurationRequest(4, 4,
                GeometryEmbeddingDimension.THREE_D, GraphSymmetryFilter.BOTH, 10,
                Duration.ofSeconds(1), null, null);
        GeometryConfigurationGenerationResult result = new GeometryConfigurationGenerator().generate(invalid);
        assertFalse(result.status().mayContinue());
        assertFalse(result.status().explanation().isBlank());
        assertFalse(result.status().diagnostics().isEmpty());
    }

    @Test void topologySelectionCreatesNonEmptyRenderModel() throws Exception {
        GeometryConfiguration configuration = new GeometryConfigurationGenerator().generate(request())
                .configurations().getFirst();
        GeometryDiscoveryPhasedWorkflow workflow = new GeometryDiscoveryPhasedWorkflow();
        workflow.generateConfigurations(request());
        workflow.selectForGeometryTest(configuration.id());
        GeometryDiscoveryRenderModel render = workflow.renderModel();
        assertNotNull(render);
        assertTrue(render.topologyPreview());
        assertEquals(configuration.nodeCount(), render.model().nodes().size());
        assertEquals(configuration.edges().size(), render.model().connections().size());
    }

    @Test void displayTransformDoesNotChangeScientificCoordinates() {
        List<org.joml.Vector3d> scientific = List.of(new org.joml.Vector3d(10, 2, 0),
                new org.joml.Vector3d(12, 2, 0), new org.joml.Vector3d(11, 4, 0));
        GeometryDiscoveryDisplayTransform transform = GeometryDiscoveryDisplayTransform
                .calculate(scientific, 4);
        assertEquals(10, scientific.getFirst().x);
        assertNotEquals(scientific.getFirst(), transform.display(scientific.getFirst()));
        assertEquals(10, scientific.getFirst().x);
    }

    @Test void incrementalSessionTransitionsAndResetAreDeterministic() throws Exception {
        GeometryConfiguration configuration = new GeometryConfigurationGenerator().generate(request())
                .configurations().getFirst();
        GeometryTestRequest test = new GeometryTestRequest(configuration.id(),
                GeometryEmbeddingDimension.TWO_D, 1, 19, 1, null,
                GeometrySymmetryPolicy.MEASURE_ONLY, 1000);
        GeometryTestIncrementalSession session = new GeometryTestIncrementalSession(configuration, test);
        List<org.joml.Vector3d> initial = session.currentCoordinates();
        session.resume();
        session.step();
        assertNotEquals(initial, session.currentCoordinates());
        session.pause();
        List<org.joml.Vector3d> paused = session.currentCoordinates();
        session.step(0);
        assertEquals(paused, session.currentCoordinates());
        session.reset();
        assertEquals(initial, session.currentCoordinates());
        assertEquals(GeometryTestSessionState.READY, session.state());
    }

    @Test void incrementalAndCompatibilityRunsUseSameTopology() throws Exception {
        GeometryConfiguration configuration = new GeometryConfigurationGenerator().generate(request())
                .configurations().getFirst();
        GeometryTestRequest test = new GeometryTestRequest(configuration.id(),
                GeometryEmbeddingDimension.TWO_D, 1, 29, 1, null,
                GeometrySymmetryPolicy.MEASURE_ONLY, 2000);
        GeometryTestIncrementalSession incremental = new GeometryTestIncrementalSession(configuration, test);
        incremental.resume();
        while (incremental.state() == GeometryTestSessionState.RUNNING) incremental.step();
        GeometryTestResult complete = new GeometryTestSession(configuration, test).run();
        assertEquals(configuration.edges(), configuration.edges());
        assertEquals(complete.realization().status(), incremental.result().realization().status());
        assertEquals(complete.realization().finalCoordinates().size(),
                incremental.result().realization().finalCoordinates().size());
        for (int index = 0; index < complete.realization().finalCoordinates().size(); index++)
            assertTrue(complete.realization().finalCoordinates().get(index)
                    .distance(incremental.result().realization().finalCoordinates().get(index)) < 1e-9);
    }

    @Test void framingBoundsRemainPositiveAcrossRepresentativeScales() {
        GeometryDiscoveryViewState view = new GeometryDiscoveryViewState();
        GeometryDiscoveryModel small = new GeometryDiscoveryModel("small", "",
                List.of(new DiscoveryNode(0, new org.joml.Vector3d(0, 0, 0), false),
                        new DiscoveryNode(1, new org.joml.Vector3d(1e-9, 0, 0), false)),
                List.of(new DiscoveryConnection(0, 1)));
        GeometryDiscoveryModel large = new GeometryDiscoveryModel("large", "",
                List.of(new DiscoveryNode(0, new org.joml.Vector3d(-1e9, 0, 0), false),
                        new DiscoveryNode(1, new org.joml.Vector3d(1e9, 0, 0), false)),
                List.of(new DiscoveryConnection(0, 1)));
        GeometryViewFit smallFit = view.calculateFit(small);
        GeometryViewFit largeFit = view.calculateFit(large);
        assertTrue(smallFit.nearPlane() > 0 && smallFit.farPlane() > smallFit.nearPlane());
        assertTrue(largeFit.nearPlane() > 0 && largeFit.farPlane() > largeFit.nearPlane());
    }
}
