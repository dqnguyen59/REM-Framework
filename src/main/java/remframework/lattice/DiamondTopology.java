package remframework.lattice;

import org.joml.Vector3d;
import org.joml.Vector3i;
import java.util.Arrays;

/** Allocation-free regular diamond addressing; addresses are not physical directions. */
public final class DiamondTopology {
    private static final int[][] BASIS={{0,0,0},{1,1,1},{0,2,2},{1,3,3},{2,0,2},{3,1,3},{2,2,0},{3,3,1}};
    private static final int[][] OFFSETS={{1,1,1},{1,-1,-1},{-1,1,-1},{-1,-1,1}};
    private final int x,y,z,cy,cz,count;
    public DiamondTopology(int x,int y,int z) {
        if(!ExperimentalDiamondLatticeModel.validDimensions(new Vector3i(x,y,z)))throw new IllegalArgumentException("Even dimensions >=2 required");
        long n;
        try{n=Math.multiplyExact(Math.multiplyExact((long)x,y),z);}catch(ArithmeticException e){throw new IllegalArgumentException("Array capacity exceeded",e);}
        if(n>Integer.MAX_VALUE-8)throw new IllegalArgumentException("Array capacity exceeded");
        this.x=x;this.y=y;this.z=z;cy=y/2;cz=z/2;count=(int)n;
    }
    public int size(){return count;}
    public int siteId(int sx,int sy,int sz) {
        int ix=Math.floorDiv(sx,4),iy=Math.floorDiv(sy,4),iz=Math.floorDiv(sz,4);
        if(ix<0||ix>=x/2||iy<0||iy>=cy||iz<0||iz>=cz)return -1;
        for(int b=0;b<8;b++)if(BASIS[b][0]==Math.floorMod(sx,4)&&BASIS[b][1]==Math.floorMod(sy,4)&&BASIS[b][2]==Math.floorMod(sz,4))return 8*((ix*cy+iy)*cz+iz)+b;
        return -1;
    }
    public int idAtIndex(Vector3i index){return 8*((index.x/2*cy+index.y/2)*cz+index.z/2)+((index.x&1)<<2)+((index.y&1)<<1)+(index.z&1);}
    public Vector3d position(int id,Vector3d target) {
        int cell=id/8,b=id%8;
        return target.set(4*(cell/(cy*cz))+BASIS[b][0]-(x-0.5),
            4*(cell/cz%cy)+BASIS[b][1]-(y-0.5),4*(cell%cz)+BASIS[b][2]-(z-0.5)).div(Math.sqrt(3));
    }
    public Vector3i index(int id,Vector3i target) {
        int cell=id/8,b=id%8;return target.set(2*(cell/(cy*cz))+(b>>2),2*(cell/cz%cy)+((b>>1)&1),2*(cell%cz)+(b&1));
    }
    /** Fills ascending direct-neighbor IDs, matching the historical Node slot order. */
    public int neighbors(int id,int[] result) {
        int cell=id/8,b=id%8,sx=4*(cell/(cy*cz))+BASIS[b][0],sy=4*(cell/cz%cy)+BASIS[b][1],sz=4*(cell%cz)+BASIS[b][2];
        int sign=(sx&1)==0?1:-1,n=0;
        for(var offset:OFFSETS) {
            int nx=sx+sign*offset[0],ny=sy+sign*offset[1],nz=sz+sign*offset[2];
            int ix=Math.floorDiv(nx,4),iy=Math.floorDiv(ny,4),iz=Math.floorDiv(nz,4);
            if(ix<0||ix>=x/2||iy<0||iy>=cy||iz<0||iz>=cz)continue;
            for(int nb=0;nb<8;nb++)if(BASIS[nb][0]==Math.floorMod(nx,4)&&BASIS[nb][1]==Math.floorMod(ny,4)&&BASIS[nb][2]==Math.floorMod(nz,4)) {
                result[n++]=8*((ix*cy+iy)*cz+iz)+nb;break;
            }
        }
        Arrays.sort(result,0,n);return n;
    }
}
