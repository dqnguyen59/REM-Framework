package remframework.lattice;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import remframework.geometry.discovery.research.diamond.DiamondSphericalSweep;
import remframework.opengl.model.DiamondProbePlacement;
import remframework.opengl.model.entity.Node;
import remframework.settings.AppSettings;
import remframework.settings.Project;
import static org.junit.jupiter.api.Assertions.*;

class DiamondSphericalRuntimeTest {
    @Test void drivesSourceAcrossAxesPreservesFixedNodesAndRetainsIncompleteWindows() throws Exception {
        var project=Project.getInstance();var scene=project.scene;var lattice=scene.getLattice();
        var manager=LatticeManager.getInstance();manager.initialize();var oldModel=manager.selected().identifier();
        var oldSize=new Vector3i();project.getDimension(oldSize);
        double oldK=project.getConstantFrequency(),oldGamma=project.getConstantElectricFactor();
        var settings=AppSettings.getInstance();int oldLimit=settings.getDiamondProbeMaximumUpdates();
        var oldOccupancies=java.util.List.copyOf(project.occupancies);
        try {
            manager.select(LatticeManager.EXPERIMENTAL_DIAMOND_ID);project.setDimension(60,60,60,false);
            project.setConstantFrequency(.5);project.setConstantElectricFactor(1);settings.setDiamondProbeMaximumUpdates(8);
            lattice.clearEntities();lattice.buildHeadless();long deadline=System.nanoTime()+30_000_000_000L;
            while(!project.getRemModel().isLoadingReady()&&System.nanoTime()<deadline)Thread.sleep(10);
            assertTrue(project.getRemModel().isLoadingReady());lattice.update();scene.clearProbes();
            scene.requestDiamondProbes(lattice.getNodeArray(),DiamondProbePlacement.create(lattice.getNodeArray(),8,2,1));
            scene.applyPendingDiamondProbes();
            var sweep=new DiamondSphericalSweep(32,1,2);var experiment=lattice.getDisturbance();
            experiment.requestSphericalProbeRun(2,.001,0,1e-9,1e-9,sweep);experiment.initializePending(lattice);
            assertTrue(experiment.running(),experiment.summary());assertEquals(8,experiment.safeSteps());
            Node peak=null;double maximum=0;
            for(Object value:lattice.getNodeArray()) {
                Node node=(Node)value;double displacement=node.getPosition().distance(node.getEquilibriumPosition());
                if(displacement>maximum){maximum=displacement;peak=node;}
            }
            assertNotNull(peak);assertEquals(.001,maximum,1e-12);
            project.getRemModel().startSimulation();
            while(experiment.running()) {
                scene.updateProbeMeasurements(remframework.opengl.controller.TransportSystem.getInstance());
                scene.processRemFieldEngine(3,Math.ceilDiv(lattice.getNodeArray().length,3));scene.waitForAllTasksReady();
                experiment.completedStep();
                for(Object value:lattice.getNodeArray()) {
                    Node node=(Node)value;node.updatePosition();assertTrue(node.getPosition().isFinite());
                    if(node.isFixed())assertEquals(0,node.getPosition().distance(node.getEquilibriumPosition()),1e-12);
                }
                var expected=new Vector3d(peak.getEquilibriumPosition()).fma(.001,sweep.direction(experiment.steps()));
                assertEquals(0,expected.distance(peak.getPosition()),1e-12);
            }
            assertEquals(8,experiment.steps());assertFalse(scene.getProbeShellArrivalReport().sweepsComplete());
            assertEquals(26,scene.getProbeShellArrivalReport().sweepRows().size());
            assertTrue(scene.getProbeShellArrivalReport().sweepRows().stream()
                .allMatch(r->r.result().measurement().status().equals("incomplete")));
            assertTrue(experiment.summary().contains("incomplete"));
            settings.setDiamondProbeMaximumUpdates(200);
            experiment.requestSphericalProbeRun(2,.001,0,1e-12,1e-12,new DiamondSphericalSweep(32,1,1));
            experiment.initializePending(lattice);project.getRemModel().startSimulation();
            while(experiment.running()) {
                scene.updateProbeMeasurements(remframework.opengl.controller.TransportSystem.getInstance());
                scene.processRemFieldEngine(3,Math.ceilDiv(lattice.getNodeArray().length,3));scene.waitForAllTasksReady();
                experiment.completedStep();
                for(Object value:lattice.getNodeArray())((Node)value).updatePosition();
            }
            assertTrue(scene.getProbeShellArrivalReport().sweepsComplete(),experiment.summary());
            assertTrue(experiment.steps()<experiment.safeSteps(),experiment.summary());
            assertTrue(experiment.summary().startsWith("Completed configured sweep measurements"));
            assertTrue(scene.getProbeShellArrivalReport().sweepRows().stream()
                .allMatch(r->r.result().measurement().completedCycles()==1 && r.result().measurement().samples()==32));

            scene.clearProbes();settings.setDiamondProbeMaximumUpdates(8);
            experiment.requestOrbitPreview(2,.001,new remframework.geometry.discovery.research.diamond.DiamondRotatingOrbit(32,2));
            experiment.initializePending(lattice);assertNotNull(experiment.activeOrbit());
            project.getRemModel().startSimulation();
            for(int t=0;t<130;t++) {
                scene.processRemFieldEngine(3,Math.ceilDiv(lattice.getNodeArray().length,3));scene.waitForAllTasksReady();
                experiment.completedStep();
                for(Object value:lattice.getNodeArray())((Node)value).updatePosition();
            }
            assertTrue(experiment.running());assertEquals(130,experiment.steps());
            assertTrue(scene.getProbeShellArrivalReport().sweepRows().isEmpty());
            assertTrue(experiment.summary().contains("Continuous rotating orbit"));

            // Exercise the real Identity -> Occupancy -> Node force path, without a prescribed source.
            experiment.cancel();lattice.setDiamondIdentityExcitationEnabled(true);
            for(Object value:lattice.getNodeArray()) {
                Node node=(Node)value;node.setPosition(new Vector3d(node.getEquilibriumPosition()));node.updatePosition();
                node.getVelocity().zero();node.getAcceleration().zero();node.getForce().zero();
            }
            var identity=new remframework.opengl.model.entity.Identity("orbit force test");identity.setXyzOrbit(true);
            identity.getAmplitude().set(1);identity.getPhaseIncrement().set(360.0/64,360.0/256,360.0/1024);
            var occupancy=new remframework.opengl.model.entity.Occupancy(identity,lattice.getNodeByIndex(new Vector3i(30,30,30)));
            assertFalse(occupancy.getNode().isFixed());project.getRemModel().startSimulation();
            double[] peaks=new double[3];int[] radii={4,8,12};
            for(int t=0;t<128;t++) {
                occupancy.updateOscillation();
                assertEquals(1,occupancy.getNode().getForce().length(),1e-12);
                scene.processRemFieldEngine(3,Math.ceilDiv(lattice.getNodeArray().length,3));scene.waitForAllTasksReady();
                for(Object value:lattice.getNodeArray()) {
                    Node node=(Node)value;node.updatePosition();assertTrue(node.getPosition().isFinite());
                }
                for(int i=0;i<radii.length;i++) {
                    var receiver=lattice.getNodeByIndex(new Vector3i(30+radii[i],30,30));
                    peaks[i]=Math.max(peaks[i],receiver.getPosition().distance(receiver.getEquilibriumPosition()));
                }
            }
            String csv="radius_index_offset,peak_displacement,updates\n";
            for(int i=0;i<radii.length;i++) {assertTrue(peaks[i]>1e-9,"No propagation at offset "+radii[i]);csv+=radii[i]+","+peaks[i]+",128\n";}
            java.nio.file.Files.writeString(java.nio.file.Path.of("target/identity-orbit-propagation.csv"),csv);

            project.occupancies.clear();project.occupancies.add(occupancy);
            scene.requestDiamondProbes(lattice.getNodeArray(),DiamondProbePlacement.create(lattice.getNodeArray(),8,2,2));scene.applyPendingDiamondProbes();
            identity.getPhaseIncrement().set(360.0/32,360.0/64,360.0/128);identity.getAmplitude().set(.05);
            occupancy.detachLattice();assertNull(occupancy.getNode());
            var run=scene.getIdentityOrbitSpreadRun();run.start(scene,identity,1,1,1e6,1e6);
            assertNotNull(occupancy.getNode());
            while(run.running()) {
                occupancy.updateOscillation();scene.updateProbeMeasurements(remframework.opengl.controller.TransportSystem.getInstance());
                scene.processRemFieldEngine(3,Math.ceilDiv(lattice.getNodeArray().length,3));scene.waitForAllTasksReady();
                for(Object value:lattice.getNodeArray())((Node)value).updatePosition();
            }
            assertTrue(run.status().startsWith("Complete"),run.status());assertEquals(256,identity.getOrbitUpdates());
            assertEquals(2,scene.getProbeShellArrivalReport().responseSpreads().size());
            assertTrue(scene.getProbeShellArrivalReport().responseSpreads().stream().allMatch(r->r.complete()==26&&Double.isFinite(r.spreadPercent())));
            assertTrue(scene.getProbeShellArrivalReport().sweepRows().stream().allMatch(r->r.result().measurement().samples()==128));
            try(var writer=java.nio.file.Files.newBufferedWriter(java.nio.file.Path.of("target/identity-orbit-spread-test.csv"))){run.writeSummary(scene,writer);}
            var perProbe=new java.io.StringWriter();run.writeProbes(scene,perProbe);assertEquals(53,perProbe.toString().lines().count());
            int columns=perProbe.toString().lines().findFirst().orElseThrow().split(",").length;
            assertTrue(perProbe.toString().lines().allMatch(line->line.split(",").length==columns));
            run.start(scene,identity,1,1,1e-6,1e-6);identity.getAmplitude().mul(2);
            occupancy.updateOscillation();scene.updateProbeMeasurements(remframework.opengl.controller.TransportSystem.getInstance());
            assertFalse(run.running());assertTrue(run.status().startsWith("Incomplete"));assertFalse(scene.getProbeShellArrivalReport().sweepsComplete());

        } finally {
            project.occupancies.clear();project.occupancies.addAll(oldOccupancies);
            lattice.setDiamondIdentityExcitationEnabled(false);
            lattice.getDisturbance().cancel();scene.clearProbes();project.getRemModel().stopSimulation();manager.select(oldModel);
            settings.setDiamondProbeMaximumUpdates(oldLimit);project.setDimension(oldSize.x,oldSize.y,oldSize.z,false);
            project.setConstantFrequency(oldK);project.setConstantElectricFactor(oldGamma);
        }
    }
}
