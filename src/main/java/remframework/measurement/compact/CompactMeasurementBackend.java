package remframework.measurement.compact;

import org.joml.Vector3d;

import java.util.Arrays;

/**
 * Primitive-array, measurement-only implementation of the canonical v0.1
 * cubic update. The production Node/Lattice architecture is not replaced.
 */
public final class CompactMeasurementBackend implements AutoCloseable {
    private final int nx, ny, nz, nodeCount;
    private final double gamma, cws;
    private final double[] positionX, positionY, positionZ;
    private final double[] nextPositionX, nextPositionY, nextPositionZ;
    private final double[] velocityX, velocityY, velocityZ;
    private final double[] accelerationX, accelerationY, accelerationZ;
    private int sourceIndex = -1;
    private int[] sourceIndices;
    private double sourceForceX, sourceForceY, sourceForceZ;
    private double lastAppliedSourceForceX, lastAppliedSourceForceY, lastAppliedSourceForceZ;
    private int parallelism = 1;

    public CompactMeasurementBackend(int nx, int ny, int nz,
                                     double waveSpeed, double gamma) {
        if (nx < 2 || ny < 2 || nz < 2) throw new IllegalArgumentException("All dimensions must be >= 2");
        this.nx=nx; this.ny=ny; this.nz=nz; this.nodeCount=Math.multiplyExact(nx,Math.multiplyExact(ny,nz));
        this.gamma=gamma; this.cws=waveSpeed/6.0;
        positionX=new double[nodeCount]; positionY=new double[nodeCount]; positionZ=new double[nodeCount];
        nextPositionX=new double[nodeCount]; nextPositionY=new double[nodeCount]; nextPositionZ=new double[nodeCount];
        velocityX=new double[nodeCount]; velocityY=new double[nodeCount]; velocityZ=new double[nodeCount];
        accelerationX=new double[nodeCount]; accelerationY=new double[nodeCount]; accelerationZ=new double[nodeCount];
        for(int z=0;z<nz;z++) for(int y=0;y<ny;y++) for(int x=0;x<nx;x++) { int i=index(x,y,z); positionX[i]=nextPositionX[i]=x; positionY[i]=nextPositionY[i]=y; positionZ[i]=nextPositionZ[i]=z; }
    }

    public int nodeCount(){return nodeCount;}
    public int index(int x,int y,int z){return x+nx*(y+ny*z);}
    public int x(int i){return i%nx;}
    public int y(int i){return (i/nx)%ny;}
    public int z(int i){return i/(nx*ny);}
    public void setParallelism(int value){parallelism=Math.max(1,value);}
    public void setSource(int x,int y,int z){sourceIndex=index(x,y,z);}
    /** Applies the next source force coherently to every supplied occupied node. */
    public void setSourceNodes(int[] indices){sourceIndices=indices.clone();for(int i:sourceIndices)if(i<0||i>=nodeCount)throw new IllegalArgumentException("Source node outside lattice: "+i);Arrays.sort(sourceIndices);}
    public void setSourceForce(double x,double y,double z){sourceForceX=x;sourceForceY=y;sourceForceZ=z;}
    public double positionX(int i){return positionX[i];} public double positionY(int i){return positionY[i];} public double positionZ(int i){return positionZ[i];}
    public double velocityX(int i){return velocityX[i];} public double velocityY(int i){return velocityY[i];} public double velocityZ(int i){return velocityZ[i];}
    public double accelerationX(int i){return accelerationX[i];} public double accelerationY(int i){return accelerationY[i];} public double accelerationZ(int i){return accelerationZ[i];}
    public double sourceForceX(){return sourceForceX;} public double sourceForceY(){return sourceForceY;} public double sourceForceZ(){return sourceForceZ;}

    /**
     * Returns the coherent source force applied to this occupied node by the
     * most recent update, or zero for a node outside the configured source.
     * This is a read-only diagnostic and does not alter the update equations.
     */
    public Vector3d lastAppliedSourceForce(int nodeIndex){
        boolean source=sourceIndices==null?nodeIndex==sourceIndex:Arrays.binarySearch(sourceIndices,nodeIndex)>=0;
        return source?new Vector3d(lastAppliedSourceForceX,lastAppliedSourceForceY,lastAppliedSourceForceZ):new Vector3d();
    }

    /** Executes one causal v0.1 update and commits all buffered positions. */
    public void update(){
        lastAppliedSourceForceX=sourceForceX;lastAppliedSourceForceY=sourceForceY;lastAppliedSourceForceZ=sourceForceZ;
        if(parallelism>1){
            Thread[] workers=new Thread[parallelism];
            int chunk=(nodeCount+parallelism-1)/parallelism;
            for(int t=0;t<parallelism;t++){final int start=t*chunk,end=Math.min(nodeCount,start+chunk);if(sourceIndices==null)workers[t]=Thread.startVirtualThread(()->{for(int i=start;i<end;i++)updateNode(i,false);});else workers[t]=Thread.startVirtualThread(()->updateRange(start,end));}
            for(Thread worker:workers)try{worker.join();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Compact update interrupted",e);}
        } else if(sourceIndices==null) for(int i=0;i<nodeCount;i++) updateNode(i,false);
        else updateRange(0,nodeCount);
        System.arraycopy(nextPositionX,0,positionX,0,nodeCount);
        System.arraycopy(nextPositionY,0,positionY,0,nodeCount);
        System.arraycopy(nextPositionZ,0,positionZ,0,nodeCount);
    }

    private void updateRange(int start,int end){
        int cursor=0; while(cursor<sourceIndices.length&&sourceIndices[cursor]<start)cursor++;
        for(int i=start;i<end;i++){boolean source=cursor<sourceIndices.length&&sourceIndices[cursor]==i;if(source)cursor++;updateNode(i,source);}
    }

    private void updateNode(int i, boolean source){
        int x=i%nx, y=(i/nx)%ny, z=i/(nx*ny);
        double ex=0,ey=0,ez=0;
        if(x+1<nx){int n=i+1;ex=(ex+positionX[n])-positionX[i];ey=(ey+positionY[n])-positionY[i];ez=(ez+positionZ[n])-positionZ[i];}
        if(y+1<ny){int n=i+nx;ex=(ex+positionX[n])-positionX[i];ey=(ey+positionY[n])-positionY[i];ez=(ez+positionZ[n])-positionZ[i];}
        if(z+1<nz){int n=i+nx*ny;ex=(ex+positionX[n])-positionX[i];ey=(ey+positionY[n])-positionY[i];ez=(ez+positionZ[n])-positionZ[i];}
        if(x>0){int n=i-1;ex=(ex+positionX[n])-positionX[i];ey=(ey+positionY[n])-positionY[i];ez=(ez+positionZ[n])-positionZ[i];}
        if(y>0){int n=i-nx;ex=(ex+positionX[n])-positionX[i];ey=(ey+positionY[n])-positionY[i];ez=(ez+positionZ[n])-positionZ[i];}
        if(z>0){int n=i-nx*ny;ex=(ex+positionX[n])-positionX[i];ey=(ey+positionY[n])-positionY[i];ez=(ez+positionZ[n])-positionZ[i];}
        if(source) { ex+=sourceForceX; ey+=sourceForceY; ez+=sourceForceZ; }
        else if(i==sourceIndex){ex+=sourceForceX;ey+=sourceForceY;ez+=sourceForceZ;sourceForceX=sourceForceY=sourceForceZ=0;}
        double ax=ex*gamma, ay=ey*gamma, az=ez*gamma;
        double vx=velocityX[i]+ax*cws, vy=velocityY[i]+ay*cws, vz=velocityZ[i]+az*cws;
        velocityX[i]=vx; velocityY[i]=vy; velocityZ[i]=vz;
        // Node mutates acceleration first by cws for velocity, then by .5
        // for the buffered-position term; its exposed acceleration is the
        // resulting value, not the pre-mutation force sum.
        double storedAx=ax*cws*.5, storedAy=ay*cws*.5, storedAz=az*cws*.5;
        accelerationX[i]=storedAx; accelerationY[i]=storedAy; accelerationZ[i]=storedAz;
        double px=positionX[i]+vx+storedAx, py=positionY[i]+vy+storedAy, pz=positionZ[i]+vz+storedAz;
        // The canonical lattice marks any outer-face node FIXED, and v0.1
        // restores all three coordinates for that node.
        if(x==0||x==nx-1||y==0||y==ny-1||z==0||z==nz-1){px=x;py=y;pz=z;}
        nextPositionX[i]=px; nextPositionY[i]=py; nextPositionZ[i]=pz;
    }

    /** Exact passive right-left, top-bottom, front-back distance imbalance. */
    public Vector3d gradient(int i){
        int x=i%nx,y=(i/nx)%ny,z=i/(nx*ny); double[] d=new double[6];
        if(x+1<nx)d[0]=distance(i,i+1); if(y+1<ny)d[1]=distance(i,i+nx); if(z+1<nz)d[2]=distance(i,i+nx*ny);
        if(x>0)d[3]=distance(i,i-1); if(y>0)d[4]=distance(i,i-nx); if(z>0)d[5]=distance(i,i-nx*ny);
        return new Vector3d(d[0]-d[3],d[1]-d[4],d[2]-d[5]);
    }
    private double distance(int a,int b){double dx=positionX[b]-positionX[a],dy=positionY[b]-positionY[a],dz=positionZ[b]-positionZ[a];return Math.sqrt(dx*dx+dy*dy+dz*dz);}

    public static long persistentBytesPerNode(){return 9L*Double.BYTES;}
    public static long estimatePersistentBytes(int n){return persistentBytesPerNode()*n;}
    public static long estimateTemporaryBytes(int n){return 3L*Double.BYTES*n;}
    public static long estimateNodeCount(int n){return (long)n*n*n;}
    @Override public void close(){ Arrays.fill(positionX,0); Arrays.fill(positionY,0); Arrays.fill(positionZ,0); sourceIndices=null; }
}
