package remframework.measurement.compact;

import org.joml.Vector3d;
import remframework.helpers.Constants;
import remframework.measurement.detector.Detector;
import remframework.measurement.detector.IsotropyReferenceGeometry;
import remframework.measurement.detector.IsotropyReferenceRunner;
import remframework.opengl.model.entity.Identity;
import remframework.opengl.model.entity.Occupancy;
import remframework.settings.AppSettings;
import remframework.settings.Project;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;

/** Compact CPU execution of the frozen Step 1 single-node benchmark. */
public final class CompactIsotropyReferenceRunner {
    private CompactIsotropyReferenceRunner() { }

    public static void main(String[] args) throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Usage: CompactIsotropyReferenceRunner <project.qfs> <X|Y|Z> <output-directory>");
        IsotropyReferenceGeometry.validate(); String axis=args[1].toUpperCase(Locale.ROOT); if(!Set.of("X","Y","Z").contains(axis))throw new IllegalArgumentException("axis must be X, Y or Z");
        Path out=Path.of(args[2]).toAbsolutePath().normalize(); Files.createDirectories(out); IsotropyReferenceRunner.writeStaticOutputs(out);
        Path raw=out.resolve("raw-"+axis+"-compact.csv"), telemetry=out.resolve("compact-runtime-"+axis+".csv");
        writeRuntimeHeader(telemetry); runtime(telemetry,"before-construction");
        AppSettings.getInstance().loadFromFile(Constants.BASE_PATH+Constants.APP_SETTINGS_FILE);
        Project project=Project.getInstance(); project.loadHeadlessFromFile(Path.of(args[0]).toAbsolutePath().normalize().toString()); project.getRemModel().startSimulation();
        if(project.occupancies.size()!=1)throw new IllegalStateException("Expected one source occupancy"); Occupancy source=project.occupancies.getFirst(); Identity identity=source.getIdentity();
        int nx=project.getDimensionX(),ny=project.getDimensionY(),nz=project.getDimensionZ(); int cx=source.getNodeIndex().x,cy=source.getNodeIndex().y,cz=source.getNodeIndex().z;
        CompactMeasurementBackend backend=new CompactMeasurementBackend(nx,ny,nz,project.getConstantWaveSpeed(),project.getConstantElectricFactor()); backend.setSource(cx,cy,cz); backend.setParallelism(Math.min(16,AppSettings.getInstance().getNumThreads())); runtime(telemetry,"after-array-allocation");
        writeMemoryAudit(out.resolve("compact-memory-audit.csv"),nx,ny,nz);
        List<Detector> detectors=new ArrayList<>(); for(var e:IsotropyReferenceGeometry.entries())detectors.add(new Detector(e.directionIdLabel()+"_D"+e.graphShell(),new org.joml.Vector3i(cx,cy,cz).add(e.relative()),e.graphShell(),null,e.primitiveDirection(),"exact primitive ray"));
        int compactThreads=Math.min(16,AppSettings.getInstance().getNumThreads()); run(backend,identity,axis,detectors,raw,telemetry,compactThreads); backend.close(); IsotropyReferenceRunner.processAxisOutput(axis,raw,out);
        if(Files.exists(out.resolve("stable-cycle-detector-results-X.csv"))&&Files.exists(out.resolve("stable-cycle-detector-results-Y.csv"))&&Files.exists(out.resolve("stable-cycle-detector-results-Z.csv")))CompactOutputFinalizer.finalize(out);
    }

    private static void run(CompactMeasurementBackend b,Identity identity,String axis,List<Detector> detectors,Path raw,Path telemetry,int threads)throws IOException{
        int n=detectors.size(); double[] minX=new double[n],minY=new double[n],minZ=new double[n],maxX=new double[n],maxY=new double[n],maxZ=new double[n],lastX=new double[n],lastY=new double[n],lastZ=new double[n]; Arrays.fill(minX,Double.POSITIVE_INFINITY);Arrays.fill(minY,Double.POSITIVE_INFINITY);Arrays.fill(minZ,Double.POSITIVE_INFINITY);Arrays.fill(maxX,Double.NEGATIVE_INFINITY);Arrays.fill(maxY,Double.NEGATIVE_INFINITY);Arrays.fill(maxZ,Double.NEGATIVE_INFINITY);
        double previousPhase=Double.NaN; long started=System.nanoTime(); long updates=0;
        try(BufferedWriter w=Files.newBufferedWriter(raw)){w.write("update,detectorName,completedCycleCount,cycleCompleted,cycleDerivedGradientX,cycleDerivedGradientY,cycleDerivedGradientZ,rawGradientX,rawGradientY,rawGradientZ\n");
            for(int update=1;update<=IsotropyReferenceGeometry.MEASUREMENT_END_UPDATE;update++){Vector3d force=identity.generateExcitation(); if(force!=null)b.setSourceForce(force.x,force.y,force.z); b.update(); double phase=activePhase(identity,axis); boolean complete=!Double.isNaN(previousPhase)&&phase<previousPhase; for(int j=0;j<n;j++){Vector3d g=b.gradient(b.index(detectors.get(j).getNodeIndex().x,detectors.get(j).getNodeIndex().y,detectors.get(j).getNodeIndex().z));minX[j]=Math.min(minX[j],g.x);minY[j]=Math.min(minY[j],g.y);minZ[j]=Math.min(minZ[j],g.z);maxX[j]=Math.max(maxX[j],g.x);maxY[j]=Math.max(maxY[j],g.y);maxZ[j]=Math.max(maxZ[j],g.z);if(complete){lastX[j]=(maxX[j]+minX[j])*.5;lastY[j]=(maxY[j]+minY[j])*.5;lastZ[j]=(maxZ[j]+minZ[j])*.5;minX[j]=minY[j]=minZ[j]=Double.POSITIVE_INFINITY;maxX[j]=maxY[j]=maxZ[j]=Double.NEGATIVE_INFINITY;}w.write(update+","+detectors.get(j).getName()+","+(complete?update/IsotropyReferenceGeometry.SOURCE_PERIOD_UPDATES:Math.max(0,(update-1)/IsotropyReferenceGeometry.SOURCE_PERIOD_UPDATES))+","+complete+","+lastX[j]+","+lastY[j]+","+lastZ[j]+","+g.x+","+g.y+","+g.z+"\n");}previousPhase=phase;updates++;}
        } runtime(telemetry,"after-measurement"); Files.writeString(telemetry,"elapsedNanos,"+(System.nanoTime()-started)+",updates,"+updates+",threads,"+threads+"\n",StandardOpenOption.APPEND);
    }
    private static double activePhase(Identity i,String axis){return switch(axis){case "X"->i.getPhase().x;case "Y"->i.getPhase().y;default->i.getPhase().z;};}
    private static void writeMemoryAudit(Path file,int nx,int ny,int nz)throws IOException{long nodes=(long)nx*ny*nz;try(BufferedWriter w=Files.newBufferedWriter(file)){w.write("item,bytesPerNode,totalBytes\n");w.write("persistent lattice state,72,"+CompactMeasurementBackend.estimatePersistentBytes((int)nodes)+"\n");w.write("temporary update buffers,0,0\n");w.write("boundary state,0,0\n");w.write("source state,0,0\n");w.write("detector state,0,0\n");w.write("other overhead,0,0\n");for(int d:new int[]{161,237,297,384,512})w.write("estimate "+d+"^3,"+CompactMeasurementBackend.persistentBytesPerNode()+","+CompactMeasurementBackend.estimatePersistentBytes((int)CompactMeasurementBackend.estimateNodeCount(d))+"\n");}}
    private static void writeRuntimeHeader(Path f)throws IOException{Files.writeString(f,"stage,timestamp,jvmMaxHeapBytes,jvmUsedHeapBytes,physicalMemoryBytes,availableMemoryBytes\n");}
    private static void runtime(Path f,String stage)throws IOException{Runtime r=Runtime.getRuntime();var os=(com.sun.management.OperatingSystemMXBean)java.lang.management.ManagementFactory.getOperatingSystemMXBean();var m=java.lang.management.ManagementFactory.getMemoryMXBean();Files.writeString(f,String.format(Locale.ROOT,"%s,%s,%d,%d,%d,%d\n",stage, Instant.now(),r.maxMemory(),m.getHeapMemoryUsage().getUsed(),os.getTotalMemorySize(),os.getFreeMemorySize()),StandardOpenOption.APPEND);}
}
