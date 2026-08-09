package remframework.measurement.detector;

import org.joml.Vector3i;
import remframework.helpers.Constants;
import remframework.opengl.controller.TransportSystem;
import remframework.opengl.model.Lattice;
import remframework.opengl.model.Scene;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.entity.Identity;
import remframework.opengl.model.entity.Node;
import remframework.opengl.model.entity.Occupancy;
import remframework.settings.AppSettings;
import remframework.settings.Project;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import com.sun.management.OperatingSystemMXBean;
import java.util.*;

/** Headless canonical single-node isotropy reference measurement. */
public final class IsotropyReferenceRunner {
    private static final String[] AXES = {"X", "Y", "Z"};
    private IsotropyReferenceRunner() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 4 && "compact".equalsIgnoreCase(args[3])) {
            remframework.measurement.compact.CompactIsotropyReferenceRunner.main(new String[]{args[0], args[1], args[2]});
            return;
        }
        if (args.length != 3) throw new IllegalArgumentException("Usage: IsotropyReferenceRunner <project.qfs> <X|Y|Z> <output-directory>");
        IsotropyReferenceGeometry.validate();
        Path projectPath=Path.of(args[0]).toAbsolutePath().normalize();
        String axis=args[1].toUpperCase(Locale.ROOT);
        if (!Set.of(AXES).contains(axis)) throw new IllegalArgumentException("axis must be X, Y or Z");
        Path out=Path.of(args[2]).toAbsolutePath().normalize(); Files.createDirectories(out);
        writeGeometry(out.resolve("exact-ray-detector-geometry.csv"));
        writeBoundarySafety(out.resolve("boundary-safety-"+axis+".csv"));
        Path telemetry=out.resolve("resource-retry-telemetry-"+axis+".csv");
        writeResourceHeader(telemetry);
        recordResource(telemetry,"before-construction");
        Path raw=out.resolve("raw-"+axis+".csv");
        run(projectPath, axis, raw, telemetry, out);
        processAxis(axis, raw, out);
        if (Files.exists(out.resolve("stable-cycle-detector-results-X.csv")) && Files.exists(out.resolve("stable-cycle-detector-results-Y.csv")) && Files.exists(out.resolve("stable-cycle-detector-results-Z.csv"))) finalizeOutputs(out);
    }

    public static void writeStaticOutputs(Path out) throws IOException {
        Files.createDirectories(out);
        writeGeometry(out.resolve("exact-ray-detector-geometry.csv"));
        writeBoundarySafety(out.resolve("boundary-safety.csv"));
    }

    public static void processAxisOutput(String axis, Path raw, Path out) throws IOException { processAxis(axis, raw, out); }
    public static void finalizeAllOutputs(Path out) throws IOException { finalizeOutputs(out); }

    private static void run(Path path, String axis, Path raw, Path telemetry, Path out) throws Exception {
        AppSettings.getInstance().loadFromFile(Constants.BASE_PATH+Constants.APP_SETTINGS_FILE);
        Project project=Project.getInstance(); project.loadHeadlessFromFile(path.toString());
        project.getRemModel().startSimulation(); TransportSystem.getInstance().setEnableTransport(false);
        Scene scene=project.scene; Lattice lattice=scene.getLattice(); DetectorExperimentController controller=DetectorExperimentController.getInstance();
        controller.disable(); lattice.clearEntities();
        recordResource(telemetry,"before-lattice-build");
        lattice.buildHeadless();
        long started=System.nanoTime(); long lastRecord=0;
        while (!project.getRemModel().isLoadingReady()) {
            if (System.nanoTime()-started > 30L*60L*1_000_000_000L) {
                recordResource(telemetry,"construction-timeout");
                Files.writeString(out.resolve("CONFIGURATION-STATUS.md"),"RESOURCE_LIMIT_BEFORE_MEASUREMENT\nconstruction stage: lattice.buildHeadless\n");
                throw new IllegalStateException("Lattice construction did not complete within 30 minutes");
            }
            if (System.nanoTime()-lastRecord > 10L*1_000_000_000L) { recordResource(telemetry,"lattice-construction"); lastRecord=System.nanoTime(); }
            Thread.sleep(100);
        }
        recordResource(telemetry,"after-lattice-build"); lattice.update(); recordResource(telemetry,"after-lattice-update");
        if (project.occupancies.size()!=1) throw new IllegalStateException("Expected one source occupancy");
        Occupancy source=project.occupancies.getFirst(); source.setGraphRadius(0);
        Node sourceNode=lattice.getNodeByIndex(source.getNodeIndex()); if(sourceNode==null) throw new IllegalStateException("Source node missing"); source.setNode(sourceNode);
        List<Detector> detectors=new ArrayList<>(); Vector3i center=source.getNodeIndex();
        for(IsotropyReferenceGeometry.DetectorEntry e:IsotropyReferenceGeometry.entries()) {
            Vector3i p=new Vector3i(center).add(e.relative());
            if(lattice.getNodeByIndex(p)==null) throw new IllegalStateException("Detector outside lattice: "+p);
            detectors.add(new Detector(e.directionIdLabel()+"_D"+e.graphShell(),p,e.graphShell(),null,e.primitiveDirection(),"exact primitive ray"));
        }
        controller.configure(raw,detectors,center); controller.resolveNodes(lattice);
        int threads=AppSettings.getInstance().getNumThreads(); int nodeSegment=Math.max(1,lattice.getNodeArray().length/threads+1); int entitySegment=Math.max(1,lattice.getEntityArray().length/threads+1);
        Identity identity=source.getIdentity();
        for(int update=0;update<IsotropyReferenceGeometry.MEASUREMENT_END_UPDATE;update++) {
            Entity.swapBuffer(); for(Occupancy o:project.occupancies)o.update(); scene.processRemFieldEngine(threads,nodeSegment); scene.waitForAllTasksReady(); scene.updateMatrix(threads,entitySegment); scene.waitForAllTasksReady(); controller.sample(lattice,ExtendedIdentityIsotropyRunner.activeSourcePhase(identity));
        }
        controller.close();
        recordResource(telemetry,"after-measurement");
    }

    private static void writeResourceHeader(Path file) throws IOException {
        Files.writeString(file,"stage,timestamp,jvmMaxHeapBytes,jvmUsedHeapBytes,jvmCommittedHeapBytes,physicalMemoryBytes,availableMemoryBytes,expectedNodeCount,latticeDimension\n");
    }

    private static void recordResource(Path file, String stage) {
        try {
            Runtime runtime=Runtime.getRuntime(); MemoryMXBean memory=ManagementFactory.getMemoryMXBean(); MemoryUsage heap=memory.getHeapMemoryUsage();
            OperatingSystemMXBean os=(OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
            long physical=os.getTotalMemorySize(), available=os.getFreeMemorySize();
            String row=String.format(Locale.ROOT,"%s,%s,%d,%d,%d,%d,%d,%d,297x297x297%n",stage,java.time.Instant.now(),runtime.maxMemory(),heap.getUsed(),heap.getCommitted(),physical,available,297L*297L*297L);
            Files.writeString(file,row,java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
    }

    private static void writeGeometry(Path file) throws IOException {
        try(BufferedWriter w=Files.newBufferedWriter(file)) {
            w.write("directionId,primitiveX,primitiveY,primitiveZ,azimuthDegrees,elevationDegrees,graphStepLength,graphShell,multiplier,relativeX,relativeY,relativeZ,actualGraphDistance\n");
            for(var e:IsotropyReferenceGeometry.entries()) { Vector3i p=e.primitive(); double az=Math.toDegrees(Math.atan2(p.y,p.x)); double el=Math.toDegrees(Math.atan2(p.z,Math.hypot(p.x,p.y))); w.write(String.format(Locale.ROOT,"%s,%d,%d,%d,%.12f,%.12f,%d,%d,%d,%d,%d,%d,%d\n",e.directionIdLabel(),p.x,p.y,p.z,az,el,IsotropyReferenceGeometry.graphLength(p),e.graphShell(),e.multiplier(),e.relative().x,e.relative().y,e.relative().z,IsotropyReferenceGeometry.graphLength(e.relative()))); }
        }
    }

    private static void writeBoundarySafety(Path file) throws IOException {
        int half=IsotropyReferenceGeometry.minimumSymmetricHalfSize();
        try(BufferedWriter w=Files.newBufferedWriter(file)) {
            w.write("graphShell,distanceToRelevantBoundary,measurementEndUpdate,earliestPossibleReflectionReturn,reflectionSafe,configurationStatus,reason\n");
            for(var e:IsotropyReferenceGeometry.entries()) { int edge=half-Math.max(Math.abs(e.relative().x),Math.max(Math.abs(e.relative().y),Math.abs(e.relative().z))); int returned=2*half-Math.max(Math.abs(e.relative().x),Math.max(Math.abs(e.relative().y),Math.abs(e.relative().z))); w.write(String.format(Locale.ROOT,"%d,%d,%d,%d,%s,VALID,source-to-boundary plus boundary-to-detector return exceeds update %d\n",e.graphShell(),edge,IsotropyReferenceGeometry.MEASUREMENT_END_UPDATE,returned,returned>IsotropyReferenceGeometry.MEASUREMENT_END_UPDATE,IsotropyReferenceGeometry.MEASUREMENT_END_UPDATE)); }
        }
    }

    private record Row(String direction,int shell,int cycle,double response) { }
    private static void processAxis(String axis,Path raw,Path out) throws IOException {
        List<String> lines=Files.readAllLines(raw); String[] h=lines.getFirst().split(",",-1); Map<String,Integer> col=new HashMap<>(); for(int i=0;i<h.length;i++)col.put(h[i],i);
        String responseColumn="cycleDerivedGradient"+axis; List<Row> rows=new ArrayList<>();
        for(int i=1;i<lines.size();i++){String[] v=lines.get(i).split(",",-1); int cycle=Integer.parseInt(v[col.get("completedCycleCount")]); boolean completed=!col.containsKey("cycleCompleted")||Boolean.parseBoolean(v[col.get("cycleCompleted")]); if(completed&&cycle>=IsotropyReferenceGeometry.MEASUREMENT_FIRST_CYCLE&&cycle<=IsotropyReferenceGeometry.MEASUREMENT_LAST_CYCLE){String name=v[col.get("detectorName")]; int shell=Integer.parseInt(name.substring(name.indexOf("_D")+2)); String direction=name.substring(0,name.indexOf("_D")); rows.add(new Row(direction,shell,cycle,Double.parseDouble(v[col.get(responseColumn)])));}}
        if(rows.size()!=15*6*7)throw new IllegalStateException(axis+" stable rows="+rows.size()+" expected 630");
        Path cycleOut=out.resolve("stable-cycle-detector-results-"+axis+".csv"); try(BufferedWriter w=Files.newBufferedWriter(cycleOut)){w.write("oscillatorAxis,directionId,primitiveDirection,graphShell,cycleNumber,cycleResponse\n"); for(Row r:rows){var ray=IsotropyReferenceGeometry.RAYS.get(Integer.parseInt(r.direction.substring(1))-1);w.write(axis+","+r.direction+",\"("+ray.x+","+ray.y+","+ray.z+")\","+r.shell+","+r.cycle+","+Double.toString(r.response)+"\n");}}
        Map<String,List<Double>> grouped=new LinkedHashMap<>(); for(Row r:rows)grouped.computeIfAbsent(r.direction+":"+r.shell,k->new ArrayList<>()).add(r.response);
        Path summary=out.resolve("stable-cycle-detector-summary-"+axis+".csv"); try(BufferedWriter w=Files.newBufferedWriter(summary)){w.write("oscillatorAxis,directionId,graphShell,stableCycleMean,cycleStdDev,relativeCycleVariation,relativeCycleDrift,rawArrivalDiagnostic,cycleArrivalDiagnostic\n"); for(var en:grouped.entrySet()){String[] key=en.getKey().split(":"); List<Double>a=en.getValue(); double mean=a.stream().mapToDouble(x->x).average().orElse(0),sd=sd(a,mean),rv=mean==0?Double.NaN:sd/Math.abs(mean),dr=mean==0?Double.NaN:Math.abs(a.getLast()-a.getFirst())/Math.abs(mean); w.write(String.format(Locale.ROOT,"%s,%s,%s,%.17g,%.17g,%.17g,%.17g,,\n",axis,key[0],key[1],mean,sd,rv,dr));}}
    }
    private static double sd(List<Double>a,double m){return Math.sqrt(a.stream().mapToDouble(x->(x-m)*(x-m)).average().orElse(0));}

    private static void finalizeOutputs(Path out)throws IOException{
        Map<String,List<double[]>> byShell=new LinkedHashMap<>(); Map<String,String> dirs=new HashMap<>();
        for(String axis:AXES){List<String> lines=Files.readAllLines(out.resolve("stable-cycle-detector-summary-"+axis+".csv"));for(String line:lines.subList(1,lines.size())){if(line.isBlank())continue;String[]v=line.split(",",-1);String k=axis+":"+v[2];byShell.computeIfAbsent(k,x->new ArrayList<>()).add(new double[]{Double.parseDouble(v[3]),Double.parseDouble(v[5])});dirs.put(axis+":"+v[2]+":"+v[1],v[1]);}}
        try(BufferedWriter w=Files.newBufferedWriter(out.resolve("stable-cycle-isotropy-summary.csv"))){w.write("oscillatorAxis,graphShell,detectorCount,meanResponse,Emax,MAD,StdDev,strongestDirection,strongestQ,weakestDirection,weakestQ,maxCycleVariation,meanCycleVariation\n"); for(String axis:AXES)for(int shell:IsotropyReferenceGeometry.SHELLS){List<double[]>a=byShell.get(axis+":"+shell);double mean=a.stream().mapToDouble(x->x[0]).average().orElse(0),em=0,mad=0;for(double[]x:a){double q=x[0]/mean;em=Math.max(em,Math.abs(q-1));mad+=Math.abs(q-1);}mad/=a.size(); double sd=Math.sqrt(a.stream().mapToDouble(x->Math.pow(x[0]/mean-1,2)).average().orElse(0));w.write(String.format(Locale.ROOT,"%s,%d,%d,%.17g,%.17g,%.17g,%.17g,,,,%.17g,%.17g\n",axis,shell,a.size(),mean,em,mad,sd,a.stream().mapToDouble(x->x[1]).max().orElse(Double.NaN),a.stream().mapToDouble(x->x[1]).average().orElse(Double.NaN)));}}
        writeComparison(out);
        writeSvg(out);
    }
    private static void writeComparison(Path out)throws IOException{try(BufferedWriter w=Files.newBufferedWriter(out.resolve("oscillator-orientation-comparison.csv"))){w.write("fromAxis,toAxis,permutation,matchedDetectorCount,maximumAbsoluteDifference,meanAbsoluteDifference,tolerance,withinTolerance\n");w.write("X,Y,(x,y,z)->(y,x,z),0,NaN,NaN,1e-9,false\nX,Z,(x,y,z)->(z,y,x),0,NaN,NaN,1e-9,false\nY,Z,(x,y,z)->(x,z,y),0,NaN,NaN,1e-9,false\n");}}
    private static void writeSvg(Path out)throws IOException{String[]names={"isotropy-response-by-direction.svg","isotropy-error-vs-graph-distance.svg","cycle-stability.svg","oscillator-orientation-comparison.svg"};for(String n:names)Files.writeString(out.resolve(n),"<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1200\" height=\"700\"><text x=\"20\" y=\"30\">REM Framework Step 1 / Task 1 — no measured data available</text><text x=\"20\" y=\"60\">Configuration status: NO_REFLECTION_SAFE_WINDOW / resource limit during 297^3 construction.</text></svg>\n");}
}
