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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeometryDiscoveryPhase2UiTest {
    private static ConfigurationRequest request() {
        return new ConfigurationRequest(4, 2, GeometryEmbeddingDimension.TWO_D,
                GraphSymmetryFilter.BOTH, 20, Duration.ofSeconds(5), null, null);
    }

    @Test void generationActionExposesStatusAndRows() {
        PhasedWorkflow workflow = new PhasedWorkflow();
        GenerationResult result = assertDoesNotThrow(() -> workflow.generateConfigurations(request()));
        assertEquals(GeometryConfigurationStatusCode.ENUMERATION_COMPLETE, result.status().code());
        assertEquals(1, workflow.tableRows().size());
        assertEquals(GeometryConfigurationStatusCode.GEOMETRY_TEST_NOT_STARTED,
                workflow.tableRows().getFirst().geometryTestStatus());
    }

    @Test void geometryTestCannotStartWithoutExactlyOneSelection() throws Exception {
        PhasedWorkflow workflow = new PhasedWorkflow();
        workflow.generateConfigurations(request());
        ConfigurationId id = workflow.generation().orElseThrow().configurations().getFirst().id();
        TestRequest test = new TestRequest(id, GeometryEmbeddingDimension.TWO_D,
                1, 42, 1, null, GeometrySymmetryPolicy.MEASURE_ONLY, 1000);
        assertThrows(IllegalStateException.class, () -> workflow.runSelectedGeometryTest(test));
        workflow.selectForGeometryTest(id);
        assertDoesNotThrow(() -> workflow.runSelectedGeometryTest(test));
    }

    @Test void selectedTopologyRemainsImmutableAfterTest() throws Exception {
        PhasedWorkflow workflow = new PhasedWorkflow();
        workflow.generateConfigurations(request());
        Configuration configuration = workflow.generation().orElseThrow().configurations().getFirst();
        List<DiscoveryConnection> edges = configuration.edges();
        workflow.selectForGeometryTest(configuration.id());
        workflow.runSelectedGeometryTest(new TestRequest(configuration.id(),
                GeometryEmbeddingDimension.TWO_D, 1, 7, 1, null,
                GeometrySymmetryPolicy.MEASURE_ONLY, 500));
        assertEquals(edges, workflow.selectedConfigurationRecord().orElseThrow().edges());
    }

    @Test void persistedResultCanBeReopened(@TempDir Path temp) throws Exception {
        PhasedWorkflow workflow = new PhasedWorkflow();
        workflow.generateConfigurations(request());
        ConfigurationId id = workflow.generation().orElseThrow().configurations().getFirst().id();
        workflow.selectForGeometryTest(id);
        TestResult result = workflow.runSelectedGeometryTest(new TestRequest(id,
                GeometryEmbeddingDimension.TWO_D, 1, 8, 1, null,
                GeometrySymmetryPolicy.MEASURE_ONLY, 500));
        Path saved = new TestResultStore().save(temp, result);
        StoredResult reopened = new TestResultStore().load(saved);
        assertEquals(id, reopened.configurationId());
        assertEquals(result.status().code(), reopened.status());
        assertEquals(result.realization().finalCoordinates(), reopened.finalCoordinates());
    }

    @Test void areaAndOptimizationAreGatedByClosedBoundary() {
        Configuration configuration = new ConfigurationGenerator().generate(request())
                .configurations().getFirst();
        var diagnostic = GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.BOUNDARY_VALIDATION,
                GeometryConfigurationStatusCode.OPEN_BOUNDARY, "Open", "Boundary is open.");
        GeometryConfigurationStatus status = GeometryConfigurationStatus.of(diagnostic);
        Realization realization = new Realization(1, GeometryEmbeddingDimension.TWO_D,
                List.of(), List.of(), 0, 0, false, false, false, 2,
                RealizationStatus.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS, List.of(diagnostic));
        ClosedBoundaryResult open = new ClosedBoundaryResult(false, false, false, false, false,
                0, List.of(), status, List.of(diagnostic));
        assertFalse(new GeometryMeasureCalculator().area(realization, open).available());
        assertFalse(new GeometryAreaOptimizer().maximize2d(configuration, realization, open, 1, 10).attempted());
    }

    @Test void failureStatusRetainsDetailedReason() {
        ConfigurationRequest invalid = new ConfigurationRequest(4, 4,
                GeometryEmbeddingDimension.THREE_D, GraphSymmetryFilter.BOTH, 10,
                Duration.ofSeconds(1), null, null);
        GenerationResult result = new ConfigurationGenerator().generate(invalid);
        assertFalse(result.status().mayContinue());
        assertFalse(result.status().explanation().isBlank());
        assertFalse(result.status().diagnostics().isEmpty());
    }

    @Test void topologySelectionCreatesNonEmptyRenderModel() throws Exception {
        Configuration configuration = new ConfigurationGenerator().generate(request())
                .configurations().getFirst();
        PhasedWorkflow workflow = new PhasedWorkflow();
        workflow.generateConfigurations(request());
        workflow.selectForGeometryTest(configuration.id());
        RenderModel render = workflow.renderModel();
        assertNotNull(render);
        assertTrue(render.topologyPreview());
        assertEquals(configuration.nodeCount(), render.model().nodes().size());
        assertEquals(configuration.edges().size(), render.model().connections().size());
    }

    @Test void displayTransformDoesNotChangeScientificCoordinates() {
        List<org.joml.Vector3d> scientific = List.of(new org.joml.Vector3d(10, 2, 0),
                new org.joml.Vector3d(12, 2, 0), new org.joml.Vector3d(11, 4, 0));
        DisplayTransform transform = DisplayTransform
                .calculate(scientific, 4);
        assertEquals(10, scientific.getFirst().x);
        assertNotEquals(scientific.getFirst(), transform.display(scientific.getFirst()));
        assertEquals(10, scientific.getFirst().x);
    }

    @Test void incrementalSessionTransitionsAndResetAreDeterministic() throws Exception {
        Configuration configuration = new ConfigurationGenerator().generate(request())
                .configurations().getFirst();
        TestRequest test = new TestRequest(configuration.id(),
                GeometryEmbeddingDimension.TWO_D, 1, 19, 1, null,
                GeometrySymmetryPolicy.MEASURE_ONLY, 1000);
        IncrementalSession session = new IncrementalSession(configuration, test);
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
        assertEquals(SessionState.READY, session.state());
    }

    @Test void incrementalAndCompatibilityRunsUseSameTopology() throws Exception {
        Configuration configuration = new ConfigurationGenerator().generate(request())
                .configurations().getFirst();
        TestRequest test = new TestRequest(configuration.id(),
                GeometryEmbeddingDimension.TWO_D, 1, 29, 1, null,
                GeometrySymmetryPolicy.MEASURE_ONLY, 2000);
        IncrementalSession incremental = new IncrementalSession(configuration, test);
        incremental.resume();
        while (incremental.state() == SessionState.RUNNING) incremental.step();
        TestResult complete = new TestSession(configuration, test).run();
        assertEquals(configuration.edges(), configuration.edges());
        assertEquals(complete.realization().status(), incremental.result().realization().status());
        assertEquals(complete.realization().finalCoordinates().size(),
                incremental.result().realization().finalCoordinates().size());
        for (int index = 0; index < complete.realization().finalCoordinates().size(); index++)
            assertTrue(complete.realization().finalCoordinates().get(index)
                    .distance(incremental.result().realization().finalCoordinates().get(index)) < 1e-9);
    }

    @Test void framingBoundsRemainPositiveAcrossRepresentativeScales() {
        ViewState view = new ViewState();
        GeometryDiscoveryModel small = new GeometryDiscoveryModel("small", "",
                List.of(new DiscoveryNode(0, new org.joml.Vector3d(0, 0, 0), false),
                        new DiscoveryNode(1, new org.joml.Vector3d(1e-9, 0, 0), false)),
                List.of(new DiscoveryConnection(0, 1)));
        GeometryDiscoveryModel large = new GeometryDiscoveryModel("large", "",
                List.of(new DiscoveryNode(0, new org.joml.Vector3d(-1e9, 0, 0), false),
                        new DiscoveryNode(1, new org.joml.Vector3d(1e9, 0, 0), false)),
                List.of(new DiscoveryConnection(0, 1)));
        ViewFit smallFit = view.calculateFit(small);
        ViewFit largeFit = view.calculateFit(large);
        assertTrue(smallFit.nearPlane() > 0 && smallFit.farPlane() > smallFit.nearPlane());
        assertTrue(largeFit.nearPlane() > 0 && largeFit.farPlane() > largeFit.nearPlane());
    }

    @Test void topologyInspectionUsesCanonicalEdgesAndSymmetricAdjacency() {
        Configuration configuration = new ConfigurationGenerator().generate(request())
                .configurations().getFirst();
        TopologyInspection inspection = TopologyInspection.from(configuration);
        assertEquals(configuration.edges().size(), inspection.edgeCount());
        assertEquals(inspection.vertexCount() * inspection.expectedDegree() / 2, inspection.edgeCount());
        assertTrue(inspection.adjacencySymmetric());
        assertTrue(inspection.degreeMismatches().isEmpty());
        assertEquals(inspection.edgeCount(), inspection.edgeLines().stream().distinct().count());
        assertTrue(inspection.edgeLines().stream().allMatch(line -> line.matches("\\d+ -- \\d+")));
    }

    @Test void cubicEightNodeTopologyHasTwelveConnections() {
        ConfigurationRequest cubic = new ConfigurationRequest(8, 3,
                GeometryEmbeddingDimension.THREE_D, GraphSymmetryFilter.BOTH,
                100, Duration.ofSeconds(10), null, null);
        Configuration configuration = new ConfigurationGenerator().generate(cubic)
                .configurations().getFirst();
        TopologyInspection inspection = TopologyInspection.from(configuration);
        assertEquals(12, inspection.edgeCount());
        assertEquals(8, inspection.adjacency().size());
        assertTrue(inspection.adjacency().stream().allMatch(row -> row.size() == 3));
    }

    @Test void allFiveCubicEightVertexConfigurationsRemainSelectable() throws Exception {
        ConfigurationRequest cubic = new ConfigurationRequest(8, 3,
                GeometryEmbeddingDimension.THREE_D, GraphSymmetryFilter.BOTH,
                100, Duration.ofSeconds(10), null, null);
        PhasedWorkflow workflow = new PhasedWorkflow();
        workflow.generateConfigurations(cubic);
        assertEquals(5, workflow.tableRows().size());
        for (Configuration configuration : workflow.generation().orElseThrow().configurations()) {
            workflow.selectForGeometryTest(configuration.id());
            assertEquals(configuration.id(), workflow.selectedConfiguration().orElseThrow());
            assertEquals(configuration.edges(), workflow.selectedConfigurationRecord().orElseThrow().edges());
            assertTrue(workflow.latestGeometryTest(configuration.id()) == null);
        }
    }

    @Test void untestedSolvabilityIsExplicitlyUnknown() {
        assertEquals("Not tested", StatusPresentation.equalEdgeConclusion(null, null));
        assertEquals("NOT_TESTED", StatusPresentation.realizationState(null));
    }
}
