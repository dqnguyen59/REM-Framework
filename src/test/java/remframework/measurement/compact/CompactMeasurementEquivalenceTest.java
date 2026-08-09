package remframework.measurement.compact;

import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import remframework.opengl.helpers.Neighbor;
import remframework.opengl.model.entity.EntityType;
import remframework.opengl.model.entity.Node;
import remframework.settings.Project;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deterministic canonical-object versus compact-SoA regressions. */
class CompactMeasurementEquivalenceTest {
    private static final double TOLERANCE = 1e-12;

    @Test void smallSingleNode180Updates() { run(5,5,5,0,0,0,180); }
    @Test void mediumSingleNode180Updates() { run(7,6,5,1,1,2,180); }
    @Test void largerCanonicalFeasible180Updates() { run(11,9,7,5,4,3,180); }

    private static void run(int nx,int ny,int nz,int sx,int sy,int sz,int updates) {
        Project project=Project.getInstance(); project.setConstantFrequency(1.0); project.setConstantElectricFactor(1.0); project.getRemModel().startSimulation();
        int count=nx*ny*nz; List<Node> nodes=new ArrayList<>(count);
        for(int z=0;z<nz;z++)for(int y=0;y<ny;y++)for(int x=0;x<nx;x++){Node n=new Node(null,null,new Vector3d(x,y,z),new Vector3d(),1);n.setIndex(x,y,z);if(x==0||x==nx-1||y==0||y==ny-1||z==0||z==nz-1)n.setType(EntityType.FIXED);nodes.add(n);}
        for(int z=0;z<nz;z++)for(int y=0;y<ny;y++)for(int x=0;x<nx;x++){Node n=nodes.get(x+nx*(y+ny*z));if(x+1<nx){link(n,Neighbor.RIGHT,nodes.get(n.getIndex().x+1+nx*(y+ny*z)));}if(y+1<ny)link(n,Neighbor.TOP,nodes.get(x+nx*((y+1)+ny*z)));if(z+1<nz)link(n,Neighbor.FRONT,nodes.get(x+nx*(y+ny*(z+1))));}
        CompactMeasurementBackend compact=new CompactMeasurementBackend(nx,ny,nz,1,1);compact.setSource(sx,sy,sz);
        int source=sx+nx*(sy+ny*sz); Metrics metrics=new Metrics();
        for(int update=1;update<=updates;update++){
            double phase=(update*20)%360, force=Math.sin(Math.toRadians(phase))*200;
            nodes.get(source).addForce(new Vector3d(force,0,0)); compact.setSourceForce(force,0,0);
            for(Node n:nodes)n.updateNodeProperties(Project.getInstance().settings.getNodeFormulaVersion());
            compact.update(); for(Node n:nodes)n.updatePosition();
            for(int i=0;i<count;i++){Node n=nodes.get(i);metrics.add(compact.positionX(i),n.getPosition().x);metrics.add(compact.positionY(i),n.getPosition().y);metrics.add(compact.positionZ(i),n.getPosition().z);metrics.add(compact.velocityX(i),n.getVelocity().x);metrics.add(compact.velocityY(i),n.getVelocity().y);metrics.add(compact.velocityZ(i),n.getVelocity().z);metrics.add(compact.accelerationX(i),n.getAcceleration().x);metrics.add(compact.accelerationY(i),n.getAcceleration().y);metrics.add(compact.accelerationZ(i),n.getAcceleration().z);}
            metrics.add(compact.sourceForceX(),nodes.get(source).getForce().x);
        }
        System.out.println("compact-equivalence "+nx+"x"+ny+"x"+nz+" maxAbs="+metrics.max+" meanAbs="+(metrics.sum/metrics.count)+" maxRel="+metrics.maxRelative+" tolerance="+TOLERANCE);
        assertTrue(metrics.max<=TOLERANCE,"compact mismatch maxAbs="+metrics.max);
    }
    private static void link(Node a,Neighbor d,Node b){a.setNeighbor(d,b);Neighbor o=switch(d){case RIGHT->Neighbor.LEFT;case LEFT->Neighbor.RIGHT;case TOP->Neighbor.BOTTOM;case BOTTOM->Neighbor.TOP;case FRONT->Neighbor.BACK;case BACK->Neighbor.FRONT;};b.setNeighbor(o,a);}
    private static final class Metrics {double max,sum,maxRelative;long count;void add(double a,double b){double d=Math.abs(a-b);max=Math.max(max,d);sum+=d;count++;maxRelative=Math.max(maxRelative,d/Math.max(Math.max(Math.abs(a),Math.abs(b)),Double.MIN_NORMAL));}}
}
