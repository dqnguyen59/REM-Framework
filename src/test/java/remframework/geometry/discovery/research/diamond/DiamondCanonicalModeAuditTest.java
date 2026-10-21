package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import org.joml.Vector3d;
import static org.junit.jupiter.api.Assertions.*;
class DiamondCanonicalModeAuditTest {
    @Test void actualKernelMatchesPlaneModeRecurrence() {
        for(var d:DiamondDirectionalComparison.directions()) assertTrue(DiamondCanonicalModeAudit.kernelResidual(d,8)<1e-10);
    }
    @Test void longWaveModesMatchActualKernel() {
        for(double ratio:new double[]{16,32,128})
            for(var d:DiamondDirectionalComparison.directions()) assertTrue(DiamondCanonicalModeAudit.kernelResidual(d,ratio)<1e-10);
    }
    @Test void longWavePhaseLimitAndAttenuationAreFinite() {
        for(var d:DiamondDirectionalComparison.directions()) {
            var m=DiamondCanonicalModeAudit.acoustic(new Vector3d(d).mul(.001),1,.5);
            assertEquals(.5/Math.sqrt(6),m.phaseSpeed(),1e-7);
            assertTrue(m.attenuationPerFrame()>0 && m.attenuationPerFrame()<1);
        }
    }
}

