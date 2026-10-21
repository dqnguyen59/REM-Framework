package remframework.lattice;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.GeometryConfigurationStatusCode;
import static org.junit.jupiter.api.Assertions.*;
class DiamondMemoryBudgetTest {
    @Test void capacityDependsOnBudgetRatherThanAxisCap() {
        var size=new Vector3i(100,100,100);
        long required=1_000_000L*DiamondMemoryBudget.ESTIMATED_BYTES_PER_NODE;
        assertTrue(DiamondMemoryBudget.estimate(size,required).allowed());
        var denied=DiamondMemoryBudget.estimate(size,required-1);
        assertFalse(denied.allowed());assertEquals(GeometryConfigurationStatusCode.RESOURCE_LIMIT_REACHED,denied.code());
        assertEquals(required,denied.requiredBytes());
    }
    @Test void rejectsInvalidAndOverflowRequestsWithoutAllocating() {
        assertEquals(GeometryConfigurationStatusCode.INVALID_PARAMETERS,DiamondMemoryBudget.estimate(new Vector3i(3),Long.MAX_VALUE).code());
        assertFalse(DiamondMemoryBudget.estimate(new Vector3i(Integer.MAX_VALUE-1),Long.MAX_VALUE).allowed());
        assertFalse(DiamondMemoryBudget.estimate(new Vector3i(2),0).allowed());
        assertTrue(DiamondMemoryBudget.availableBudget()>=0);
    }
}
