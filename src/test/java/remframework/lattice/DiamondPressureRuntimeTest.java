package remframework.lattice;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import remframework.opengl.controller.DiamondOccupancyPressure;
import remframework.opengl.controller.TransportSystem;
import remframework.opengl.model.Lattice;
import remframework.opengl.model.entity.*;
import remframework.settings.Project;
import static org.junit.jupiter.api.Assertions.*;

class DiamondPressureRuntimeTest {
    @Test void runtimeOptInAccumulatesPressureWithoutTransferOrMomentumCaps() throws Exception {
        var project=Project.getInstance();var manager=LatticeManager.getInstance();manager.initialize();
        var lattice=Lattice.getInstance();var transport=TransportSystem.getInstance();
        String oldModel=manager.selected().identifier();Vector3i oldSize=new Vector3i();project.getDimension(oldSize);
        double oldG=transport.getGradientGain(),oldA=transport.getAccelerationGain(),oldM=transport.getTransportMomentumGain();
        double oldMax=transport.getTransportMomentumMax(),oldThreshold=transport.getTransferThreshold();
        boolean oldEnable=transport.isEnableTransport();
        try {
            manager.select(LatticeManager.EXPERIMENTAL_DIAMOND_ID);project.setDimension(10,10,10,false);
            lattice.clearEntities();lattice.buildHeadless();ready(project);lattice.update();
            assertFalse(lattice.isDiamondPressureEnabled());
            Node source=java.util.Arrays.stream(lattice.getNodeArray()).map(n->(Node)n)
                    .filter(n->n.getType()!=EntityType.FIXED)
                    .min(java.util.Comparator.comparingDouble(n->n.getEquilibriumPosition().lengthSquared())).orElseThrow();
            Node neighbor=java.util.Arrays.stream(source.getNeighbors()).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
            Vector3d delta=new Vector3d(neighbor.getPosition()).sub(source.getPosition()).normalize().mul(.1);
            neighbor.setPosition(new Vector3d(neighbor.getPosition()).add(delta));neighbor.updatePosition();
            Identity identity=new Identity("runtime pressure");identity.getAmplitude().set(.2,0,0);
            Occupancy actual=new Occupancy(identity,source);
            Identity referenceIdentity=new Identity("expected pressure");referenceIdentity.getAmplitude().set(.2,0,0);
            Occupancy expected=new Occupancy(referenceIdentity,source);
            actual.getPressure().set(.1,.2,.3);expected.getPressure().set(actual.getPressure());
            transport.setGradientGain(2);transport.setAccelerationGain(3);transport.setTransportMomentumGain(.5);
            transport.setTransportMomentumMax(0);transport.setTransferThreshold(0);transport.setEnableTransport(true);
            transport.update(actual);assertEquals(new Vector3d(.1,.2,.3),actual.getPressure());
            lattice.setDiamondPressureEnabled(true);
            for(int i=0;i<10;i++) {
                transport.update(actual);DiamondOccupancyPressure.update(expected,2,3,.5);
                assertEquals(expected.getPressure(),actual.getPressure());assertSame(source,actual.getNode());
            }
            assertTrue(actual.getTransportMomentum().length()>0);assertEquals(0,actual.getTransportCyclesX());
            Vector3d retained=new Vector3d(actual.getPressure());
            lattice.setDiamondPressureEnabled(false);transport.update(actual);assertEquals(retained,actual.getPressure());
            lattice.setDiamondPressureEnabled(true);assertEquals(retained,actual.getPressure());
            lattice.getDisturbance().request(8,.001);lattice.getDisturbance().initializePending(lattice);
            assertFalse(lattice.getDisturbance().running());
            assertTrue(lattice.getDisturbance().summary().contains("occupancy pressure"));
            lattice.clearEntities();lattice.buildHeadless();ready(project);lattice.update();
            assertFalse(lattice.isDiamondPressureEnabled());
        } finally {
            lattice.setDiamondPressureEnabled(false);
            transport.setGradientGain(oldG);transport.setAccelerationGain(oldA);transport.setTransportMomentumGain(oldM);
            transport.setTransportMomentumMax(oldMax);transport.setTransferThreshold(oldThreshold);transport.setEnableTransport(oldEnable);
            project.getRemModel().stopSimulation();manager.select(oldModel);project.setDimension(2,2,2,false);
            lattice.clearEntities();lattice.buildHeadless();ready(project);lattice.update();project.setDimension(oldSize.x,oldSize.y,oldSize.z,false);
        }
    }
    private void ready(Project project) throws InterruptedException {
        long end=System.nanoTime()+30_000_000_000L;
        while(!project.getRemModel().isLoadingReady()) {if(System.nanoTime()>end)fail("Build timeout");Thread.sleep(5);}
    }
}
