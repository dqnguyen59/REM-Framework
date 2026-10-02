package remframework.geometry.discovery.graph.canonical;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;
import remframework.geometry.discovery.*;
import remframework.geometry.discovery.configuration.*;
import remframework.geometry.discovery.realization.session.*;
import remframework.geometry.discovery.viewport.*;
import remframework.geometry.discovery.workflow.*;
import remframework.geometry.discovery.persistence.*;
import remframework.geometry.discovery.ui.*;
import remframework.geometry.discovery.graph.canonical.*;
import remframework.geometry.discovery.graph.symmetry.*;
import remframework.geometry.discovery.optimization.*;

import java.util.List;

/** Exact canonical labeling for the documented exhaustive range N &lt;= 8. */
public final class Canonicalizer {
    public String canonicalLabel(int nodeCount, List<DiscoveryConnection> edges) {
        if (nodeCount > ConfigurationGenerator.MAX_EXHAUSTIVE_N)
            throw new IllegalArgumentException("Exact canonical labeling is bounded to N <= "
                    + ConfigurationGenerator.MAX_EXHAUSTIVE_N + ".");
        return CanonicalGraphSignature.of(nodeCount, edges);
    }
}
