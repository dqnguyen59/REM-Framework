package remframework.geometry.discovery.research.diamond;
import java.nio.file.*;
import java.util.*;
import org.joml.Vector3d;
import remframework.geometry.discovery.*;
/** Independent actual-Node finite acoustic packet check; centroid speed is not trough/energy speed. */
public final class DiamondIndependentSpeedAudit {
    private DiamondIndependentSpeedAudit() { }
    public static double spread(List<Double> speeds) {
        if(speeds.size()!=26 || speeds.stream().anyMatch(v->!Double.isFinite(v)||v<=0))return Double.NaN;
        var s=speeds.stream().mapToDouble(Double::doubleValue).summaryStatistics();
        return 100*(s.getMax()-s.getMin())/s.getAverage();
    }
    public static void main(String[] args) throws Exception {
        var path=Path.of("docs/research/diamond-independent-speed.csv");
        var csv=new StringBuilder("case,direction,cells,width,grid,speed,field_error_percent,mixture_error_percent,status\n");
        var speeds=new ArrayList<Double>();var baseline=new ArrayList<Vector3d>();
        var directions=DiamondDirectionalComparison.directions();
        for(int i=0;i<26;i++) {
            var r=run(csv,"angular",i,28,8,4,directions.get(i));
            speeds.add(r==null?Double.NaN:r.measured().length());baseline.add(r==null?null:new Vector3d(r.measured()));
            Files.writeString(path,csv);System.out.println("Independent angular "+(i+1)+"/26");
        }
        double domain=0,grid=0;
        for(int i:new int[]{0,1,4}) {
            var d=run(csv,"domain",i,32,8,4,directions.get(i));
            var g=run(csv,"grid",i,28,8,5,directions.get(i));
            if(d==null||g==null||baseline.get(i)==null){domain=Double.NaN;grid=Double.NaN;}
            else {domain=Math.max(domain,100*d.measured().distance(baseline.get(i))/baseline.get(i).length());
                grid=Math.max(grid,100*g.measured().distance(baseline.get(i))/baseline.get(i).length());}
            Files.writeString(path,csv);System.out.println("Independent controls direction "+i);
        }
        double spread=spread(speeds);
        var code=Double.isFinite(spread)&&Double.isFinite(domain)&&Double.isFinite(grid)?GeometryConfigurationStatusCode.GEOMETRIC_SYMMETRY_UNKNOWN:GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS;
        var report=String.format(Locale.ROOT,"Actual Node v0.1 /4, k=.5 gamma=1; wavelength16, width8, 40 updates. Paired quadrature displacement-intensity centroid. 26 directions,28cells; domain32 and Fourier half-grid5 controls on directions0,1,4.\nSpread (max-min)/mean: %.6f%%. Max domain vector change: %.6f%%. Max grid vector change: %.6f%%.\n1%% numerical target: %s. %s.\nFinite bandwidth, displacement weighting and three-direction-only controls remain. Not energy speed, trough speed, a monochromatic limit, or full REM isotropy certification. Failures retained in CSV.\n",spread,domain,grid,Double.isFinite(spread)?(spread<=1?"met; validation pending":"not met"):"inconclusive",code);
        Files.writeString(Path.of("docs/research/diamond-independent-speed.txt"),report);System.out.print(report);
    }
    private static DiamondSpectralPacketAudit.Result run(StringBuilder csv,String name,int index,int cells,double width,int grid,Vector3d direction) {
        try {
            var r=DiamondSpectralPacketAudit.run(cells,16,direction,40,width,grid);
            double speed=r.measured().length(),pred=r.mixturePrediction().length();
            if(!Double.isFinite(speed)||speed<=0||!Double.isFinite(pred)||pred<=0||!Double.isFinite(r.relativeFieldError()))throw new IllegalStateException("Invalid packet output: "+r.status().code());
            csv.append(String.format(Locale.ROOT,"%s,%d,%d,%.1f,%d,%.12g,%.9f,%.9f,%s%n",name,index,cells,width,grid,speed,100*r.relativeFieldError(),100*r.measured().distance(r.mixturePrediction())/pred,r.status().code()));
            return r;
        } catch(RuntimeException failure) {
            var status=GeometryConfigurationStatus.of(GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.GEOMETRY_REALIZATION,GeometryConfigurationStatusCode.NO_REALIZATION_FOUND_WITHIN_SEARCH_LIMITS,"Independent packet measurement",failure.toString()));
            csv.append(name+","+index+","+cells+","+width+","+grid+",NaN,NaN,NaN,"+status.code()+"\n");
            System.out.println(status);return null;
        }
    }
}