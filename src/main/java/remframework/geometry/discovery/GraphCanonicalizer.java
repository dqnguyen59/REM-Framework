package remframework.geometry.discovery;

import java.util.List;

/** Exact canonical labeling for the documented exhaustive range N &lt;= 8. */
public final class GraphCanonicalizer {
    public String canonicalLabel(int nodeCount, List<DiscoveryConnection> edges) {
        if (nodeCount > GeometryConfigurationGenerator.MAX_EXHAUSTIVE_N)
            throw new IllegalArgumentException("Exact canonical labeling is bounded to N <= "
                    + GeometryConfigurationGenerator.MAX_EXHAUSTIVE_N + ".");
        return CanonicalGraphSignature.of(nodeCount, edges);
    }
}
