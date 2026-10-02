package remframework.geometry.discovery.configuration;

/** Explicit combinatorial surface assumption used by parameter arithmetic. */
public enum SurfaceTopologyAssumption {
    SPHERE_LIKE(2);

    private final int eulerCharacteristic;

    SurfaceTopologyAssumption(int eulerCharacteristic) {
        this.eulerCharacteristic = eulerCharacteristic;
    }

    public int eulerCharacteristic() {
        return eulerCharacteristic;
    }
}
