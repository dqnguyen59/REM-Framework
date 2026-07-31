package remframework.geometry.discovery;

public enum GraphSymmetryFilter {
    SYMMETRIC_ONLY, ASYMMETRIC_ONLY, BOTH;
    public boolean accepts(long automorphisms) {
        return this == BOTH || this == SYMMETRIC_ONLY && automorphisms > 1
                || this == ASYMMETRIC_ONLY && automorphisms == 1;
    }
}
