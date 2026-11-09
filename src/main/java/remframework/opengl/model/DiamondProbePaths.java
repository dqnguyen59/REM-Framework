package remframework.opengl.model;

import java.util.Arrays;
import java.util.List;
import remframework.lattice.DiamondTopology;

/** Shortest connected-bond counts through movable diamond nodes, measured from one source. */
public final class DiamondProbePaths {
    private final int[] hops;
    private final byte[] degree;
    private final DiamondTopology topology;
    private final int source;

    public record Sample(int minimum,int maximum,double weightedMean) { }

    public DiamondProbePaths(DiamondTopology topology,int source) {
        this(topology,source,id->true);
    }

    public DiamondProbePaths(DiamondTopology topology,int source,java.util.function.IntPredicate movable) {
        this.topology=topology;
        this.source=source;
        hops=new int[topology.size()];Arrays.fill(hops,-1);
        degree=new byte[topology.size()];var neighbors=new int[4];
        for(int id=0;id<degree.length;id++)degree[id]=movable.test(id)?(byte)topology.neighbors(id,neighbors):0;
        if(source<0||source>=hops.length||degree[source]!=4)
            throw new IllegalArgumentException("Path source must be a movable interior node");
        var queue=new int[hops.length];int head=0,tail=0;queue[tail++]=source;hops[source]=0;
        while(head<tail) {
            int id=queue[head++],count=topology.neighbors(id,neighbors);
            for(int i=0;i<count;i++) {
                int neighbor=neighbors[i];
                if(degree[neighbor]==4&&hops[neighbor]<0){hops[neighbor]=hops[id]+1;queue[tail++]=neighbor;}
            }
        }
    }

    public int hops(int node){return hops[node];}
    public boolean interior(int node){return degree[node]==4;}

    /** One deterministic shortest route, including both endpoints; ties use neighbor order. */
    public List<Integer> pathTo(int target) {
        if(target<0||target>=hops.length||hops[target]<0)return List.of();
        var reverse=new java.util.ArrayList<Integer>();var neighbors=new int[4];int node=target;
        reverse.add(node);
        while(hops[node]>0) {
            int count=topology.neighbors(node,neighbors),next=-1;
            for(int i=0;i<count;i++)if(hops[neighbors[i]]==hops[node]-1){next=neighbors[i];break;}
            if(next<0)throw new IllegalStateException("Broken shortest-path distance field");
            node=next;reverse.add(node);
        }
        java.util.Collections.reverse(reverse);return List.copyOf(reverse);
    }

    /**
     * Minimizes squared distance to the source-to-probe segment among minimum-hop routes.<br><br>
     * Only predecessors one hop nearer the source are admitted. Dynamic programming on this
     * acyclic graph changes the displayed representative, never its bond count.
     */
    public List<Integer> pathTo(int target,org.joml.Vector3d lineEnd) {
        if(target<0||target>=hops.length||hops[target]<0)return List.of();
        if(lineEnd==null||!lineEnd.isFinite())throw new IllegalArgumentException("Finite probe coordinate required");
        if(target==source)return List.of(source);
        var origin=topology.position(source,new org.joml.Vector3d());
        var direction=new org.joml.Vector3d(lineEnd).sub(origin);double lengthSquared=direction.lengthSquared();
        if(!Double.isFinite(lengthSquared)||lengthSquared<=0)throw new IllegalArgumentException("Finite nonzero source-to-probe segment required");
        int distance=hops[target];var neighbors=new int[4];
        var layers=new java.util.ArrayList<List<Integer>>(java.util.Collections.nCopies(distance+1,List.of()));
        layers.set(distance,List.of(target));
        for(int h=distance;h>0;h--) {
            var previous=new java.util.TreeSet<Integer>();
            for(int node:layers.get(h)) {
                int count=topology.neighbors(node,neighbors);
                for(int i=0;i<count;i++)if(hops[neighbors[i]]==h-1)previous.add(neighbors[i]);
            }
            layers.set(h-1,List.copyOf(previous));
        }
        record Choice(double cost,int previous) { }
        var choices=new java.util.HashMap<Integer,Choice>();choices.put(source,new Choice(0,-1));
        for(int h=1;h<=distance;h++)for(int node:layers.get(h)) {
            double best=Double.POSITIVE_INFINITY;int predecessor=-1;
            int count=topology.neighbors(node,neighbors);
            for(int i=0;i<count;i++)if(hops[neighbors[i]]==h-1) {
                var candidate=choices.get(neighbors[i]);
                if(candidate!=null&&(predecessor<0||candidate.cost()<best-1e-12*Math.max(1,Math.abs(best)))) {
                    best=candidate.cost();predecessor=neighbors[i];
                }
            }
            if(predecessor<0)throw new IllegalStateException("Broken shortest-path predecessor graph");
            var relative=topology.position(node,new org.joml.Vector3d()).sub(origin);
            double t=Math.max(0,Math.min(1,relative.dot(direction)/lengthSquared));
            double deviation=relative.fma(-t,direction).lengthSquared();
            choices.put(node,new Choice(best+deviation,predecessor));
        }
        var reverse=new java.util.ArrayList<Integer>();
        for(int node=target;node>=0;node=choices.get(node).previous())reverse.add(node);
        java.util.Collections.reverse(reverse);return List.copyOf(reverse);
    }

    /**
     * A virtual probe has several sampling nodes, so it has a hop range.<br><br>
     * The weighted mean is a sampling diagnostic, not a uniquely traversed wave path.
     */
    public Sample sample(List<Integer> nodes,List<Double> weights) {
        if(nodes.size()!=weights.size()||nodes.isEmpty())throw new IllegalArgumentException("Matching sampling nodes and weights required");
        int min=Integer.MAX_VALUE,max=-1;double mean=0,sum=0;
        for(int i=0;i<nodes.size();i++) {
            double weight=weights.get(i);
            if(!Double.isFinite(weight)||weight<0)throw new IllegalArgumentException("Nonnegative finite weights required");
            if(weight==0)continue;
            int distance=hops(nodes.get(i));
            if(distance<0)throw new IllegalArgumentException("Probe sampling node is unreachable through movable nodes");
            min=Math.min(min,distance);max=Math.max(max,distance);mean+=weight*distance;sum+=weight;
        }
        if(Math.abs(sum-1)>1e-10)throw new IllegalArgumentException("Sampling weights must sum to one");
        return new Sample(min,max,mean);
    }
}
