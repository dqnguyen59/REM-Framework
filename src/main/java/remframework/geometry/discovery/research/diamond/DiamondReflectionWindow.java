package remframework.geometry.discovery.research.diamond;
import java.util.*;
import java.util.function.Function;
/** Conservative graph-causal cutoff: min boundary b [distance(source,b)+distance(b,receiver)] - 1. */
public final class DiamondReflectionWindow {
    private DiamondReflectionWindow() { }
    private record Visit<T>(T node,int steps) { }
    public static <T> int safeSteps(Collection<T> sources,Collection<T> boundaries,Collection<T> receivers,Function<T,List<T>> neighbors) {
        if(sources.isEmpty() || boundaries.isEmpty() || receivers.isEmpty()) throw new IllegalArgumentException("Source, boundary and receiver sets required");
        Map<T,Integer> sourceDistance=new HashMap<>();ArrayDeque<T> queue=new ArrayDeque<>();
        for(T source:sources) if(sourceDistance.putIfAbsent(source,0)==null) queue.add(source);
        while(!queue.isEmpty()) {T node=queue.remove();int next=sourceDistance.get(node)+1;
            for(T neighbor:neighbors.apply(node)) if(sourceDistance.putIfAbsent(neighbor,next)==null) queue.add(neighbor);}
        Map<T,Integer> reflectedDistance=new HashMap<>();
        PriorityQueue<Visit<T>> frontier=new PriorityQueue<>(Comparator.comparingInt(Visit::steps));
        for(T boundary:boundaries) {Integer steps=sourceDistance.get(boundary);if(steps!=null){reflectedDistance.put(boundary,steps);frontier.add(new Visit<>(boundary,steps));}}
        while(!frontier.isEmpty()) {var visit=frontier.remove();if(visit.steps()!=reflectedDistance.get(visit.node()))continue;
            for(T neighbor:neighbors.apply(visit.node())) {int next=visit.steps()+1;
                if(next<reflectedDistance.getOrDefault(neighbor,Integer.MAX_VALUE)){reflectedDistance.put(neighbor,next);frontier.add(new Visit<>(neighbor,next));}}}
        int earliest=Integer.MAX_VALUE;for(T receiver:receivers) earliest=Math.min(earliest,reflectedDistance.getOrDefault(receiver,Integer.MAX_VALUE));
        if(earliest==Integer.MAX_VALUE) throw new IllegalArgumentException("No connected source-boundary-receiver path");
        return earliest-1;
    }
}
