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

public record TopologyDiagnosticSettings(
        double eigenvalueRelativeTolerance,
        double oppositeDirectionToleranceRadians,
        int sphericalSampleCount) {
    public static TopologyDiagnosticSettings defaults() {
        return new TopologyDiagnosticSettings(1e-4, Math.toRadians(15), 256);
    }
}
