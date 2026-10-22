package remframework.lattice;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import remframework.opengl.controller.TransportSystem;
import remframework.opengl.model.Lattice;
import remframework.opengl.model.entity.*;
import remframework.settings.Project;
import static org.junit.jupiter.api.Assertions.*;

class DiamondTransportRuntimeTest {
    @Test void optInMovesOccupancyLocallyPreservesResidualAndPublishesMeasurements() throws Exception {
        var project=Project.getInstance();var manager=LatticeManager.getInstance();manager.initialize();
        var lattice=Lattice.getInstance();var transport=TransportSystem.getInstance();
        String oldModel=manager.selected().identifier();Vector3i oldSize=new Vector3i();project.getDimension(oldSize);
        double oldG=transport.getGradientGain(),oldA=transport.getAccelerationGain(),oldM=transport.getTransportMomentumGain(),oldT=transport.getTransferThreshold();
        boolean oldEnable=transport.isEnableTransport();
        try {
            manager.select(LatticeManager.EXPERIMENTAL_DIAMOND_ID);project.setDimension(10,10,10,false);
            lattice.clearEntities();lattice.buildHeadless();ready(project);lattice.update();
            assertFalse(lattice.isDiamondTransportEnabled());
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),()-> {
                var model=new remframework.gui.model.NuklearModel();
                assertNotNull(model.createInstance(remframework.gui.model.NuklearModel.Frame.addProbes));
            });
            Node source=java.util.Arrays.stream(lattice.getNodeArray()).map(n->(Node)n)
                .filter(n->!n.isFixed()).min(java.util.Comparator.comparingDouble(n->n.getEquilibriumPosition().lengthSquared())).orElseThrow();
            Node target=java.util.Arrays.stream(source.getNeighbors()).filter(n->n!=null&&!n.isFixed()).findFirst().orElseThrow();
            Vector3d direction=new Vector3d(target.getPosition()).sub(source.getPosition()).normalize();
            var identity=new Identity("runtime transport");identity.getAmplitude().set(.2,0,0);
            var o=new Occupancy(identity,source);o.getPressure().set(new Vector3d(direction).mul(2));
            o.getTransportMomentum().set(new Vector3d(direction).mul(.2));
            transport.setGradientGain(0);transport.setAccelerationGain(0);transport.setTransportMomentumGain(0);
            transport.setTransferThreshold(.5);transport.setEnableTransport(true);
            transport.update(o);assertSame(source,o.getNode());assertEquals(2,o.getPressure().length(),1e-12);
            lattice.setDiamondTransportEnabled(true);assertTrue(lattice.isDiamondPressureEnabled());
            transport.update(o);
            assertSame(target,o.getNode());assertEquals(target.getIndex(),o.getNodeIndex());
            assertTrue(o.getPressure().distance(new Vector3d(direction).mul(1.7))<1e-12);
            assertTrue(o.getTransportMomentum().distance(new Vector3d(direction).mul(.2))<1e-12);
            assertEquals(1,o.diamondTransportMeasurement().transfers());
            assertEquals(1,o.diamondTransportMeasurement().updates());
            assertEquals(1,o.diamondTransportMeasurement().pathLength(),1e-12);
            assertTrue(source.getPosition().equals(source.getEquilibriumPosition(),1e-12));
            assertTrue(target.getPosition().equals(target.getEquilibriumPosition(),1e-12));
            // A physical local deformation, without seeded pressure or momentum,
            // must drive the full measurement -> pressure -> neighbor-transfer path.
            Node drivenSource=source;
            target.setPosition(new Vector3d(target.getPosition()).fma(.1,direction));target.updatePosition();
            Identity drivenIdentity=new Identity("deformation driven");drivenIdentity.getAmplitude().set(.2,0,0);
            Occupancy driven=new Occupancy(drivenIdentity,drivenSource);
            transport.setGradientGain(2);transport.setAccelerationGain(3);transport.setTransportMomentumGain(.5);
            transport.setTransferThreshold(.1);
            Vector3d g=drivenSource.readGradientImbalance().mul(2);
            Vector3d generatedMomentum=new Vector3d(g).mul(3*g.length()/.2*.5);
            transport.update(driven);
            assertSame(target,driven.getNode());
            assertTrue(driven.getTransportMomentum().distance(generatedMomentum)<1e-12);
            assertTrue(driven.getPressure().distance(new Vector3d(generatedMomentum).fma(-.1,direction))<1e-12);
            transport.setEnableTransport(false);transport.update(o);assertSame(target,o.getNode());
            assertTrue(o.diamondTransportMeasurement().status().contains("Transport disabled"));
            var retained=new Vector3d(o.getPressure());
            lattice.setDiamondPressureEnabled(false);assertFalse(lattice.isDiamondTransportEnabled());
            transport.update(o);assertEquals(retained,o.getPressure());
            o.resetDiamondTransportMeasurement();assertEquals(retained,o.getPressure());assertEquals(0,o.diamondTransportMeasurement().transfers());
            lattice.setDiamondTransportEnabled(true);
            lattice.getDisturbance().request(8,.001);lattice.getDisturbance().initializePending(lattice);
            assertFalse(lattice.getDisturbance().running());assertTrue(lattice.getDisturbance().summary().contains("transport"));
            lattice.clearEntities();lattice.buildHeadless();ready(project);lattice.update();
            assertFalse(lattice.isDiamondTransportEnabled());assertFalse(lattice.isDiamondPressureEnabled());
        } finally {
            lattice.setDiamondPressureEnabled(false);transport.setGradientGain(oldG);transport.setAccelerationGain(oldA);
            transport.setTransportMomentumGain(oldM);transport.setTransferThreshold(oldT);transport.setEnableTransport(oldEnable);
            project.getRemModel().stopSimulation();manager.select(oldModel);project.setDimension(2,2,2,false);
            lattice.clearEntities();lattice.buildHeadless();ready(project);lattice.update();project.setDimension(oldSize.x,oldSize.y,oldSize.z,false);
        }
    }
    private void ready(Project project) throws InterruptedException {
        long end=System.nanoTime()+30_000_000_000L;
        while(!project.getRemModel().isLoadingReady()){if(System.nanoTime()>end)fail("Build timeout");Thread.sleep(5);}
    }
}
