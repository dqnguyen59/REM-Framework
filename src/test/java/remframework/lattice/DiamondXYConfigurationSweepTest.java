package remframework.lattice;

import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import remframework.opengl.controller.TransportSystem;
import remframework.opengl.model.*;
import remframework.opengl.model.entity.Node;
import remframework.settings.Project;
import java.util.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reproducible XY slab experiment; a small spread is not a test assertion. */
class DiamondXYConfigurationSweepTest {
    @Test void sweep() throws Exception {
        var project=Project.getInstance();var scene=project.scene;var lattice=scene.getLattice();
        var manager=LatticeManager.getInstance();manager.initialize();String oldModel=manager.selected().identifier();
        var size=new Vector3i();project.getDimension(size);var saved=List.copyOf(project.getProbeDefinitions());
        double oldK=project.getConstantFrequency(),oldGamma=project.getConstantElectricFactor();
        int side=Integer.getInteger("xySweepSide",200);
        var wavelengths=Arrays.stream(System.getProperty("xySweepWavelengths","4,8,12,16,20,24").split(",")).mapToDouble(Double::parseDouble).toArray();
        double[] thresholds={1e-12,3e-12,1e-11,3e-11,1e-10,3e-10,1e-9,3e-9,1e-8,3e-8,1e-7,3e-7,1e-6,3e-6};
        String output=System.getProperty("xySweepOutput","diamond-xy-configuration-sweep.csv");
        var report=new StringBuilder("side,depth,wavelength,radius,threshold,position_arrivals,gradient_arrivals,position_spread_percent,gradient_spread_percent,lateral_safe_updates\n");
        try {
            manager.select(LatticeManager.EXPERIMENTAL_DIAMOND_ID);project.setDimension(side,side,4,false);
            project.setConstantFrequency(.5);project.setConstantElectricFactor(1);lattice.clearEntities();lattice.buildHeadless();ready(project);lattice.update();scene.clearProbes();
            assertNotNull(lattice.getNodeArray(),"Lattice build rejected; inspect resource diagnostics");
            scene.requestDiamondProbes(lattice.getNodeArray(),DiamondProbePlacement.create(lattice.getNodeArray(),48,12,3,true));scene.applyPendingDiamondProbes();
            assertEquals(24,scene.getProbes().size());
            // Fixed top/bottom surfaces belong to this experiment; exclude lateral reflections.
            var center=lattice.getPhysicalSliceCenter();double maxX=0,maxY=0;
            for(Object value:lattice.getNodeArray()){var p=((Node)value).getEquilibriumPosition();maxX=Math.max(maxX,Math.abs(p.x-center.x));maxY=Math.max(maxY,Math.abs(p.y-center.y));}
            var boundaries=new ArrayList<Node>();
            for(Object value:lattice.getNodeArray()) {var n=(Node)value;var p=n.getEquilibriumPosition();
                if(Math.abs(p.x-center.x)>maxX-1 || Math.abs(p.y-center.y)>maxY-1)boundaries.add(n);
            }
            var receiverNodes=scene.getProbes().stream().flatMap(p->p.getOccupancies().stream()).map(o->o.getNode()).distinct().toList();
            for(double wavelength:wavelengths) {
                var experiment=lattice.getDisturbance();experiment.requestProbeRun(wavelength,0,1e-7,1e-7);experiment.initializePending(lattice);
                assertTrue(experiment.running(),experiment.summary());assertEquals(200,experiment.safeSteps());
                if(Boolean.getBoolean("xySweepQuiet")) {
                    for(Object value:lattice.getNodeArray()) {var n=(Node)value;n.setPosition(new org.joml.Vector3d(n.getEquilibriumPosition()));n.updatePosition();n.getVelocity().zero();n.getAcceleration().zero();}
                    scene.updateProbeMeasurements(TransportSystem.getInstance());
                }
                var sources=new ArrayList<Node>();
                for(Object value:lattice.getNodeArray()){var n=(Node)value;var p=n.getEquilibriumPosition();if(!n.isFixed() && Math.hypot(p.x-center.x,p.y-center.y)<1.5*wavelength)sources.add(n);}
                int lateralSafe=remframework.geometry.discovery.research.diamond.DiamondReflectionWindow.safeSteps(sources,boundaries,receiverNodes,n->{
                    var neighbors=new ArrayList<Node>();for(var slot:remframework.opengl.helpers.Neighbor.values()){var other=n.getNeighbor(slot);if(other!=null)neighbors.add(other);}return neighbors;
                });
                var measurements=new ArrayList<ProbeArrivalMeasurement>();
                for(int shell=0;shell<3;shell++)for(double threshold:thresholds){var m=new ProbeArrivalMeasurement();assertTrue(m.start(scene.getProbes(),shell,threshold,threshold));measurements.add(m);}
                project.getRemModel().startSimulation();
                while(experiment.running()) {
                    scene.updateProbeMeasurements(TransportSystem.getInstance());
                    if(experiment.steps()<lateralSafe)for(var m:measurements)m.sample();
                    scene.processRemFieldEngine(3,Math.ceilDiv(lattice.getNodeArray().length,3));scene.waitForAllTasksReady();
                    for(Object value:lattice.getNodeArray())((Node)value).updatePosition();experiment.completedStep();
                }
                for(var m:measurements){m.finish();var s=m.snapshot();assertEquals(8,s.results().size());
                    report.append(side).append(",4,").append(wavelength).append(',').append(48+12*s.shell()).append(',').append(s.positionThreshold()).append(',')
                        .append(s.results().stream().filter(r->r.positionArrivalUpdate()>0).count()).append(',')
                        .append(s.results().stream().filter(r->r.gradientArrivalUpdate()>0).count()).append(',')
                        .append(ProbeArrivalMeasurement.equalRadiusApparentSpread(s.results(),false)).append(',')
                        .append(ProbeArrivalMeasurement.equalRadiusApparentSpread(s.results(),true)).append(',').append(lateralSafe).append('\n');
                }
                Files.writeString(Path.of(output),report);System.out.println("XY_SWEEP_COMPLETE side="+side+" wavelength="+wavelength+" lateralSafe="+lateralSafe);
            }
        } finally {
            scene.clearProbes();project.getProbeDefinitions().clear();project.getProbeDefinitions().addAll(saved);project.getRemModel().stopSimulation();
            manager.select(oldModel);project.setConstantFrequency(oldK);project.setConstantElectricFactor(oldGamma);project.setDimension(2,2,2,false);
            lattice.clearEntities();lattice.buildHeadless();ready(project);lattice.update();project.setDimension(size.x,size.y,size.z,false);
        }
    }
    private void ready(Project project) throws InterruptedException {
        long end=System.nanoTime()+120_000_000_000L;
        while(!project.getRemModel().isLoadingReady()){if(System.nanoTime()>end)fail("Build timeout");Thread.sleep(5);}
    }
}
