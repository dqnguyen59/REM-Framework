import remframework.lattice.DiamondTopology;
import remframework.opengl.model.DiamondProbePaths;
import org.joml.Vector3d;
import org.joml.Vector3i;
public class PulseWindowAudit {
 public static void main(String[] args) {
  var t=new DiamondTopology(120,120,120);int source=t.idAtIndex(new Vector3i(60));
  var p=new DiamondProbePaths(t,source);var origin=t.position(source,new Vector3d());var point=new Vector3d();
  int boundary=Integer.MAX_VALUE,stencil=0;var ns=new int[4];
  for(int id=0;id<t.size();id++) {int h=p.hops(id);if(h<0)continue;if(origin.distance(t.position(id,point))<=41)stencil=Math.max(stencil,h);
   int count=t.neighbors(id,ns);for(int i=0;i<count;i++)if(!p.interior(ns[i]))boundary=Math.min(boundary,h);
  }
  System.out.println("nearest affected interior node="+boundary+" hops; radius-36 stencil hop upper bound="+stencil+"; safe update limit="+Math.min(160,2*boundary-stencil-2));
 }
}
