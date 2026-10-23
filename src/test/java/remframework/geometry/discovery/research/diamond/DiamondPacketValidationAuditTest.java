package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DiamondPacketValidationAuditTest {
 @Test void sensitivityRetainsInvalidResultsAndUsesBaselineDenominator() {
  assertEquals(1,DiamondPacketValidationAudit.relativeChange(.2,.202),1e-10);
  assertEquals(0,DiamondPacketValidationAudit.relativeChange(.2,.2));
  assertTrue(Double.isNaN(DiamondPacketValidationAudit.relativeChange(.2,Double.NaN)));
  assertTrue(Double.isNaN(DiamondPacketValidationAudit.relativeChange(0,.2)));
 }
}