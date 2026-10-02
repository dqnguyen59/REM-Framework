package remframework.geometry.discovery.realization.session;

import java.util.*;
import org.joml.Vector3d;

/** Coherent-winding, outward-alignment, and measure-only convexity diagnostics. */
public final class OrientationConvexityCalculator {
    private static final double EPS=1e-12;
    public OrientationConvexityDiagnostics calculate(List<List<Integer>> input,List<Vector3d> p,double tolerance){
        if(input.isEmpty()||p.isEmpty())return OrientationConvexityDiagnostics.undetermined("No accepted face complex.");
        List<List<Integer>> faces=coherentFaces(input);if(faces==null)return new OrientationConvexityDiagnostics(OrientationState.INCOHERENT_ORIENTATION,Double.NaN,Double.NaN,Double.NaN,0,false,"Face cycles cannot be assigned one coherent manifold winding.");
        Vector3d center=new Vector3d();p.forEach(center::add);center.div(p.size());
        List<Plane> planes=new ArrayList<>();double signed=0;
        for(List<Integer> face:faces){Plane plane=plane(face,p);if(plane==null)return new OrientationConvexityDiagnostics(OrientationState.DEGENERATE_FACE_NORMAL,Double.NaN,Double.NaN,Double.NaN,0,false,"At least one accepted face has a degenerate normal.");planes.add(plane);signed+=plane.normal.dot(new Vector3d(plane.center).sub(center));}
        boolean reversed=signed<0;if(reversed)planes=planes.stream().map(x->new Plane(x.center,new Vector3d(x.normal).negate(),x.vertices)).toList();
        double outward=0,sum=0,max=0;int count=0,violations=0;
        for(Plane plane:planes){Vector3d radial=new Vector3d(plane.center).sub(center);double alignment=radial.lengthSquared()<=EPS?Double.NaN:plane.normal.dot(radial.normalize());if(!Double.isFinite(alignment))return OrientationConvexityDiagnostics.undetermined("A face centroid coincides with the selected center.");outward=Math.max(outward,Math.max(0,-alignment));for(int id=0;id<p.size();id++)if(!plane.vertices.contains(id)){double v=Math.max(0,new Vector3d(p.get(id)).sub(plane.center).dot(plane.normal));max=Math.max(max,v);sum+=v*v;count++;if(v>tolerance)violations++;}}
        return new OrientationConvexityDiagnostics(outward==0?OrientationState.COHERENT_AND_OUTWARD:OrientationState.COHERENT_BUT_INWARD,outward,max,count==0?0:Math.sqrt(sum/count),violations,max<=tolerance,(reversed?"A global winding reversal was applied. ":"")+"Winding is coherent; convexity remains measure-only.");
    }
    public static List<List<Integer>> coherentFaces(List<List<Integer>> input){
        List<List<Integer>> faces=input.stream().map(List::copyOf).toList();int n=faces.size();
        Boolean[] flip=new Boolean[n];flip[0]=false;ArrayDeque<Integer> q=new ArrayDeque<>();q.add(0);
        while(!q.isEmpty()){
            int a=q.remove();
            for(int b=0;b<n;b++)if(a!=b){
                int relation=relation(faces.get(a),faces.get(b));if(relation==0)continue;
                boolean wanted=flip[a]^(relation==1);
                if(flip[b]==null){flip[b]=wanted;q.add(b);}else if(flip[b]!=wanted)return null;
            }
        }
        if(Arrays.stream(flip).anyMatch(Objects::isNull))return null;
        List<List<Integer>> result=new ArrayList<>();
        for(int i=0;i<n;i++){List<Integer> f=new ArrayList<>(faces.get(i));if(flip[i])Collections.reverse(f);result.add(List.copyOf(f));}
        return result;
    }
    private static int relation(List<Integer>a,List<Integer>b){for(int i=0;i<a.size();i++){int x=a.get(i),y=a.get((i+1)%a.size());for(int j=0;j<b.size();j++){int u=b.get(j),v=b.get((j+1)%b.size());if(x==u&&y==v)return 1;if(x==v&&y==u)return -1;}}return 0;}
    private static Plane plane(List<Integer> f,List<Vector3d> p){Vector3d c=new Vector3d();f.forEach(id->c.add(p.get(id)));c.div(f.size());Vector3d n=new Vector3d();for(int i=0;i<f.size();i++){Vector3d a=p.get(f.get(i)),b=p.get(f.get((i+1)%f.size()));n.x+=(a.y-b.y)*(a.z+b.z);n.y+=(a.z-b.z)*(a.x+b.x);n.z+=(a.x-b.x)*(a.y+b.y);}return n.lengthSquared()<=EPS?null:new Plane(c,n.normalize(),Set.copyOf(f));}
    private record Plane(Vector3d center,Vector3d normal,Set<Integer> vertices){}
}
