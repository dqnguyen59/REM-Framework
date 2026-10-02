package remframework.geometry.discovery;
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

import java.util.ArrayList;
import java.util.List;

public final class FixedDegreeValidator {
    public FixedDegreeValidation validate(int nodeCount, int degree) {
        List<String> errors = new ArrayList<>();
        if (degree < 2)
            errors.add("Dynamic Elastic Discovery requires at least two neighbors per node.");
        if (nodeCount < degree + 1)
            errors.add("Node count must be at least K + 1.");
        if (degree >= nodeCount)
            errors.add("Exact neighbors per node must be smaller than node count.");
        if ((long) nodeCount * degree % 2 != 0)
            errors.add("N * K must be even for an undirected regular graph.");
        if (degree == 2 && nodeCount < 3)
            errors.add("K = 2 requires at least 3 nodes.");
        if (degree >= 3 && nodeCount < 4)
            errors.add("K >= 3 requires at least 4 nodes.");
        return new FixedDegreeValidation(errors.isEmpty(), errors,
                nearest(nodeCount, degree, -1), nearest(nodeCount, degree, 1));
    }

    public boolean validNodeCount(int nodeCount, int degree) {
        return validate(nodeCount, degree).valid();
    }

    private Integer nearest(int n, int k, int direction) {
        for (int candidate = n + direction; candidate >= 1 && candidate <= 1_000_000;
             candidate += direction)
            if (basicValid(candidate, k)) return candidate;
        return null;
    }
    private boolean basicValid(int n, int k) {
        return k >= 2 && n >= k + 1 && n >= (k >= 3 ? 4 : 3) && ((long)n * k) % 2 == 0;
    }
}
