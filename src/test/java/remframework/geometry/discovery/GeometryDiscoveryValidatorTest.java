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

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeometryDiscoveryValidatorTest {
    private final GeometryDiscoveryValidator validator = new GeometryDiscoveryValidator();
    private final GeometryDiscoverySettings settings = GeometryDiscoverySettings.defaults(10);

    @Test
    void rejectsDuplicateNodeIds() {
        assertCode(model(List.of(node(0, true), node(0, false), node(1, false)),
                List.of(new DiscoveryConnection(0, 1))), "DUPLICATE_NODE_ID");
    }

    @Test
    void rejectsSelfConnections() {
        assertCode(model(List.of(node(0, true), node(1, false)),
                List.of(new DiscoveryConnection(0, 0), new DiscoveryConnection(0, 1))),
                "SELF_CONNECTION");
    }

    @Test
    void rejectsDuplicateUndirectedConnections() {
        assertCode(model(List.of(node(0, true), node(1, false)),
                List.of(new DiscoveryConnection(0, 1), new DiscoveryConnection(1, 0))),
                "DUPLICATE_CONNECTION");
    }

    @Test
    void rejectsMissingConnectionEndpoints() {
        assertCode(model(List.of(node(0, true), node(1, false)),
                List.of(new DiscoveryConnection(0, 1), new DiscoveryConnection(1, 7))),
                "MISSING_ENDPOINT");
    }

    @Test
    void reportsDisconnectedGraphs() {
        assertCode(model(List.of(node(0, true), node(1, false), node(2, false)),
                List.of(new DiscoveryConnection(0, 1))), "DISCONNECTED_GRAPH");
    }

    @Test
    void rejectsInvalidNumericalSettingsAndReportsMultipleProblems() {
        GeometryDiscoverySettings invalid = new GeometryDiscoverySettings(
                -1, Double.NaN, 0, 0, -2, -1, 1, 0, Double.POSITIVE_INFINITY, 0);
        DiscoveryValidationResult result = validator.validate(
                GeometryDiscoveryExamples.line(10), invalid);
        assertFalse(result.isValid());
        assertTrue(result.messages().stream().filter(m -> m.code().equals("INVALID_SETTING"))
                .count() >= 8);
    }

    private void assertCode(GeometryDiscoveryModel model, String code) {
        DiscoveryValidationResult result = validator.validate(model, settings);
        assertFalse(result.isValid());
        assertTrue(result.messages().stream().anyMatch(message -> message.code().equals(code)),
                () -> result.messages().toString());
    }

    private GeometryDiscoveryModel model(List<DiscoveryNode> nodes,
                                         List<DiscoveryConnection> connections) {
        return new GeometryDiscoveryModel("test", "test", nodes, connections);
    }

    private DiscoveryNode node(int id, boolean fixed) {
        return new DiscoveryNode(id, new Vector3d(id, 0, 0), fixed);
    }
}
