package remframework.geometry.discovery.research.diamond;

import java.util.*;
import java.nio.file.*;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import remframework.geometry.discovery.*;

/** Prescribed diamond reference; independent of REM mechanics and face-sharing experiments. */
public final class DiamondReference {
    private DiamondReference() { }
    record Site(int x,int y,int z) {}
    public static GeometryDiscoveryModel create(int cells,double length) {
        if(cells<1 || cells>20 || !Double.isFinite(length) || length<1e-6 || length>1e6) throw new IllegalArgumentException("cells 1..20; length 1e-6..1e6");
        return build(cells,length);
    }
    /** Explicit headless research domain; normal reference/UI limit remains20 cells. */
    public static GeometryDiscoveryModel createResearchDomain(int cells,double length) {
        if(cells<1 || cells>32 || !Double.isFinite(length) || length<1e-6 || length>1e6) throw new IllegalArgumentException("research cells1..32; length1e-6..1e6");
        return build(cells,length);
    }
    public static GeometryDiscoveryModel createResearchDomain(int x,int y,int z,double length) {
        if(x<1 || y<1 || z<1 || x>Integer.MAX_VALUE/4 || y>Integer.MAX_VALUE/4 || z>Integer.MAX_VALUE/4 || !Double.isFinite(length) || length<1e-6 || length>1e6)
            throw new IllegalArgumentException("research dimensions must be positive and fit quarter-cell coordinates; length 1e-6..1e6");
        var estimate=remframework.lattice.DiamondMemoryBudget.estimate(new org.joml.Vector3i(2*x,2*y,2*z));
        if(!estimate.allowed()) throw new IllegalArgumentException(estimate.error());
        return build(x,y,z,length);
    }
    private static GeometryDiscoveryModel build(int cells,double length) {return build(cells,cells,cells,length);}
    private static GeometryDiscoveryModel build(int cellsX,int cellsY,int cellsZ,double length) {
        int[][] basis={{0,0,0},{1,1,1},{0,2,2},{1,3,3},{2,0,2},{3,1,3},{2,2,0},{3,3,1}};
        Map<Site,Integer> ids=new LinkedHashMap<>();
        for(int x=0;x<cellsX;x++) for(int y=0;y<cellsY;y++) for(int z=0;z<cellsZ;z++) for(var b:basis)
            ids.put(new Site(4*x+b[0],4*y+b[1],4*z+b[2]),ids.size());
        double scale=length/Math.sqrt(3);
        var rotation=new Quaterniond().rotationTo(new Vector3d(1,1,1).normalize(),new Vector3d(1,0,0));
        List<DiscoveryNode> nodes=new ArrayList<>();
        ids.forEach((site,id)->nodes.add(new DiscoveryNode(id,rotation.transform(new Vector3d(site.x(),site.y(),site.z()).mul(scale)),true)));
        List<DiscoveryConnection> edges=new ArrayList<>();
        int[][] offsets={{1,1,1},{1,-1,-1},{-1,1,-1},{-1,-1,1}};
        ids.forEach((site,id)->{ if((site.x()&1)==0) for(var d:offsets) {
            Integer other=ids.get(new Site(site.x()+d[0],site.y()+d[1],site.z()+d[2]));
            if(other!=null) edges.add(new DiscoveryConnection(id,other));
        }});
        return new GeometryDiscoveryModel("diamond-reference","Prescribed fixed diamond lattice: "+cellsX+" x "+cellsY+" x "+cellsZ+" cells; equal nearest-neighbor length "+length+". Interior degree 4; open boundary degrees lower. Not face-sharing tetrahedra or emergent REM isotropy.",nodes,edges);
    }
}

