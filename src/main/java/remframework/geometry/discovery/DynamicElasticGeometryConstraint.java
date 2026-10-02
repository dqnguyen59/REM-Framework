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

/**
 * A geometric relation that is deliberately not a topology connection and
 * therefore never contributes to graph degree or lattice export.
 */
public record DynamicElasticGeometryConstraint(
        int firstNodeId, int secondNodeId, double targetDistance, String label) {
}
