package remframework.geometry.discovery.research.tetrahedral;

import java.util.List;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.*;
import static org.junit.jupiter.api.Assertions.*;

class FaceSharingTetrahedralRelaxationTest {
    @Test void rigidSolverAlignmentPreservesAllDistancesAndTopology() {
        var preview=FaceSharingTetrahedralPreview.create(3,1000,10);
        var aligned=FaceSharingTetrahedralRelaxation.solverInput(preview.model());
        assertEquals(preview.model().connections(),aligned.connections());
        assertEquals(new Vector3d(),aligned.nodes().get(0).position());
        assertTrue(aligned.nodes().get(0).fixedPosition());
        assertEquals(10,aligned.nodes().get(1).position().x,1e-10);
        assertEquals(0,aligned.nodes().get(1).position().y,1e-10);
        assertEquals(0,aligned.nodes().get(1).position().z,1e-10);
        for(int a=0;a<aligned.nodes().size();a++) for(int b=a+1;b<aligned.nodes().size();b++)
            assertEquals(preview.model().nodes().get(a).position().distance(preview.model().nodes().get(b).position()),
                aligned.nodes().get(a).position().distance(aligned.nodes().get(b).position()),1e-10);
        assertTrue(new GeometryDiscoveryValidator().validate(aligned,GeometryDiscoverySettings.defaults(10)).isValid());
    }

    @Test void boundedTrialRetainsTopologyBaselineAndFailureReasons() {
        var preview=FaceSharingTetrahedralPreview.create(3,1000,10);
        var positions=preview.model().nodes().stream().map(n -> new Vector3d(n.position())).toList();
        var trial=FaceSharingTetrahedralRelaxation.run(preview,GeometryDiscoverySettings.defaults(10).withMaximumIterations(2000));
        assertEquals(56,trial.result().model().nodes().size());
        assertEquals(preview.model().connections(),trial.result().model().connections());
        assertEquals(preview.topology().generations().getLast().degrees(),
            trial.result().model().sortedNodes().stream().map(n -> n.neighborIds().size()).toList());
        assertEquals(positions,preview.model().nodes().stream().map(DiscoveryNode::position).toList());
        assertEquals(2000,trial.result().metrics().iterationCount());
        assertTrue(trial.result().metrics().allFinite());
        assertFalse(trial.result().converged());
        assertEquals(42,trial.before().overlappingTetrahedronPairs());
        assertTrue(trial.after().minimumSeparation()>trial.before().minimumSeparation());
        assertTrue(trial.after().separationViolations()>0);
        assertTrue(trial.after().overlappingTetrahedronPairs()>0);
        assertTrue(trial.statuses().stream().anyMatch(s -> s.code()==GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS && !s.mayContinue()));
        assertTrue(trial.statuses().stream().anyMatch(s -> s.code()==GeometryConfigurationStatusCode.SELF_INTERSECTING && !s.mayContinue()));
        assertTrue(trial.statuses().stream().noneMatch(s -> s.code()==GeometryConfigurationStatusCode.VALID_CANDIDATE));
    }

    @Test void simpleSeedCanConvergeWithoutBeingQualifiedAsIsotropic() {
        var trial=FaceSharingTetrahedralRelaxation.run(FaceSharingTetrahedralPreview.create(0,4,10),
            GeometryDiscoverySettings.defaults(10).withMaximumIterations(100));
        assertTrue(trial.result().converged());
        assertEquals(0,trial.after().overlappingTetrahedronPairs());
        assertEquals(0,trial.after().separationViolations());
        assertEquals(0,trial.after().degenerateTetrahedra());
        assertTrue(trial.statuses().stream().anyMatch(s -> s.code()==GeometryConfigurationStatusCode.GEOMETRIC_SYMMETRY_UNKNOWN));
    }

    @Test void invalidSolverParametersProduceStructuredDiagnostic() {
        var trial=FaceSharingTetrahedralRelaxation.run(FaceSharingTetrahedralPreview.create(0,4,10),
            GeometryDiscoverySettings.defaults(10).withMaximumIterations(0));
        assertTrue(trial.statuses().stream().anyMatch(s -> s.code()==GeometryConfigurationStatusCode.INVALID_PARAMETERS
            && !s.mayContinue() && s.explanation().contains("maximumIterations")));
    }
}
