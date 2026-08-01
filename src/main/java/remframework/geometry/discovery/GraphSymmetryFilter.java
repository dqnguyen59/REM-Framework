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

public enum GraphSymmetryFilter {
    SYMMETRIC_ONLY, ASYMMETRIC_ONLY, BOTH;
    public boolean accepts(long automorphisms) {
        return this == BOTH || this == SYMMETRIC_ONLY && automorphisms > 1
                || this == ASYMMETRIC_ONLY && automorphisms == 1;
    }
}
