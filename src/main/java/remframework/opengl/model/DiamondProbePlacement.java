package remframework.opengl.model;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector4f;
import remframework.geometry.discovery.research.diamond.DiamondDirectionalComparison;
import remframework.geometry.discovery.research.diamond.DiamondReceiverSampling;
import remframework.opengl.model.entity.Node;
import java.util.ArrayList;
import java.util.List;

/** Physical diamond shells; no cubic axis traversal or storage-coordinate interpolation. */
public final class DiamondProbePlacement {
    private DiamondProbePlacement() { }

    /**
     * Targets are center + radius * unitDirection for 26 crystal-axis directions.
     * The two FCC-sublattice primitive-grid interpolants are averaged. Weights are
     * nonnegative and sum to one; incomplete/fixed boundary stencils are rejected.
     * <br><br>
     * Called off the UI thread with an immutable reference to the completed node array.
     */
    public static List<ProbeDefinition> create(Object[] values, int firstRadius, int spacing, int shells) {
        return create(values,firstRadius,spacing,shells,false);
    }
    public static List<ProbeDefinition> create(Object[] values,int firstRadius,int spacing,int shells,boolean xyOnly) {
        return create(values,firstRadius,spacing,shells,xyOnly,8);
    }
    public static boolean validXYDirectionCount(int count) {
        return count==8 || count==16 || count==32 || count==64;
    }
    public static List<Vector3d> xyDirections(int count) {
        if(!validXYDirectionCount(count))throw new IllegalArgumentException("Choose 8, 16, 32 or 64 XY directions");
        var directions=new ArrayList<Vector3d>(count);
        for(int ray=0;ray<count;ray++) {
            double angle=2*Math.PI*ray/count;
            directions.add(new Vector3d(Math.cos(angle),Math.sin(angle),0));
        }
        return List.copyOf(directions);
    }
    public static List<ProbeDefinition> create(Object[] values,int firstRadius,int spacing,int shells,boolean xyOnly,int xyDirectionCount) {
        var directions=xyOnly?xyDirections(xyDirectionCount):DiamondDirectionalComparison.directions();
        return create(values,firstRadius,spacing,shells,xyOnly,directions,null);
    }
    /** Rotated receivers at exact physical radii about a specified source position. */
    public static List<ProbeDefinition> create(Object[] values,int firstRadius,int spacing,int shells,
                                               boolean xyOnly,List<Vector3d> directions,Vector3d origin) {
        if(directions.isEmpty() || directions.stream().anyMatch(d->!d.isFinite() || Math.abs(d.length()-1)>1e-9))
            throw new IllegalArgumentException("Unit receiver directions required");
        if(values==null || values.length==0)throw new IllegalArgumentException("Load a diamond lattice first");
        if(firstRadius<1 || spacing<1 || shells<1 || shells>4096/directions.size())
            throw new IllegalArgumentException("Positive radius/spacing and at most "+(4096/directions.size())+" shells required (4096 probe budget)");
        long largest=firstRadius+(long)(shells-1)*spacing;
        if(largest>Integer.MAX_VALUE)throw new IllegalArgumentException("Requested radius overflows integer capacity");
        Node[] nodes=new Node[values.length];Vector3d minimum=new Vector3d(Double.POSITIVE_INFINITY),center=new Vector3d();
        for(int i=0;i<values.length;i++) {
            Node n=(Node)values[i];
            if(!n.isGeometricDistanceImbalance())throw new IllegalArgumentException("Diamond probes require a diamond lattice");
            nodes[i]=n;minimum.min(n.getEquilibriumPosition());center.add(n.getEquilibriumPosition());
        }
        center.div(nodes.length);
        if(origin!=null)center.set(origin);
        var dimensions=new org.joml.Vector3i();for(var n:nodes)dimensions.max(n.getIndex());dimensions.add(1,1,1);
        var topology=new remframework.lattice.DiamondTopology(dimensions.x,dimensions.y,dimensions.z);
        if(topology.size()!=nodes.length)throw new IllegalArgumentException("Complete regular diamond node array required");
        int[] byId=new int[nodes.length];for(int i=0;i<nodes.length;i++)byId[topology.idAtIndex(nodes[i].getIndex())]=i;
        Quaterniond rotation=new Quaterniond().rotationTo(new Vector3d(1,1,1).normalize(),new Vector3d(1,0,0));
        var sampler=new DiamondReceiverSampling.Sampler(nodes.length,
            id->rotation.transform(new Vector3d(nodes[id].getEquilibriumPosition()).sub(minimum)),
            id->(xyOnly || !nodes[id].isFixed()) && !nodes[id].isWall(),1,
            (x,y,z)->{int id=topology.siteId(x,y,z);return id<0?-1:byId[id];});
        List<ProbeDefinition> definitions=new ArrayList<>();
        for(int shell=0;shell<shells;shell++) {
            int radius=firstRadius+shell*spacing;int ray=0;
            for(Vector3d direction:directions) {
                Vector3d target=new Vector3d(direction).mul(radius).add(center);
                var receiver=sampler.receiver(rotation.transform(new Vector3d(target).sub(minimum)),"shell "+(shell+1)+", direction "+(ray+1));
                List<ProbeDefinition.InterpolationSource> sources=new ArrayList<>();
                for(int j=0;j<receiver.nodes().size();j++)sources.add(new ProbeDefinition.InterpolationSource(
                    nodes[receiver.nodes().get(j)].getIndex(),receiver.weights().get(j)));
                var placement=new ProbeDefinition.Placement(List.of(new ProbeDefinition.Target(shell,ray,xyOnly?"Diamond XY":"Diamond XYZ",radius,
                    target,direction,radius,0,null,0,-1)),0,xyOnly?"Diamond physical XY":"Diamond physical XYZ");
                definitions.add(ProbeDefinition.interpolated("probe-diamond-"+shell+"-"+ray,
                    ProbeDetectorType.DIAMOND_INTERPOLATED,new Vector4f(.2f,.8f,1,1),placement,sources));ray++;
            }
        }
        return List.copyOf(definitions);
    }
}
