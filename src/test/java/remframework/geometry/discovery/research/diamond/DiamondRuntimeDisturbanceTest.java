package remframework.geometry.discovery.research.diamond;
import org.junit.jupiter.api.Test;
import org.joml.Vector3d;
import org.joml.Vector3i;
import remframework.lattice.*;
import remframework.opengl.model.*;
import remframework.opengl.model.entity.*;
import remframework.settings.*;
import remframework.geometry.discovery.GeometryConfigurationStatusCode;
import static org.junit.jupiter.api.Assertions.*;
class DiamondRuntimeDisturbanceTest {
    @Test void probeRunAcceptsConfiguredLargeAndSmallAmplitudes() {
        var experiment = new DiamondRuntimeDisturbance();
        for (double amplitude : new double[]{.001, 10, 25}) {
            assertDoesNotThrow(() -> experiment.requestProbeRun(8, amplitude, 0, 1e-7, 1e-7));
            var request = new DiamondRuntimeDisturbance.Request(8, amplitude);
            assertEquals(amplitude, DiamondRuntimeDisturbance.displacement(0, request));
        }
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> experiment.requestProbeRun(8, invalid, 0, 1e-7, 1e-7));
        experiment.cancel();
    }
    @Test void probeWavelengthIsConfigurableAndValidated() {
        var experiment=new DiamondRuntimeDisturbance();
        for(double wavelength:new double[]{2,4,6,8,12,16}) {
            assertDoesNotThrow(()->experiment.requestProbeRun(wavelength,0,1e-7,1e-7));
            assertEquals(1.5*wavelength,new DiamondRuntimeDisturbance.Request(wavelength,.001).supportRadius());
        }
        for(double invalid:new double[]{0,1.9,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->experiment.requestProbeRun(invalid,0,1e-7,1e-7));
        experiment.cancel();
    }
    @Test void compactPacketHasControlledAmplitudeAndSupport() {
        var request=new DiamondRuntimeDisturbance.Request(8,.001);
        assertEquals(.001,DiamondRuntimeDisturbance.displacement(0,request));
        assertEquals(0,DiamondRuntimeDisturbance.displacement(12,request));
        assertEquals(0,DiamondRuntimeDisturbance.displacement(100,request));
        for(double r=0;r<12;r+=.01) assertTrue(Math.abs(DiamondRuntimeDisturbance.displacement(r,request))<=.001);
    }
    @Test void fixedGeometryRetainsEnvelopeWhileCarrierChanges() {
        var shortWave=new DiamondRuntimeDisturbance.Request(4,.001,0,8);
        var longWave=new DiamondRuntimeDisturbance.Request(12,.001,0,8);
        assertEquals(12,shortWave.supportRadius());assertEquals(12,longWave.supportRadius());
        assertEquals(0,DiamondRuntimeDisturbance.displacement(12,shortWave));
        assertEquals(0,DiamondRuntimeDisturbance.displacement(12,longWave));
        assertNotEquals(DiamondRuntimeDisturbance.displacement(2,shortWave),DiamondRuntimeDisturbance.displacement(2,longWave));
        for(double r=0;r<12;r+=.1)assertTrue(Math.abs(DiamondRuntimeDisturbance.displacement(r,longWave))<=.001);
    }    @Test void runtimeResetsRepeatsEvolvesAndStopsBeforeBoundary() throws Exception {
        var project=Project.getInstance();var manager=LatticeManager.getInstance();manager.initialize();
        String oldModel=manager.selected().identifier();var oldSize=new Vector3i();project.getDimension(oldSize);
        double oldK=project.getConstantFrequency(),oldGamma=project.getConstantElectricFactor();var lattice=Lattice.getInstance();
        try {
            manager.select(LatticeManager.EXPERIMENTAL_DIAMOND_ID);int dimension=Integer.getInteger("diamondTestDimension",48);project.setDimension(dimension,dimension,dimension,false);
            project.setConstantFrequency(.5);project.setConstantElectricFactor(1);lattice.clearEntities();for(var pool:java.lang.management.ManagementFactory.getMemoryPoolMXBeans())pool.resetPeakUsage();System.out.println("Memory availability: "+DiamondMemoryBudget.memoryAvailability()+"; limiting resource: "+DiamondMemoryBudget.memoryAvailability().limitingResource());var estimate=DiamondMemoryBudget.estimate(new Vector3i(dimension));System.out.println("Domain memory audit: "+estimate);assertTrue(estimate.allowed(),estimate.code()+": "+estimate.error());lattice.buildHeadless();ready(project);lattice.update();
            long peakHeap=java.lang.management.ManagementFactory.getMemoryPoolMXBeans().stream().filter(pool->pool.getType()==java.lang.management.MemoryType.HEAP).mapToLong(pool->pool.getPeakUsage().getUsed()).sum();System.out.println("Build heap pool peak envelope MiB: "+peakHeap/1048576.0);
            double wavelength=Double.parseDouble(System.getProperty("diamondTestWavelength","8")),offset=Double.parseDouble(System.getProperty("diamondTestReceiverOffset","0"));
            double geometry=Double.parseDouble(System.getProperty("diamondTestGeometry",Double.toString(wavelength)));
            // Affine physical displacement must interpolate exactly after axis alignment.
            var affineReceivers=new DiamondRuntimeReceivers(lattice,geometry,.001,offset);
            assertNull(affineReceivers.failure());
            for(Object value:lattice.getNodeArray()) {
                Node node=(Node)value;var p=node.getEquilibriumPosition();
                node.setPosition(new Vector3d(p).add(.001*(1+.01*p.x+.02*p.y-.03*p.z),0,0));
            }
            affineReceivers.sample(1);
            var affineSignals=affineReceivers.recordedSignals();
            for(int direction=0;direction<26;direction++) {
                var target=new Vector3d(DiamondDirectionalComparison.directions().get(direction))
                    .mul(1.5*geometry+4+offset).add(lattice.getPhysicalSliceCenter());
                assertEquals(.001*(1+.01*target.x+.02*target.y-.03*target.z),affineSignals[direction][1],1e-12);
            }
            var experiment=lattice.getDisturbance();experiment.requestFixedGeometry(wavelength,.001,geometry,offset);experiment.initializePending(lattice);
            assertTrue(experiment.running(),experiment.summary());assertTrue(experiment.safeSteps()>26);assertEquals(26,experiment.arrivals().size());
            var initial=java.util.Arrays.stream(lattice.getNodeArray()).map(o->new Vector3d(((Node)o).getPosition())).toList();
            var source=(Node)lattice.getNodeArray()[16000];source.getVelocity().set(1,2,3);source.getAcceleration().set(4,5,6);
            source.setPosition(new Vector3d(100));source.updatePosition();
            experiment.requestFixedGeometry(wavelength,.001,geometry,offset);experiment.initializePending(lattice);int index=0;
            for(Object value:lattice.getNodeArray()){Node node=(Node)value;assertEquals(initial.get(index++),node.getPosition());assertEquals(new Vector3d(),node.getVelocity());}
            project.getRemModel().startSimulation();boolean moved=false;
            while(experiment.running()) {
                project.scene.processRemFieldEngine(3,Math.ceilDiv(lattice.getNodeArray().length,3));project.scene.waitForAllTasksReady();
                for(Object value:lattice.getNodeArray()){Node node=(Node)value;node.updatePosition();assertTrue(node.getPosition().isFinite());
                    if(node.getType()==EntityType.FIXED) assertEquals(node.getEquilibriumPosition(),node.getPosition());}
                experiment.completedStep();
            }
            index=0;for(Object value:lattice.getNodeArray()) if(((Node)value).getPosition().distance(initial.get(index++))>1e-12) moved=true;
            assertTrue(moved);System.out.println("Runtime receiver audit: "+experiment.summary());
            System.out.println(experiment.twoRadiusSummary());System.out.println(experiment.troughSummary());assertEquals(26,experiment.troughMeasurements().size());
            assertEquals(26,experiment.pairedMeasurements().size());
            for(int direction=0;direction<26;direction++)System.out.println(experiment.directionDiagnostic(direction));
            String traceOutput=System.getProperty("diamondTraceOutput");
            if(traceOutput!=null) {
                var csv=new StringBuilder("direction,step,inner,outer\n");
                for(int direction=0;direction<26;direction++) {
                    var inner=experiment.innerTrace(direction);var outer=experiment.outerTrace(direction);
                    for(int step=0;step<Math.min(inner.length,outer.length);step++)
                        csv.append(direction).append(',').append(step).append(',').append(inner[step]).append(',').append(outer[step]).append('\n');
                }
                java.nio.file.Files.writeString(java.nio.file.Path.of(traceOutput),csv);
            }
            var originalTrace=experiment.innerTrace(0);var copiedTrace=experiment.innerTrace(0);copiedTrace[0]=999;assertEquals(originalTrace[0],experiment.innerTrace(0)[0]);
            for(var row:experiment.thresholdComparisons()) System.out.println("Threshold "+row.label()+": "+row.value());
            assertEquals(4,experiment.thresholdComparisons().size());
            assertEquals(26,experiment.thresholdComparisons().get(1).arrivals());
            // The historical 27.258% value used the previous rotated runtime orientation.
            assertTrue(Double.isFinite(experiment.thresholdComparisons().get(1).speedSpreadPercent()));
            assertNotNull(experiment.status());
            assertTrue(experiment.arrivals().stream().allMatch(a->a.radius()==1.5*geometry+4+offset && a.stencilRadius()<4));
            assertEquals(experiment.safeSteps(),experiment.steps());assertFalse(project.getRemModel().isSimulating());
            var savedComparisons=experiment.thresholdComparisons();assertEquals("Completed run",experiment.resultsLabel());
            experiment.cancel();assertEquals("Previous run (retained)",experiment.resultsLabel());
            assertEquals(savedComparisons,experiment.thresholdComparisons());
            experiment.request(1000,.001);experiment.initializePending(lattice);
            assertEquals(GeometryConfigurationStatusCode.INVALID_PARAMETERS,experiment.status().code());assertFalse(experiment.running());
        } finally {
            project.getRemModel().stopSimulation();manager.select(oldModel);project.setConstantFrequency(oldK);project.setConstantElectricFactor(oldGamma);
            project.setDimension(2,2,2,false);lattice.clearEntities();lattice.buildHeadless();ready(project);lattice.update();
            project.setDimension(oldSize.x,oldSize.y,oldSize.z,false);
        }
    }
    private static void ready(Project project) throws InterruptedException {
        long end=System.nanoTime()+30_000_000_000L;while(!project.getRemModel().isLoadingReady()){if(System.nanoTime()>end)fail("Build timeout");Thread.sleep(5);}
    }
}
