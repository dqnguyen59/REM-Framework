package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import org.joml.Vector3d;
import static org.junit.jupiter.api.Assertions.*;
class DiamondDispersionAnalysisTest {
    @Test void acousticPlaneModeMatchesRealSpaceMechanicsAcceleration() {
        var model=DiamondReference.create(3,10);var mechanics=new DiamondMechanics(model,.5,.1);
        int source=model.nodes().stream().filter(n->n.id()%2==0 && n.neighborIds().size()==4
            && n.neighborIds().stream().allMatch(id->model.nodes().get(id).neighborIds().size()==4)).findFirst().orElseThrow().id();
        var q=new Vector3d(.03,.05,.07);double scale=10/Math.sqrt(3),real=0,imag=0;
        for(var bond:java.util.List.of(new Vector3d(1,1,1),new Vector3d(1,-1,-1),new Vector3d(-1,1,-1),new Vector3d(-1,-1,1))) {
            double phase=q.dot(bond.mul(scale));real+=Math.cos(phase);imag+=Math.sin(phase);
        }
        double shift=Math.atan2(imag,real);
        var inverse=new org.joml.Quaterniond().rotationTo(new Vector3d(1,1,1).normalize(),new Vector3d(1,0,0)).conjugate();
        var origin=model.nodes().get(source).position();double amplitude=1e-4;
        for(var n:model.nodes()) if(!mechanics.fixed(n.id())) {
            var crystal=inverse.transform(new Vector3d(n.position()).sub(origin));
            mechanics.displace(n.id(),new Vector3d(amplitude*Math.cos(q.dot(crystal)-(n.id()%2==0?0:shift)),0,0));
        }
        double omega=DiamondDispersionAnalysis.acoustic(q,10,.5).omega();
        assertEquals(-omega*omega*amplitude,mechanics.acceleration(source).x,1e-12);
    }
    @Test void axisBranchMatchesExactStencilSpectrum() {
        double q=.5;var mode=DiamondDispersionAnalysis.acoustic(new Vector3d(q,0,0),1,.5);
        assertEquals(.5*Math.sqrt(1-Math.cos(q/Math.sqrt(3))),mode.omega(),1e-12);
    }
    @Test void longWavelengthLimitIsIndependentOfDirection() {
        for(var d:DiamondDirectionalComparison.directions()) {
            var mode=DiamondDispersionAnalysis.acoustic(new Vector3d(d).mul(1e-4),1,.5);
            assertEquals(.5/Math.sqrt(6),mode.phaseSpeed(),1e-8);assertEquals(.5/Math.sqrt(6),mode.groupVelocity().length(),1e-8);
        }
    }
    @Test void groupVelocityMatchesNumericalFrequencyGradient() {
        var q=new Vector3d(.3,.5,.7);var mode=DiamondDispersionAnalysis.acoustic(q,1,.5);double h=1e-6;
        for(int axis=0;axis<3;axis++) {
            var plus=new Vector3d(q);plus.setComponent(axis,plus.get(axis)+h);var minus=new Vector3d(q);minus.setComponent(axis,minus.get(axis)-h);
            double derivative=(DiamondDispersionAnalysis.acoustic(plus,1,.5).omega()-DiamondDispersionAnalysis.acoustic(minus,1,.5).omega())/(2*h);
            assertEquals(derivative,mode.groupVelocity().get(axis),1e-9);
        }
    }
    @Test void directionalSpreadDecreasesAtLongerWavelength() {
        var shortWave=DiamondDispersionAnalysis.spread(4,256);var longWave=DiamondDispersionAnalysis.spread(32,256);
        assertTrue(shortWave.phase()>longWave.phase());assertTrue(shortWave.group()>longWave.group());
    }
}
