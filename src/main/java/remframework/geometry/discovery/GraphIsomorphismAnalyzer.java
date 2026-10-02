package remframework.geometry.discovery;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;

import java.util.List;

/** Exact bounded graph-isomorphism analysis by adjacency-preserving permutations. */
public final class GraphIsomorphismAnalyzer {
    public boolean areIsomorphic(int nodeCount, List<DiscoveryConnection> first,
                                 List<DiscoveryConnection> second) {
        if (first.size() != second.size()) return false;
        boolean[][] a = matrix(nodeCount, first), b = matrix(nodeCount, second);
        int[] map = new int[nodeCount];
        boolean[] used = new boolean[nodeCount];
        return search(0, map, used, a, b);
    }
    private boolean search(int depth, int[] map, boolean[] used, boolean[][] a, boolean[][] b) {
        if (depth == map.length) return true;
        int degree = degree(a, depth);
        for (int target=0; target<map.length; target++) {
            if (used[target] || degree(b,target) != degree) continue;
            boolean compatible=true;
            for (int prior=0;prior<depth;prior++)
                if (a[depth][prior] != b[target][map[prior]]) { compatible=false; break; }
            if (!compatible) continue;
            map[depth]=target; used[target]=true;
            if (search(depth+1,map,used,a,b)) return true;
            used[target]=false;
        }
        return false;
    }
    public static boolean[][] matrix(int n, List<DiscoveryConnection> edges) {
        boolean[][] result=new boolean[n][n];
        for (DiscoveryConnection e:edges) result[e.firstNodeId()][e.secondNodeId()]
                = result[e.secondNodeId()][e.firstNodeId()]=true;
        return result;
    }
    private static int degree(boolean[][] graph,int node) {
        int result=0; for(boolean edge:graph[node]) if(edge) result++; return result;
    }
}
