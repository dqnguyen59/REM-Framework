package remframework.geometry.discovery.measurement.regularity;

import org.joml.Vector3d;
import remframework.geometry.discovery.ClosedBoundaryResult;
import remframework.geometry.discovery.DerivedFace;
import remframework.geometry.discovery.DiscoveryConnection;
import remframework.geometry.discovery.configuration.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ThreeDRegularityCalculator {
    public ThreeDRegularityMetrics calculate(Configuration graph, List<Vector3d> points,
            ClosedBoundaryResult boundary, double targetEdgeLength) {
        if (!boundary.closed()) throw new IllegalArgumentException("A qualified closed boundary is required.");
        Vector3d centroid = centroid(points);
        List<Double> edges = graph.edges().stream().map(e -> points.get(e.firstNodeId()).distance(points.get(e.secondNodeId()))).toList();
        double maxError = edges.stream().mapToDouble(v -> Math.abs(v-targetEdgeLength)).max().orElse(0);
        List<Vector3d> normals = boundary.orientedFaces().stream().map(f -> normal(f,points,centroid)).toList();
        List<Double> dihedrals = new ArrayList<>();
        for (DiscoveryConnection edge : graph.edges()) { List<Integer> incident = new ArrayList<>();
            for(int i=0;i<boundary.orientedFaces().size();i++) if(containsEdge(boundary.orientedFaces().get(i),edge)) incident.add(i);
            if(incident.size()==2) dihedrals.add(Math.PI-Math.acos(clamp(normals.get(incident.get(0)).dot(normals.get(incident.get(1)))))); }
        List<Double> vertexRadii = points.stream().map(p -> p.distance(centroid)).toList();
        List<Double> faceRadii = new ArrayList<>(), midpointRadii = new ArrayList<>();
        List<FaceMetrics> faceMetrics = new ArrayList<>();
        Map<Integer,List<Double>> areas = new LinkedHashMap<>(), angleVariances = new LinkedHashMap<>();
        for(DerivedFace face:boundary.orientedFaces()) { List<Vector3d> fp=face.nodeIds().stream().map(points::get).toList();
            Vector3d fc=centroid(fp); faceRadii.add(fc.distance(centroid)); List<Double> lengths=new ArrayList<>(), angles=new ArrayList<>();
            for(int i=0;i<fp.size();i++){Vector3d previous=fp.get((i-1+fp.size())%fp.size()),current=fp.get(i),next=fp.get((i+1)%fp.size());
                lengths.add(current.distance(next)); Vector3d a=new Vector3d(previous).sub(current),b=new Vector3d(next).sub(current);
                angles.add(Math.acos(clamp(a.dot(b)/(a.length()*b.length()))));}
            double perimeter=lengths.stream().mapToDouble(Double::doubleValue).sum(), area=area(fp);
            Statistics angleStats=Statistics.of(angles); faceMetrics.add(new FaceMetrics(face.id(),fp.size(),area,perimeter,angleStats,Statistics.of(lengths),List.of(fc.x,fc.y,fc.z)));
            areas.computeIfAbsent(fp.size(), ignored->new ArrayList<>()).add(area);
            angleVariances.computeIfAbsent(fp.size(), ignored->new ArrayList<>()).add(angleStats.variance()); }
        for(DiscoveryConnection edge:graph.edges()) midpointRadii.add(new Vector3d(points.get(edge.firstNodeId())).add(points.get(edge.secondNodeId())).mul(.5).distance(centroid));
        Map<Integer,Statistics> areaGroups=new LinkedHashMap<>(),angleGroups=new LinkedHashMap<>();
        areas.forEach((k,v)->areaGroups.put(k,Statistics.of(v))); angleVariances.forEach((k,v)->angleGroups.put(k,Statistics.of(v)));
        return new ThreeDRegularityMetrics(Statistics.of(edges),maxError,Statistics.of(dihedrals),
                Statistics.of(vertexRadii),Statistics.of(faceRadii),Statistics.of(midpointRadii),faceMetrics,areaGroups,angleGroups);
    }
    private static Vector3d centroid(List<Vector3d> p){Vector3d c=new Vector3d();p.forEach(c::add);return c.div(p.size());}
    private static Vector3d normal(DerivedFace f,List<Vector3d> p,Vector3d center){Vector3d n=new Vector3d();List<Integer> ids=f.nodeIds();
        for(int i=0;i<ids.size();i++){Vector3d a=p.get(ids.get(i)),b=p.get(ids.get((i+1)%ids.size()));n.x+=(a.y-b.y)*(a.z+b.z);n.y+=(a.z-b.z)*(a.x+b.x);n.z+=(a.x-b.x)*(a.y+b.y);}n.normalize();
        Vector3d fc=centroid(ids.stream().map(p::get).toList());if(n.dot(new Vector3d(fc).sub(center))<0)n.negate();return n;}
    private static boolean containsEdge(DerivedFace f,DiscoveryConnection e){for(int i=0;i<f.nodeIds().size();i++){int a=f.nodeIds().get(i),b=f.nodeIds().get((i+1)%f.nodeIds().size());if(Math.min(a,b)==e.lowerId()&&Math.max(a,b)==e.upperId())return true;}return false;}
    private static double area(List<Vector3d> p){Vector3d sum=new Vector3d();for(int i=0;i<p.size();i++)sum.add(new Vector3d(p.get(i)).cross(p.get((i+1)%p.size())));return sum.length()/2;}
    private static double clamp(double v){return Math.max(-1,Math.min(1,v));}
}
