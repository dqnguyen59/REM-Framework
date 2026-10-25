package remframework.lattice;

import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import remframework.opengl.model.entity.Node;
import remframework.opengl.model.DiamondProbePlacement;
import remframework.settings.Project;

import static org.junit.jupiter.api.Assertions.*;

class DiamondXYExcitationTest {
    @ParameterizedTest
    @ValueSource(ints={8,16,32,64})
    void slabPacketDisplacesOnlyZAndPreservesFixedNodes(int directionCount) throws Exception {
        var project = Project.getInstance();
        var scene = project.scene;
        var lattice = scene.getLattice();
        var manager = LatticeManager.getInstance();
        manager.initialize();
        String oldModel = manager.selected().identifier();
        var oldSize = new Vector3i();
        project.getDimension(oldSize);
        double oldK = project.getConstantFrequency(), oldGamma = project.getConstantElectricFactor();
        try {
            manager.select(LatticeManager.EXPERIMENTAL_DIAMOND_ID);
            project.setDimension(96, 96, 4, false);
            project.setConstantFrequency(.5);
            project.setConstantElectricFactor(1);
            lattice.clearEntities();
            lattice.buildHeadless();
            long deadline = System.nanoTime() + 30_000_000_000L;
            while (!project.getRemModel().isLoadingReady() && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(project.getRemModel().isLoadingReady());
            lattice.update();
            scene.clearProbes();
            var definitions=DiamondProbePlacement.create(lattice.getNodeArray(),16,4,2,true,directionCount);
            assertEquals(2*directionCount,definitions.size());
            for(var definition:definitions) {
                var target=definition.getPlacement().targets().getFirst();
                assertEquals(target.requestedRadius(),target.requestedCoordinate().distance(lattice.getPhysicalSliceCenter()),1e-10);
                assertEquals(1,definition.getInterpolationSources().stream().mapToDouble(s->s.weight()).sum(),1e-12);
            }
            scene.requestDiamondProbes(lattice.getNodeArray(), definitions);
            scene.applyPendingDiamondProbes();
            assertEquals(2*directionCount, scene.getProbes().size());
            var experiment = lattice.getDisturbance();
            for(double wavelength:new double[]{2,4,8})for(var axis:remframework.geometry.discovery.research.diamond.DiamondExcitationAxis.values()) {
            var effective=axis.resolve(true);
            experiment.requestProbeRun(wavelength, 10, 0, 1e-7, 1e-7,axis);
            experiment.initializePending(lattice);
            assertTrue(experiment.running(), experiment.summary());
            assertTrue(experiment.summary().contains(effective+" excitation"));
            assertEquals(directionCount,scene.getProbeArrivalMeasurement().snapshot().results().size());
            var shellRows=scene.getProbeShellArrivalReport().rows();
            assertEquals(2,shellRows.size());
            assertEquals(16,shellRows.getFirst().radius());assertEquals(20,shellRows.getLast().radius());
            assertTrue(shellRows.stream().allMatch(r->r.snapshot().running() && r.snapshot().results().size()==directionCount));
            int displaced = 0;
            double actualPeak=0;
            for (Object value : lattice.getNodeArray()) {
                var node = (Node) value;
                var equilibrium = node.getEquilibriumPosition();
                var position = node.getPosition();
                var delta=new org.joml.Vector3d(position).sub(equilibrium);
                actualPeak=Math.max(actualPeak,Math.abs(effective.component(delta)));
                if(node.isFixed())assertEquals(new org.joml.Vector3d(),delta);
                else if(delta.length()>1e-12)displaced++;
                var expected=effective.displaced(new org.joml.Vector3d(),effective.component(delta));
                assertEquals(expected.x,delta.x,1e-12);assertEquals(expected.y,delta.y,1e-12);assertEquals(expected.z,delta.z,1e-12);
            }
            assertTrue(displaced > 0);
            assertEquals(10,actualPeak,1e-12,"Initial sampled peak must not decrease with wavelength");
            }
        } finally {
            lattice.getDisturbance().cancel();
            scene.clearProbes();
            project.getRemModel().stopSimulation();
            manager.select(oldModel);
            project.setDimension(oldSize.x, oldSize.y, oldSize.z, false);
            project.setConstantFrequency(oldK);
            project.setConstantElectricFactor(oldGamma);
        }
    }
}
