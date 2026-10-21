package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import org.joml.Vector3d;
import static org.junit.jupiter.api.Assertions.*;
class DiamondSpectralPacketAuditTest {
    @Test void fullBandInitializationTracksMixtureForInteriorShortWindow() {
        var r=DiamondSpectralPacketAudit.run(8,16,new Vector3d(1,0,0),1);
        assertTrue(r.measured().isFinite());assertTrue(r.mixturePrediction().isFinite());
        assertTrue(r.relativeFieldError()<.05,"Boundary truncation should remain a small short-window field error");
    }
    @Test void bandwidthAndQuadratureLimitsFailExplicitly() {
        assertThrows(IllegalArgumentException.class,()->DiamondSpectralPacketAudit.run(8,16,new Vector3d(1,0,0),1,Double.NaN,4));
        assertThrows(IllegalArgumentException.class,()->DiamondSpectralPacketAudit.run(8,16,new Vector3d(1,0,0),1,8,3));
    }
    @Test void invalidSpectralSetupFailsExplicitly() {
        assertThrows(IllegalArgumentException.class,()->DiamondSpectralPacketAudit.run(8,16,new Vector3d(),1));
        assertThrows(IllegalArgumentException.class,()->DiamondSpectralPacketAudit.run(8,Double.NaN,new Vector3d(1,0,0),1));
    }
}

