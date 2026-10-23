package remframework.geometry.discovery.research.diamond;
import java.util.*;
import org.joml.*;
import remframework.geometry.discovery.*;

/** Trilinear interpolation on each FCC primitive grid, averaged across the two sublattices. */
public final class DiamondReceiverSampling {
    private DiamondReceiverSampling() { }
    private record Site(int x,int y,int z) { }
    public static DiamondDirectionalComparison.Receiver receiver(GeometryDiscoveryModel model,Vector3d point,String name,double length) {
        return new Sampler(model.nodes().size(),id->model.nodes().get(id).position(),id->model.nodes().get(id).neighborIds().size()==4,length).receiver(point,name);
    }
    public static final class Sampler {
        @FunctionalInterface public interface SiteResolver {int id(int x,int y,int z);}
        private final Map<Site,Integer> lookup;
        private final SiteResolver direct;
        private final java.util.function.IntFunction<Vector3d> positions;
        private final java.util.function.IntPredicate interior;
        private final double length;
        private final Quaterniond inverse=new Quaterniond().rotationTo(new Vector3d(1,1,1).normalize(),new Vector3d(1,0,0)).conjugate();
        public Sampler(int count,java.util.function.IntFunction<Vector3d> positions,java.util.function.IntPredicate interior,double length) {
            this(count,positions,interior,length,null);
        }
        public Sampler(int count,java.util.function.IntFunction<Vector3d> positions,java.util.function.IntPredicate interior,double length,SiteResolver direct) {
            this.direct=direct;lookup=direct==null?new HashMap<>():null;
            this.positions=positions;this.interior=interior;this.length=length;
            if(direct==null)for(int id=0;id<count;id++) {
                var q=inverse.transform(new Vector3d(positions.apply(id))).mul(java.lang.Math.sqrt(3)/length);
                lookup.put(new Site((int)java.lang.Math.round(q.x),(int)java.lang.Math.round(q.y),(int)java.lang.Math.round(q.z)),id);
            }
        }
        public DiamondDirectionalComparison.Receiver receiver(Vector3d point,String name) {
        var q=inverse.transform(new Vector3d(point)).mul(java.lang.Math.sqrt(3)/length);
        Map<Integer,Double> weights=new LinkedHashMap<>();
        for(int sub=0;sub<=1;sub++) {
            double x=q.x-sub,y=q.y-sub,z=q.z-sub;
            double[] primitive={(-x+y+z)/4,(x-y+z)/4,(x+y-z)/4};int[] floor=new int[3];double[] frac=new double[3];
            for(int i=0;i<3;i++) {floor[i]=(int)java.lang.Math.floor(primitive[i]+1e-12);frac[i]=java.lang.Math.max(0,primitive[i]-floor[i]);}
            for(int a=0;a<2;a++) for(int b=0;b<2;b++) for(int c=0;c<2;c++) {
                double w=.5*(a==0?1-frac[0]:frac[0])*(b==0?1-frac[1]:frac[1])*(c==0?1-frac[2]:frac[2]);
                if(w<1e-14) continue;
                int u=floor[0]+a,v=floor[1]+b,t=floor[2]+c;
                Integer id=direct==null?lookup.get(new Site(2*(v+t)+sub,2*(u+t)+sub,2*(u+v)+sub)):
                    Integer.valueOf(direct.id(2*(v+t)+sub,2*(u+t)+sub,2*(u+v)+sub));
                if(id==null || id<0 || !interior.test(id)) throw new IllegalArgumentException("Receiver stencil reaches boundary: "+name);
                weights.merge(id,w,Double::sum);
            }
        }
        double far=0;for(int id:weights.keySet()) far=java.lang.Math.max(far,positions.apply(id).distance(point));
        return new DiamondDirectionalComparison.Receiver(name,List.copyOf(weights.keySet()),List.copyOf(weights.values()),far);
    }
}
}
