package remframework.geometry.discovery;

public record GeometryDiscoveryActionAvailability(boolean enabled, String disabledReason) {
    public static GeometryDiscoveryActionAvailability available() {
        return new GeometryDiscoveryActionAvailability(true, "");
    }
    public static GeometryDiscoveryActionAvailability disabled(String reason) {
        return new GeometryDiscoveryActionAvailability(false, reason);
    }
}
