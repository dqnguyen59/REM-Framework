package remframework.geometry.discovery.viewport;

import org.joml.Vector3d;
import remframework.geometry.discovery.DerivedFace;
import remframework.geometry.discovery.DiscoveryConnection;

import java.util.ArrayList;
import java.util.List;

/** Computes local inspection data once from one immutable realization snapshot. */
public final class ElementMeasurementCalculator {
    private ElementMeasurementCalculator() { }

    public static SelectedElementMeasurements measure(RenderModel model, SelectedElement selection,
            double targetLength, double tolerance) {
        List<Vector3d> p=model.scientificCoordinates();
        Vector3d center=mean(p);
        return switch(selection.type()) {
            case CENTER -> basic(selection,model.revision(),center,List.of(),List.of(),List.of(),0,"Geometry Center");
            case VERTEX -> vertex(model,selection,Integer.parseInt(selection.id()),center);
            case EDGE -> edge(model,selection,targetLength,tolerance,center);
            case FACE -> face(model,selection,center);
        };
    }

    private static SelectedElementMeasurements vertex(RenderModel m,SelectedElement s,int id,Vector3d center){
        List<Integer> neighbors=new ArrayList<>();List<String> edges=new ArrayList<>(),faces=new ArrayList<>();
        for(DiscoveryConnection e:m.model().connections())if(e.firstNodeId()==id||e.secondNodeId()==id){int other=e.firstNodeId()==id?e.secondNodeId():e.firstNodeId();neighbors.add(other);edges.add(SelectedElement.edge(id,other).id());}
        for(DerivedFace f:m.derivedFaces())if(f.nodeIds().contains(id))faces.add(f.id());
        return basic(s,m.revision(),m.scientificCoordinates().get(id),neighbors,edges,faces,m.scientificCoordinates().get(id).distance(center),"Degree "+neighbors.size());
    }
    private static SelectedElementMeasurements edge(RenderModel m,SelectedElement s,double target,double tolerance,Vector3d center){
        String[] q=s.id().split("-");int a=Integer.parseInt(q[1]),b=Integer.parseInt(q[2]);Vector3d pa=m.scientificCoordinates().get(a),pb=m.scientificCoordinates().get(b);double length=pa.distance(pb),error=Math.abs(length-target);Vector3d mid=new Vector3d(pa).add(pb).mul(.5);List<String> faces=m.derivedFaces().stream().filter(f->containsEdge(f.nodeIds(),a,b)).map(DerivedFace::id).toList();
        return new SelectedElementMeasurements(s,m.revision(),null,List.of(a,b),List.of(s.id()),faces,length,target,error,tolerance,tolerance>0?error/tolerance:Double.NaN,mid,mid.distance(center),Double.NaN,Double.NaN,Double.NaN,List.of(),null,null,Double.NaN,Double.NaN,Double.NaN,"Measured edge");
    }
    private static SelectedElementMeasurements face(RenderModel m,SelectedElement s,Vector3d center){
        DerivedFace f=m.derivedFaces().stream().filter(x->x.id().equals(s.id())).findFirst().orElseThrow();List<Vector3d> v=f.nodeIds().stream().map(m.scientificCoordinates()::get).toList();Vector3d centroid=mean(v),normal=newell(v);double perimeter=0,area=0;List<Double> angles=new ArrayList<>(),edges=new ArrayList<>();List<String> edgeIds=new ArrayList<>();
        for(int i=0;i<v.size();i++){Vector3d previous=v.get((i+v.size()-1)%v.size()),current=v.get(i),next=v.get((i+1)%v.size());perimeter+=current.distance(next);area+=new Vector3d(current).sub(centroid).cross(new Vector3d(next).sub(centroid)).length()*.5;angles.add(angle(previous,current,next));edgeIds.add(SelectedElement.edge(f.nodeIds().get(i),f.nodeIds().get((i+1)%v.size())).id());}
        double maxPlane=v.stream().mapToDouble(x->Math.abs(new Vector3d(x).sub(centroid).dot(normal))).max().orElse(Double.NaN);double signed=new Vector3d(centroid).sub(center).dot(normal);double distance=Math.abs(new Vector3d(center).sub(centroid).dot(normal));String state=normal.lengthSquared()==0?"DEGENERATE":signed>0?"OUTWARD":"INWARD";
        return new SelectedElementMeasurements(s,m.revision(),null,f.nodeIds(),edgeIds,List.of(f.id()),Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,null,Double.NaN,area,perimeter,maxPlane,angles,centroid,normal,signed,distance,centroid.distance(center),state);
    }
    private static SelectedElementMeasurements basic(SelectedElement s,long revision,Vector3d p,List<Integer> vertices,List<String> edges,List<String> faces,double distance,String state){return new SelectedElementMeasurements(s,revision,p,vertices,edges,faces,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,null,distance,Double.NaN,Double.NaN,Double.NaN,List.of(),null,null,Double.NaN,Double.NaN,Double.NaN,state);}
    private static Vector3d mean(List<Vector3d> p){Vector3d c=new Vector3d();p.forEach(c::add);return p.isEmpty()?c:c.div(p.size());}
    private static Vector3d newell(List<Vector3d> v){Vector3d n=new Vector3d();for(int i=0;i<v.size();i++){Vector3d a=v.get(i),b=v.get((i+1)%v.size());n.x+=(a.y-b.y)*(a.z+b.z);n.y+=(a.z-b.z)*(a.x+b.x);n.z+=(a.x-b.x)*(a.y+b.y);}return n.lengthSquared()<1e-24?n.zero():n.normalize();}
    private static double angle(Vector3d a,Vector3d b,Vector3d c){Vector3d x=new Vector3d(a).sub(b).normalize(),y=new Vector3d(c).sub(b).normalize();return Math.acos(Math.max(-1,Math.min(1,x.dot(y))));}
    private static boolean containsEdge(List<Integer> cycle,int a,int b){for(int i=0;i<cycle.size();i++){int x=cycle.get(i),y=cycle.get((i+1)%cycle.size());if((x==a&&y==b)||(x==b&&y==a))return true;}return false;}
}
