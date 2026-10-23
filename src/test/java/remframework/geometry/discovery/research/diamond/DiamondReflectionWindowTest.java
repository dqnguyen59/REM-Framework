package remframework.geometry.discovery.research.diamond;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DiamondReflectionWindowTest {
    private static List<Integer> line(int node){var result=new ArrayList<Integer>();if(node>0)result.add(node-1);if(node<10)result.add(node+1);return result;}
    @Test void waitsUntilReflectionsCouldReachAnyReceiver() {
        assertEquals(6,DiamondReflectionWindow.safeSteps(List.of(5),List.of(0,10),List.of(2,8),DiamondReflectionWindowTest::line));
        assertEquals(9,DiamondReflectionWindow.safeSteps(List.of(5),List.of(0,10),List.of(5),DiamondReflectionWindowTest::line));
        assertEquals(5,DiamondReflectionWindow.safeSteps(List.of(4,5,6),List.of(0,10),List.of(2,8),DiamondReflectionWindowTest::line));
    }
    @Test void sameBoundaryMustBeUsedForBothLegsAndDisconnectedFails() {
        assertEquals(9,DiamondReflectionWindow.safeSteps(List.of(1),List.of(0,10),List.of(9),DiamondReflectionWindowTest::line));
        assertThrows(IllegalArgumentException.class,()->DiamondReflectionWindow.safeSteps(List.of(1),List.of(10),List.of(9),n->List.of()));
    }
}
