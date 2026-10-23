package remframework.lattice;

import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import remframework.opengl.controller.TransportSystem;
import remframework.opengl.model.*;
import remframework.opengl.model.entity.Node;
import remframework.settings.Project;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Node integrator experiment; acceptance is reported, never assumed. */
class DiamondProbePropagationTest {
    @Test void controlledPacketMeasuresAllDirectionsAcrossThresholdsAndRadii() throws Exception {
        var project=Project.getInstance();var scene=project.scene;var lattice=scene.getLattice();
        var manager=LatticeManager.getInstance();manager.initialize();String oldModel=manager.selected().identifier();
        var oldSize=new Vector3i();project.getDimension(oldSize);var saved=List.copyOf(project.getProbeDefinitions());
        double oldK=project.getConstantFrequency(),oldGamma=project.getConstantElectricFactor();
        try {
            int side=Integer.getInteger("diamondProbeSide",64),depth=Integer.getInteger("diamondProbeDepth",64);
            manager.select(LatticeManager.EXPERIMENTAL_DIAMOND_ID);project.setDimension(side,side,depth,false);
            project.setConstantFrequency(.5);project.setConstantElectricFactor(1);
            lattice.clearEntities();lattice.buildHeadless();ready(project);lattice.update();scene.clearProbes();
            boolean xy=Boolean.getBoolean("diamondProbeXY");int count=xy?8:26;
            scene.requestDiamondProbes(lattice.getNodeArray(),DiamondProbePlacement.create(lattice.getNodeArray(),16,4,2,xy));
            scene.applyPendingDiamondProbes();assertEquals(count*2,scene.getProbes().size());
            double wavelength=Double.parseDouble(System.getProperty("diamondProbeWavelength","8"));
            var experiment=lattice.getDisturbance();experiment.requestProbeRun(wavelength,1,1e-7,1e-7);experiment.initializePending(lattice);
            assertTrue(experiment.running(),experiment.summary());
            if(depth==4) {
                assertEquals(200,experiment.safeSteps());
                assertTrue(experiment.summary().contains("surface effects INCLUDED"));
            }
            assertTrue(scene.getProbeArrivalMeasurement().snapshot().hasMeasurement());
            var measurements=new ArrayList<ProbeArrivalMeasurement>();
            for(int shell=0;shell<2;shell++)for(double threshold:new double[]{1e-6,1e-7,1e-8}) {
                var m=new ProbeArrivalMeasurement();assertTrue(m.start(scene.getProbes(),shell,threshold,threshold));measurements.add(m);
            }
            project.getRemModel().startSimulation();
            while(experiment.running()) {
                scene.updateProbeMeasurements(TransportSystem.getInstance());
                for(var m:measurements)m.sample();
                scene.processRemFieldEngine(3,Math.ceilDiv(lattice.getNodeArray().length,3));scene.waitForAllTasksReady();
                for(Object value:lattice.getNodeArray())((Node)value).updatePosition();
                experiment.completedStep();
            }
            assertEquals(experiment.safeSteps(),experiment.steps());
            var report=new StringBuilder("radius,threshold,updates,position_arrivals,gradient_arrivals,position_spread_percent,gradient_spread_percent\n");
            for(var m:measurements) {
                var s=m.snapshot();assertEquals(count,s.results().size());
                if(depth>=32)assertTrue(s.results().stream().anyMatch(r->r.maximumPositionChange()>1e-10),"Packet must reach at least one probe");
                report.append(s.shell()==0?16:20).append(',').append(s.positionThreshold()).append(',').append(s.elapsedUpdates()).append(',')
                    .append(s.results().stream().filter(r->r.positionArrivalUpdate()>0).count()).append(',')
                    .append(s.results().stream().filter(r->r.gradientArrivalUpdate()>0).count()).append(',')
                    .append(ProbeArrivalMeasurement.equalRadiusApparentSpread(s.results(),false)).append(',')
                    .append(ProbeArrivalMeasurement.equalRadiusApparentSpread(s.results(),true)).append('\n');
            }
            System.out.println(report);
            java.nio.file.Files.writeString(java.nio.file.Path.of("diamond-probe-propagation-"+(xy?"xy-":"")+wavelength+"-results.csv"),report);
        } finally {
            scene.clearProbes();project.getProbeDefinitions().clear();project.getProbeDefinitions().addAll(saved);
            project.getRemModel().stopSimulation();manager.select(oldModel);project.setConstantFrequency(oldK);project.setConstantElectricFactor(oldGamma);
            project.setDimension(2,2,2,false);lattice.clearEntities();lattice.buildHeadless();ready(project);lattice.update();
            project.setDimension(oldSize.x,oldSize.y,oldSize.z,false);
        }
    }
    private void ready(Project project) throws InterruptedException {
        long end=System.nanoTime()+60_000_000_000L;
        while(!project.getRemModel().isLoadingReady()){if(System.nanoTime()>end)fail("Build timeout");Thread.sleep(5);}
    }
}
