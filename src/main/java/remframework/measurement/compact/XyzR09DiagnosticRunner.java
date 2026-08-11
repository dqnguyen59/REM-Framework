package remframework.measurement.compact;

import org.joml.Vector3d;
import remframework.helpers.Constants;
import remframework.measurement.detector.ExtendedIdentityShape;
import remframework.measurement.detector.IsotropyReferenceGeometry;
import remframework.opengl.model.entity.Identity;
import remframework.settings.AppSettings;
import remframework.settings.Project;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Component-wise R09 diagnostic for the Phase 5 multi-axis oscillator. */
public final class XyzR09DiagnosticRunner {
    private static final int R=96,N=333,UPDATES=198,PERIOD=18;
    private static final double BASELINE_Q=2.4968715547720990, BASELINE_MEAN=0.06669444463224276;
    private record C(String id,String file) {}
    private XyzR09DiagnosticRunner() {}
    public static void main(String[] a) throws Exception {
        if(a.length<1) throw new IllegalArgumentException("usage: <config-id|finalize> [output-root]");
        IsotropyReferenceGeometry.validate(); Path root=Path.of(a.length>1?a[1]:"measurements/xyz-r09-diagnostic-r096"); Files.createDirectories(root);
        if(a[0].equals("finalize")){finalizeReport(root);return;}
        C c=switch(a[0]){case "Z_ONLY"->new C("Z_ONLY","samples/isotropy-reference-v2-Z.qfs");case "XYZ_IN_PHASE"->new C("XYZ_IN_PHASE","samples/isotropy-reference-v2-XYZ-in-phase.qfs");case "XYZ_0_120_240"->new C("XYZ_0_120_240","samples/isotropy-reference-v2-XYZ-0-120-240.qfs");case "XYZ_0_90_180"->new C("XYZ_0_90_180","samples/isotropy-reference-v2-XYZ-0-90-180.qfs");default->throw new IllegalArgumentException("unknown config "+a[0]);};
        run(c,root.resolve(c.id));
    }
    private static void run(C c,Path out) throws Exception {
        Files.createDirectories(out); AppSettings.getInstance().loadFromFile(Constants.BASE_PATH+Constants.APP_SETTINGS_FILE); Project p=Project.getInstance(); p.loadHeadlessFromFile(Path.of(c.file).toAbsolutePath().toString()); p.getRemModel().startSimulation();
        Identity id=p.occupancies.getFirst().getIdentity(); int cx=p.occupancies.getFirst().getNodeIndex().x,cy=p.occupancies.getFirst().getNodeIndex().y,cz=p.occupancies.getFirst().getNodeIndex().z;
        List<int[]> src=new ArrayList<>(); for(int x=-(R-1);x<=R-1;x++)for(int y=-(R-1);y<=R-1;y++)for(int z=-(R-1);z<=R-1;z++)if(ExtendedIdentityShape.score(x,y,z,0,0)<R)src.add(new int[]{x,y,z});
        int shell=120; int[][] d=new int[15][3]; for(int i=0;i<15;i++){var v=IsotropyReferenceGeometry.RAYS.get(i);int m=shell/IsotropyReferenceGeometry.graphLength(v);d[i]=new int[]{cx+v.x*m,cy+v.y*m,cz+v.z*m};}
        CompactMeasurementBackend b=new CompactMeasurementBackend(N,N,N,p.getConstantWaveSpeed(),p.getConstantElectricFactor()); b.setParallelism(1); b.setSourceNodes(src.stream().mapToInt(v->b.index(cx+v[0],cy+v[1],cz+v[2])).toArray());
        try(BufferedWriter ot=Files.newBufferedWriter(out.resolve("oscillator-trace.csv")); BufferedWriter rt=Files.newBufferedWriter(out.resolve("r09-update-vector.csv")); BufferedWriter at=Files.newBufferedWriter(out.resolve("all-detector-gradient-components.csv")); BufferedWriter ct=Files.newBufferedWriter(out.resolve("r09-cycle-components.csv"))){
            ot.write("update,activeX,activeY,activeZ,phaseX,phaseY,phaseZ,phaseAngleX,phaseAngleY,phaseAngleZ,phaseIncrementX,phaseIncrementY,phaseIncrementZ,excitationX,excitationY,excitationZ,excitationMagnitude\n"); rt.write("update,configuration,directionId,directionX,directionY,directionZ,gradientX,gradientY,gradientZ,gradientMagnitude\n"); at.write("update,configuration,directionId,directionX,directionY,directionZ,gradientX,gradientY,gradientZ,gradientMagnitude\n"); ct.write("configuration,directionId,directionX,directionY,directionZ,cycleNumber,minimumGradientX,maximumGradientX,cycleResponseX,minimumGradientY,maximumGradientY,cycleResponseY,minimumGradientZ,maximumGradientZ,cycleResponseZ\n");
            double[] lo={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},hi={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY}; double sx=0,sy=0,sz=0; int count=0; double prev=Double.NaN;
            for(int u=1;u<=UPDATES;u++){Vector3d e=id.generateExcitation(); b.setSourceForce(e.x,e.y,e.z); b.update(); var g=b.gradient(b.index(d[8][0],d[8][1],d[8][2])); double mag=e.length();
                ot.write(String.format(Locale.ROOT,"%d,%s,%s,%s,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g\n",u,id.getActive().x,id.getActive().y,id.getActive().z,id.getPhase().x,id.getPhase().y,id.getPhase().z,id.getPhaseAngle().x,id.getPhaseAngle().y,id.getPhaseAngle().z,id.getPhaseIncrement().x,id.getPhaseIncrement().y,id.getPhaseIncrement().z,e.x,e.y,e.z,mag));
                rt.write(String.format(Locale.ROOT,"%d,%s,R09,1,1,1,%.17g,%.17g,%.17g,%.17g\n",u,c.id,g.x,g.y,g.z,g.length()));
                for(int j=0;j<15;j++){var q=b.gradient(b.index(d[j][0],d[j][1],d[j][2])); at.write(String.format(Locale.ROOT,"%d,%s,R%02d,%d,%d,%d,%.17g,%.17g,%.17g,%.17g\n",u,c.id,j+1,IsotropyReferenceGeometry.RAYS.get(j).x,IsotropyReferenceGeometry.RAYS.get(j).y,IsotropyReferenceGeometry.RAYS.get(j).z,q.x,q.y,q.z,q.length()));}
                lo[0]=Math.min(lo[0],g.x);lo[1]=Math.min(lo[1],g.y);lo[2]=Math.min(lo[2],g.z);hi[0]=Math.max(hi[0],g.x);hi[1]=Math.max(hi[1],g.y);hi[2]=Math.max(hi[2],g.z); double ph=id.getPhase().z; if(!Double.isNaN(prev)&&ph<prev){int cyc=u/PERIOD;if(cyc>=6&&cyc<=11){double[] m={(lo[0]+hi[0])/2,(lo[1]+hi[1])/2,(lo[2]+hi[2])/2};sx+=m[0];sy+=m[1];sz+=m[2];count++;ct.write(String.format(Locale.ROOT,"%s,R09,1,1,1,%d,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g\n",c.id,cyc,lo[0],hi[0],m[0],lo[1],hi[1],m[1],lo[2],hi[2],m[2]));} Arrays.fill(lo,Double.POSITIVE_INFINITY);Arrays.fill(hi,Double.NEGATIVE_INFINITY);} prev=ph;
            }
            double mx=sx/count,my=sy/count,mz=sz/count; String status=c.id.equals("Z_ONLY")&&Math.abs(mz-BASELINE_MEAN)>1e-12?"BASELINE_EQUIVALENCE_FAILED":"VALID_DIAGNOSTIC"; Files.writeString(out.resolve("metadata.csv"),"status,"+status+"\nconfiguration,"+c.id+"\nmeanResponseX,"+mx+"\nmeanResponseY,"+my+"\nmeanResponseZ,"+mz+"\n"); Files.writeString(out.resolve("CONFIGURATION-STATUS.md"),"ConfigurationStatus = "+status+"\n");
        } finally {b.close();}
    }
    private static void finalizeReport(Path root)throws IOException {StringBuilder csv=new StringBuilder("configuration,meanResponseX,meanResponseY,meanResponseZ,ratioXvsZOnly,ratioYvsZOnly,ratioZvsZOnly,status\n"); StringBuilder r=new StringBuilder("# R09=(1,1,1) XYZ oscillator diagnostic\n\nControl gate: persisted V2 R09 Z response = "+BASELINE_MEAN+" (Q="+BASELINE_Q+").\n\n| Configuration | Mean X | Mean Y | Mean Z | Status |\n|---|---:|---:|---:|---|\n"); for(String n:List.of("Z_ONLY","XYZ_IN_PHASE","XYZ_0_120_240","XYZ_0_90_180")){Map<String,String> v=read(root.resolve(n).resolve("metadata.csv"));String st=v.getOrDefault("status","MEASUREMENT_INCOMPLETE");if(!v.containsKey("meanResponseX")){csv.append(n+",,,,,,"+st+"\n");r.append("| "+n+" | — | — | — | "+st+" |\n");continue;}double x=Double.parseDouble(v.get("meanResponseX")),y=Double.parseDouble(v.get("meanResponseY")),q=Double.parseDouble(v.get("meanResponseZ"));csv.append(String.format(Locale.ROOT,"%s,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%s\n",n,x,y,q,x/BASELINE_MEAN,y/BASELINE_MEAN,q/BASELINE_MEAN,st));r.append(String.format(Locale.ROOT,"| %s | %.17g | %.17g | %.17g | %s |\n",n,x,y,q,st));}Files.writeString(root.resolve("configuration-summary.csv"),csv.toString());r.append("\nThis is a component-wise diagnostic only; no XYZ scalar Emax/MAD/StdDev or isotropy claim is defined.\n");Files.writeString(root.resolve("report.md"),r.toString());Files.writeString(root.resolve("CONFIGURATION-STATUS.md"),"ConfigurationStatus = MEASUREMENT_INCOMPLETE\n"); }
    private static Map<String,String> read(Path p)throws IOException{Map<String,String>m=new HashMap<>();for(String s:Files.readAllLines(p)){String[]x=s.split(",",2);if(x.length==2)m.put(x[0],x[1]);}return m;}
}
