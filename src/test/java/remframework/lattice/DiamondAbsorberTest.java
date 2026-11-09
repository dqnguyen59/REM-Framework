package remframework.lattice;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import remframework.opengl.model.entity.Node;
import remframework.settings.Project;
import static org.junit.jupiter.api.Assertions.*;

class DiamondAbsorberTest {
    @Test void interiorAndLegacyUpdatesAreIdenticalAndOnlyOptInEdgesDamp() {
        var legacy=new Node.FrameParameters(remframework.settings.Settings.NodeFormulaVersion.v_0_1,.16,1,0,0,true);
        var absorber=new Node.FrameParameters(remframework.settings.Settings.NodeFormulaVersion.v_0_1,.16,1,0,0,true,true);
        double[] outputs=new double[3];
        for(int i=0;i<3;i++) {
            var node=new Node(null,null,new Vector3d(),new Vector3d(),1);
            node.setAbsorptionFactor(i==1?1f:.25f);
            node.getVelocity().set(1,0,0);node.addForce(new Vector3d(.1,0,0));
            node.updateNodeProperties(i==0?legacy:absorber);node.updatePosition();
            outputs[i]=node.getPosition().x;
        }
        assertEquals(outputs[0],outputs[1],0);
        assertTrue(outputs[2]<outputs[0]);
        assertFalse(legacy.diamondSponge());
    }
    @Test void boundaryDampingReducesLateInteriorResponseAndLeavesEarlyInteriorUnchanged() throws Exception {
        var project=Project.getInstance();var lattice=project.scene.getLattice();
        var manager=LatticeManager.getInstance();manager.initialize();var oldModel=manager.selected().identifier();
        var oldSize=new Vector3i();project.getDimension(oldSize);
        int oldLayers=lattice.getNumEdgeAbsorptionLayers();boolean oldSponge=lattice.isDiamondSpongeEnabled();
        double oldK=project.getConstantFrequency(),oldGamma=project.getConstantElectricFactor();
        try {
            manager.select(LatticeManager.EXPERIMENTAL_DIAMOND_ID);project.setDimension(32,32,32,false);
            project.setConstantFrequency(.8);project.setConstantElectricFactor(1);
            lattice.setNumEdgeAbsorptionLayers(8);lattice.clearEntities();lattice.buildHeadless();
            long deadline=System.nanoTime()+30_000_000_000L;
            while(!project.getRemModel().isLoadingReady()&&System.nanoTime()<deadline)Thread.sleep(10);
            assertTrue(project.getRemModel().isLoadingReady());lattice.update();
            double[][] traces=new double[2][500];
            for(int run=0;run<2;run++) {
                lattice.setDiamondSpongeEnabled(run==1);var parameters=lattice.frameParameters();
                for(Object value:lattice.getNodeArray()) {
                    Node n=(Node)value;var p=new Vector3d(n.getEquilibriumPosition());
                    if(!n.isFixed())p.z+=.01*Math.exp(-p.lengthSquared()/8);
                    n.setPosition(p);n.updatePosition();n.getVelocity().zero();n.getAcceleration().zero();n.getForce().zero();
                }
                for(int t=0;t<500;t++) {
                    for(Object value:lattice.getNodeArray())((Node)value).updateNodeProperties(parameters);
                    for(Object value:lattice.getNodeArray()) {
                        Node n=(Node)value;n.updatePosition();assertTrue(n.getPosition().isFinite());
                        if(n.getEquilibriumPosition().lengthSquared()<16)
                            traces[run][t]+=n.getPosition().distanceSquared(n.getEquilibriumPosition());
                    }
                }
            }
            for(int t=0;t<5;t++)assertEquals(traces[0][t],traces[1][t],1e-18);
            double baseline=0,absorbed=0;
            for(int t=150;t<500;t++){baseline+=traces[0][t];absorbed+=traces[1][t];}
            System.out.println("Late interior squared response: fixed="+baseline+", absorber="+absorbed+", ratio="+absorbed/baseline);
            assertTrue(absorbed<baseline*.5,"Expected at least 50% less late interior squared response");
            lattice.setNumEdgeAbsorptionLayers(16);lattice.applyPendingAbsorptionLayerChange();
            for(Object value:lattice.getNodeArray())assertEquals(1f,((Node)value).getAbsorptionFactor());
            lattice.setDiamondSpongeEnabled(false);assertFalse(lattice.frameParameters().diamondSponge());
        } finally {
            project.getRemModel().stopSimulation();lattice.clearEntities();manager.select(oldModel);
            project.setDimension(oldSize);project.setConstantFrequency(oldK);project.setConstantElectricFactor(oldGamma);
            lattice.setNumEdgeAbsorptionLayers(oldLayers);lattice.setDiamondSpongeEnabled(oldSponge);
        }
    }
}
