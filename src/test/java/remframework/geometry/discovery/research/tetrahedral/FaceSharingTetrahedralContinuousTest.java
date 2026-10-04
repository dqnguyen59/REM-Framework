package remframework.geometry.discovery.research.tetrahedral;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FaceSharingTetrahedralContinuousTest {
    @Test void continuousBatchCrossesFirstDeformedClosureAndKeepsUpdating() {
        var b=new FaceSharingTetrahedralBuilder(10,100,true);b.enableContinuousLayout();b.queueNodes(8);
        for(int frame=0;frame<2000 && b.remainingBatchNodes()>0;frame++) b.updateLayout(20);
        assertEquals(0,b.remainingBatchNodes());assertEquals(12,b.snapshot().nodeCount());
        assertTrue(b.checkTargetLengths().absoluteError()>1e-7);
        int before=b.liveUpdates();var topology=b.snapshot();b.updateLayout(20);
        assertTrue(b.liveUpdates()>before);assertEquals(topology,b.snapshot());
        b.toggleLayoutPause();before=b.liveUpdates();b.updateLayout(20);assertEquals(before,b.liveUpdates());
        b.toggleLayoutPause();b.updateLayout(20);assertTrue(b.liveUpdates()>before);
    }
    @Test void desiredTotalIncludesSeedAndDoesNotOvershoot() {
        var b=new FaceSharingTetrahedralBuilder(10,100,true);b.enableContinuousLayout();b.queueToTotal(12);
        for(int frame=0;frame<2000 && b.remainingBatchNodes()>0;frame++) b.updateLayout(20);
        assertEquals(12,b.snapshot().nodeCount());assertEquals(0,b.remainingBatchNodes());
        b.updateLayout(100);assertEquals(12,b.snapshot().nodeCount());
        b.queueToTotal(12);assertEquals(0,b.remainingBatchNodes());
        b.queueToTotal(11);assertEquals(12,b.snapshot().nodeCount());
    }
    @Test void cancellationAndUndoDoNotLeaveBatchRunning() {
        var b=new FaceSharingTetrahedralBuilder(10,100,true);b.enableContinuousLayout();b.queueNodes(10);
        b.cancelBatch();b.updateLayout(20);assertEquals(4,b.snapshot().nodeCount());
        b.addNode();b.updateLayout(20);assertTrue(b.removeNode());assertEquals(4,b.snapshot().nodeCount());
        b.queueNodes(5);b.reset();assertEquals(0,b.remainingBatchNodes());assertEquals(4,b.snapshot().nodeCount());
    }
}
