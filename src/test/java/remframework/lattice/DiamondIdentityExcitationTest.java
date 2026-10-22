package remframework.lattice;
import org.junit.jupiter.api.Test;
import org.joml.*;
import remframework.opengl.model.*;
import remframework.opengl.model.entity.*;
import remframework.opengl.controller.TransportSystem;
import remframework.settings.Project;
import remframework.geometry.discovery.GeometryConfigurationStatusCode;
import static org.junit.jupiter.api.Assertions.*;
class DiamondIdentityExcitationTest {
    @Test void optInExcitesThroughOccupancyWithoutEnablingTransport() throws Exception {
        var project=Project.getInstance();var manager=LatticeManager.getInstance();manager.initialize();
        var lattice=Lattice.getInstance();String oldModel=manager.selected().identifier();var oldSize=new Vector3i();project.getDimension(oldSize);
        double oldK=project.getConstantFrequency(),oldGamma=project.getConstantElectricFactor();
        try {
            manager.select(LatticeManager.EXPERIMENTAL_DIAMOND_ID);project.setDimension(10,10,10,false);
            project.setConstantFrequency(.5);project.setConstantElectricFactor(1);lattice.clearEntities();lattice.buildHeadless();ready(project);lattice.update();
            assertFalse(lattice.allowsIdentityExcitation());
            var nodes=java.util.Arrays.stream(lattice.getNodeArray()).map(n->(Node)n).toList();
            var source=nodes.stream().filter(n->n.getType()!=EntityType.FIXED).min(java.util.Comparator.comparingDouble(n->n.getEquilibriumPosition().lengthSquared())).orElseThrow();
            var identity=new Identity("diamond excitation test");identity.getActive().x=true;identity.getAmplitude().x=.001;identity.getPhaseIncrement().x=90;
            var occupancy=new Occupancy(identity,source);occupancy.getPressure().set(.1,.2,.3);occupancy.getTransportMomentum().set(.4,.5,.6);
            occupancy.updateOscillation();assertEquals(0,identity.getPhase().x);assertEquals(new Vector3d(),source.getForce());
            lattice.setDiamondIdentityExcitationEnabled(true);assertTrue(lattice.allowsIdentityExcitation());
            occupancy.updateOscillation();assertEquals(90,identity.getPhase().x);assertEquals(.001,source.getForce().x,1e-15);
            var pressure=new Vector3d(occupancy.getPressure());var momentum=new Vector3d(occupancy.getTransportMomentum());
            for(int step=0;step<12;step++) {
                if(step>0)occupancy.updateOscillation();TransportSystem.getInstance().update(occupancy);
                for(var node:nodes)node.updateNodeProperties(lattice.frameParameters());
                for(var node:nodes){node.updatePosition();assertTrue(node.getPosition().isFinite());if(node.getType()==EntityType.FIXED)assertEquals(node.getEquilibriumPosition(),node.getPosition());}
            }
            assertSame(source,occupancy.getNode());assertEquals(pressure,occupancy.getPressure());assertEquals(momentum,occupancy.getTransportMomentum());
            assertTrue(nodes.stream().filter(n->n!=source).anyMatch(n->n.getPosition().distance(n.getEquilibriumPosition())>1e-9));
            assertEquals(GeometryConfigurationStatusCode.GEOMETRY_TEST_NOT_STARTED,lattice.diamondRemIntegrationStatus().code());
            lattice.getDisturbance().request(8,.001);lattice.getDisturbance().initializePending(lattice);
            assertFalse(lattice.getDisturbance().running());assertEquals(GeometryConfigurationStatusCode.INVALID_PARAMETERS,lattice.getDisturbance().status().code());
            lattice.setDiamondIdentityExcitationEnabled(false);var phase=new Vector3d(identity.getPhase());occupancy.updateOscillation();assertEquals(phase,identity.getPhase());
            lattice.setDiamondIdentityExcitationEnabled(true);lattice.clearEntities();lattice.buildHeadless();ready(project);lattice.update();assertFalse(lattice.isDiamondIdentityExcitationEnabled());
            occupancy.detachLattice();
            assertNull(occupancy.getNode());assertEquals(pressure,occupancy.getPressure());assertEquals(momentum,occupancy.getTransportMomentum());
            lattice.releaseForReplacement();
            assertNull(lattice.getNodeMatrix());assertNull(lattice.getBaseEntity());
            assertEquals(0,lattice.getNodeArray().length);assertEquals(0,lattice.getEntityArray().length);assertTrue(lattice.getEntities().isEmpty());
        } finally {
            lattice.setDiamondIdentityExcitationEnabled(false);project.setConstantFrequency(oldK);project.setConstantElectricFactor(oldGamma);manager.select(oldModel);
            project.setDimension(2,2,2,false);lattice.clearEntities();lattice.buildHeadless();ready(project);lattice.update();project.setDimension(oldSize.x,oldSize.y,oldSize.z,false);
        }
    }
    private static void ready(Project project) throws InterruptedException {
        long end=System.nanoTime()+30_000_000_000L;while(!project.getRemModel().isLoadingReady()){if(System.nanoTime()>end)fail("Build timeout");Thread.sleep(5);}
    }
}