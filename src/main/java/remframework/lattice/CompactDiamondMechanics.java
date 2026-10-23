package remframework.lattice;

import org.joml.Vector3d;
import java.util.Arrays;

/**
 * Mechanical-only primitive-array backend for large headless experiments.
 * <br><br>
 * Uses the exact Node v0.1 update order, including its stored acceleration scaling.
 * No rendering, Identity, occupancy-pressure or transport state is allocated.
 */
public final class CompactDiamondMechanics {
    private final DiamondTopology topology;
    private final int[] neighbors;
    private double[] x,y,z,nextX,nextY,nextZ;
    private final double[] vx,vy,vz,ax,ay,az;
    private double[] fx,fy,fz;
    public CompactDiamondMechanics(int sx,int sy,int sz) {
        topology=new DiamondTopology(sx,sy,sz);int n=topology.size();
        long payload=112L*n,bytes=payload+payload/8+64L*1024*1024;
        if(bytes>DiamondMemoryBudget.availableBudget())throw new IllegalArgumentException("Compact mechanical state exceeds safe memory budget: "+bytes+" bytes including allocation margin");
        x=new double[n];y=new double[n];z=new double[n];nextX=new double[n];nextY=new double[n];nextZ=new double[n];
        vx=new double[n];vy=new double[n];vz=new double[n];ax=new double[n];ay=new double[n];az=new double[n];
        neighbors=new int[Math.multiplyExact(4,n)];Arrays.fill(neighbors,-1);int[] links=new int[4];var p=new Vector3d();
        for(int id=0;id<n;id++){topology.position(id,p);x[id]=nextX[id]=p.x;y[id]=nextY[id]=p.y;z[id]=nextZ[id]=p.z;
            int count=topology.neighbors(id,links);System.arraycopy(links,0,neighbors,4*id,count);}
    }
    public int size(){return topology.size();}
    public long stateBytes(){return 112L*size()+(fx==null?0:24L*size());}
    public boolean isFixed(int id){return neighbors[4*id+3]<0;}
    public Vector3d equilibrium(int id,Vector3d target){return topology.position(id,target);}
    public Vector3d position(int id,Vector3d target){return target.set(x[id],y[id],z[id]);}
    public Vector3d velocity(int id,Vector3d target){return target.set(vx[id],vy[id],vz[id]);}
    public Vector3d acceleration(int id,Vector3d target){return target.set(ax[id],ay[id],az[id]);}
    public Vector3d imbalance(int id,Vector3d target) {
        target.zero();var p=new Vector3d();var otherPosition=new Vector3d();var bond=new Vector3d();topology.position(id,p);
        for(int slot=0;slot<4;slot++){int other=neighbors[4*id+slot];if(other<0)continue;
            topology.position(other,otherPosition);double rest=p.distance(otherPosition);
            bond.set(x[other]-x[id],y[other]-y[id],z[other]-z[id]);double length=bond.length();
            if(!(length>0)||!Double.isFinite(length))throw new IllegalStateException("Collapsed or nonfinite bond");
            target.fma((length-rest)/length,bond);
        }return target;
    }
    /** Small receiver stencils are built without a per-node coordinate hash map. */
    public remframework.geometry.discovery.research.diamond.DiamondReceiverSampling.Sampler sampler(boolean allowFixed) {
        var rotation=new org.joml.Quaterniond().rotationTo(new Vector3d(1,1,1).normalize(),new Vector3d(1,0,0));
        var minimum=topology.position(0,new Vector3d());
        return new remframework.geometry.discovery.research.diamond.DiamondReceiverSampling.Sampler(size(),
            id->rotation.transform(topology.position(id,new Vector3d()).sub(minimum)),id->allowFixed||!isFixed(id),1,topology::siteId);
    }
    public void setPosition(int id,Vector3d p){x[id]=nextX[id]=p.x;y[id]=nextY[id]=p.y;z[id]=nextZ[id]=p.z;}
    public void setVelocity(int id,Vector3d v){vx[id]=v.x;vy[id]=v.y;vz[id]=v.z;}
    public void setForce(int id,Vector3d force){if(fx==null){if(24L*size()>DiamondMemoryBudget.availableBudget())throw new IllegalArgumentException("Optional force arrays exceed safe memory budget");fx=new double[size()];fy=new double[size()];fz=new double[size()];}fx[id]=force.x;fy[id]=force.y;fz[id]=force.z;}
    /** waveStep is k*k/4 for this experimental diamond runtime, not a new integrator. */
    public void step(double waveStep,double gamma) {
        if(!Double.isFinite(waveStep)||waveStep<=0||!Double.isFinite(gamma)||gamma<=0)throw new IllegalArgumentException("Positive finite mechanics parameters required");
        for(int id=0;id<size();id++) {
            double sx=0,sy=0,sz=0;
            for(int slot=0;slot<4;slot++){int other=neighbors[4*id+slot];if(other<0)continue;
                sx+=x[other];sx-=x[id];sy+=y[other];sy-=y[id];sz+=z[other];sz-=z[id];}
            sx=(sx+(fx==null?0:fx[id]))*gamma;sy=(sy+(fy==null?0:fy[id]))*gamma;sz=(sz+(fz==null?0:fz[id]))*gamma;
            ax[id]=sx*waveStep;ay[id]=sy*waveStep;az[id]=sz*waveStep;
            vx[id]+=ax[id];vy[id]+=ay[id];vz[id]+=az[id];
            ax[id]*=.5;ay[id]*=.5;az[id]*=.5;
            nextX[id]=(x[id]+vx[id])+ax[id];nextY[id]=(y[id]+vy[id])+ay[id];nextZ[id]=(z[id]+vz[id])+az[id];
            if(isFixed(id)){nextX[id]=x[id];nextY[id]=y[id];nextZ[id]=z[id];}
        }
        double[] swap=x;x=nextX;nextX=swap;swap=y;y=nextY;nextY=swap;swap=z;z=nextZ;nextZ=swap;
        if(fx!=null){Arrays.fill(fx,0);Arrays.fill(fy,0);Arrays.fill(fz,0);}
    }
}
