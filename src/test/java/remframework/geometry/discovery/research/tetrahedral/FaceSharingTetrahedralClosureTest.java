package remframework.geometry.discovery.research.tetrahedral;

import java.util.*;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.*;
import static org.junit.jupiter.api.Assertions.*;

class FaceSharingTetrahedralClosureTest {
    @Test void explicitIdentificationProducesValidClosedFiveTetrahedronRing() {
        var candidate=FaceSharingTetrahedralClosure.candidate(10);
        assertEquals(GeometryConfigurationStatusCode.GENERATED,candidate.status().code());
        assertEquals(7,candidate.model().nodes().size());
        assertEquals(16,candidate.model().connections().size());
        assertEquals(5,candidate.topology().tetrahedra().size());
        assertEquals(10,candidate.topology().exposedFaces().size());
        assertEquals(15,candidate.topology().faceIncidence().size());
        assertEquals(6,candidate.originalNodeMapping().get(8));
        assertEquals(6,candidate.originalNodeMapping().get(11));
        assertEquals(List.of(6,6,4,4,4,4,4),candidate.topology().degrees());
        assertEquals(16,candidate.model().connections().stream().map(DiscoveryConnection::undirectedKey).distinct().count());
        assertTrue(candidate.topology().faceIncidence().values().stream().allMatch(n -> n==1 || n==2));
        assertTrue(candidate.topology().tetrahedra().stream().allMatch(t ->
            Set.of(t.a(),t.b(),t.c(),t.d()).size()==4));
        assertTrue(FaceSharingTetrahedralClosure.validateClosedRing(candidate.topology()).isEmpty());
        var snapshot=candidate.topology();
        var corrupt=new FaceSharingTetrahedralExperiment.Snapshot(snapshot.generation(),snapshot.nodeBirthGenerations(),
            snapshot.edges(),snapshot.tetrahedra().subList(0,4),snapshot.faceIncidence());
        assertFalse(FaceSharingTetrahedralClosure.validateClosedRing(corrupt).isEmpty());
        assertThrows(UnsupportedOperationException.class,() -> candidate.originalNodeMapping().clear());
    }

    @Test void deformedRingSettlesWithoutExactEdgeOrCrystalQualification() {
        var trial=FaceSharingTetrahedralClosure.run(10,2000);
        assertTrue(trial.stationary());
        assertTrue(trial.iterations()<2000);
        assertTrue(trial.result().metrics().allFinite());
        assertFalse(trial.result().converged()); // Existing exact-edge criterion is deliberately unchanged.
        assertEquals(0,trial.assessment().overlappingTetrahedronPairs());
        assertEquals(0,trial.assessment().degenerateTetrahedra());
        assertEquals(0,trial.assessment().separationViolations());
        assertTrue(trial.maximumRelativeEdgeError()>0);
        assertTrue(trial.maximumRelativeEdgeError()<.02);
        assertEquals(trial.candidate().model().connections(),trial.result().model().connections());
        assertTrue(trial.statuses().stream().noneMatch(s -> s.code()==GeometryConfigurationStatusCode.VALID_CANDIDATE));
    }

    @Test void targetChangesScaleThisTrialAndSearchLimitsRemainExplicit() {
        var full=FaceSharingTetrahedralClosure.run(10,2000);
        var smaller=FaceSharingTetrahedralClosure.run(5,2000);
        assertTrue(smaller.stationary());
        assertEquals(full.maximumRelativeEdgeError(),smaller.maximumRelativeEdgeError(),1e-8);
        assertEquals(.5*full.assessment().minimumSeparation(),smaller.assessment().minimumSeparation(),1e-7);
        var limited=FaceSharingTetrahedralClosure.run(10,1);
        assertFalse(limited.stationary());
        assertEquals(1,limited.iterations());
        assertTrue(limited.statuses().stream().anyMatch(s ->
            s.code()==GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS && !s.mayContinue()));
        assertEquals(7,limited.result().model().nodes().size());
        var invalid=FaceSharingTetrahedralClosure.run(Double.NaN,2000);
        assertNull(invalid.result());
        assertEquals(GeometryConfigurationStatusCode.INVALID_PARAMETERS,invalid.statuses().getFirst().code());
        var invalidBudget=FaceSharingTetrahedralClosure.run(10,0);
        assertTrue(invalidBudget.statuses().stream().anyMatch(s -> s.code()==GeometryConfigurationStatusCode.INVALID_PARAMETERS));
    }

    @Test void viewerSwitchIsIsolatedAndRestoresBuilderState() {
        var viewer=new GeometryDiscoveryVisualizer(FaceSharingTetrahedralPreview.firstLayer(10),
            GeometryDiscoverySettings.defaults(10),"test");
        viewer.enableTetrahedralBuilder(10,100);
        for(int i=0;i<9;i++) viewer.addTetrahedralNode();
        var original=viewer.tetrahedralBuilder().snapshot();
        String progress=viewer.tetrahedralBuilder().progress();
        viewer.showTetrahedralClosure(10);
        assertNull(viewer.tetrahedralBuilder());
        assertNotNull(viewer.closureTrial());
        assertEquals(7,viewer.session().model().nodes().size());
        viewer.start();viewer.singleStep();viewer.runToConvergence();viewer.updateFrame();
        assertFalse(viewer.session().isRunning());
        viewer.showTetrahedralClosure(5);
        assertEquals(5,viewer.session().settings().targetDistance());
        viewer.returnToTetrahedralBuilder();
        assertNull(viewer.closureTrial());
        assertEquals(original,viewer.tetrahedralBuilder().snapshot());
        assertEquals(progress,viewer.tetrahedralBuilder().progress());
        assertEquals(10,viewer.tetrahedralBuilder().baseLength());
    }
}
