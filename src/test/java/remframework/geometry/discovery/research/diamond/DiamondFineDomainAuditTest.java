package remframework.geometry.discovery.research.diamond;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DiamondFineDomainAuditTest {
    @Test void enlargedSampleHasExpectedCountAndDegreeBounds() {
        var model=DiamondReference.create(9,5);assertEquals(5832,model.nodes().size());
        assertEquals(4913,model.nodes().stream().filter(n->n.neighborIds().size()==4).count());
        assertTrue(model.nodes().stream().allMatch(n->n.neighborIds().size()>=1 && n.neighborIds().size()<=4));
        assertThrows(IllegalArgumentException.class,()->DiamondReference.create(21,5));
    }
    @Test void expandedHeadlessDomainPreservesTopologyAndNormalLimits() {
        var model=DiamondReference.createResearchDomain(21,1);
        assertEquals(8*21*21*21,model.nodes().size());
        assertEquals(41*41*41,model.nodes().stream().filter(n->n.neighborIds().size()==4).count());
        assertTrue(model.connections().stream().allMatch(e->Math.abs(model.nodes().get(e.firstNodeId()).position().distance(model.nodes().get(e.secondNodeId()).position())-1)<1e-10));
        assertThrows(IllegalArgumentException.class,()->DiamondReference.createResearchDomain(33,1));
        assertThrows(IllegalArgumentException.class,()->DiamondReference.create(21,1));
    }
    @Test void matchingMeasurementsHaveZeroDomainDifference() {
        var r=DiamondDirectionalComparison.run(4,10,.5,.1,true,15);
        assertEquals(0,DiamondFineDomainAudit.peakDifference(r,r));
    }
}

