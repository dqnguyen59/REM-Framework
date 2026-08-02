package remframework.geometry.discovery.configuration;

import java.util.Map;
import java.util.TreeMap;

/** Exact counts keyed by face edge count for FACE_COMPOSITION primary mode. */
public record ExactFaceCounts(Map<Integer, Long> countsByEdgeCount) {
    public ExactFaceCounts {
        countsByEdgeCount = countsByEdgeCount == null ? Map.of()
                : Map.copyOf(new TreeMap<>(countsByEdgeCount));
    }

    public static ExactFaceCounts of(Map<Integer, ? extends Number> values) {
        Map<Integer,Long> result=new TreeMap<>();
        values.forEach((size,count)->result.put(size,count.longValue()));
        return new ExactFaceCounts(result);
    }
}
