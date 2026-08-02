package remframework.geometry.discovery.configuration;

import java.util.*;

/** Immutable, coordinate-free set of first-class ordered combinatorial faces. */
public record CombinatorialFaceComplex(int vertexCount,List<List<Integer>> faces) {
    public CombinatorialFaceComplex {faces=faces.stream().map(List::copyOf).toList();}
    public String encode(){return faces.stream().map(face->face.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("-"))).collect(java.util.stream.Collectors.joining(";"));}
    public static CombinatorialFaceComplex decode(int vertices,String value){if(value==null||value.isBlank())return new CombinatorialFaceComplex(vertices,List.of());return new CombinatorialFaceComplex(vertices,Arrays.stream(value.split(";")).map(face->Arrays.stream(face.split("-")).map(Integer::parseInt).toList()).toList());}
    public List<remframework.geometry.discovery.DiscoveryConnection> edges(){Map<Long,remframework.geometry.discovery.DiscoveryConnection> result=new TreeMap<>();for(List<Integer> face:faces)for(int i=0;i<face.size();i++){var edge=new remframework.geometry.discovery.DiscoveryConnection(face.get(i),face.get((i+1)%face.size()));result.put(edge.undirectedKey(),edge);}return List.copyOf(result.values());}
}
