package remframework.opengl.model;

import org.junit.jupiter.api.Test;
import org.joml.Vector3i;
import remframework.lattice.DiamondTopology;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DiamondProbePathsTest {
    @Test void followsTheProbeLineWithoutAddingBondsForThePreviouslyBentRoute() {
        var topology=new DiamondTopology(120,120,120);int source=topology.idAtIndex(new Vector3i(60));
        int target=topology.idAtIndex(new Vector3i(69,42,64));var paths=new DiamondProbePaths(topology,source);
        var origin=topology.position(source,new org.joml.Vector3d());
        var lineEnd=new org.joml.Vector3d(.497051625386297,-.8095113394900796,.31245971410378254).mul(24).add(origin);
        var original=paths.pathTo(target);var aligned=paths.pathTo(target,lineEnd);
        assertEquals(37,aligned.size());assertEquals(original.size(),aligned.size());
        assertEquals(source,aligned.getFirst());assertEquals(target,aligned.getLast());
        assertTrue(routeScore(topology,aligned,origin,lineEnd)<routeScore(topology,original,origin,lineEnd)/2);
        var neighbors=new int[4];
        for(int i=1;i<aligned.size();i++) {
            assertEquals(i,paths.hops(aligned.get(i)));
            int count=topology.neighbors(aligned.get(i-1),neighbors);boolean connected=false;
            for(int j=0;j<count;j++)if(neighbors[j]==aligned.get(i))connected=true;
            assertTrue(connected);
        }
        assertEquals(aligned,paths.pathTo(target,lineEnd));
    }

    @Test void linePreferenceMatchesExhaustiveShortestRouteSearchAndAvoidsBlockedNodes() {
        var topology=new DiamondTopology(16,16,16);int source=topology.idAtIndex(new Vector3i(8));
        int blocked=topology.siteId(17,17,17),target=topology.siteId(18,18,16);
        var paths=new DiamondProbePaths(topology,source,id->id!=blocked);
        var origin=topology.position(source,new org.joml.Vector3d());
        var end=topology.position(target,new org.joml.Vector3d()).add(.2,.1,-.1);
        var route=paths.pathTo(target,end);assertFalse(route.contains(blocked));
        assertEquals(paths.hops(target)+1,route.size());
        assertEquals(exhaustiveScore(topology,paths,target,origin,end),routeScore(topology,route,origin,end),1e-10);
        assertTrue(paths.pathTo(blocked,end).isEmpty());assertTrue(paths.pathTo(-1,end).isEmpty());
        assertEquals(List.of(source),paths.pathTo(source,origin));
        assertThrows(IllegalArgumentException.class,()->paths.pathTo(target,origin));
        assertThrows(IllegalArgumentException.class,()->paths.pathTo(target,new org.joml.Vector3d(Double.NaN)));
    }

    private static double deviation(DiamondTopology topology,int node,org.joml.Vector3d origin,org.joml.Vector3d end) {
        var p=topology.position(node,new org.joml.Vector3d());var delta=new org.joml.Vector3d(end).sub(origin);
        double fraction=Math.max(0,Math.min(1,new org.joml.Vector3d(p).sub(origin).dot(delta)/delta.lengthSquared()));
        return p.distanceSquared(new org.joml.Vector3d(origin).fma(fraction,delta));
    }
    private static double routeScore(DiamondTopology topology,List<Integer> route,org.joml.Vector3d origin,org.joml.Vector3d end) {
        return route.stream().mapToDouble(node->deviation(topology,node,origin,end)).sum();
    }
    private static double exhaustiveScore(DiamondTopology topology,DiamondProbePaths paths,int node,org.joml.Vector3d origin,org.joml.Vector3d end) {
        if(paths.hops(node)==0)return 0;
        double minimum=Double.POSITIVE_INFINITY;var neighbors=new int[4];int count=topology.neighbors(node,neighbors);
        for(int i=0;i<count;i++)if(paths.hops(neighbors[i])==paths.hops(node)-1)
            minimum=Math.min(minimum,exhaustiveScore(topology,paths,neighbors[i],origin,end));
        return minimum+deviation(topology,node,origin,end);
    }
    @Test void reconstructsMinimalConnectedRouteAndHandlesSourceAndUnreachableTargets() {
        var topology=new DiamondTopology(16,16,16);int source=topology.idAtIndex(new Vector3i(8));
        int target=topology.siteId(20,16,16);var paths=new DiamondProbePaths(topology,source);
        var route=paths.pathTo(target);assertEquals(source,route.getFirst());assertEquals(target,route.getLast());
        assertEquals(5,route.size());assertEquals(paths.hops(target),route.size()-1);
        int[] neighbors=new int[4];
        for(int i=1;i<route.size();i++) {
            int count=topology.neighbors(route.get(i-1),neighbors);boolean connected=false;
            for(int j=0;j<count;j++)if(neighbors[j]==route.get(i))connected=true;
            assertTrue(connected);assertEquals(i,paths.hops(route.get(i)));
            var current=topology.position(route.get(i),new org.joml.Vector3d());
            var previous=topology.position(route.get(i-1),new org.joml.Vector3d());
            assertEquals(1,current.distance(previous),1e-12);
            if(i>1) {
                var incoming=new org.joml.Vector3d(previous).sub(topology.position(route.get(i-2),new org.joml.Vector3d()));
                var outgoing=new org.joml.Vector3d(current).sub(previous);
                assertEquals(Math.acos(1./3),incoming.angle(outgoing),1e-12);
            }
        }
        assertEquals(route,paths.pathTo(target));assertEquals(List.of(source),paths.pathTo(source));
        assertTrue(paths.pathTo(-1).isEmpty());assertTrue(paths.pathTo(topology.siteId(0,0,0)).isEmpty());
    }
    @Test void countsConnectedBondsInsteadOfEuclideanDistance() {
        var topology=new DiamondTopology(16,16,16);
        int source=topology.idAtIndex(new Vector3i(8));
        var paths=new DiamondProbePaths(topology,source);
        assertEquals(0,paths.hops(source));
        assertEquals(1,paths.hops(topology.siteId(17,17,17)));
        assertEquals(2,paths.hops(topology.siteId(18,18,16)));
        assertEquals(4,paths.hops(topology.siteId(20,16,16)));
        assertEquals(-1,paths.hops(topology.siteId(0,0,0)));
    }

    @Test void virtualProbeKeepsItsStencilHopRangeAndWeightedDiagnostic() {
        var topology=new DiamondTopology(16,16,16);
        var paths=new DiamondProbePaths(topology,topology.idAtIndex(new Vector3i(8)));
        var nodes=List.of(topology.siteId(17,17,17),topology.siteId(20,16,16));
        var sample=paths.sample(nodes,List.of(.25,.75));
        assertEquals(1,sample.minimum());assertEquals(4,sample.maximum());assertEquals(3.25,sample.weightedMean());
        assertEquals(1,paths.sample(nodes,List.of(1.,0.)).maximum());
        assertThrows(IllegalArgumentException.class,()->paths.sample(nodes,List.of(.25,.5)));
    }

    @Test void fixedOrWallNodesCannotProvideAShortcut() {
        var topology=new DiamondTopology(16,16,16);int source=topology.idAtIndex(new Vector3i(8));
        int blocked=topology.siteId(17,17,17);
        var paths=new DiamondProbePaths(topology,source,id->id!=blocked);
        assertEquals(-1,paths.hops(blocked));
        assertTrue(paths.hops(topology.siteId(18,18,16))>2);
        var route=paths.pathTo(topology.siteId(18,18,16));assertFalse(route.contains(blocked));
        assertEquals(paths.hops(topology.siteId(18,18,16)),route.size()-1);
        assertThrows(IllegalArgumentException.class,()->new DiamondProbePaths(topology,source,id->id!=source));
    }
}
