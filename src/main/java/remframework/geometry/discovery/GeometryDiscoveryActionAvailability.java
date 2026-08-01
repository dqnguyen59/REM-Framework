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

public record GeometryDiscoveryActionAvailability(boolean enabled, String disabledReason) {
    public static GeometryDiscoveryActionAvailability available() {
        return new GeometryDiscoveryActionAvailability(true, "");
    }
    public static GeometryDiscoveryActionAvailability disabled(String reason) {
        return new GeometryDiscoveryActionAvailability(false, reason);
    }
}
