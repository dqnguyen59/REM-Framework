package remframework.geometry.discovery.measurement.regularity;

import org.joml.Vector3d;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.Configuration;
import remframework.geometry.discovery.experiment.GeometryCenterDefinition;

import java.util.*;

/** Measures a previously accepted 3D face complex using interior dihedral angles. */
public final class ThreeDRegularityCalculator {
    private static final double NORMAL_EPSILON=1e-12;

    public ThreeDRegularityMetrics calculate(Configuration graph,List<Vector3d> points,
            ClosedBoundaryResult boundary,double targetEdgeLength){
        return calculate(graph,points,boundary,targetEdgeLength,GeometryCenterDefinition.VERTEX_CENTROID,1e-7);
    }

    public ThreeDRegularityMetrics calculate(Configuration graph,List<Vector3d> points,
            ClosedBoundaryResult boundary,double targetEdgeLength,GeometryCenterDefinition centerDefinition,
            double facePlanarityTolerance){
        Vector3d center=vertexCentroid(points);
        if(boundary==null||!boundary.closed()||boundary.orientedFaces().isEmpty())
            return unavailable(center,centerDefinition,"Accepted closed manifold face set is unavailable.");
        List<Double> edgeLengths=graph.edges().stream().map(edge->points.get(edge.firstNodeId()).distance(points.get(edge.secondNodeId()))).toList();
        double maxTargetError=edgeLengths.stream().mapToDouble(value->Math.abs(value-targetEdgeLength)).max().orElse(0);
        List<FacePlaneDiagnostic> planes=new ArrayList<>();List<FaceMetrics> faces=new ArrayList<>();
        List<Double> faceCenterRadii=new ArrayList<>(),planeRadii=new ArrayList<>(),alignments=new ArrayList<>();
        Map<Integer,List<Double>> areas=new LinkedHashMap<>(),angleVariances=new LinkedHashMap<>();
        for(DerivedFace face:boundary.orientedFaces()){
            List<Vector3d> vertices=face.nodeIds().stream().map(points::get).toList();Vector3d faceCenter=vertexCentroid(vertices);
            Vector3d normal=newell(vertices);boolean degenerate=normal.lengthSquared()<=NORMAL_EPSILON*NORMAL_EPSILON;
            FaceOrientationDisposition disposition=FaceOrientationDisposition.INDETERMINATE;
            if(!degenerate){normal.normalize();double outward=normal.dot(new Vector3d(faceCenter).sub(center));
                if(outward> NORMAL_EPSILON)disposition=FaceOrientationDisposition.ALREADY_OUTWARD;
                else if(outward< -NORMAL_EPSILON){normal.negate();disposition=FaceOrientationDisposition.REVERSED_TO_OUTWARD;}}
            List<Double> deviations=degenerate?vertices.stream().map(ignored->Double.POSITIVE_INFINITY).toList()
                    :vertices.stream().map(vertex->Math.abs(new Vector3d(vertex).sub(faceCenter).dot(normal))).toList();
            double maximum=deviations.stream().mapToDouble(Double::doubleValue).max().orElse(Double.POSITIVE_INFINITY);
            double mean=deviations.stream().mapToDouble(Double::doubleValue).average().orElse(Double.POSITIVE_INFINITY);
            double rms=Math.sqrt(deviations.stream().mapToDouble(value->value*value).average().orElse(Double.POSITIVE_INFINITY));
            double planeRadius=degenerate?Double.NaN:Math.abs(new Vector3d(center).sub(faceCenter).dot(normal));
            Vector3d radial=new Vector3d(faceCenter).sub(center);double alignment=degenerate||radial.lengthSquared()<=NORMAL_EPSILON?Double.NaN:normal.dot(radial.normalize());
            planes.add(new FacePlaneDiagnostic(face.id(),faceCenter,normal,deviations,maximum,mean,rms,
                    facePlanarityTolerance,!degenerate&&maximum<=facePlanarityTolerance,degenerate,disposition,planeRadius,alignment));
            if(Double.isFinite(planeRadius))planeRadii.add(planeRadius);if(Double.isFinite(alignment))alignments.add(alignment);
            faceCenterRadii.add(faceCenter.distance(center));
            List<Double> lengths=new ArrayList<>(),interior=new ArrayList<>(),turning=new ArrayList<>();
            for(int i=0;i<vertices.size();i++){Vector3d previous=vertices.get((i-1+vertices.size())%vertices.size()),current=vertices.get(i),next=vertices.get((i+1)%vertices.size());
                lengths.add(current.distance(next));Vector3d a=new Vector3d(previous).sub(current),b=new Vector3d(next).sub(current);
                double angle=Math.acos(clamp(a.dot(b)/Math.max(NORMAL_EPSILON,a.length()*b.length())));interior.add(angle);turning.add(Math.PI-angle);}
            double perimeter=lengths.stream().mapToDouble(Double::doubleValue).sum(),area=area(vertices);
            Statistics angleStats=Statistics.of(interior);double target=(vertices.size()-2.0)*Math.PI/vertices.size();
            double maxAngleDeviation=interior.stream().mapToDouble(value->Math.abs(value-target)).max().orElse(Double.NaN);
            faces.add(new FaceMetrics(face.id(),vertices.size(),area,perimeter,angleStats,Statistics.of(turning),
                    Statistics.of(lengths),target,maxAngleDeviation,List.of(faceCenter.x,faceCenter.y,faceCenter.z)));
            areas.computeIfAbsent(vertices.size(),ignored->new ArrayList<>()).add(area);
            angleVariances.computeIfAbsent(vertices.size(),ignored->new ArrayList<>()).add(angleStats.variance());
        }
        List<DihedralEdgeAngle> pairs=new ArrayList<>();
        for(DiscoveryConnection edge:graph.edges()){List<Integer> incident=new ArrayList<>();for(int i=0;i<boundary.orientedFaces().size();i++)if(containsEdge(boundary.orientedFaces().get(i),edge))incident.add(i);
            if(incident.size()==2&&!planes.get(incident.get(0)).degenerate()&&!planes.get(incident.get(1)).degenerate()){
                double interior=Math.PI-Math.acos(clamp(planes.get(incident.get(0)).outwardNormal().dot(planes.get(incident.get(1)).outwardNormal())));
                pairs.add(new DihedralEdgeAngle(edge.lowerId(),edge.upperId(),interior));}}
        List<Double> vertexRadii=points.stream().map(point->point.distance(center)).toList();
        List<Double> midpointRadii=graph.edges().stream().map(edge->new Vector3d(points.get(edge.firstNodeId())).add(points.get(edge.secondNodeId())).mul(.5).distance(center)).toList();
        Map<Integer,Statistics> areaGroups=new LinkedHashMap<>(),angleGroups=new LinkedHashMap<>();areas.forEach((key,value)->areaGroups.put(key,Statistics.of(value)));angleVariances.forEach((key,value)->angleGroups.put(key,Statistics.of(value)));
        Statistics planeStats=Statistics.of(planeRadii),alignmentStats=Statistics.of(alignments);double maxPlaneMeanDeviation=planeRadii.stream().mapToDouble(value->Math.abs(value-planeStats.mean())).max().orElse(Double.NaN);
        Double relative=Double.isFinite(planeStats.mean())&&Math.abs(planeStats.mean())>NORMAL_EPSILON?maxPlaneMeanDeviation/Math.abs(planeStats.mean()):null;
        double maxAlignmentDeviation=alignments.stream().mapToDouble(value->Math.abs(1-value)).max().orElse(Double.NaN);
        return new ThreeDRegularityMetrics(Statistics.of(edgeLengths),maxTargetError,
                Statistics.of(pairs.stream().map(DihedralEdgeAngle::interiorAngleRadians).toList()),Statistics.of(vertexRadii),
                Statistics.of(faceCenterRadii),Statistics.of(midpointRadii),faces,areaGroups,angleGroups,true,"AVAILABLE",
                centerDefinition,center,planes,planeStats,maxPlaneMeanDeviation,relative,alignmentStats,maxAlignmentDeviation,pairs);
    }

    private static ThreeDRegularityMetrics unavailable(Vector3d center,GeometryCenterDefinition definition,String reason){Statistics empty=Statistics.of(List.of());return new ThreeDRegularityMetrics(empty,Double.NaN,empty,empty,empty,empty,List.of(),Map.of(),Map.of(),false,reason,definition,center,List.of(),empty,Double.NaN,null,empty,Double.NaN,List.of());}
    private static Vector3d vertexCentroid(List<Vector3d> points){Vector3d center=new Vector3d();points.forEach(center::add);return points.isEmpty()?center:center.div(points.size());}
    private static Vector3d newell(List<Vector3d> vertices){Vector3d normal=new Vector3d();for(int i=0;i<vertices.size();i++){Vector3d a=vertices.get(i),b=vertices.get((i+1)%vertices.size());normal.x+=(a.y-b.y)*(a.z+b.z);normal.y+=(a.z-b.z)*(a.x+b.x);normal.z+=(a.x-b.x)*(a.y+b.y);}return normal;}
    private static boolean containsEdge(DerivedFace face,DiscoveryConnection edge){for(int i=0;i<face.nodeIds().size();i++){int a=face.nodeIds().get(i),b=face.nodeIds().get((i+1)%face.nodeIds().size());if(Math.min(a,b)==edge.lowerId()&&Math.max(a,b)==edge.upperId())return true;}return false;}
    private static double area(List<Vector3d> vertices){Vector3d sum=new Vector3d();for(int i=0;i<vertices.size();i++)sum.add(new Vector3d(vertices.get(i)).cross(vertices.get((i+1)%vertices.size())));return sum.length()/2;}
    private static double clamp(double value){return Math.max(-1,Math.min(1,value));}
}
