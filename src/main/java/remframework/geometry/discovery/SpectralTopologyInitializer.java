package remframework.geometry.discovery;

import org.joml.Vector3d;
import remframework.geometry.discovery.configuration.Configuration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic topology-only embedding from a three-dimensional adjacency eigenspace. */
public final class SpectralTopologyInitializer {
    private static final double EIGENVALUE_TOLERANCE=1e-8;
    public List<Vector3d> initialize(Configuration topology,double targetEdgeLength){
        int n=topology.nodeCount();double[][] a=new double[n][n],vectors=new double[n][n];
        for(int i=0;i<n;i++)vectors[i][i]=1;
        topology.edges().forEach(e->{a[e.firstNodeId()][e.secondNodeId()]=1;a[e.secondNodeId()][e.firstNodeId()]=1;});
        jacobi(a,vectors,Math.max(100,n*n*50));
        List<Integer> indices=new ArrayList<>();for(int i=0;i<n;i++)indices.add(i);
        indices.sort(Comparator.<Integer>comparingDouble(i->a[i][i]).reversed().thenComparingInt(Integer::intValue));
        List<Integer> selected=null;
        for(int start=0;start<indices.size();){int end=start+1;double value=a[indices.get(start)][indices.get(start)];while(end<indices.size()&&Math.abs(a[indices.get(end)][indices.get(end)]-value)<=EIGENVALUE_TOLERANCE*Math.max(1,Math.abs(value)))end++;if(end-start>=3&&start>0){selected=indices.subList(start,end).subList(0,3);break;}start=end;}
        if(selected==null)throw new IllegalArgumentException("Topology has no deterministic three-dimensional adjacency eigenspace.");
        List<Vector3d> result=new ArrayList<>();for(int vertex=0;vertex<n;vertex++)result.add(new Vector3d(vectors[vertex][selected.get(0)],vectors[vertex][selected.get(1)],vectors[vertex][selected.get(2)]));
        double mean=topology.edges().stream().mapToDouble(e->result.get(e.firstNodeId()).distance(result.get(e.secondNodeId()))).average().orElseThrow();if(!(mean>1e-12))throw new IllegalArgumentException("Spectral embedding has degenerate topology edges.");result.forEach(p->p.mul(targetEdgeLength/mean));return result;
    }
    private static void jacobi(double[][] a,double[][] v,int maximum){int n=a.length;for(int iteration=0;iteration<maximum;iteration++){int p=0,q=1;double largest=0;for(int i=0;i<n;i++)for(int j=i+1;j<n;j++)if(Math.abs(a[i][j])>largest){largest=Math.abs(a[i][j]);p=i;q=j;}if(largest<1e-13)return;double angle=.5*Math.atan2(2*a[p][q],a[q][q]-a[p][p]),c=Math.cos(angle),s=Math.sin(angle);for(int k=0;k<n;k++)if(k!=p&&k!=q){double kp=a[k][p],kq=a[k][q];a[k][p]=a[p][k]=c*kp-s*kq;a[k][q]=a[q][k]=s*kp+c*kq;}double pp=a[p][p],qq=a[q][q],pq=a[p][q];a[p][p]=c*c*pp-2*s*c*pq+s*s*qq;a[q][q]=s*s*pp+2*s*c*pq+c*c*qq;a[p][q]=a[q][p]=0;for(int k=0;k<n;k++){double kp=v[k][p],kq=v[k][q];v[k][p]=c*kp-s*kq;v[k][q]=s*kp+c*kq;}}throw new IllegalStateException("Adjacency eigensolver did not converge.");}
}
