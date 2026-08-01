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

public enum GeometryDiscoveryWorkflowStage {
    CANDIDATE(1, "Candidate"),
    GEOMETRY_RELAXATION(2, "Geometry Relaxation"),
    EVALUATION(3, "Evaluation"),
    TOPOLOGY_SEARCH(4, "Topology Search"),
    RESULTS(5, "Results");

    private final int number;
    private final String label;
    GeometryDiscoveryWorkflowStage(int number, String label) {
        this.number = number;
        this.label = label;
    }
    public int number() { return number; }
    public String label() { return label; }
}
