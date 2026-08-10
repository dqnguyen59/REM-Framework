package remframework.measurement.compact;

import com.sun.management.OperatingSystemMXBean;
import org.joml.Vector3d;
import remframework.helpers.Constants;
import remframework.measurement.detector.IsotropyReferenceGeometry;
import remframework.opengl.model.entity.Identity;
import remframework.opengl.model.entity.Occupancy;
import remframework.settings.AppSettings;
import remframework.settings.Project;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/** One atomic compact Step 3 indentation shape measurement. */
public final class Step3IndentationShapeRunner {
    private static final int UPDATES = 198;
    private static final int PERIOD = IsotropyReferenceGeometry.SOURCE_PERIOD_UPDATES;
    private static final int WARMUP = 5;
    private static final int CYCLES = 6;
    private static final int D = 24;
    private static final double STEP2_EMAX = 2.5633920365873792;
    private static final List<Integer> RADII = List.of(5, 8, 11, 14, 17, 20, 23, 26, 29, 32, 35, 38, 41, 44, 47, 48);
    private record Result(String shapeId, int radius, double indentation, String axis, int occupied, int boundary, int crossing,
                          double[] q, double mean, double amplitude, double emax, double mad, double stddev,
                          double axisBody, long constructionNanos, long measurementNanos, long peakHeap, long peakProcess) { }

    private Step3IndentationShapeRunner() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("Usage: Step3IndentationShapeRunner <project.qfs> <output-directory> <base-radius> <indentation> <axis>");
        Path project = Path.of(args[0]).toAbsolutePath().normalize();
        Path root = Path.of(args[1]).toAbsolutePath().normalize();
        int radius = Integer.parseInt(args[2]);
        double indentation = Double.parseDouble(args[3]);
        String axis = args[4].toUpperCase(Locale.ROOT);
        if (!RADII.contains(radius) || indentation < 0 || !Double.isFinite(indentation) || !List.of("X", "Y", "Z").contains(axis)) throw new IllegalArgumentException("Invalid Step 3 shape arguments");
        IsotropyReferenceGeometry.validate(); Files.createDirectories(root);
        String shapeId = shapeId(radius, indentation);
        Path finalDir = root.resolve(shapeId);
        if (isComplete(finalDir)) { updateGlobals(root); System.out.println(shapeId + " already COMPLETE; reused"); return; }
        if (Files.exists(finalDir)) archive(finalDir, root);
        Path staging = root.resolve(shapeId + ".MEASUREMENT_INCOMPLETE");
        if (Files.exists(staging)) archive(staging, root);
        Files.createDirectories(staging);
        try {
            Result result = measure(staging, shapeId, radius, indentation, axis, project);
            writeShapeOutputs(staging, result);
            writeMetadata(staging.resolve("metadata.csv"), result, "VALID", "");
            Files.move(staging, finalDir, StandardCopyOption.ATOMIC_MOVE);
            updateGlobals(root);
            System.out.printf(Locale.ROOT, "%s axis=%s Emax=%.17g MAD=%.17g StdDev=%.17g B=%.17g%n", shapeId, axis, result.emax, result.mad, result.stddev, result.axisBody);
        } catch (Throwable failure) {
            writeFailure(staging.resolve("metadata.csv"), shapeId, radius, indentation, axis, failure);
            if (failure instanceof Exception exception) throw exception;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException("Shape measurement failed", failure);
        }
    }

    private static Result measure(Path out, String shapeId, int radius, double indentation, String axis, Path projectPath) throws Exception {
        long constructionStart = System.nanoTime();
        AppSettings.getInstance().loadFromFile(Constants.BASE_PATH + Constants.APP_SETTINGS_FILE);
        Project project = Project.getInstance(); project.loadHeadlessFromFile(projectPath.toString()); project.getRemModel().startSimulation();
        if (project.occupancies.size() != 1) throw new IllegalStateException("Expected one source occupancy");
        Occupancy source = project.occupancies.getFirst(); Identity identity = source.getIdentity();
        int nx = project.getDimensionX(), ny = project.getDimensionY(), nz = project.getDimensionZ();
        int cx = source.getNodeIndex().x, cy = source.getNodeIndex().y, cz = source.getNodeIndex().z;
        CompactMeasurementBackend backend = new CompactMeasurementBackend(nx, ny, nz, project.getConstantWaveSpeed(), project.getConstantElectricFactor());
        backend.setParallelism(Math.min(16, AppSettings.getInstance().getNumThreads()));
        List<int[]> offsets = offsets(radius, indentation);
        int[] sourceNodes = offsets.stream().mapToInt(p -> backend.index(cx + p[0], cy + p[1], cz + p[2])).toArray(); backend.setSourceNodes(sourceNodes);
        int[][] detectors = detectorCoordinates(cx, cy, cz); long constructionNanos = System.nanoTime() - constructionStart;
        writeConfiguration(out, shapeId, radius, indentation, axis, nx, ny, nz, cx, cy, cz, offsets, source.getIdentity().getAmplitude().z);
        double[][] cycleResponses = new double[CYCLES][15]; double[] min = new double[15], max = new double[15]; Arrays.fill(min, Double.POSITIVE_INFINITY); Arrays.fill(max, Double.NEGATIVE_INFINITY);
        int captured = 0; double previousPhase = Double.NaN; long measurementStart = System.nanoTime();
        try {
            for (int update = 1; update <= UPDATES; update++) {
                Vector3d force = identity.generateExcitation(); if (force == null) throw new IllegalStateException("Source identity is inactive");
                double component = axis.equals("X") ? force.x : axis.equals("Y") ? force.y : force.z;
                backend.setSourceForce(axis.equals("X") ? component : 0, axis.equals("Y") ? component : 0, axis.equals("Z") ? component : 0); backend.update();
                double phase = axis.equals("X") ? identity.getPhase().x : axis.equals("Y") ? identity.getPhase().y : identity.getPhase().z;
                boolean complete = !Double.isNaN(previousPhase) && phase < previousPhase;
                for (int i = 0; i < 15; i++) { double value = component(backend.gradient(backend.index(detectors[i][0], detectors[i][1], detectors[i][2])), axis); min[i] = Math.min(min[i], value); max[i] = Math.max(max[i], value); }
                if (complete) { int cycle = update / PERIOD; if (cycle > WARMUP && captured < CYCLES) { for (int i = 0; i < 15; i++) cycleResponses[captured][i] = (min[i] + max[i]) * .5; captured++; } Arrays.fill(min, Double.POSITIVE_INFINITY); Arrays.fill(max, Double.NEGATIVE_INFINITY); }
                previousPhase = phase;
            }
        } finally { backend.close(); }
        if (captured != CYCLES) throw new IllegalStateException("Expected cycles 6..11, captured " + captured);
        double[] means = new double[15]; for (int i = 0; i < 15; i++) for (int c = 0; c < CYCLES; c++) means[i] += cycleResponses[c][i] / CYCLES;
        double mean = Arrays.stream(means).average().orElseThrow(); double[] q = Arrays.stream(means).map(v -> v / mean).toArray();
        double emax = Arrays.stream(q).map(v -> Math.abs(v - 1)).max().orElseThrow(); double mad = Arrays.stream(q).map(v -> Math.abs(v - 1)).average().orElseThrow(); double stddev = Math.sqrt(Arrays.stream(q).map(v -> (v - 1) * (v - 1)).average().orElseThrow());
        int[] topology = topology(radius, indentation); double axisBody = q[8] - q[0];
        writeCycles(out.resolve("cycle-results.csv"), cycleResponses); long measurementNanos = System.nanoTime() - measurementStart;
        return new Result(shapeId, radius, indentation, axis, topology[0], topology[1], topology[2], q, mean, Math.abs(identity.getAmplitude().get(axis.equals("X") ? 0 : axis.equals("Y") ? 1 : 2)), emax, mad, stddev, axisBody, constructionNanos, measurementNanos, peakHeap(), peakProcess());
    }

    private static void writeShapeOutputs(Path out, Result r) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(out.resolve("directional-results.csv"))) { w.write("directionId,primitiveX,primitiveY,primitiveZ,graphShell,Q\n"); for (int i = 0; i < 15; i++) { var ray = IsotropyReferenceGeometry.RAYS.get(i); w.write(String.format(Locale.ROOT, "R%02d,%d,%d,%d,%d,%.17g%n", i + 1, ray.x, ray.y, ray.z, D, r.q[i])); } }
        Files.writeString(out.resolve("directional-response.svg"), directionalSvg(r));
    }

    private static void writeCycles(Path file, double[][] cycles) throws IOException { try (BufferedWriter w = Files.newBufferedWriter(file)) { w.write("cycle," + rayHeader() + "\n"); for (int c = 0; c < CYCLES; c++) w.write((c + 6) + "," + csv(cycles[c]) + "\n"); } }
    private static void writeMetadata(Path file, Result r, String status, String reason) throws IOException { try (BufferedWriter w = Files.newBufferedWriter(file)) { w.write("key,value\n"); put(w,"status",status); put(w,"shapeId",r.shapeId); put(w,"baseRadius",r.radius); put(w,"indentation",r.indentation); put(w,"axis",r.axis); put(w,"lattice","297^3"); put(w,"D",D); put(w,"primitiveRayCount",15); put(w,"updates",UPDATES); put(w,"cyclesDiscarded","1..5"); put(w,"measurementCycles","6..11"); put(w,"occupiedNodeCount",r.occupied); put(w,"boundaryNodeCount",r.boundary); put(w,"boundaryCrossingEdgeCount",r.crossing); put(w,"cycleEmax",r.emax); put(w,"cycleMAD",r.mad); put(w,"cycleStdDev",r.stddev); put(w,"axisQ",r.q[0]); put(w,"bodyDiagonalQ",r.q[8]); put(w,"axisBodyDifference",r.axisBody); put(w,"constructionNanos",r.constructionNanos); put(w,"measurementNanos",r.measurementNanos); put(w,"timePerUpdateNanos",r.measurementNanos/(double)UPDATES); put(w,"peakJvmHeapBytes",r.peakHeap); put(w,"peakProcessMemoryBytes",r.peakProcess); put(w,"completionEpoch",Instant.now()); put(w,"failureReason",reason); } }
    private static void writeFailure(Path file, String id, int radius, double indentation, String axis, Throwable t) { try { Files.createDirectories(file.toAbsolutePath().getParent()); Files.writeString(file, "key,value\nstatus,MEASUREMENT_INCOMPLETE\nshapeId,"+id+"\nbaseRadius,"+radius+"\nindentation,"+indentation+"\naxis,"+axis+"\nfailureReason,"+t.toString().replace('\n',' ')+'\n'); } catch (IOException ignored) { } }
    private static void writeConfiguration(Path out, String id, int radius, double indentation, String axis, int nx, int ny, int nz, int cx, int cy, int cz, List<int[]> offsets, double amplitude) throws IOException { Files.writeString(out.resolve("configuration.properties"), String.format(Locale.ROOT, "shapeId=%s\nbaseRadius=%d\nindentation=%.17g\naxis=%s\nlattice=%d^3\ndimensions=%d,%d,%d\nsourceNode=%d,%d,%d\nD=%d\namplitude=%.17g\nshapePredicate=abs(dx)+abs(dy)+abs(dz)+indentation*min(abs(dx),abs(dy),abs(dz)) < baseRadius\n", id, radius, indentation, axis, 297, nx, ny, nz, cx, cy, cz, D, amplitude)); try (BufferedWriter w = Files.newBufferedWriter(out.resolve("shape-nodes.csv"))) { w.write("dx,dy,dz,graphDistance\n"); for (int[] p : offsets) w.write(String.format(Locale.ROOT, "%d,%d,%d,%d%n", p[0],p[1],p[2],Math.abs(p[0])+Math.abs(p[1])+Math.abs(p[2]))); } }

    private static void updateGlobals(Path root) throws IOException {
        List<Result> rows = new ArrayList<>(); try (var stream = Files.list(root)) { for (Path d : stream.filter(p -> p.getFileName().toString().matches("r\\d{3}-i\\d{6}")).sorted().toList()) if (isComplete(d)) rows.add(read(d)); }
        try (BufferedWriter w = Files.newBufferedWriter(root.resolve("all-shapes.csv"))) { w.write("shapeId,baseRadius,indentation,status,occupiedNodeCount,boundaryNodeCount,boundaryCrossingEdgeCount,cycleEmax,cycleMAD,cycleStdDev,axisQ,bodyDiagonalQ,axisBodyDifference,globalRecord,globalBestForRadius,constructionNanos,measurementNanos,peakJvmHeapBytes,peakProcessMemoryBytes,timePerUpdateNanos"); for (int i=0;i<15;i++) w.write(",Q"+String.format(Locale.ROOT,"%02d",i+1)); w.write("\n"); double global = STEP2_EMAX; String globalId="STEP2-R005-I000000"; for (Result r:rows) { boolean bestRadius = rows.stream().filter(x->x.radius==r.radius).min(Comparator.comparingDouble(x->x.emax)).map(x->x.shapeId.equals(r.shapeId)).orElse(false); boolean record = r.emax < global; if(record){global=r.emax;globalId=r.shapeId;} w.write(String.format(Locale.ROOT,"%s,%d,%.17g,%s,%d,%d,%d,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%s,%s,%d,%d,%d,%d,%.17g",r.shapeId,r.radius,r.indentation,record?"NEW_GLOBAL_BEST":bestRadius?"NEW_BEST_FOR_RADIUS":"VALID",r.occupied,r.boundary,r.crossing,r.emax,r.mad,r.stddev,r.q[0],r.q[8],r.axisBody,record, bestRadius,r.constructionNanos,r.measurementNanos,r.peakHeap,r.peakProcess,r.measurementNanos/(double)UPDATES)); for(double q:r.q)w.write(String.format(Locale.ROOT,",%.17g",q));w.write("\n"); } }
        try (BufferedWriter w = Files.newBufferedWriter(root.resolve("best-shapes.csv"))) { w.write("baseRadius,unindentedShapeId,unindentedEmax,bestShapeId,bestIndentation,bestEmax,bestMAD,bestStdDev,improvementVsUnindented,relativeImprovement,globalBestSoFarEmax,globalBestSoFarRadius,globalBestSoFarIndentation\n"); double global=STEP2_EMAX; int globalR=5; double globalI=0; for(int radius:RADII){List<Result> rs=rows.stream().filter(x->x.radius==radius).toList(); if(rs.isEmpty())continue;Result zero=rs.stream().filter(x->x.indentation==0).findFirst().orElse(null),best=rs.stream().min(Comparator.comparingDouble(x->x.emax)).orElseThrow();if(best.emax<global){global=best.emax;globalR=radius;globalI=best.indentation;}w.write(String.format(Locale.ROOT,"%d,%s,%.17g,%s,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%d,%.17g%n",radius,zero==null?"":zero.shapeId,zero==null?Double.NaN:zero.emax,best.shapeId,best.indentation,best.emax,best.mad,best.stddev,zero==null?Double.NaN:zero.emax-best.emax,zero==null?Double.NaN:1-best.emax/zero.emax,global,globalR,globalI));} }
        updateProgress(root, rows);
        writeReports(root, rows);
    }
    private static void updateProgress(Path root,List<Result> rows)throws IOException{try(BufferedWriter w=Files.newBufferedWriter(root.resolve("search-progress.csv"))){w.write("currentRadius,currentShapeId,currentIndentation,searchPhase,lowerBracket,upperBracket,coarseStep,fineStep,lastCompletedShape,radiusComplete,experimentComplete\n");boolean all=RADII.stream().allMatch(r->rows.stream().anyMatch(x->x.radius==r));for(int r:RADII){long count=rows.stream().filter(x->x.radius==r).count();Result lastR=rows.stream().filter(x->x.radius==r).max(Comparator.comparing(x->x.indentation)).orElse(null);w.write(String.format(Locale.ROOT,"%d,%s,%.17g,%s,,,,%s,%s,%s%n",r,lastR==null?"":lastR.shapeId,lastR==null?Double.NaN:lastR.indentation,count==0?"PENDING":"COARSE_AND_FINE",lastR==null?"":lastR.shapeId,count>0?"true":"false",all?"true":"false"));}}}
    private static void writeReports(Path root,List<Result> rows)throws IOException{writeMetricSvg(root.resolve("indentation-error-by-radius.svg"),rows,"Step 3 Emax by radius and indentation",false);writeMetricSvg(root.resolve("best-emax-vs-radius.svg"),rows,"Step 3 best Emax by radius",true);StringBuilder ind=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1400\" height=\"500\"><text x=\"20\" y=\"25\">Best indentation by configured radius</text><text x=\"20\" y=\"55\">");StringBuilder dir;for(int radius:RADII){List<Result>rs=rows.stream().filter(x->x.radius==radius).toList();if(rs.isEmpty())continue;Result best=rs.stream().min(Comparator.comparingDouble(x->x.emax)).orElseThrow();ind.append("R").append(radius).append("=").append(best.indentation).append(" ");Result zero=rs.stream().filter(x->x.indentation==0).findFirst().orElse(best);dir=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1500\" height=\"600\"><text x=\"20\" y=\"25\">R=").append(radius).append(" unindented versus best indented; ideal Q=1</text><text x=\"20\" y=\"55\">");for(int i=0;i<15;i++)dir.append("R").append(String.format(Locale.ROOT,"%02d",i+1)).append(" un=").append(zero.q[i]).append(" best=").append(best.q[i]).append(" ");Files.writeString(root.resolve("best-directional-response-r"+radius+".svg"),dir.append("</text></svg>\n").toString());Files.writeString(root.resolve("best-shape-r"+radius+".svg"),shapeSvg(root.resolve(best.shapeId),best));}Files.writeString(root.resolve("best-indentation-vs-radius.svg"),ind.append("</text></svg>\n").toString());StringBuilder report=new StringBuilder("# REM Framework Step 3 / Task 1 — Indented Extended Identity Sweep\n\n## Confirmed\n\nAll listed Z-axis shapes with status VALID used the compact backend, 297^3 lattice, D=24, 15 exact rays, 198 updates, cycles 1..5 discarded, and cycles 6..11 measured.\n\n## Step 2 reference\n\nR=5 unindented Emax=2.5633920365873792.\n\n## Best shape per radius\n\nbaseRadius,unindentedEmax,bestIndentation,bestEmax,improvement\n");double global=STEP2_EMAX;Result globalResult=null;for(int radius:RADII){List<Result>rs=rows.stream().filter(x->x.radius==radius).toList();if(rs.isEmpty())continue;Result zero=rs.stream().filter(x->x.indentation==0).findFirst().orElseThrow(),best=rs.stream().min(Comparator.comparingDouble(x->x.emax)).orElseThrow();if(best.emax<global){global=best.emax;globalResult=best;}report.append(String.format(Locale.ROOT,"%d,%.17g,%.17g,%.17g,%.17g\n",radius,zero.emax,best.indentation,best.emax,zero.emax-best.emax));}if(globalResult!=null){report.append("\n## Global best\n\n").append(String.format(Locale.ROOT,"R=%d, indentation=%.17g, Emax=%.17g, MAD=%.17g, StdDev=%.17g. Q values: ",globalResult.radius,globalResult.indentation,globalResult.emax,globalResult.mad,globalResult.stddev));for(double q:globalResult.q)report.append(String.format(Locale.ROOT,"%.17g ",q));report.append("\n\nThe body-diagonal Q is ").append(globalResult.q[8]).append(" and axis Q is ").append(globalResult.q[0]).append(".\n");}report.append("\n## Scope\n\nX/Y validation is run only after the Z search and is recorded separately. No complete-isotropy claim is made.\n");Files.writeString(root.resolve("report.md"),report.toString());}
    private static void writeMetricSvg(Path file,List<Result> rows,String title,boolean bestOnly)throws IOException{StringBuilder b=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1600\" height=\"600\"><text x=\"20\" y=\"25\">").append(title).append("</text><text x=\"20\" y=\"55\">");for(int radius:RADII){List<Result>rs=rows.stream().filter(x->x.radius==radius).toList();if(rs.isEmpty())continue;if(bestOnly){Result r=rs.stream().min(Comparator.comparingDouble(x->x.emax)).orElseThrow();b.append("R").append(radius).append("=").append(r.emax).append(" ");}else for(Result r:rs)b.append("R").append(radius).append("/I").append(r.indentation).append("=").append(r.emax).append(" ");}Files.writeString(file,b.append("</text></svg>\n").toString());}
    private static String shapeSvg(Path dir,Result r)throws IOException{long[] shell=new long[r.radius+1];for(String line:Files.readAllLines(dir.resolve("shape-nodes.csv")).subList(1,Files.readAllLines(dir.resolve("shape-nodes.csv")).size())){String[]v=line.split(",");shell[Math.min(r.radius,Integer.parseInt(v[3]))]++;}StringBuilder b=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1400\" height=\"500\"><text x=\"20\" y=\"25\">Best shape ").append(r.shapeId).append("; occupied-node graph-shell projection</text><text x=\"20\" y=\"55\">");for(int i=0;i<shell.length;i++)b.append("d").append(i).append("=").append(shell[i]).append(" ");return b.append("</text></svg>\n").toString();}
    private static Result read(Path d)throws IOException{Properties p=new Properties();for(String line:Files.readAllLines(d.resolve("metadata.csv")).subList(1,Files.readAllLines(d.resolve("metadata.csv")).size())){int i=line.indexOf(',');if(i>0)p.setProperty(line.substring(0,i),line.substring(i+1));}String[] m=Files.readAllLines(d.resolve("directional-results.csv")).get(1).split(",");List<String> lines=Files.readAllLines(d.resolve("directional-results.csv"));double[] q=new double[15];for(int i=0;i<15;i++)q[i]=Double.parseDouble(lines.get(i+1).substring(lines.get(i+1).lastIndexOf(',')+1));return new Result(p.getProperty("shapeId"),Integer.parseInt(p.getProperty("baseRadius")),Double.parseDouble(p.getProperty("indentation")),p.getProperty("axis"),Integer.parseInt(p.getProperty("occupiedNodeCount")),Integer.parseInt(p.getProperty("boundaryNodeCount")),Integer.parseInt(p.getProperty("boundaryCrossingEdgeCount")),q,0,0,Double.parseDouble(p.getProperty("cycleEmax")),Double.parseDouble(p.getProperty("cycleMAD")),Double.parseDouble(p.getProperty("cycleStdDev")),Double.parseDouble(p.getProperty("axisBodyDifference")),Long.parseLong(p.getProperty("constructionNanos")),Long.parseLong(p.getProperty("measurementNanos")),Long.parseLong(p.getProperty("peakJvmHeapBytes")),Long.parseLong(p.getProperty("peakProcessMemoryBytes")));}
    private static boolean isComplete(Path d){return Files.isDirectory(d)&&Files.exists(d.resolve("metadata.csv"))&&Files.exists(d.resolve("cycle-results.csv"))&&Files.exists(d.resolve("directional-results.csv"))&&readStatus(d).equals("VALID");} private static String readStatus(Path d){try{for(String l:Files.readAllLines(d.resolve("metadata.csv")))if(l.startsWith("status,"))return l.substring(7); }catch(IOException ignored){}return "";} private static void archive(Path d,Path root)throws IOException{Files.move(d,root.resolve(d.getFileName()+".MEASUREMENT_INCOMPLETE-"+System.currentTimeMillis()),StandardCopyOption.REPLACE_EXISTING);}
    private static String shapeId(int r,double i){return String.format(Locale.ROOT,"r%03d-i%06d",r,Math.round(i*100000));} private static List<int[]> offsets(int r,double ind){List<int[]>o=new ArrayList<>();for(int x=-(r-1);x<=r-1;x++)for(int y=-(r-1);y<=r-1;y++)for(int z=-(r-1);z<=r-1;z++)if(Math.abs(x)+Math.abs(y)+Math.abs(z)+ind*Math.min(Math.abs(x),Math.min(Math.abs(y),Math.abs(z)))<r)o.add(new int[]{x,y,z});return o;} private static int[] topology(int r,double ind){List<int[]>o=offsets(r,ind);java.util.HashSet<String>s=new java.util.HashSet<>();for(int[]p:o)s.add(p[0]+":"+p[1]+":"+p[2]);int b=0,e=0;for(int[]p:o){boolean boundary=false;for(int[]d:new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}})if(!s.contains((p[0]+d[0])+":"+(p[1]+d[1])+":"+(p[2]+d[2]))){boundary=true;e++;}if(boundary)b++;}return new int[]{o.size(),b,e};}
    private static int[][] detectorCoordinates(int x,int y,int z){int[][]d=new int[15][3];for(int i=0;i<15;i++){var ray=IsotropyReferenceGeometry.RAYS.get(i);int m=D/IsotropyReferenceGeometry.graphLength(ray);d[i]=new int[]{x+ray.x*m,y+ray.y*m,z+ray.z*m};}return d;} private static double component(Vector3d v,String a){return a.equals("X")?v.x:a.equals("Y")?v.y:v.z;} private static String csv(double[]a){StringBuilder b=new StringBuilder();for(int i=0;i<a.length;i++)b.append(i==0?"":",").append(String.format(Locale.ROOT,"%.17g",a[i]));return b.toString();} private static String rayHeader(){StringBuilder b=new StringBuilder();for(int i=0;i<15;i++)b.append(i==0?"R01":",R"+String.format(Locale.ROOT,"%02d",i+1));return b.toString();}
    private static String directionalSvg(Result r){StringBuilder b=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1400\" height=\"500\"><text x=\"20\" y=\"25\">"+r.shapeId+" axis="+r.axis+"; ideal Q=1</text><text x=\"20\" y=\"55\">");for(int i=0;i<15;i++)b.append("R").append(String.format(Locale.ROOT,"%02d",i+1)).append("=").append(r.q[i]).append(" ");return b.append("</text></svg>\n").toString();}
    private static void put(BufferedWriter w,String k,Object v)throws IOException{w.write(k+","+String.valueOf(v).replace('\n',' ')+'\n');} private static long peakHeap(){MemoryMXBean m=ManagementFactory.getMemoryMXBean();return Math.max(m.getHeapMemoryUsage().getUsed(),Runtime.getRuntime().totalMemory());} private static long peakProcess(){try{for(String l:Files.readAllLines(Path.of("/proc/self/status")))if(l.startsWith("VmHWM:"))return Long.parseLong(l.replaceAll("[^0-9]",""))*1024;}catch(Exception ignored){}return -1;}
}
