package remframework.geometry.discovery;

import org.joml.Vector3d;

import java.util.LinkedHashSet;
import java.util.Set;

public final class DiscoveryNode {
    private final int id;
    private final Vector3d position;
    private final Vector3d previousPosition;
    private final boolean fixedPosition;
    private final Set<Integer> neighborIds = new LinkedHashSet<>();

    public DiscoveryNode(int id, Vector3d position, boolean fixedPosition) {
        this.id = id;
        this.position = new Vector3d(position);
        this.previousPosition = new Vector3d(position);
        this.fixedPosition = fixedPosition;
    }

    public DiscoveryNode copy() {
        DiscoveryNode copy = new DiscoveryNode(id, position, fixedPosition);
        copy.previousPosition.set(previousPosition);
        copy.neighborIds.addAll(neighborIds);
        return copy;
    }

    public int id() { return id; }
    public Vector3d position() { return position; }
    public Vector3d previousPosition() { return previousPosition; }
    public boolean fixedPosition() { return fixedPosition; }
    public Set<Integer> neighborIds() { return Set.copyOf(neighborIds); }

    void addNeighbor(int neighborId) {
        neighborIds.add(neighborId);
    }
}
