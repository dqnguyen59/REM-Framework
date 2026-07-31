package remframework.geometry.discovery;

public record GeometryConfigurationId(String value) implements Comparable<GeometryConfigurationId> {
    public GeometryConfigurationId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Configuration ID is required.");
    }
    @Override public int compareTo(GeometryConfigurationId other) { return value.compareTo(other.value); }
    @Override public String toString() { return value; }
}
