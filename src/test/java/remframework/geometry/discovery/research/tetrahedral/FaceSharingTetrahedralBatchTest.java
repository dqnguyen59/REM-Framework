package remframework.geometry.discovery.research.tetrahedral;

import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.GeometryConfigurationStatusCode;
import static org.junit.jupiter.api.Assertions.*;

class FaceSharingTetrahedralBatchTest {
    @Test void smallBatchAddsRequestedAdditionalNodesAndMeasuresEveryConnection() {
        var builder=new FaceSharingTetrahedralBuilder(10,100,true);
        var result=builder.addNodes(4);
        assertTrue(result.completed());assertEquals(4,result.addedNodes());assertEquals(4,result.attachments());
        assertEquals(8,builder.snapshot().nodeCount());assertTrue(result.lengths().matches());
        for(var edge:builder.model().connections()) {
            var model=builder.model();
            assertEquals(10,model.nodes().get(edge.firstNodeId()).position()
                .distance(model.nodes().get(edge.secondNodeId()).position()),result.lengths().tolerance());
        }
    }
    @Test void batchStopsAtFirstDeformedClosureAndRetainsFailureForUndo() {
        var builder=new FaceSharingTetrahedralBuilder(10,100,true);
        var result=builder.addNodes(20);
        assertFalse(result.completed());assertEquals(7,result.addedNodes());assertEquals(8,result.attachments());
        assertEquals(11,builder.snapshot().nodeCount());assertEquals(9,builder.snapshot().tetrahedra().size());
        assertFalse(result.lengths().matches());assertTrue(result.lengths().absoluteError()>result.lengths().tolerance());
        assertTrue(builder.statuses().stream().anyMatch(s -> s.code()==GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS
            && s.explanation().contains("Batch stopped") && s.explanation().contains("Connection")));
        var failing=builder.snapshot();var retry=builder.addNodes(1);
        assertEquals(0,retry.attachments());assertEquals(failing,builder.snapshot());
        assertTrue(builder.removeNode());assertTrue(builder.checkTargetLengths().matches());
    }
    @Test void invalidCountAndNodeLimitPreserveDiagnoses() {
        var builder=new FaceSharingTetrahedralBuilder(10,4,true);
        assertFalse(builder.addNodes(0).completed());assertEquals(4,builder.snapshot().nodeCount());
        assertTrue(builder.statuses().stream().anyMatch(s -> s.code()==GeometryConfigurationStatusCode.INVALID_PARAMETERS));
        assertFalse(builder.addNodes(1).completed());assertEquals(4,builder.snapshot().nodeCount());
        assertTrue(builder.statuses().stream().anyMatch(s -> s.code()==GeometryConfigurationStatusCode.RESOURCE_LIMIT_REACHED));
    }
}
