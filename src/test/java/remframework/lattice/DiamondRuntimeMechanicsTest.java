package remframework.lattice;
import org.junit.jupiter.api.Test;
import org.joml.Vector3d;
import org.joml.Vector3i;
import remframework.geometry.discovery.research.diamond.*;
import remframework.opengl.model.*;
import remframework.opengl.model.entity.*;
import remframework.opengl.controller.TransportSystem;
import remframework.settings.*;
import static org.junit.jupiter.api.Assertions.*;
class DiamondRuntimeMechanicsTest {
    @Test void conventionalCellStepsFollowRuntimeAxes() {
        var nodes=new ExperimentalDiamondLatticeModel().nodes(new Vector3i(8,6,4));
        var origin=nodes.get(0).position();
        double spacing=4/Math.sqrt(3);
        assertTrue(new Vector3d(nodes.get(8*3*2).position()).sub(origin).distance(new Vector3d(spacing,0,0))<1e-12);
        assertTrue(new Vector3d(nodes.get(8*2).position()).sub(origin).distance(new Vector3d(0,spacing,0))<1e-12);
        assertTrue(new Vector3d(nodes.get(8).position()).sub(origin).distance(new Vector3d(0,0,spacing))<1e-12);
    }
    @Test void runtimeModelValidatesWithoutTreatingStorageEdgesAsFixed() {
        var model=new ExperimentalDiamondLatticeModel();assertTrue(new LatticeModelValidator().validate(model).isValid());
        assertEquals(1000,model.nodes(new Vector3i(10)).size());assertEquals(4,model.mechanicalDivisor());
        assertEquals(729,model.nodes(new Vector3i(10)).stream().filter(n->!model.fixedBoundary(n,new Vector3i(10))).count());
        assertFalse(SystemCubicLatticeModel.create(false).mechanicsOnly());assertEquals(6,SystemCubicLatticeModel.create(false).mechanicalDivisor());
    }
    @Test void generatedDefinitionsMatchDiscoveryReferenceExactly() {
        var nodes=new ExperimentalDiamondLatticeModel().nodes(new Vector3i(8,6,4));
        var reference=DiamondReference.createResearchDomain(4,3,2,1);
        var inverse=new org.joml.Quaterniond().rotationTo(new Vector3d(1,0,0),new Vector3d(1,1,1).normalize());
        var center=new Vector3d();for(var node:reference.nodes())center.add(node.position());center.div(nodes.size());
        for(int id=0;id<nodes.size();id++) {
            var expected=reference.nodes().get(id);var actual=nodes.get(id);
            assertTrue(actual.position().distance(inverse.transform(new Vector3d(expected.position()).sub(center)))<1e-12);
            assertEquals(expected.neighborIds().stream().sorted().map(Object::toString).toList(),
                actual.neighbors().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).map(java.util.Map.Entry::getValue).toList());
        }
    }    @Test void rectangularDimensionsPreserveDiamondBonds() {
        var model=new ExperimentalDiamondLatticeModel();var size=new Vector3i(12,8,6);
        assertEquals(size,model.dimensions(size));var nodes=model.nodes(size);
        assertEquals(576,nodes.size());assertEquals(385,nodes.stream().filter(n->n.neighbors().size()==4).count());
        for(var node:nodes) for(var neighbor:node.neighbors().values())
            assertEquals(1,node.position().distance(nodes.get(Integer.parseInt(neighbor)).position()),1e-12);
        assertThrows(IllegalArgumentException.class,()->model.dimensions(new Vector3i(11,8,6)));
        assertEquals(264,model.nodes(new Vector3i(66,2,2)).size());
    }
    @Test void sceneMechanicsMatchesAdapterAndGatesTransport() throws Exception {
        var project=Project.getInstance();var manager=LatticeManager.getInstance();manager.initialize();
        String original=manager.selected().identifier();double oldK=project.getConstantFrequency(),oldGamma=project.getConstantElectricFactor();
        var oldFormula=project.settings.getNodeFormulaVersion();var oldDimension=new Vector3i();project.getDimension(oldDimension);
        var lattice=Lattice.getInstance();var scene=project.scene;
        try {
            assertTrue(manager.select(LatticeManager.EXPERIMENTAL_DIAMOND_ID));project.setDimension(10,10,10,false);lattice.clearEntities();lattice.buildHeadless();awaitReady(project);lattice.update();
            project.setConstantFrequency(.5);project.setConstantElectricFactor(1);project.settings.setNodeFormulaVersion(Settings.NodeFormulaVersion.v_0_2);
            assertEquals(1000,lattice.getNodeArray().length);assertTrue(lattice.isMechanicsOnly());
            assertEquals(Settings.NodeFormulaVersion.v_0_1,lattice.frameParameters().nodeFormulaVersion());assertEquals(.25/4,lattice.frameParameters().waveStep());
            var model=new ExperimentalDiamondLatticeModel();var definitions=model.nodes(new Vector3i(10));var reference=DiamondReference.create(5,1);
            var adapter=new DiamondCanonicalAdapter(reference,.5,1,DiamondCanonicalAdapter.Normalization.RESEARCH_FOUR);
            var inverse=new org.joml.Quaterniond().rotationTo(new Vector3d(1,0,0),new Vector3d(1,1,1).normalize());
            var forward=new org.joml.Quaterniond(inverse).invert();
            var translation=inverse.transform(new Vector3d(reference.nodes().get(0).position())).sub(definitions.get(0).position());
            int source=definitions.stream().filter(d->d.neighbors().size()==4).min(java.util.Comparator.comparingDouble(d->d.position().lengthSquared())).orElseThrow().id().transform(Integer::parseInt);
            var runtimeSource=lattice.getNode(definitions.get(source).index());var delta=new Vector3d(.001,0,0);
            runtimeSource.setPosition(new Vector3d(runtimeSource.getPosition()).add(delta));runtimeSource.updatePosition();adapter.displace(source,forward.transform(new Vector3d(delta)));
            var occupancy=new Occupancy();occupancy.getPressure().set(.1,.2,.3);
            TransportSystem.getInstance().update(occupancy);occupancy.updateOscillation();assertEquals(new Vector3d(.1,.2,.3),occupancy.getPressure());
            project.getRemModel().startSimulation();
            for(int step=0;step<12;step++) {
                adapter.step();scene.processRemFieldEngine(3,Math.ceilDiv(1000,3));scene.waitForAllTasksReady();
                for(var d:definitions) lattice.getNode(d.index()).updatePosition();
                for(var d:definitions) {
                    int id=Integer.parseInt(d.id());var node=lattice.getNode(d.index());
                    assertTrue(node.getPosition().distance(inverse.transform(adapter.position(id)).sub(translation))<1e-10,"position id"+id);
                    assertTrue(node.getVelocity().distance(inverse.transform(adapter.velocity(id)))<1e-10,"velocity id"+id);
                    assertEquals(d.neighbors().size()<4,node.getType()==EntityType.FIXED);
                }
            }
            assertNotNull(lattice.getMechanicsStatus());assertTrue(lattice.validateMechanicalState());
            var beforePulse=definitions.stream().map(d->new Vector3d(lattice.getNode(d.index()).getPosition())).toList();
            lattice.requestMechanicalPulse();lattice.applyPendingMechanicalPulse();
            int changed=0;for(int id=0;id<definitions.size();id++) if(lattice.getNode(definitions.get(id).index()).getPosition().distance(beforePulse.get(id))>1e-10) changed++;
            assertEquals(1,changed);
            var afterPulse=new Vector3d(runtimeSource.getPosition());lattice.applyPendingMechanicalPulse();assertEquals(afterPulse,runtimeSource.getPosition());
            runtimeSource.setPosition(new Vector3d(Double.NaN,0,0));runtimeSource.updatePosition();
            assertFalse(lattice.validateMechanicalState());assertFalse(project.getRemModel().isSimulating());
            assertEquals(remframework.geometry.discovery.GeometryConfigurationStatusCode.NUMERICAL_FAILURE,lattice.getMechanicsStatus().code());
            project.setDimension(12,8,6,false);lattice.clearEntities();lattice.buildHeadless();awaitReady(project);lattice.update();
            assertEquals(576,lattice.getNodeArray().length);assertEquals(12,project.getDimensionX());
            assertEquals(8,project.getDimensionY());assertEquals(6,project.getDimensionZ());assertTrue(lattice.validateMechanicalState());
        } finally {
            project.getRemModel().stopSimulation();project.setConstantFrequency(oldK);project.setConstantElectricFactor(oldGamma);project.settings.setNodeFormulaVersion(oldFormula);
            manager.select(original);project.setDimension(2,2,2,false);lattice.clearEntities();lattice.buildHeadless();awaitReady(project);lattice.update();
            project.setDimension(oldDimension.x,oldDimension.y,oldDimension.z,false);
        }
    }
    private static void awaitReady(Project project) throws InterruptedException {
        long deadline=System.nanoTime()+10_000_000_000L;
        while(!project.getRemModel().isLoadingReady()) {if(System.nanoTime()>deadline) fail("Runtime lattice construction timed out");Thread.sleep(5);}
    }
}


