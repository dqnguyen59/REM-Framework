package remframework.lattice;
import java.util.*;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Explicit experimental mechanics bridge; indices are storage addresses, not physical directions. */
public final class ExperimentalDiamondLatticeModel implements LatticeModel {

    private static final int[][] BASIS={{0,0,0},{1,1,1},{0,2,2},{1,3,3},{2,0,2},{3,1,3},{2,2,0},{3,3,1}};
    private static final int[][] OFFSETS={{1,1,1},{1,-1,-1},{-1,1,-1},{-1,-1,1}};
    @Override public List<NodeDefinition> nodes(Vector3i requested) {
        var size=dimensions(requested);
        int cx=size.x/2,cy=size.y/2,cz=size.z/2,count=size.x*size.y*size.z;
        // Conventional crystal axes coincide with the cubic runtime X/Y/Z axes.
        var center=new Vector3d(2*(cx-1)+1.5,2*(cy-1)+1.5,2*(cz-1)+1.5);
        // Generate definitions on demand: avoid constructing a second discovery graph and retaining all definitions.
        return Collections.unmodifiableList(new AbstractList<>() {
            @Override public int size() {return count;}
            @Override public NodeDefinition get(int id) {
                Objects.checkIndex(id,count);
                int cell=id/8,b=id%8,x=cell/(cy*cz),y=cell/cz%cy,z=cell%cz;
                int sx=4*x+BASIS[b][0],sy=4*y+BASIS[b][1],sz=4*z+BASIS[b][2];
                int[] ids=new int[4];int neighbors=0,sign=(sx&1)==0?1:-1;
                for(var offset:OFFSETS) {
                    int nx=sx+sign*offset[0],ny=sy+sign*offset[1],nz=sz+sign*offset[2];
                    int ix=Math.floorDiv(nx,4),iy=Math.floorDiv(ny,4),iz=Math.floorDiv(nz,4);
                    if(ix<0||ix>=cx||iy<0||iy>=cy||iz<0||iz>=cz)continue;
                    for(int nb=0;nb<8;nb++) if(BASIS[nb][0]==Math.floorMod(nx,4)
                        && BASIS[nb][1]==Math.floorMod(ny,4)&&BASIS[nb][2]==Math.floorMod(nz,4)) {
                        ids[neighbors++]=8*((ix*cy+iy)*cz+iz)+nb;break;
                    }
                }
                Arrays.sort(ids,0,neighbors);var links=new LinkedHashMap<Integer,String>();
                for(int slot=0;slot<neighbors;slot++)links.put(slot,Integer.toString(ids[slot]));
                var position=new Vector3d(sx,sy,sz).sub(center).div(Math.sqrt(3));
                return new NodeDefinition(Integer.toString(id),new Vector3i(2*x+(b>>2),2*y+((b>>1)&1),2*z+(b&1)),position,Map.copyOf(links));
            }
        });
    }    @Override public String type() {return "experimental-diamond-mechanics";}
    @Override public double equilibriumDistance() {return 1;}
    public static boolean validDimensions(Vector3i d) {
        return d.x>=2 && d.y>=2 && d.z>=2 && d.x%2==0 && d.y%2==0 && d.z%2==0;
    }
    @Override public Vector3i dimensions(Vector3i requested) {
        var estimate=DiamondMemoryBudget.estimate(requested);if(!estimate.allowed()) throw new IllegalArgumentException(estimate.error());
        return new Vector3i(requested);
    }
    
    @Override public boolean mechanicsOnly() {return true;}
    @Override public double mechanicalDivisor() {return 4;}
    @Override public boolean fixedBoundary(NodeDefinition node,Vector3i dimensions) {return node.neighbors().size()<4;}
}
