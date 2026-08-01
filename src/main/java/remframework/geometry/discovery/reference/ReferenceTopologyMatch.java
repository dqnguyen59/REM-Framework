package remframework.geometry.discovery.reference;

public record ReferenceTopologyMatch(ReferenceTopology topology, boolean exactMatch,
        String matchingMethod, String referenceVersion, String explanation) {
    public static final String GEOMETRY_DISCLAIMER =
            "Topology match does not prove that a realized geometry matches the reference geometry.";
}
