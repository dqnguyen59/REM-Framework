package remframework.geometry.discovery.research.diamond;
import java.nio.file.*;
import java.util.*;
import remframework.geometry.discovery.*;
/** Follow-up controls for the tagged finite-packet result; preserves source and Node mechanics. */
public final class DiamondPacketValidationAudit {
    private DiamondPacketValidationAudit() { }
    private record Setup(String name,int cells,double width,int grid) { }
    private record Row(String name,int direction,int cells,double width,int grid,double speed,double fieldError,double mixtureError,String status) {
        String csv(){return String.format(Locale.ROOT,"%s,%d,%d,%.1f,%d,%.12g,%.9f,%.9f,%s%n",name,direction,cells,width,grid,speed,fieldError,mixtureError,status);}
    }
    public static double relativeChange(double baseline,double changed) {
        return !Double.isFinite(baseline)||baseline<=0||!Double.isFinite(changed)||changed<=0?Double.NaN:100*Math.abs(changed-baseline)/baseline;
    }
    private static String key(String name,int direction){return name+":"+direction;}
    private static void load(Path path,Map<String,Row> rows) throws Exception {
        if(!Files.exists(path))return;
        for(var line:Files.readAllLines(path).subList(1,Files.readAllLines(path).size())) {
            if(line.isBlank())continue;var p=line.split(",");
            var r=new Row(p[0],Integer.parseInt(p[1]),Integer.parseInt(p[2]),Double.parseDouble(p[3]),Integer.parseInt(p[4]),Double.parseDouble(p[5]),Double.parseDouble(p[6]),Double.parseDouble(p[7]),p[8]);
            rows.put(key(r.name(),r.direction()),r);
        }
    }
    private static void save(Path path,Map<String,Row> rows) throws Exception {
        var csv=new StringBuilder("case,direction,cells,width,grid,speed,field_error_percent,mixture_error_percent,status\n");
        rows.values().forEach(r->csv.append(r.csv()));Files.writeString(path,csv);
    }
    private static void measure(Setup setup,int direction,Map<String,Row> rows) {
        if(rows.containsKey(key(setup.name(),direction)))return;
        try {
            var r=DiamondSpectralPacketAudit.run(setup.cells(),16,DiamondDirectionalComparison.directions().get(direction),40,setup.width(),setup.grid());
            double speed=r.measured().length(),pred=r.mixturePrediction().length();
            if(!Double.isFinite(speed)||speed<=0||!Double.isFinite(pred)||pred<=0||!Double.isFinite(r.relativeFieldError()))throw new IllegalStateException("Invalid measurement "+r.status().code());
            rows.put(key(setup.name(),direction),new Row(setup.name(),direction,setup.cells(),setup.width(),setup.grid(),speed,100*r.relativeFieldError(),100*r.measured().distance(r.mixturePrediction())/pred,r.status().code().name()));
        }catch(RuntimeException failure) {
            var status=GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GEOMETRY_REALIZATION,GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS,"Packet validation "+setup.name()+" direction "+direction,failure.toString()));
            System.out.println(status);
            rows.put(key(setup.name(),direction),new Row(setup.name(),direction,setup.cells(),setup.width(),setup.grid(),Double.NaN,Double.NaN,Double.NaN,status.code().name()));
        }
    }
    private static double speed(Map<String,Row> rows,String name,int direction) {
        var row=rows.get(key(name,direction));return row==null?Double.NaN:row.speed();
    }
    private static double maxChange(Map<String,Row> rows,String a,String b,int[] directions) {
        double max=0;for(int d:directions)max=Math.max(max,relativeChange(speed(rows,a,d),speed(rows,b,d)));return max;
    }
    public static void main(String[] args) throws Exception {
        var path=Path.of("docs/research/diamond-packet-validation.csv");var rows=new LinkedHashMap<String,Row>();
        // Reuse the tagged baseline and its already completed three-direction controls; new file is resumable.
        load(Path.of("docs/research/diamond-independent-speed.csv"),rows);load(path,rows);
        var setups=List.of(new Setup("domain",32,8,4),new Setup("grid",28,8,5));
        for(var setup:setups)for(int d=0;d<26;d++) {
            measure(setup,d,rows);save(path,rows);System.out.println("Validation "+setup.name()+" "+(d+1)+"/26");
        }
        int[] controls={0,1,4};
        int[] all=java.util.stream.IntStream.range(0,26).toArray();
        for(var setup:List.of(new Setup("bandBaseline",32,8,5),new Setup("narrow",32,10,5)))for(int d:setup.name().equals("narrow")?all:controls) {
            measure(setup,d,rows);save(path,rows);System.out.println("Validation "+setup.name()+" direction "+d);
        }
        for(int d=0;d<26;d++) {
            measure(new Setup("narrowDomain",28,10,5),d,rows);save(path,rows);System.out.println("Validation bandwidth "+(d+1)+"/26");
        }
        var report=new StringBuilder("Actual Node v0.1 /4, k=.5 gamma=1, wavelength16,40 updates; quadrature displacement-intensity centroid.\nAll26-direction baseline, domain, grid and narrower-band sets; narrower-band domain controls on all26 sampled directions. Original baseline/6 controls reused from tagged CSV; remaining runs use unchanged spectral packet runner.\n");
        boolean complete=true;
        for(String name:List.of("angular","domain","grid","narrowDomain","narrow")) {
            var speeds=new ArrayList<Double>();for(int d=0;d<26;d++)speeds.add(speed(rows,name,d));double spread=DiamondIndependentSpeedAudit.spread(speeds);
            complete&=Double.isFinite(spread);report.append(String.format(Locale.ROOT,"%s: spread %.6f%%; numerical1%% target %s.%n",name,spread,Double.isFinite(spread)?(spread<=1?"met":"not met"):"inconclusive"));
        }

        double domain=maxChange(rows,"angular","domain",all),grid=maxChange(rows,"angular","grid",all),band=maxChange(rows,"grid","narrowDomain",all),narrowDomain=maxChange(rows,"narrow","narrowDomain",all);
        complete&=Double.isFinite(domain)&&Double.isFinite(grid)&&Double.isFinite(band)&&Double.isFinite(narrowDomain);
        report.append(String.format(Locale.ROOT,"Max scalar-speed sensitivity: domain %.6f%%; grid %.6f%%; bandwidth %.6f%%; narrower-packet domain %.6f%%.%n",domain,grid,band,narrowDomain));
        var code=complete?GeometryConfigurationStatusCode.GEOMETRIC_SYMMETRY_UNKNOWN:GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS;
        report.append(code+". No full isotropy acceptance: finite mixture/centroid observable, angular coverage limited to26 directions and one displacement polarization. GUI trough result remains separate. No mechanics changes. Per-run failures retained.\n");
        Files.writeString(Path.of("docs/research/diamond-packet-validation.txt"),report);System.out.print(report);
    }
}