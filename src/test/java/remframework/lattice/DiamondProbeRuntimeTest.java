package remframework.lattice;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import remframework.gui.model.NuklearModel;
import remframework.gui.view.frames.dialogs.FrameAddDiamondProbes;
import remframework.opengl.controller.TransportSystem;
import remframework.opengl.model.*;
import remframework.opengl.model.entity.*;
import remframework.settings.Project;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DiamondProbeRuntimeTest {
    @Test void shellsInstallAtFrameBoundaryAndSamplePassivelyWithPressureAndTransportOff() throws Exception {
        var project=Project.getInstance();var scene=project.scene;var lattice=scene.getLattice();
        var manager=LatticeManager.getInstance();manager.initialize();String oldModel=manager.selected().identifier();
        Vector3i oldSize=new Vector3i();project.getDimension(oldSize);var oldDefinitions=List.copyOf(project.getProbeDefinitions());
        try {
            manager.select(LatticeManager.EXPERIMENTAL_DIAMOND_ID);project.setDimension(16,16,16,false);
            lattice.clearEntities();lattice.buildHeadless();ready(project);lattice.update();scene.clearProbes();
            assertInstanceOf(FrameAddDiamondProbes.class,new NuklearModel().createInstance(NuklearModel.Frame.addProbes));
            var nodes=lattice.getNodeArray();var definitions=DiamondProbePlacement.create(nodes,2,2,2);
            var cubic=new ProbeDefinition("probe-cubic-saved",new Vector3i(1),new Vector4f(1));
            project.getProbeDefinitions().clear();project.getProbeDefinitions().add(cubic);
            scene.requestDiamondProbes(nodes,definitions);assertEquals(0,scene.getProbes().size());
            scene.applyPendingDiamondProbes();assertEquals(52,scene.getProbes().size());
            assertTrue(project.getProbeDefinitions().contains(cubic));
            assertFalse(lattice.isDiamondPressureEnabled());assertFalse(lattice.isDiamondTransportEnabled());
            scene.updateProbeMeasurements(TransportSystem.getInstance());
            scene.requestDiamondProbeArrival(0,1e-8,1e-8);
            assertFalse(scene.getProbeArrivalMeasurement().snapshot().hasMeasurement());
            scene.applyPendingDiamondProbeArrival();
            assertTrue(scene.getProbeArrivalMeasurement().snapshot().hasMeasurement());
            for(Object value:nodes) {
                Node n=(Node)value;Vector3d p=n.getEquilibriumPosition();
                n.setPosition(new Vector3d(p).add(.001*p.y+.01,0,0));n.updatePosition();
            }
            scene.updateProbeMeasurements(TransportSystem.getInstance());
            for(var probe:scene.getProbes()) {
                var target=probe.getDefinition().getPlacement().targets().getFirst().requestedCoordinate();
                assertTrue(probe.getObservation().position().distance(new Vector3d(target).add(.001*target.y+.01,0,0))<1e-12);
                Vector3d expected=new Vector3d();
                for(int i=0;i<probe.getOccupancies().size();i++) {
                    var o=probe.getOccupancies().get(i);
                    expected.fma(probe.getDefinition().getInterpolationSources().get(i).weight(),o.getNode().readGradientImbalance());
                    assertEquals(new Vector3d(),o.getPressure());assertEquals(new Vector3d(),o.getTransportMomentum());
                    assertEquals(2,o.getCumulativeSampleCountForAudit());
                }
                assertTrue(probe.getObservation().gradientImbalance().distance(expected)<1e-12);
            }
            var result=scene.getProbeArrivalMeasurement().snapshot();assertEquals(26,result.results().size());
            assertTrue(result.results().stream().allMatch(r->r.positionArrivalUpdate()==1));
            // Sublattice averaging can cancel the uniform affine gradient response.
            // A curved displacement field must still be detected as a local gradient change.
            scene.getProbeArrivalMeasurement().start(scene.getProbes(),0,1e-8,1e-8);
            for(Object value:nodes) {
                Node n=(Node)value;double x=n.getEquilibriumPosition().x;
                n.setPosition(new Vector3d(n.getPosition()).add(.001*x*x,0,0));n.updatePosition();
            }
            scene.updateProbeMeasurements(TransportSystem.getInstance());
            assertTrue(scene.getProbeArrivalMeasurement().snapshot().results().stream().anyMatch(r->r.gradientArrivalUpdate()==1));
            project.replaceGeneratedProbeDefinitions(List.of(cubic));
            assertEquals(52,project.getProbeDefinitions().stream().filter(d->d.getDetectorType()==ProbeDetectorType.DIAMOND_INTERPOLATED).count());
            // Old-generation work is rejected after the node array changes.
            lattice.update();scene.requestDiamondProbes(nodes,definitions);scene.applyPendingDiamondProbes();
            assertTrue(scene.getDiamondProbeStatus().contains("Lattice changed"));
        } finally {
            scene.clearProbes();project.getProbeDefinitions().clear();project.getProbeDefinitions().addAll(oldDefinitions);
            project.getRemModel().stopSimulation();manager.select(oldModel);project.setDimension(2,2,2,false);
            lattice.clearEntities();lattice.buildHeadless();ready(project);lattice.update();project.setDimension(oldSize.x,oldSize.y,oldSize.z,false);
        }
    }
    private void ready(Project project) throws InterruptedException {
        long end=System.nanoTime()+30_000_000_000L;
        while(!project.getRemModel().isLoadingReady()){if(System.nanoTime()>end)fail("Build timeout");Thread.sleep(5);}
    }
}
