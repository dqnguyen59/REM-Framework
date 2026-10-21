package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import org.joml.Vector3d;
import static org.junit.jupiter.api.Assertions.*;
class DiamondCanonicalGroupAuditTest {
    private static double frequency(Vector3d q) {
        return DiamondCanonicalModeAudit.acoustic(q,1,.5).phaseSpeed()*q.length();
    }
    @Test void groupMatchesDiscreteFrequencyGradient() {
        for(var q:java.util.List.of(new Vector3d(.3,.5,.7),new Vector3d(1,0,0),new Vector3d(.2,.2,.2))) {
            var group=DiamondCanonicalGroupAudit.groupVelocity(q,1,.5);double h=1e-5;
            for(int axis=0;axis<3;axis++) {
                var plus=new Vector3d(q);plus.setComponent(axis,plus.get(axis)+h);
                var minus=new Vector3d(q);minus.setComponent(axis,minus.get(axis)-h);
                assertEquals((frequency(plus)-frequency(minus))/(2*h),group.get(axis),1e-8);
            }
        }
    }
    @Test void longWaveGroupLimitIsIsotropic() {
        for(var d:DiamondDirectionalComparison.directions()) {
            var group=DiamondCanonicalGroupAudit.groupVelocity(new Vector3d(d).mul(1e-4),1,.5);
            assertTrue(group.distance(new Vector3d(d).mul(.5/Math.sqrt(6)))<1e-8);
        }
    }
    @Test void zeroWavevectorFailsExplicitly() {
        assertThrows(IllegalArgumentException.class,()->DiamondCanonicalGroupAudit.groupVelocity(new Vector3d(),1,.5));
    }
}
