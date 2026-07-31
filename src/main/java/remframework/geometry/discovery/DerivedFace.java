package remframework.geometry.discovery;
import java.util.List;
public record DerivedFace(String id, List<Integer> nodeIds, boolean chordless,
                          boolean planar, double planarityError) {
    public DerivedFace { nodeIds=List.copyOf(nodeIds); }
}
