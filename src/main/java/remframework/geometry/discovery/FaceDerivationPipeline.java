package remframework.geometry.discovery;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;

import org.joml.Vector3d;
import java.util.*;
import remframework.geometry.discovery.eligibility.TopologyEligibilityAnalyzer;

public final class FaceDerivationPipeline {
    private static final double TOLERANCE=1e-7;
    public FaceDerivationResult derive(Configuration graph,Realization realization){
        List<DerivedFace> candidates=new ArrayList<>(); Set<String> seen=new TreeSet<>();
        if(realization.dimension()==GeometryEmbeddingDimension.TWO_D){
            List<Integer> boundary=cycleBoundary(graph);
            if(boundary!=null){String key=key(boundary);candidates.add(new DerivedFace("face-"+key,boundary,true,true,0));}
        } else {
            List<List<Integer>> topologicalFaces=new TopologyEligibilityAnalyzer().deriveTopologicalFaces(graph);
            for(List<Integer> cycle:topologicalFaces){double error=planarity(cycle,realization.finalCoordinates());
                candidates.add(new DerivedFace("face-"+key(cycle),canonicalCycle(cycle),true,error<=TOLERANCE,error));}
        }
        List<DerivedFace> faces=candidates.stream().filter(DerivedFace::chordless).filter(DerivedFace::planar).toList();
        boolean unique=!faces.isEmpty();
        GeometryConfigurationDiagnostic d=unique?GeometryConfigurationDiagnostic.info(GeometryConfigurationStage.FACE_DERIVATION,GeometryConfigurationStatusCode.GENERATED,"Faces derived","Candidate simple cycles were canonicalized, filtered for chords, and tested for geometric planarity.")
                :GeometryConfigurationDiagnostic.stop(GeometryConfigurationStage.FACE_DERIVATION,GeometryConfigurationStatusCode.FACES_NOT_DERIVABLE,"Faces not derivable","No planar chordless boundary cycles were found.");
        return new FaceDerivationResult(candidates,faces,unique,unique,GeometryConfigurationStatus.of(d),List.of(d));
    }
    private void dfs(int start,int current,boolean[][] a,boolean[] used,List<Integer> path,Set<String> seen,List<DerivedFace> out,List<Vector3d> p){
        used[current]=true;path.add(current);
        for(int next=0;next<a.length;next++)if(a[current][next]){
            if(next==start&&path.size()>=3){List<Integer> cycle=List.copyOf(path);String key=key(cycle);if(seen.add(key)){boolean chordless=chordless(cycle,a);double error=planarity(cycle,p);out.add(new DerivedFace("face-"+key,canonicalCycle(cycle),chordless,error<=TOLERANCE,error));}}
            else if(!used[next]&&next>=start)dfs(start,next,a,used,path,seen,out,p);
        }
        path.remove(path.size()-1);used[current]=false;
    }
    private boolean chordless(List<Integer> c,boolean[][] a){int n=c.size();for(int i=0;i<n;i++)for(int j=i+1;j<n;j++)if(a[c.get(i)][c.get(j)]&&j!=i+1&&!(i==0&&j==n-1))return false;return true;}
    private double planarity(List<Integer> c,List<Vector3d> p){if(c.size()<=3)return 0;Vector3d o=p.get(c.get(0));Vector3d normal=new Vector3d(p.get(c.get(1))).sub(o).cross(new Vector3d(p.get(c.get(2))).sub(o));if(normal.lengthSquared()<1e-20)return Double.POSITIVE_INFINITY;normal.normalize();double max=0;for(int id:c)max=Math.max(max,Math.abs(new Vector3d(p.get(id)).sub(o).dot(normal)));return max;}
    private List<Integer> cycleBoundary(Configuration g){if(g.nodeCount()<3||g.adjacency().stream().anyMatch(v->v.size()!=2))return null;List<Integer> r=new ArrayList<>();int prev=-1,current=0;do{r.add(current);List<Integer> n=g.adjacency().get(current);int next=n.get(0)==prev?n.get(1):n.get(0);prev=current;current=next;}while(current!=0&&r.size()<=g.nodeCount());return current==0&&r.size()==g.nodeCount()?r:null;}
    static String key(List<Integer> c){List<Integer> x=canonicalCycle(c);return x.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("-"));}
    public static List<Integer> canonicalCycle(List<Integer> c){int n=c.size(),min=Collections.min(c),at=c.indexOf(min);List<Integer>a=new ArrayList<>(),b=new ArrayList<>();for(int i=0;i<n;i++){a.add(c.get((at+i)%n));b.add(c.get((at-i+n)%n));}for(int i=0;i<n;i++){int cmp=Integer.compare(a.get(i),b.get(i));if(cmp<0)return a;if(cmp>0)return b;}return a;}
}
