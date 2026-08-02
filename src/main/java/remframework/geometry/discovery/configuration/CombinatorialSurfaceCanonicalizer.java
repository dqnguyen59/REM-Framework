package remframework.geometry.discovery.configuration;

import java.util.*;

/** Canonical rooted-rotation traversal for connected orientable face complexes. */
public final class CombinatorialSurfaceCanonicalizer {
    public Result canonicalize(CombinatorialFaceComplex surface) {
        List<List<Integer>> rotation=rotation(surface);boolean[][] adjacency=adjacency(surface);String best=null;int[] bestMap=null;long matches=0;Set<Integer> rootOrbit=new HashSet<>();
        for(int root=0;root<surface.vertexCount();root++)for(int neighbor:rotation.get(root))for(boolean reverse:new boolean[]{false,true}){
            int[] map=label(rotation,root,neighbor,reverse);if(Arrays.stream(map).anyMatch(v->v<0))continue;
            String code=encode(adjacency,map);if(best==null||code.compareTo(best)<0){best=code;bestMap=map;matches=1;rootOrbit.clear();rootOrbit.add(root);}else if(code.equals(best)){matches++;rootOrbit.add(root);}
        }
        if(best==null)throw new IllegalArgumentException("A connected orientable surface is required for canonicalization.");
        return new Result(surface.vertexCount()+":"+best,bestMap,matches,rootOrbit.size()==surface.vertexCount());
    }
    private static int[] label(List<List<Integer>> rotation,int root,int neighbor,boolean reverse){int n=rotation.size();int[] map=new int[n];Arrays.fill(map,-1);map[root]=0;map[neighbor]=1;int next=2;ArrayDeque<Integer> queue=new ArrayDeque<>();boolean[] queued=new boolean[n];queue.add(root);queued[root]=true;while(!queue.isEmpty()){int vertex=queue.remove();List<Integer> ring=rotation.get(vertex);if(ring.isEmpty())continue;int start=0,best=Integer.MAX_VALUE;for(int i=0;i<ring.size();i++){int label=map[ring.get(i)];if(label>=0&&label<best){best=label;start=i;}}for(int offset=0;offset<ring.size();offset++){int index=reverse?Math.floorMod(start-offset,ring.size()):(start+offset)%ring.size();int candidate=ring.get(index);if(map[candidate]<0)map[candidate]=next++;if(!queued[candidate]){queued[candidate]=true;queue.add(candidate);}}}return map;}
    private static String encode(boolean[][] adjacency,int[] map){int[] inverse=new int[map.length];for(int old=0;old<map.length;old++)inverse[map[old]]=old;StringBuilder value=new StringBuilder(map.length*(map.length-1)/2);for(int i=0;i<map.length;i++)for(int j=i+1;j<map.length;j++)value.append(adjacency[inverse[i]][inverse[j]]?'1':'0');return value.toString();}
    private static boolean[][] adjacency(CombinatorialFaceComplex surface){boolean[][] result=new boolean[surface.vertexCount()][surface.vertexCount()];for(var edge:surface.edges())result[edge.firstNodeId()][edge.secondNodeId()]=result[edge.secondNodeId()][edge.firstNodeId()]=true;return result;}
    private static List<List<Integer>> rotation(CombinatorialFaceComplex surface){List<Map<Integer,Integer>> successor=new ArrayList<>();for(int i=0;i<surface.vertexCount();i++)successor.add(new HashMap<>());for(List<Integer> face:surface.faces())for(int i=0;i<face.size();i++){int vertex=face.get(i),previous=face.get(Math.floorMod(i-1,face.size())),next=face.get((i+1)%face.size());successor.get(vertex).put(previous,next);}List<List<Integer>> result=new ArrayList<>();for(Map<Integer,Integer> map:successor){if(map.isEmpty()){result.add(List.of());continue;}int start=map.keySet().stream().min(Integer::compare).orElseThrow();List<Integer> ring=new ArrayList<>();int current=start;do{ring.add(current);current=map.getOrDefault(current,-1);}while(current>=0&&current!=start&&ring.size()<=map.size());result.add(current==start&&ring.size()==map.size()?List.copyOf(ring):List.of());}return result;}
    public static List<Integer> canonicalCycle(List<Integer> cycle){List<Integer> best=null;for(boolean reverse:new boolean[]{false,true})for(int start=0;start<cycle.size();start++){List<Integer> value=new ArrayList<>();for(int i=0;i<cycle.size();i++)value.add(cycle.get(reverse?Math.floorMod(start-i,cycle.size()):(start+i)%cycle.size()));if(best==null||compare(value,best)<0)best=value;}return best;}
    private static int compare(List<Integer>a,List<Integer>b){for(int i=0;i<a.size();i++){int c=Integer.compare(a.get(i),b.get(i));if(c!=0)return c;}return 0;}
    public record Result(String code,int[] vertexLabels,long automorphismCount,boolean vertexTransitive){public Result{vertexLabels=vertexLabels.clone();}}
}
