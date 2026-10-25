package remframework.lattice;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.research.diamond.*;
import remframework.opengl.model.DiamondProbePlacement;
import remframework.opengl.model.entity.Node;
import remframework.settings.Project;
import static org.junit.jupiter.api.Assertions.*;

class DiamondBulkExcitationAxisTest {
    @Test void bulkPacketAndPhysicalReceiversUseEverySelectedAxis() throws Exception {
        var project=Project.getInstance();var scene=project.scene;var lattice=scene.getLattice();
        var manager=LatticeManager.getInstance();manager.initialize();var oldModel=manager.selected().identifier();
        var oldSize=new Vector3i();project.getDimension(oldSize);
        double oldK=project.getConstantFrequency(),oldGamma=project.getConstantElectricFactor();
        try {
            manager.select(LatticeManager.EXPERIMENTAL_DIAMOND_ID);project.setDimension(48,48,48,false);
            project.setConstantFrequency(.5);project.setConstantElectricFactor(1);
            lattice.clearEntities();lattice.buildHeadless();
            long deadline=System.nanoTime()+30_000_000_000L;
            while(!project.getRemModel().isLoadingReady()&&System.nanoTime()<deadline)Thread.sleep(10);
            assertTrue(project.getRemModel().isLoadingReady());lattice.update();scene.clearProbes();
            scene.requestDiamondProbes(lattice.getNodeArray(),DiamondProbePlacement.create(lattice.getNodeArray(),10,4,2));scene.applyPendingDiamondProbes();
            for(var axis:new DiamondExcitationAxis[]{DiamondExcitationAxis.X,DiamondExcitationAxis.Y,DiamondExcitationAxis.Z}) {
                var experiment=lattice.getDisturbance();experiment.requestProbeRun(4,.001,0,1e-7,1e-7,axis);experiment.initializePending(lattice);
                assertTrue(experiment.running(),experiment.summary());assertTrue(experiment.summary().contains(axis+"-polarized"));
                double peak=0;
                for(Object value:lattice.getNodeArray()) {
                    var node=(Node)value;var delta=new Vector3d(node.getPosition()).sub(node.getEquilibriumPosition());
                    if(node.isFixed())assertEquals(new Vector3d(),delta);
                    var expected=axis.displaced(new Vector3d(),axis.component(delta));
                    assertEquals(expected.x,delta.x,1e-12);assertEquals(expected.y,delta.y,1e-12);assertEquals(expected.z,delta.z,1e-12);
                    peak=Math.max(peak,delta.length());
                }
                assertTrue(peak>0);experiment.cancel();
                var receivers=new DiamondRuntimeReceivers(lattice,4,.001,0,axis);assertNull(receivers.failure());
                for(Object value:lattice.getNodeArray()) {
                    var node=(Node)value;var p=node.getEquilibriumPosition();
                    node.setPosition(axis.displaced(p,.001*(1+.01*p.x+.02*p.y-.03*p.z)));
                }
                receivers.sample(1);
                var signals=receivers.recordedSignals();
                for(int i=0;i<26;i++) {
                    var point=new Vector3d(DiamondDirectionalComparison.directions().get(i)).mul(10).add(lattice.getPhysicalSliceCenter());
                    assertEquals(.001*(1+.01*point.x+.02*point.y-.03*point.z),signals[i][1],1e-12);
                }
            }
        } finally {
            lattice.getDisturbance().cancel();scene.clearProbes();project.getRemModel().stopSimulation();manager.select(oldModel);
            project.setDimension(oldSize.x,oldSize.y,oldSize.z,false);project.setConstantFrequency(oldK);project.setConstantElectricFactor(oldGamma);
        }
    }
}
