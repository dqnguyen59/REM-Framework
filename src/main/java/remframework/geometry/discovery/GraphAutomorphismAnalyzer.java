package remframework.geometry.discovery;

import java.util.List;

/** Counts every adjacency-preserving permutation; also reports vertex transitivity. */
public final class GraphAutomorphismAnalyzer {
    public Result analyze(int n, List<DiscoveryConnection> edges) {
        boolean[][] graph=GraphIsomorphismAnalyzer.matrix(n,edges);
        int[] map=new int[n]; boolean[] used=new boolean[n]; boolean[] orbit=new boolean[n];
        long[] count={0}; enumerate(0,map,used,graph,count,orbit);
        return new Result(count[0], java.util.stream.IntStream.range(0,n).allMatch(i -> orbit[i]));
    }
    private void enumerate(int depth,int[] map,boolean[] used,boolean[][] graph,
                           long[] count,boolean[] orbit) {
        if(depth==map.length){ count[0]++; orbit[map[0]]=true; return; }
        for(int target=0;target<map.length;target++) {
            if(used[target]) continue;
            boolean compatible=true;
            for(int prior=0;prior<depth;prior++)
                if(graph[depth][prior]!=graph[target][map[prior]]) { compatible=false; break; }
            if(!compatible) continue;
            map[depth]=target; used[target]=true;
            enumerate(depth+1,map,used,graph,count,orbit); used[target]=false;
        }
    }
    public record Result(long groupSize, boolean vertexTransitive) {
        public GraphSymmetryClassification classification() {
            if(groupSize==1) return GraphSymmetryClassification.ASYMMETRIC;
            return vertexTransitive ? GraphSymmetryClassification.HIGHLY_SYMMETRIC
                    : GraphSymmetryClassification.SYMMETRIC;
        }
    }
}
