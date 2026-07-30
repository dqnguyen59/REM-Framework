package remframework.geometry.discovery;

public record DiscoveryConnection(int firstNodeId, int secondNodeId) {
    public int lowerId() { return Math.min(firstNodeId, secondNodeId); }
    public int upperId() { return Math.max(firstNodeId, secondNodeId); }
    public long undirectedKey() {
        return ((long) lowerId() << 32) ^ (upperId() & 0xffffffffL);
    }
}
