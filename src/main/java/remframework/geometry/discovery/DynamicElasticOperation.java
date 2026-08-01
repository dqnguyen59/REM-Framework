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

public enum DynamicElasticOperation {
    READY("Ready"),
    RUNNING_CONTINUOUSLY("Running continuously"),
    RUNNING_UNTIL_EQUILIBRIUM("Running until equilibrium"),
    PAUSED("Paused"),
    EQUILIBRIUM_REACHED("Equilibrium reached"),
    MAXIMUM_ITERATIONS_REACHED("Maximum iterations reached"),
    NUMERICAL_FAILURE("Numerical failure");

    private final String label;
    DynamicElasticOperation(String label) { this.label = label; }
    public String label() { return label; }
}
