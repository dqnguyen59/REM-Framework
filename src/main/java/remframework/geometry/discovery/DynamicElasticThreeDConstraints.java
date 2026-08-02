package remframework.geometry.discovery;

import org.joml.Vector3d;
import remframework.geometry.discovery.experiment.*;
import java.util.*;

/** General normalized face-complex energies and bounded finite-difference gradients. */
public final class DynamicElasticThreeDConstraints {
    private static final double EPS=1e-12;
    private final List<List<Integer>> faces;
    private final ThreeDExperimentConditions modes;
    private final ThreeDConstraintStrengths strengths;
    private final double scale;
    private final Map<Long,List<Integer>> faceEdgeIncidence;

    public DynamicElasticThreeDConstraints(List<List<Integer>> faces,
            ThreeDExperimentConditions modes,ThreeDConstraintStrengths strengths,double targetEdgeLength){
        this.faces=faces.stream().map(List::copyOf).toList();this.modes=modes;this.strengths=strengths;scale=targetEdgeLength;faceEdgeIncidence=buildIncidence(this.faces);
    }

    public void addForces(List<DynamicElasticNode> nodes,Map<Integer,Vector3d> forces){
        if(faces.isEmpty())return;List<Vector3d> p=nodes.stream().map(n->new Vector3d(n.position())).toList();
        double h=Math.max(1e-7,scale*1e-5);
        for(DynamicElasticNode node:nodes){if(node.fixed())continue;Vector3d gradient=new Vector3d();
            for(int axis=0;axis<3;axis++){Vector3d q=p.get(node.id());double old=q.get(axis);q.setComponent(axis,old+h);double plus=energy(p);q.setComponent(axis,old-h);double minus=energy(p);q.setComponent(axis,old);gradient.setComponent(axis,(plus-minus)/(2*h));}
            if(gradient.isFinite())forces.get(node.id()).sub(gradient);
        }
    }

    public ThreeDConstraintDiagnostics diagnostics(List<DynamicElasticNode> nodes){return diagnosticsCoordinates(nodes.stream().map(n->new Vector3d(n.position())).toList());}

    private double energy(List<Vector3d> p){double value=0;
        if(modes.facePlanarity()==FacePlanarityMode.REQUIRE_PLANAR_FACES)value+=strengths.facePlanarity()*DevelopmentTimingDiagnostics.measure("stage2.face-planarity-objective",()->planarityEnergy(p));
        if(modes.faceAngles()==FaceAngleConditionMode.REQUIRE_EQUAL_ANGLES_WITHIN_EACH_FACE)value+=strengths.faceAngles()*DevelopmentTimingDiagnostics.measure("stage2.face-angle-objective",()->angleEnergy(p));
        if(modes.dihedrals()==DihedralConditionMode.REQUIRE_EQUAL_DIHEDRAL_ANGLES)value+=strengths.dihedrals()*DevelopmentTimingDiagnostics.measure("stage2.dihedral-objective",()->dihedralValues(p).variance);
        if(modes.facePlaneRadii()==FacePlaneRadiusMode.REQUIRE_EQUAL_FACE_PLANE_RADII)value+=strengths.facePlaneRadii()*DevelopmentTimingDiagnostics.measure("stage2.face-plane-radius-objective",()->radiusValues(p).variance)/(scale*scale);
        return value;
    }
    private double planarityEnergy(List<Vector3d> p){double sum=0;int count=0;for(List<Integer> f:faces){Plane plane=plane(f,p);for(int id:f){double d=Math.abs(new Vector3d(p.get(id)).sub(plane.point).dot(plane.normal))/scale;sum+=d*d;count++;}}return count==0?0:sum/count;}
    private double angleEnergy(List<Vector3d> p){double sum=0;int count=0;for(List<Integer> f:faces){double target=(f.size()-2.0)*Math.PI/f.size();for(int i=0;i<f.size();i++){Vector3d a=new Vector3d(p.get(f.get((i-1+f.size())%f.size()))).sub(p.get(f.get(i)));Vector3d b=new Vector3d(p.get(f.get((i+1)%f.size()))).sub(p.get(f.get(i)));if(a.lengthSquared()>EPS&&b.lengthSquared()>EPS){double angle=Math.acos(clamp(a.normalize().dot(b.normalize())));double d=angle-target;sum+=d*d;count++;}}}return count==0?0:sum/count;}
    private Values dihedralValues(List<Vector3d> p){List<Double> values=new ArrayList<>();for(var e:faceEdgeIncidence.entrySet())if(e.getValue().size()==2){Vector3d a=plane(faces.get(e.getValue().get(0)),p).normal,b=plane(faces.get(e.getValue().get(1)),p).normal;double raw=Math.acos(clamp(Math.abs(a.dot(b))));values.add(raw);}return values(values);}
    private Values radiusValues(List<Vector3d> p){Vector3d center=new Vector3d();p.forEach(center::add);center.div(p.size());List<Double> values=new ArrayList<>();for(List<Integer> f:faces){Plane plane=plane(f,p);values.add(Math.abs(new Vector3d(center).sub(plane.point).dot(plane.normal)));}return values(values);}
    private ThreeDConstraintDiagnostics diagnosticsCoordinates(List<Vector3d> p){double planar=0,angle=0;for(List<Integer> f:faces){Plane plane=plane(f,p);double target=(f.size()-2.0)*Math.PI/f.size();for(int i=0;i<f.size();i++){planar=Math.max(planar,Math.abs(new Vector3d(p.get(f.get(i))).sub(plane.point).dot(plane.normal)));Vector3d a=new Vector3d(p.get(f.get((i-1+f.size())%f.size()))).sub(p.get(f.get(i))),b=new Vector3d(p.get(f.get((i+1)%f.size()))).sub(p.get(f.get(i)));if(a.lengthSquared()>EPS&&b.lengthSquared()>EPS)angle=Math.max(angle,Math.abs(Math.acos(clamp(a.normalize().dot(b.normalize())))-target));}}
        Values d=dihedralValues(p),r=radiusValues(p);double volume=volume(p);boolean manifold=faceEdgeIncidence.values().stream().allMatch(x->x.size()==2);return new ThreeDConstraintDiagnostics(planar,angle,d.maxDeviation,d.variance,r.maxDeviation,r.variance,volume,manifold,Double.isFinite(planar+angle+d.variance+r.variance+volume));}
    private double volume(List<Vector3d> p){Vector3d center=new Vector3d();p.forEach(center::add);center.div(p.size());double sum=0;for(List<Integer> f:faces){List<Integer> ordered=new ArrayList<>(f);Plane plane=plane(f,p);Vector3d fc=plane.point;if(plane.normal.dot(new Vector3d(fc).sub(center))<0)Collections.reverse(ordered);Vector3d a=p.get(ordered.get(0));for(int i=1;i+1<ordered.size();i++)sum+=a.dot(new Vector3d(p.get(ordered.get(i))).cross(p.get(ordered.get(i+1))))/6;}return Math.abs(sum);}
    private Plane plane(List<Integer> f,List<Vector3d> p){Vector3d center=new Vector3d();for(int id:f)center.add(p.get(id));center.div(f.size());Vector3d n=new Vector3d();for(int i=0;i<f.size();i++){Vector3d a=p.get(f.get(i)),b=p.get(f.get((i+1)%f.size()));n.x+=(a.y-b.y)*(a.z+b.z);n.y+=(a.z-b.z)*(a.x+b.x);n.z+=(a.x-b.x)*(a.y+b.y);}if(n.lengthSquared()<=EPS){for(int i=1;i+1<f.size();i++){n.set(p.get(f.get(i))).sub(p.get(f.get(0))).cross(new Vector3d(p.get(f.get(i+1))).sub(p.get(f.get(0))));if(n.lengthSquared()>EPS)break;}}if(n.lengthSquared()<=EPS)n.set(0,0,1);else n.normalize();return new Plane(center,n);}
    private static Map<Long,List<Integer>> buildIncidence(List<List<Integer>> faces){Map<Long,List<Integer>> result=new HashMap<>();for(int fi=0;fi<faces.size();fi++){List<Integer> face=faces.get(fi);for(int i=0;i<face.size();i++)result.computeIfAbsent(key(face.get(i),face.get((i+1)%face.size())),ignored->new ArrayList<>()).add(fi);}Map<Long,List<Integer>> immutable=new HashMap<>();result.forEach((key,value)->immutable.put(key,List.copyOf(value)));return Map.copyOf(immutable);}
    private static Values values(List<Double> v){if(v.isEmpty())return new Values(0,0);double mean=v.stream().mapToDouble(Double::doubleValue).average().orElse(0),variance=v.stream().mapToDouble(x->{double d=x-mean;return d*d;}).average().orElse(0),max=v.stream().mapToDouble(x->Math.abs(x-mean)).max().orElse(0);return new Values(variance,max);}
    private static long key(int a,int b){return ((long)Math.min(a,b)<<32)|(Math.max(a,b)&0xffffffffL);}
    private static double clamp(double x){return Math.max(-1,Math.min(1,x));}
    private record Plane(Vector3d point,Vector3d normal){}
    private record Values(double variance,double maxDeviation){}
}
