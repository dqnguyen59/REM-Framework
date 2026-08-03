package remframework.geometry.discovery.realization.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3d;
import remframework.geometry.discovery.DiscoveryConnection;
import remframework.geometry.discovery.FixedDegreeGraph;
import remframework.geometry.discovery.experiment.RegularityMode;
import remframework.geometry.discovery.experiment.ThreeDExperimentConditions;

/** Pure Stage 2 measurement pass. It never changes topology or solver forces. */
public final class Stage2MeasurementCalculator {
    private static final double EPSILON = 1e-12;

    public Map<String, ConstraintMeasurement> calculate(FixedDegreeGraph graph,
            List<List<Integer>> faces, List<Vector3d> points,
            ThreeDExperimentConditions conditions, long revision) {
        return calculate(graph,faces,points,conditions,revision,1d);
    }

    public Map<String, ConstraintMeasurement> calculate(FixedDegreeGraph graph,
            List<List<Integer>> faces, List<Vector3d> points,
            ThreeDExperimentConditions conditions, long revision, double targetEdgeLength) {
        Map<String, ConstraintMeasurement> result = new LinkedHashMap<>();
        result.put("edge-equality", edgeMeasurement(graph, points, conditions, revision,targetEdgeLength));
        if (faces == null || faces.isEmpty()) {
            for (String id : List.of("outward-face-orientation", "face-planarity", "face-angle-equality",
                    "dihedral-equality", "face-plane-radius-equality", "normal-center-alignment",
                    "equal-face-area", "convexity", "self-intersection"))
                result.put(id, unavailable(id, MeasurementState.UNAVAILABLE_MISSING_FACE_COMPLEX,
                        "MISSING_FACE_COMPLEX", "The topology-derived face complex is unavailable.", "", revision));
        } else {
            FacePass pass = facePass(faces, points);
            result.put("outward-face-orientation", orientation(faces, points, revision));
            result.put("face-planarity", facePlanarity(pass, conditions, revision));
            result.put("face-angle-equality", faceAngles(pass, conditions, revision));
            result.put("dihedral-equality", dihedral(pass, graph, conditions, revision));
            result.put("face-plane-radius-equality", facePlaneDistance(pass, conditions, revision));
            result.put("normal-center-alignment", normalAlignment(faces, points, pass, conditions, revision));
            result.put("equal-face-area", faceArea(pass, revision));
            result.put("convexity", convexity(faces, points, revision));
            result.put("self-intersection", unavailable("self-intersection", MeasurementState.NOT_IMPLEMENTED,
                    "NOT_IMPLEMENTED", "Self-intersection measurement is supplied by the boundary diagnostic.", "", revision));
        }
        result.put("vertex-radius-equality", radialMeasurement("vertex-radius-equality", vertexRadii(points),
                conditions.vertexRadii(), "length", "maximum absolute deviation from the mean vertex distance", revision));
        result.put("face-centroid-radius-equality", radialMeasurement("face-centroid-radius-equality", faceCentroidRadii(faces, points),
                conditions.faceCentroidRadii(), "length", "maximum absolute deviation from the mean face-centroid distance", revision));
        result.put("edge-midpoint-radius-equality", radialMeasurement("edge-midpoint-radius-equality", edgeMidpointRadii(graph, points),
                conditions.edgeMidpointRadii(), "length", "maximum absolute deviation from the mean edge-center distance", revision));
        return Map.copyOf(result);
    }

    private ConstraintMeasurement edgeMeasurement(FixedDegreeGraph graph, List<Vector3d> points,
            ThreeDExperimentConditions conditions, long revision,double targetEdgeLength) {
        if (graph.connections().isEmpty()) return unavailable("edge-equality", MeasurementState.UNAVAILABLE_EMPTY_SAMPLE_SET,
                "EMPTY_SAMPLE_SET", "No topology edges are available.", "length", revision);
        List<Double> errors = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        for (DiscoveryConnection edge : graph.connections()) {
            Vector3d a = point(points, edge.firstNodeId()), b = point(points, edge.secondNodeId());
            if (a == null || b == null || !a.isFinite() || !b.isFinite()) invalid.add(edgeLabel(edge));
            else errors.add(Math.abs(a.distance(b) - targetEdgeLength));
        }
        if (!invalid.isEmpty()) return unavailable("edge-equality", MeasurementState.NUMERICALLY_INVALID,
                "NON_FINITE_COORDINATE", "Endpoint coordinates are non-finite.", "length", errors.size(), invalid.size(), invalid, revision);
        return measured("edge-equality", max(errors), 1e-3, "length",
                "maximum absolute deviation from target connected-edge length", errors.size(), revision);
    }

    private ConstraintMeasurement facePlanarity(FacePass pass, ThreeDExperimentConditions c, long revision) {
        if (!pass.invalidFaces.isEmpty()) return unavailable("face-planarity", pass.planarity.isEmpty()
                ? MeasurementState.UNAVAILABLE_DEGENERATE_FACE : MeasurementState.PARTIAL_MEASUREMENT,
                "DEGENERATE_FACE", "One or more faces has no valid plane.", "length", pass.planarity.size(),
                pass.invalidFaces.size(), pass.invalidFaces, revision);
        return measured("face-planarity", max(pass.planarity), c.facePlanarityTolerance(), "length",
                "maximum vertex-to-face-plane deviation", pass.planarity.size(), revision);
    }

    private ConstraintMeasurement faceAngles(FacePass pass, ThreeDExperimentConditions c, long revision) {
        if (!pass.invalidFaces.isEmpty() || pass.angles.isEmpty()) return unavailable("face-angle-equality",
                pass.invalidFaces.isEmpty()?MeasurementState.UNAVAILABLE_EMPTY_SAMPLE_SET:MeasurementState.UNAVAILABLE_DEGENERATE_FACE,
                pass.invalidFaces.isEmpty()?"EMPTY_SAMPLE_SET":"DEGENERATE_FACE",
                "Each ordered face must have nonzero consecutive edge lengths.", "radians", pass.angles.size(),
                pass.invalidFaces.size(), pass.invalidFaces, revision);
        return measured("face-angle-equality", max(pass.angles), c.absoluteRegularityTolerance(), "radians",
                "maximum absolute deviation from each face's regular-polygon target angle", pass.angles.size(), revision);
    }

    private ConstraintMeasurement dihedral(FacePass pass, FixedDegreeGraph graph, ThreeDExperimentConditions c, long revision) {
        if (!pass.invalidFaces.isEmpty()) return unavailable("dihedral-equality", MeasurementState.UNAVAILABLE_DEGENERATE_FACE,
                "DEGENERATE_FACE", "Two valid adjacent face normals are required.", "radians", 0,
                pass.invalidFaces.size(), pass.invalidFaces, revision);
        List<Double> values = new ArrayList<>();
        for (DiscoveryConnection edge : graph.connections()) {
            List<Integer> incident = new ArrayList<>();
            for (int i=0;i<pass.faces.size();i++) if (containsEdge(pass.faces.get(i), edge)) incident.add(i);
            if (incident.size()==2 && pass.normals.get(incident.get(0))!=null && pass.normals.get(incident.get(1))!=null)
                values.add(Math.PI-Math.acos(clamp(pass.normals.get(incident.get(0)).dot(pass.normals.get(incident.get(1))))));
        }
        if (values.isEmpty()) return unavailable("dihedral-equality", MeasurementState.UNAVAILABLE_EMPTY_SAMPLE_SET,
                "EMPTY_SAMPLE_SET", "No valid adjacent face pair is available.", "radians", 0, 0, List.of(), revision);
        return measured("dihedral-equality", deviation(values), c.absoluteRegularityTolerance(), "radians",
                "maximum absolute deviation from the mean interior dihedral angle", values.size(), revision);
    }

    private ConstraintMeasurement facePlaneDistance(FacePass pass, ThreeDExperimentConditions c, long revision) {
        if (!pass.invalidFaces.isEmpty() || pass.planeDistances.isEmpty()) return unavailable("face-plane-radius-equality",
                pass.invalidFaces.isEmpty()?MeasurementState.UNAVAILABLE_EMPTY_SAMPLE_SET:MeasurementState.UNAVAILABLE_DEGENERATE_FACE,
                pass.invalidFaces.isEmpty()?"EMPTY_SAMPLE_SET":"DEGENERATE_FACE",
                "A valid face plane is required for center-to-face-plane distance.", "length", pass.planeDistances.size(),
                pass.invalidFaces.size(), pass.invalidFaces, revision);
        return measured("face-plane-radius-equality", deviation(pass.planeDistances), c.absoluteRegularityTolerance(), "length",
                "maximum deviation from the mean center-to-face-plane distance", pass.planeDistances.size(), revision);
    }

    private ConstraintMeasurement normalAlignment(List<List<Integer>> faces, List<Vector3d> points, FacePass pass,
            ThreeDExperimentConditions c, long revision) {
        if (!pass.invalidFaces.isEmpty()) return unavailable("normal-center-alignment", MeasurementState.UNAVAILABLE_INVALID_NORMAL,
                "INVALID_NORMAL", "At least one face normal is invalid.", "dimensionless", pass.alignments.size(),
                pass.invalidFaces.size(), pass.invalidFaces, revision);
        if (pass.alignments.isEmpty()) return unavailable("normal-center-alignment", MeasurementState.UNAVAILABLE_EMPTY_SAMPLE_SET,
                "EMPTY_SAMPLE_SET", "No nonzero face-center radial vector is available.", "dimensionless", 0, 0, List.of(), revision);
        return measured("normal-center-alignment", max(pass.alignments), c.absoluteRegularityTolerance(), "dimensionless",
                "maximum absolute value of 1 minus outward-normal/radial alignment", pass.alignments.size(), revision);
    }

    private ConstraintMeasurement faceArea(FacePass pass, long revision) {
        if (!pass.invalidFaces.isEmpty() || pass.areas.isEmpty()) return unavailable("equal-face-area",
                pass.invalidFaces.isEmpty()?MeasurementState.UNAVAILABLE_EMPTY_SAMPLE_SET:MeasurementState.UNAVAILABLE_DEGENERATE_FACE,
                pass.invalidFaces.isEmpty()?"EMPTY_SAMPLE_SET":"DEGENERATE_FACE",
                "Each ordered face must have a nondegenerate triangulation.", "area", pass.areas.size(),
                pass.invalidFaces.size(), pass.invalidFaces, revision);
        return measured("equal-face-area", deviation(pass.areas), null, "area",
                "maximum absolute deviation from the mean face area", pass.areas.size(), revision);
    }

    private ConstraintMeasurement orientation(List<List<Integer>> faces, List<Vector3d> points, long revision) {
        OrientationConvexityDiagnostics d = new OrientationConvexityCalculator().calculate(faces, points, 1e-7);
        return switch (d.orientation()) {
            case COHERENT_AND_OUTWARD -> qualitative("outward-face-orientation", "COHERENT_OUTWARD", "Face winding is coherent and outward.", revision);
            case COHERENT_BUT_INWARD -> qualitative("outward-face-orientation", "COHERENT_INWARD", "Face winding is coherent but inward.", revision);
            case INCOHERENT_ORIENTATION -> unavailable("outward-face-orientation", MeasurementState.UNAVAILABLE_INCOHERENT_ORIENTATION,
                    "INCOHERENT_ORIENTATION", d.explanation(), "", 0, 0, List.of(), revision).withQualitativeState("INCOHERENT");
            case DEGENERATE_FACE_NORMAL -> unavailable("outward-face-orientation", MeasurementState.UNAVAILABLE_INVALID_NORMAL,
                    "INVALID_NORMAL", d.explanation(), "", 0, 1, List.of(), revision).withQualitativeState("UNDETERMINED");
            default -> unavailable("outward-face-orientation", MeasurementState.UNAVAILABLE_INVALID_CENTER,
                    "INVALID_CENTER", d.explanation(), "", 0, 0, List.of(), revision).withQualitativeState("UNDETERMINED");
        };
    }

    private ConstraintMeasurement convexity(List<List<Integer>> faces, List<Vector3d> points, long revision) {
        OrientationConvexityDiagnostics d = new OrientationConvexityCalculator().calculate(faces, points, 1e-7);
        if (d.orientation()==OrientationState.INCOHERENT_ORIENTATION) return unavailable("convexity",
                MeasurementState.UNAVAILABLE_INCOHERENT_ORIENTATION,"INCOHERENT_ORIENTATION",d.explanation(),"length",0,0,List.of(),revision);
        if (d.orientation()==OrientationState.DEGENERATE_FACE_NORMAL) return unavailable("convexity",
                MeasurementState.UNAVAILABLE_INVALID_NORMAL,"INVALID_NORMAL",d.explanation(),"length",0,1,List.of(),revision);
        if (!Double.isFinite(d.maximumConvexityViolation())) return unavailable("convexity",
                MeasurementState.UNAVAILABLE_INVALID_CENTER,"INVALID_CENTER",d.explanation(),"length",d.violatingPairs(),0,List.of(),revision);
        return measured("convexity", d.maximumConvexityViolation(), 1e-7, "length",
                "maximum positive nonincident vertex-to-outward-face-plane violation", d.violatingPairs(), revision);
    }

    private ConstraintMeasurement radialMeasurement(String id, List<Double> values, RegularityMode mode,
            String unit, String formula, long revision) {
        if (mode==RegularityMode.NONE) return unavailable(id, MeasurementState.NOT_REQUESTED,"NOT_REQUESTED",
                "This measurement is not requested by the active profile.",unit,0,0,List.of(),revision);
        if (values.isEmpty()) return unavailable(id, MeasurementState.UNAVAILABLE_EMPTY_SAMPLE_SET,"EMPTY_SAMPLE_SET",
                "No finite samples are available.",unit,0,0,List.of(),revision);
        return measured(id, deviation(values), 1e-6, unit, formula, values.size(), revision);
    }

    private FacePass facePass(List<List<Integer>> faces, List<Vector3d> points) {
        Vector3d center=centroid(points);List<String> invalid=new ArrayList<>();List<Double> planarity=new ArrayList<>(),angles=new ArrayList<>(),planeDistances=new ArrayList<>(),alignments=new ArrayList<>(),areas=new ArrayList<>();List<Vector3d> normals=new ArrayList<>();
        for (int fi=0;fi<faces.size();fi++) { List<Integer> face=faces.get(fi);String id="face-"+fi;List<Vector3d> v=new ArrayList<>();boolean finite=face.size()>=3;for(int node:face){Vector3d p=point(points,node);if(p==null||!p.isFinite())finite=false;else v.add(p);}
            Vector3d c=finite?centroid(v):new Vector3d();Vector3d n=finite?newell(v):new Vector3d();if(!finite||n.lengthSquared()<=EPSILON*EPSILON){invalid.add(id);normals.add(null);continue;}n.normalize();normals.add(n);double max=0;for(Vector3d p:v)max=Math.max(max,Math.abs(new Vector3d(p).sub(c).dot(n)));planarity.add(max);Vector3d radial=new Vector3d(c).sub(center);if(radial.lengthSquared()>EPSILON*EPSILON)alignments.add(Math.abs(1-n.dot(radial.normalize())));planeDistances.add(Math.abs(new Vector3d(center).sub(c).dot(n)));double area=0;for(int i=1;i<v.size()-1;i++){Vector3d a=new Vector3d(v.get(i)).sub(v.get(0)),b=new Vector3d(v.get(i+1)).sub(v.get(0));double triangle=a.cross(b,new Vector3d()).length()/2;if(!Double.isFinite(triangle)||triangle<=EPSILON){invalid.add(id);break;}area+=triangle;}if(!invalid.contains(id))areas.add(area);for(int i=0;i<v.size();i++){Vector3d a=new Vector3d(v.get((i-1+v.size())%v.size())).sub(v.get(i)),b=new Vector3d(v.get((i+1)%v.size())).sub(v.get(i));double denominator=a.length()*b.length();if(denominator<=EPSILON){invalid.add(id);break;}double angle=Math.acos(clamp(a.dot(b)/denominator));angles.add(Math.abs(angle-(v.size()-2d)*Math.PI/v.size()));}}
        return new FacePass(faces,invalid,planarity,angles,planeDistances,alignments,areas,normals);
    }
    private static List<Double> vertexRadii(List<Vector3d> p){Vector3d c=centroid(p);List<Double> r=new ArrayList<>();for(Vector3d v:p)if(v!=null&&v.isFinite()&&c.isFinite())r.add(v.distance(c));return r;}
    private static List<Double> faceCentroidRadii(List<List<Integer>> fs,List<Vector3d> p){List<Double> r=new ArrayList<>();Vector3d c=centroid(p);if(fs==null)return r;for(List<Integer> f:fs){List<Vector3d> v=f.stream().map(i->point(p,i)).filter(x->x!=null&&x.isFinite()).toList();if(v.size()==f.size()&&v.size()>=3)r.add(centroid(v).distance(c));}return r;}
    private static List<Double> edgeMidpointRadii(FixedDegreeGraph g,List<Vector3d> p){Vector3d c=centroid(p);List<Double> r=new ArrayList<>();for(DiscoveryConnection e:g.connections()){Vector3d a=point(p,e.firstNodeId()),b=point(p,e.secondNodeId());if(a!=null&&b!=null&&a.isFinite()&&b.isFinite())r.add(new Vector3d(a).add(b).mul(.5).distance(c));}return r;}
    private static Vector3d point(List<Vector3d> p,int i){return p!=null&&i>=0&&i<p.size()?p.get(i):null;}
    private static Vector3d centroid(List<Vector3d> p){Vector3d c=new Vector3d();if(p==null||p.isEmpty())return c;for(Vector3d v:p)if(v!=null&&v.isFinite())c.add(v);return c.div(p.size());}
    private static Vector3d newell(List<Vector3d> v){Vector3d n=new Vector3d();for(int i=0;i<v.size();i++){Vector3d a=v.get(i),b=v.get((i+1)%v.size());n.x+=(a.y-b.y)*(a.z+b.z);n.y+=(a.z-b.z)*(a.x+b.x);n.z+=(a.x-b.x)*(a.y+b.y);}return n;}
    private static boolean containsEdge(List<Integer> f,DiscoveryConnection e){for(int i=0;i<f.size();i++){int a=f.get(i),b=f.get((i+1)%f.size());if(Math.min(a,b)==e.lowerId()&&Math.max(a,b)==e.upperId())return true;}return false;}
    private static String edgeLabel(DiscoveryConnection e){return "edge-"+e.lowerId()+"-"+e.upperId();}
    private static double max(List<Double> v){return v.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);}
    private static double deviation(List<Double> v){double mean=v.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);return v.stream().mapToDouble(x->Math.abs(x-mean)).max().orElse(Double.NaN);}
    private static double clamp(double x){return Math.max(-1,Math.min(1,x));}
    private static ConstraintMeasurement measured(String id,double raw,Double tolerance,String unit,String formula,int samples,long revision){return new ConstraintMeasurement(id,MeasurementState.MEASURED,raw,tolerance,tolerance==null?null:raw/tolerance,unit,"",formula,List.of(),samples,0,revision,"");}
    private static ConstraintMeasurement unavailable(String id,MeasurementState state,String code,String reason,String unit,long revision){return unavailable(id,state,code,reason,unit,0,0,List.of(),revision);}
    private static ConstraintMeasurement unavailable(String id,MeasurementState state,String code,String reason,String unit,int samples,int invalid,List<String> affected,long revision){return ConstraintMeasurement.unavailable(id,state,code,reason,unit,samples,invalid,affected,revision);}
    private static ConstraintMeasurement qualitative(String id,String state,String reason,long revision){return new ConstraintMeasurement(id,MeasurementState.MEASURED,null,null,null,"",state,reason,List.of(),1,0,revision,state);}
    private record FacePass(List<List<Integer>> faces,List<String> invalidFaces,List<Double> planarity,List<Double> angles,List<Double> planeDistances,List<Double> alignments,List<Double> areas,List<Vector3d> normals){}
}
