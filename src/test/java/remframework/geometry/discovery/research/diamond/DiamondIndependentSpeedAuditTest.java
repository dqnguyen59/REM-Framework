package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DiamondIndependentSpeedAuditTest {
 @Test void spreadUsesMeanAndRequiresEveryDirection() {
  var values=new ArrayList<Double>(Collections.nCopies(26,.2));assertEquals(0,DiamondIndependentSpeedAudit.spread(values));
  values.set(0,.21);assertEquals(100*.01/(5.21/26),DiamondIndependentSpeedAudit.spread(values),1e-10);
  values.set(0,Double.NaN);assertTrue(Double.isNaN(DiamondIndependentSpeedAudit.spread(values)));
  assertTrue(Double.isNaN(DiamondIndependentSpeedAudit.spread(List.of(.2))));
 }
}