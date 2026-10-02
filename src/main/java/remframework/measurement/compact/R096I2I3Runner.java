package remframework.measurement.compact;

import org.joml.Vector3d;
import remframework.helpers.Constants;
import remframework.measurement.detector.ExtendedIdentityShape;
import remframework.measurement.detector.IsotropyReferenceGeometry;
import remframework.opengl.model.entity.Identity;
import remframework.settings.AppSettings;
import remframework.settings.Project;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Resumable R=96 two-parameter shape experiment using the frozen V2 benchmark. */
public final class R096I2I3Runner {
    private static final int R=96, UPDATES=198, PERIOD=18, CYCLES=6, DIMENSION=333;
    private static final double BASELINE=1.4968715547720990;
    private static final String CANONICAL_CONFIGURATION="samples/isotropy-reference-v2-Z.qfs";
    private static final String CANONICAL_CONFIGURATION_SHA256="b07f739c06ba15546756fef818fb1b9e1c4cc1b76e2aa61c544302f5f5cab3ad";
    private static final double[] GRID={0,.25,.5,.75,1,1.25,1.5,2};
    private record Result(double i2,double i3,String id,String hash,String status,String equivalent,
                          int occupied,int boundary,int crossing,int shell,int minGap,int maxGap,
                          double mean,double[] means,double[] q,double emax,double mad,double sd,
                          double axis,double twoAxis,double threeAxis) { }
    private R096I2I3Runner() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Usage: R096I2I3Runner <project.qfs> <output-directory> <coarse|refine|all>");
        IsotropyReferenceGeometry.validate();
        Path project=Path.of(args[0]).toAbsolutePath().normalize(), root=Path.of(args[1]).toAbsolutePath().normalize();
        validateCanonicalConfiguration(project);
        Files.createDirectories(root);
        if (args[2].equals("baseline")) {
            ensureBaseline(project,root);
            rebuildGlobal(root);
            writeProgress(root,"BASELINE_VALIDATED",0,0,best(root));
            return;
        }
        if (args[2].equals("coarse") || args[2].equals("all")) {
            ensureBaseline(project,root);
            for (double i2:GRID) for (double i3:GRID) runPair(project,root,i2,i3,"COARSE");
        }
        if (args[2].equals("refine") || args[2].equals("all")) {
            if (!Files.exists(root.resolve("all-shapes.csv"))) ensureBaseline(project,root);
            Result best=best(root);
            double step=.125;
            for (int level=1;level<=5;level++,step/=2) {
                Set<String> tested=new HashSet<>();
                for (Result r:List.of(best)) for (int dx=-1;dx<=1;dx++) for(int dy=-1;dy<=1;dy++) {
                    double i2=round(r.i2+dx*step), i3=round(r.i3+dy*step);
                    if(i2>=0&&i3>=0&&tested.add(key(i2,i3))) runPair(project,root,i2,i3,"REFINEMENT_"+level);
                }
                Result previous=best, next=best(root);
                if(next.emax<best.emax) best=next;
                writeProgress(root,"REFINEMENT",level,step,best);
                if(best.emax<=.05) break;
                if(next.hash.equals(previous.hash) && next.emax>=previous.emax) {
                    if(level>=2) break;
                }
            }
        }
        rebuildGlobal(root);
        writeProgress(root,"COMPLETE",5,.0078125,best(root));
    }

    private static void ensureBaseline(Path project,Path root) throws Exception {
        runPair(project,root,0,0,"BASELINE");
        Result b=readDir(findPair(root,0,0));
        if (b.status.equals("BASELINE_EQUIVALENCE_FAILED") || b.occupied!=1161471 ||
                Math.abs(b.emax-BASELINE)>1e-12 || Math.abs(b.mad-1.1974912325005524)>1e-12 ||
                Math.abs(b.sd-1.2221853220803358)>1e-12)
            throw new IllegalStateException("BASELINE_EQUIVALENCE_FAILED");
    }

    private static void validateCanonicalConfiguration(Path project) throws Exception {
        Path canonical=Path.of(CANONICAL_CONFIGURATION).toAbsolutePath().normalize();
        if (!project.equals(canonical) || !Files.exists(project)
                || !sha256(Files.readAllBytes(project)).equals(CANONICAL_CONFIGURATION_SHA256))
            throw new IllegalStateException("CONFIGURATION_MISMATCH: expected canonical V2 configuration " + canonical);
    }

    private static void runPair(Path project,Path root,double i2,double i3,String phase) throws Exception {
        i2=round(i2); i3=round(i3); String id=id(i2,i3); Path out=root.resolve(id);
        if (complete(out)) { rebuildGlobal(root); writeProgress(root,phase,phaseLevel(phase),0,best(root)); return; }
        if(Files.exists(out)) archive(out,root); Path stage=root.resolve(id+".MEASUREMENT_INCOMPLETE");
        if(Files.exists(stage)) archive(stage,root); Files.createDirectories(stage);
        try {
            List<int[]> points=points(i2,i3); String hash=hash(points); Path canonical=findHash(root,hash);
            Result r=canonical==null?measure(project,stage,i2,i3,id,hash,phase,points):copyEquivalent(stage,canonical,i2,i3,id,hash,phase);
            if(i2==0&&i3==0 && (r.occupied!=1161471 || Math.abs(r.emax-BASELINE)>1e-12)) r=new Result(i2,i3,id,hash,"BASELINE_EQUIVALENCE_FAILED","",r.occupied,r.boundary,r.crossing,r.shell,r.minGap,r.maxGap,r.mean,r.means,r.q,r.emax,r.mad,r.sd,r.axis,r.twoAxis,r.threeAxis);
            writeMetadata(stage,r,phase); Files.move(stage,out,StandardCopyOption.ATOMIC_MOVE); rebuildGlobal(root); writeProgress(root,phase,phaseLevel(phase),0,best(root));
            System.out.printf(Locale.ROOT,"I2=%.9f I3=%.9f Emax=%.17g status=%s%n",i2,i3,r.emax,r.status);
        } catch(Throwable t) { writeFailure(stage,i2,i3,id,phase,t); throw t; }
    }

    private static Result measure(Path projectPath,Path out,double i2,double i3,String id,String hash,String phase,List<int[]> points) throws Exception {
        AppSettings.getInstance().loadFromFile(Constants.BASE_PATH+Constants.APP_SETTINGS_FILE);
        Project project=Project.getInstance(); project.loadHeadlessFromFile(projectPath.toString()); project.getRemModel().startSimulation();
        if(project.occupancies.size()!=1) throw new IllegalStateException("BACKEND_FAILURE: expected one source occupancy");
        Identity identity=project.occupancies.getFirst().getIdentity(); int nx=project.getDimensionX(),ny=project.getDimensionY(),nz=project.getDimensionZ();
        int cx=project.occupancies.getFirst().getNodeIndex().x,cy=project.occupancies.getFirst().getNodeIndex().y,cz=project.occupancies.getFirst().getNodeIndex().z;
        int[] sg=commonShell(points); int shell=sg[0],minGap=sg[1],maxGap=sg[2]; int[][] d=new int[15][3];
        for(int i=0;i<15;i++){var v=IsotropyReferenceGeometry.RAYS.get(i);int m=shell/IsotropyReferenceGeometry.graphLength(v);d[i]=new int[]{cx+v.x*m,cy+v.y*m,cz+v.z*m};int gap=minDistance(d[i][0]-cx,d[i][1]-cy,d[i][2]-cz,points);if(gap<24)throw new IllegalStateException("DETECTOR_DISTANCE_INVALID");minGap=Math.min(minGap,gap);maxGap=Math.max(maxGap,gap);}
        CompactMeasurementBackend backend=new CompactMeasurementBackend(nx,ny,nz,project.getConstantWaveSpeed(),project.getConstantElectricFactor()); backend.setParallelism(Math.min(16,AppSettings.getInstance().getNumThreads())); backend.setSourceNodes(points.stream().mapToInt(p->backend.index(cx+p[0],cy+p[1],cz+p[2])).toArray());
        writeShape(out,i2,i3,id,hash,shell,minGap,maxGap,cx,cy,cz,points);
        double[][] cycles=new double[CYCLES][15]; double[] lo=new double[15],hi=new double[15]; Arrays.fill(lo,Double.POSITIVE_INFINITY);Arrays.fill(hi,Double.NEGATIVE_INFINITY);int captured=0;double previous=Double.NaN;
        try { for(int update=1;update<=UPDATES;update++){Vector3d f=identity.generateExcitation();if(f==null)throw new IllegalStateException("BACKEND_FAILURE: inactive source");backend.setSourceForce(0,0,f.z);backend.update();double phaseValue=identity.getPhase().z;boolean complete=!Double.isNaN(previous)&&phaseValue<previous;for(int i=0;i<15;i++){double value=backend.gradient(backend.index(d[i][0],d[i][1],d[i][2])).z;lo[i]=Math.min(lo[i],value);hi[i]=Math.max(hi[i],value);}if(complete){int cycle=update/PERIOD;if(cycle>5&&captured<CYCLES)for(int i=0;i<15;i++)cycles[captured][i]=(lo[i]+hi[i])*.5; if(cycle>5)captured++;Arrays.fill(lo,Double.POSITIVE_INFINITY);Arrays.fill(hi,Double.NEGATIVE_INFINITY);}previous=phaseValue;}} finally {backend.close();}
        if(captured!=CYCLES)throw new IllegalStateException("BACKEND_FAILURE: expected six measurement cycles, got "+captured);
        double[] means=new double[15];for(int i=0;i<15;i++)for(int c=0;c<CYCLES;c++)means[i]+=cycles[c][i]/CYCLES;double mean=Arrays.stream(means).average().orElse(Double.NaN);if(!Double.isFinite(mean)||Math.abs(mean)<1e-30)throw new IllegalStateException("NUMERIC_MEAN_UNDEFINED");double[]q=Arrays.stream(means).map(v->v/mean).toArray();double e=Arrays.stream(q).map(v->Math.abs(v-1)).max().orElseThrow(),mad=Arrays.stream(q).map(v->Math.abs(v-1)).average().orElseThrow(),sd=Math.sqrt(Arrays.stream(q).map(v->(v-1)*(v-1)).average().orElseThrow());
        writeMeasured(out,id,shell,d,cx,cy,cz,points,means,mean,q,cycles,e,mad,sd);int[] top=topology(points);return result(i2,i3,id,hash,"VALID","",top,shell,minGap,maxGap,mean,means,q,e,mad,sd);
    }

    private static Result copyEquivalent(Path out,Path canonical,double i2,double i3,String id,String hash,String phase)throws IOException {for(String n:List.of("configuration.properties","shape-nodes.csv","detector-geometry.csv","cycle-results.csv","directional-results.csv","directional-response.svg"))Files.copy(canonical.resolve(n),out.resolve(n));Result r=readDir(canonical);return new Result(i2,i3,id,hash,"EQUIVALENT_SHAPE",canonical.getFileName().toString(),r.occupied,r.boundary,r.crossing,r.shell,r.minGap,r.maxGap,r.mean,r.means,r.q,r.emax,r.mad,r.sd,r.axis,r.twoAxis,r.threeAxis);}
    private static Result result(double i2,double i3,String id,String hash,String status,String eq,int[]t,int shell,int min,int max,double mean,double[]m,double[]q,double e,double mad,double sd){return new Result(i2,i3,id,hash,status,eq,t[0],t[1],t[2],shell,min,max,mean,m,q,e,mad,sd,avg(q,new int[]{0,1}),avg(q,new int[]{2,3,4,5,6,7,9}),avg(q,new int[]{8,10,11,12,13,14}));}
    private static double avg(double[]a,int[]ix){double s=0;for(int i:ix)s+=a[i];return s/ix.length;}

    private static void rebuildGlobal(Path root)throws IOException {List<Result> rows=new ArrayList<>();try(var s=Files.list(root)){for(Path d:s.filter(x->x.getFileName().toString().matches("i2-[0-9]{6}_i3-[0-9]{6}")).sorted().toList())if(complete(d))rows.add(readDir(d));}rows.sort(Comparator.comparingDouble(Result::i2).thenComparingDouble(Result::i3));Result best=rows.stream().filter(x->!x.status.equals("BASELINE_EQUIVALENCE_FAILED")).min(Comparator.comparingDouble(Result::emax)).orElse(null);try(BufferedWriter w=Files.newBufferedWriter(root.resolve("all-shapes.csv"))){w.write("shapeId,shapeHash,I2,I3,status,equivalentToShapeId,occupiedNodeCount,boundaryNodeCount,boundaryCrossingEdgeCount,detectorShell,minimumDetectorSourceGap,maximumDetectorSourceGap,globalDirectionalMean,cycleEmax,cycleMAD,cycleStdDev,axisClassMeanQ,twoAxisClassMeanQ,threeAxisClassMeanQ,absoluteImprovement,relativeImprovement,bestSoFarEmax,bestSoFarI2,bestSoFarI3,newGlobalRecord");for(int i=0;i<15;i++)w.write(",Q"+String.format(Locale.ROOT,"%02d",i+1));w.write("\n");double b=Double.POSITIVE_INFINITY;for(Result r:rows){boolean rec=r.emax<b;if(rec)b=r.emax;w.write(String.format(Locale.ROOT,"%s,%s,%.17g,%.17g,%s,%s,%d,%d,%d,%d,%d,%d,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%s",r.id,r.hash,r.i2,r.i3,r.status,r.equivalent,r.occupied,r.boundary,r.crossing,r.shell,r.minGap,r.maxGap,r.mean,r.emax,r.mad,r.sd,r.axis,r.twoAxis,r.threeAxis,BASELINE-r.emax,1-r.emax/BASELINE,b,r.i2,r.i3,rec));for(double q:r.q)w.write(String.format(Locale.ROOT,",%.17g",q));w.write("\n");}}
        if(best!=null)writeBest(root,best);charts(root,rows,best);report(root,rows,best);}

    private static void writeBest(Path root,Result r)throws IOException {try(BufferedWriter w=Files.newBufferedWriter(root.resolve("best-shape.csv"))){w.write("I2,I3,shapeId,shapeHash,occupiedNodeCount,detectorShell,minimumDetectorSourceGap,maximumDetectorSourceGap,Emax,MAD,StdDev,axisClassMeanQ,twoAxisClassMeanQ,threeAxisClassMeanQ,absoluteImprovement,relativeImprovement");for(int i=0;i<15;i++)w.write(",Q"+String.format(Locale.ROOT,"%02d",i+1));w.write("\n");w.write(String.format(Locale.ROOT,"%.17g,%.17g,%s,%s,%d,%d,%d,%d,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g",r.i2,r.i3,r.id,r.hash,r.occupied,r.shell,r.minGap,r.maxGap,r.emax,r.mad,r.sd,r.axis,r.twoAxis,r.threeAxis,BASELINE-r.emax,1-r.emax/BASELINE));for(double q:r.q)w.write(String.format(Locale.ROOT,",%.17g",q));}}
    private static void writeProgress(Path root,String phase,int level,double step,Result best)throws IOException {long completed=0,equivalent=0;try(var s=Files.list(root)){for(Path d:s.filter(x->x.getFileName().toString().matches("i2-[0-9]{6}_i3-[0-9]{6}")).toList())if(complete(d)){completed++;if(readDir(d).status.equals("EQUIVALENT_SHAPE"))equivalent++;}}try(BufferedWriter w=Files.newBufferedWriter(root.resolve("progress.csv"))){w.write("searchPhase,coarseCandidatesTotal,coarseCandidatesCompleted,refinementLevel,refinementStep,currentI2,currentI3,uniqueShapesMeasured,equivalentShapesSkipped,bestI2,bestI3,bestEmax,experimentComplete\n");w.write(String.format(Locale.ROOT,"%s,64,%d,%d,%.17g,%.17g,%.17g,%d,%d,%.17g,%.17g,%.17g,%s\n",phase,Math.min(completed,64),level,step,best.i2,best.i3,completed-equivalent,equivalent,best.i2,best.i3,best.emax,phase.equals("COMPLETE")));}}
    private static int phaseLevel(String phase){if(phase.startsWith("REFINEMENT_"))return Integer.parseInt(phase.substring("REFINEMENT_".length()));return 0;}

    private static void charts(Path root,List<Result>rows,Result best)throws IOException {if(rows.isEmpty())return;StringBuilder land=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1100\" height=\"850\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/><text x=\"80\" y=\"25\" font-size=\"18\">Measured R=96 I2/I3 cycle Emax landscape</text><text x=\"520\" y=\"830\">I2</text><text x=\"20\" y=\"430\" transform=\"rotate(-90 20 430)\">I3</text>");double max=rows.stream().mapToDouble(Result::emax).max().orElse(BASELINE);for(Result r:rows){int x=80+(int)(r.i2/2.0*800),y=720-(int)(r.i3/2.0*650);String color=String.format(Locale.ROOT,"#%02x%02x%02x",(int)(255*r.emax/max),80,(int)(255*(1-r.emax/max)));land.append("<rect x=\"").append(x-8).append("\" y=\"").append(y-8).append("\" width=\"16\" height=\"16\" fill=\"").append(color).append("\"/><title>I2=").append(r.i2).append(" I3=").append(r.i3).append(" Emax=").append(r.emax).append("</title>");}if(best!=null){int x=80+(int)(best.i2/2*800),y=720-(int)(best.i3/2*650);land.append("<circle cx=\"").append(x).append("\" cy=\"").append(y).append("\" r=\"10\" fill=\"none\" stroke=\"black\" stroke-width=\"3\"/>");}land.append("<text x=\"80\" y=\"780\">Measured cells only; red=higher Emax, blue=lower Emax; circled=best</text></svg>\n");Files.writeString(root.resolve("i2-i3-emax-landscape.svg"),land.toString());Files.writeString(root.resolve("best-emax-vs-measurement.svg"),"<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1000\" height=\"500\"><text x=\"30\" y=\"25\">Best-so-far Emax by measured unique shape</text><polyline fill=\"none\" stroke=\"#1565c0\" points=\""+bestPoints(rows)+"\"/></svg>\n");if(best!=null){Result base=rows.stream().filter(x->x.i2==0&&x.i3==0).findFirst().orElse(best);Files.writeString(root.resolve("best-directional-response.svg"),directionalCompare(base,best));Files.writeString(root.resolve("class-response-vs-candidate.svg"),classChart(rows));Files.writeString(root.resolve("best-shape.svg"),shapeSvg(root.resolve(best.id)));}}
    private static String bestPoints(List<Result>rows){double b=Double.POSITIVE_INFINITY;StringBuilder s=new StringBuilder();int n=0;for(Result r:rows){if(r.emax<b)b=r.emax;n++;s.append(n*20).append(",").append((int)(450-b*180)).append(" ");}return s.toString();}
    private static String directionalCompare(Result base,Result best){StringBuilder s=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1200\" height=\"600\"><text x=\"40\" y=\"25\">Best versus baseline normalized Q; ideal=1</text>");for(int i=0;i<15;i++){int x=60+i*75;s.append("<circle cx=\"").append(x).append("\" cy=\"").append(500-(int)(base.q[i]*180)).append("\" r=\"4\" fill=\"red\"/><circle cx=\"").append(x).append("\" cy=\"").append(500-(int)(best.q[i]*180)).append("\" r=\"4\" fill=\"blue\"/><text x=\"").append(x-10).append("\" y=\"530\">R").append(String.format(Locale.ROOT,"%02d",i+1)).append("</text>");}return s.append("<text x=\"40\" y=\"55\">red=baseline, blue=best</text></svg>\n").toString();}
    private static String classChart(List<Result>rows){StringBuilder s=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1200\" height=\"600\"><text x=\"30\" y=\"25\">Directional class means for measured candidates</text>");int n=0;for(Result r:rows){if(r.emax<=BASELINE||r.status.equals("NEW_GLOBAL_BEST")){int x=50+n++*30;s.append("<circle cx=\"").append(x).append("\" cy=\"").append(500-r.axis*160).append("\" r=\"3\" fill=\"#1565c0\"/><circle cx=\"").append(x).append("\" cy=\"").append(500-r.twoAxis*160).append("\" r=\"3\" fill=\"#2e7d32\"/><circle cx=\"").append(x).append("\" cy=\"").append(500-r.threeAxis*160).append("\" r=\"3\" fill=\"#d32f2f\"/>");}}return s.append("<text x=\"40\" y=\"55\">blue=1-axis, green=2-axis, red=3-axis</text></svg>\n").toString();}
    private static String shapeSvg(Path dir)throws IOException{List<int[]>p=readPoints(dir.resolve("shape-nodes.csv"));StringBuilder s=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1200\" height=\"700\"><text x=\"30\" y=\"25\">Best shape center slices</text>");projection(s,p,2,40,50,"XY z=0");projection(s,p,1,420,50,"XZ y=0");projection(s,p,0,800,50,"YZ x=0");return s.append("</svg>\n").toString();}
    private static void projection(StringBuilder s,List<int[]>p,int fixed,int ox,int oy,String title){s.append("<text x=\"").append(ox).append("\" y=\"").append(oy).append("\">").append(title).append("</text>");for(int[]v:p)if(v[fixed]==0&&v[(fixed+1)%3]%2==0&&v[(fixed+2)%3]%2==0)s.append("<rect x=\"").append(ox+180+v[(fixed+1)%3]*2).append("\" y=\"").append(oy+180-v[(fixed+2)%3]*2).append("\" width=\"2\" height=\"2\" fill=\"#1565c0\"/>");}

    private static void report(Path root,List<Result>rows,Result best)throws IOException {StringBuilder s=new StringBuilder("# REM Framework Phase 3 / Task 2 — R=96 I2/I3 Shape Optimization\n\n## Baseline\n\nV2 baseline: Emax=1.4968715547720990, MAD=1.1974912325005524, StdDev=1.2221853220803358, occupiedNodeCount=1161471.\n\n## Shape family\n\nF2=m2-m1, F3=m1, with predicate `abs(dx)+abs(dy)+abs(dz)+I2*(m2-m1)+I3*m1 < 96`.\n\n## Coarse search\n\nThe complete 8×8 parameter grid is recorded in `all-shapes.csv`; equivalent occupied-node sets are marked explicitly.\n\n## Refinement\n\nDeterministic local refinement levels use spacings 0.125, 0.0625, 0.03125, 0.015625, and 0.0078125.\n\n## Global best\n\n");if(best!=null){s.append(String.format(Locale.ROOT,"I2=%.17g, I3=%.17g, shapeId=%s, Emax=%.17g, MAD=%.17g, StdDev=%.17g, occupiedNodeCount=%d, detectorShell=%d, minimumDetectorSourceGap=%d, maximumDetectorSourceGap=%d.\n\nQ values: ",best.i2,best.i3,best.id,best.emax,best.mad,best.sd,best.occupied,best.shell,best.minGap,best.maxGap));for(double q:best.q)s.append(String.format(Locale.ROOT,"%.17g ",q));s.append(String.format(Locale.ROOT,"\n\nClass means: 1-axis=%.17g, 2-axis=%.17g, 3-axis=%.17g.\n\nImprovement=%.17g absolute, %.17g relative.\n",best.axis,best.twoAxis,best.threeAxis,BASELINE-best.emax,1-best.emax/BASELINE));int li=0;for(int i=1;i<15;i++)if(Math.abs(best.q[i]-1)>Math.abs(best.q[li]-1))li=i;s.append("\nLimiting direction: R").append(String.format(Locale.ROOT,"%02d",li+1)).append(" ").append(IsotropyReferenceGeometry.RAYS.get(li)).append(".\n\nNear-zero target Emax <= 0.05: ").append(best.emax<=.05?"achieved":"not achieved").append(".\n");}s.append("\n## Scientific interpretation\n\nThe report describes the measured 15-ray benchmark only; it does not establish isotropy beyond those rays.\n\n## Recommendation\n\nReview the measured limiting directional structure before selecting the next permitted experiment.\n");Files.writeString(root.resolve("report.md"),s.toString());}

    private static List<int[]>points(double i2,double i3){List<int[]>p=new ArrayList<>();for(int x=-(R-1);x<=R-1;x++)for(int y=-(R-1);y<=R-1;y++)for(int z=-(R-1);z<=R-1;z++)if(ExtendedIdentityShape.score(x,y,z,i2,i3)<R)p.add(new int[]{x,y,z});return p;}
    private static int[]commonShell(List<int[]>p){for(int shell=24;shell<=240;shell+=12){int min=Integer.MAX_VALUE,max=0;boolean ok=true;for(var v:IsotropyReferenceGeometry.RAYS){int m=shell/IsotropyReferenceGeometry.graphLength(v),d=minDistance(v.x*m,v.y*m,v.z*m,p);if(d<24)ok=false;min=Math.min(min,d);max=Math.max(max,d);}if(ok)return new int[]{shell,min,max};}throw new IllegalStateException("DETECTOR_DISTANCE_INVALID");}
    private static int minDistance(int x,int y,int z,List<int[]>p){int d=Integer.MAX_VALUE;for(int[]v:p)d=Math.min(d,Math.abs(x-v[0])+Math.abs(y-v[1])+Math.abs(z-v[2]));return d;}
    private static int[]topology(List<int[]>p){Set<String>s=new HashSet<>();for(int[]v:p)s.add(v[0]+":"+v[1]+":"+v[2]);int b=0,e=0;int[][]ds={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};for(int[]v:p){boolean q=false;for(int[]d:ds)if(!s.contains((v[0]+d[0])+":"+(v[1]+d[1])+":"+(v[2]+d[2]))){q=true;e++;}if(q)b++;}return new int[]{p.size(),b,e};}
    private static String hash(List<int[]>p)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");for(int[]v:p)for(int x:v)for(int sh:new int[]{24,16,8,0})md.update((byte)(x>>sh));StringBuilder s=new StringBuilder();for(byte b:md.digest())s.append(String.format(Locale.ROOT,"%02x",b));return s.toString();}
    private static String sha256(byte[] bytes)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");StringBuilder s=new StringBuilder();for(byte b:md.digest(bytes))s.append(String.format(Locale.ROOT,"%02x",b));return s.toString();}
    private static Path findHash(Path root,String h)throws IOException{try(var s=Files.list(root)){for(Path d:s.filter(x->x.getFileName().toString().matches("i2-[0-9]{6}_i3-[0-9]{6}")).toList())try{if(readProperties(d.resolve("metadata.csv")).getProperty("shapeHash","").equals(h))return d;}catch(Exception ignored){}}return null;}
    private static Path findPair(Path root,double i2,double i3){return root.resolve(id(i2,i3));}
    private static String id(double i2,double i3){return String.format(Locale.ROOT,"i2-%06d_i3-%06d",Math.round(i2*100000),Math.round(i3*100000));}
    private static String key(double a,double b){return id(a,b);}
    private static double round(double v){return Math.round(v*128.0)/128.0;}
    private static boolean complete(Path d){try{String s=readProperties(d.resolve("metadata.csv")).getProperty("status","");return List.of("VALID","EQUIVALENT_SHAPE").contains(s)&&Files.exists(d.resolve("directional-results.csv"))&&Files.readString(d.resolve("detector-geometry.csv")).contains(",25,false");}catch(Exception e){return false;}}
    private static void archive(Path d,Path root)throws IOException{Files.move(d,root.resolve(d.getFileName()+"-"+Instant.now().toEpochMilli()+".MEASUREMENT_INCOMPLETE"),StandardCopyOption.REPLACE_EXISTING);}
    private static Properties readProperties(Path f)throws IOException{Properties p=new Properties();for(String l:Files.readAllLines(f).subList(1,Files.readAllLines(f).size())){int i=l.indexOf(',');if(i>0)p.setProperty(l.substring(0,i),l.substring(i+1));}return p;}
    private static Result readDir(Path d)throws IOException{Properties p=readProperties(d.resolve("metadata.csv"));double[]q=new double[15],m=new double[15];for(int i=0;i<15;i++){q[i]=Double.parseDouble(p.getProperty("Q"+String.format(Locale.ROOT,"%02d",i+1),"NaN"));m[i]=Double.parseDouble(p.getProperty("directionMeanR"+String.format(Locale.ROOT,"%02d",i+1),"NaN"));}return new Result(Double.parseDouble(p.getProperty("I2")),Double.parseDouble(p.getProperty("I3")),p.getProperty("shapeId"),p.getProperty("shapeHash"),p.getProperty("status"),p.getProperty("equivalentToShapeId", ""),Integer.parseInt(p.getProperty("occupiedNodeCount")),Integer.parseInt(p.getProperty("boundaryNodeCount")),Integer.parseInt(p.getProperty("boundaryCrossingEdgeCount")),Integer.parseInt(p.getProperty("detectorShell")),Integer.parseInt(p.getProperty("minimumDetectorSourceGap")),Integer.parseInt(p.getProperty("maximumDetectorSourceGap")),Double.parseDouble(p.getProperty("globalDirectionalMean")),m,q,Double.parseDouble(p.getProperty("cycleEmax")),Double.parseDouble(p.getProperty("cycleMAD")),Double.parseDouble(p.getProperty("cycleStdDev")),Double.parseDouble(p.getProperty("axisClassMeanQ")),Double.parseDouble(p.getProperty("twoAxisClassMeanQ")),Double.parseDouble(p.getProperty("threeAxisClassMeanQ")));}
    private static Result best(Path root)throws IOException{List<Result>r=new ArrayList<>();try(var s=Files.list(root)){for(Path d:s.filter(x->x.getFileName().toString().matches("i2-[0-9]{6}_i3-[0-9]{6}")).toList())if(complete(d))r.add(readDir(d));}return r.stream().min(Comparator.comparingDouble(Result::emax)).orElseThrow();}
    private static void writeShape(Path out,double i2,double i3,String id,String h,int shell,int min,int max,int cx,int cy,int cz,List<int[]>p)throws IOException{Files.writeString(out.resolve("configuration.properties"),String.format(Locale.ROOT,"baseRadius=96\nI2=%.17g\nI3=%.17g\nshapeId=%s\nshapeHash=%s\nshapePredicate=abs(dx)+abs(dy)+abs(dz)+I2*(m2-m1)+I3*m1 < R\ndetectorShell=%d\nminimumDetectorSourceGap=%d\nmaximumDetectorSourceGap=%d\nsourceNode=%d,%d,%d\n",i2,i3,id,h,shell,min,max,cx,cy,cz));Files.writeString(out.resolve("shape-signature.txt"),h+"\n");try(BufferedWriter w=Files.newBufferedWriter(out.resolve("shape-nodes.csv"))){w.write("dx,dy,dz\n");for(int[]v:p)w.write(v[0]+","+v[1]+","+v[2]+"\n");}}
    private static void writeMeasured(Path out,String id,int shell,int[][]d,int cx,int cy,int cz,List<int[]>points,double[]m,double mean,double[]q,double[][]cycles,double e,double mad,double sd)throws IOException {try(BufferedWriter w=Files.newBufferedWriter(out.resolve("detector-geometry.csv"))){w.write("directionId,primitiveDirection,detectorShell,detectorRelativeX,detectorRelativeY,detectorRelativeZ,minimumGraphDistanceToOccupiedRegion,detectorOccupied\n");for(int i=0;i<15;i++){var v=IsotropyReferenceGeometry.RAYS.get(i);int gap=minDistance(d[i][0]-cx,d[i][1]-cy,d[i][2]-cz,points);w.write(String.format(Locale.ROOT,"R%02d,\"(%d,%d,%d)\",%d,%d,%d,%d,%d,false\n",i+1,v.x,v.y,v.z,shell,d[i][0]-cx,d[i][1]-cy,d[i][2]-cz,gap));}}try(BufferedWriter w=Files.newBufferedWriter(out.resolve("cycle-results.csv"))){w.write("directionId,cycleNumber,cycleResponse\n");for(int c=0;c<6;c++)for(int i=0;i<15;i++)w.write(String.format(Locale.ROOT,"R%02d,%d,%.17g\n",i+1,c+6,cycles[c][i]));}try(BufferedWriter w=Files.newBufferedWriter(out.resolve("directional-results.csv"))){w.write(R096DirectionalCsv.header()+"\n");for(int i=0;i<15;i++){var v=IsotropyReferenceGeometry.RAYS.get(i);w.write(R096DirectionalCsv.row(String.format(Locale.ROOT,"R%02d",i+1),"("+v.x+","+v.y+","+v.z+")",shell,d[i][0]-cx,d[i][1]-cy,d[i][2]-cz,m[i],mean,q[i],"VALID")+"\n");}}Files.writeString(out.resolve("directional-response.svg"),"<svg xmlns=\"http://www.w3.org/2000/svg\"><text>Measured normalized Q</text></svg>\n");}
    private static void writeMetadata(Path out,Result r,String phase)throws IOException{try(BufferedWriter w=Files.newBufferedWriter(out.resolve("metadata.csv"))){w.write("key,value\n");put(w,"I2",r.i2);put(w,"I3",r.i3);put(w,"shapeId",r.id);put(w,"shapeHash",r.hash);put(w,"status",r.status);put(w,"equivalentToShapeId",r.equivalent);put(w,"searchPhase",phase);put(w,"occupiedNodeCount",r.occupied);put(w,"boundaryNodeCount",r.boundary);put(w,"boundaryCrossingEdgeCount",r.crossing);put(w,"detectorShell",r.shell);put(w,"minimumDetectorSourceGap",r.minGap);put(w,"maximumDetectorSourceGap",r.maxGap);put(w,"globalDirectionalMean",r.mean);put(w,"cycleEmax",r.emax);put(w,"cycleMAD",r.mad);put(w,"cycleStdDev",r.sd);put(w,"axisClassMeanQ",r.axis);put(w,"twoAxisClassMeanQ",r.twoAxis);put(w,"threeAxisClassMeanQ",r.threeAxis);for(int i=0;i<15;i++){put(w,"directionMeanR"+String.format(Locale.ROOT,"%02d",i+1),r.means[i]);put(w,"Q"+String.format(Locale.ROOT,"%02d",i+1),r.q[i]);}put(w,"completionEpoch",Instant.now());}}
    private static void writeFailure(Path out,double i2,double i3,String id,String phase,Throwable t){try{Files.createDirectories(out);try(BufferedWriter w=Files.newBufferedWriter(out.resolve("metadata.csv"))){w.write("key,value\nstatus,MEASUREMENT_INCOMPLETE\nI2,"+i2+"\nI3,"+i3+"\nshapeId,"+id+"\nsearchPhase,"+phase+"\nfailureReason,"+t.toString().replace('\n',' ')+'\n');}}catch(IOException ignored){}}
    private static List<int[]>readPoints(Path f)throws IOException{List<int[]>p=new ArrayList<>();for(String l:Files.readAllLines(f).subList(1,Files.readAllLines(f).size())){String[]v=l.split(",");p.add(new int[]{Integer.parseInt(v[0]),Integer.parseInt(v[1]),Integer.parseInt(v[2])});}return p;}
    private static void put(BufferedWriter w,String k,Object v)throws IOException{w.write(k+","+String.valueOf(v).replace('\n',' ')+"\n");}
}
