import org.joml.*;
import remframework.lattice.DiamondTopology;
import remframework.opengl.model.*;
import remframework.geometry.discovery.research.diamond.*;
public class MaximumRadiusAudit {
 public static void main(String[] args) {
  var t=new DiamondTopology(120,120,120);var paths=new DiamondProbePaths(t,t.idAtIndex(new Vector3i(60)));
  var origin=t.position(t.idAtIndex(new Vector3i(60)),new Vector3d());var min=t.position(0,new Vector3d());
  var rot=new Quaterniond().rotationTo(new Vector3d(1,1,1).normalize(),new Vector3d(1,0,0));
  var sampler=new DiamondReceiverSampling.Sampler(t.size(),id->rot.transform(t.position(id,new Vector3d()).sub(min)),paths::interior,1,t::siteId);
  var dirs=DiamondDirectionalComparison.directions().stream().flatMap(d->TransversePulseAnalysis.plane(d,16).stream()).toList();
  int[] maxima=new int[80];var pos=new Vector3d();
  for(int id=0;id<t.size();id++){int h=paths.hops(id);if(h<0)continue;double r=origin.distance(t.position(id,pos));for(int radius=36;radius<=70;radius++)if(r<=radius+5.)maxima[radius]=java.lang.Math.max(maxima[radius],h);}
  for(int radius=36;radius<=70;radius++) {
   int valid=0;try{for(var d:dirs){sampler.receiver(rot.transform(new Vector3d(d).mul(radius).add(origin).sub(min)),"radius "+radius);valid++;}}catch(IllegalArgumentException e){}
   System.out.printf("radius=%d valid=%d/416 graphWindow=%d%n",radius,valid,236-maxima[radius]-2);
  }
 }
}
