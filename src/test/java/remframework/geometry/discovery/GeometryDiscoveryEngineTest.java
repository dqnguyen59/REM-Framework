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
import org.junit.jupiter.api.io.TempDir;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;
import remframework.lattice.LatticeModelLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GeometryDiscoveryEngineTest {
    @TempDir Path temp;
    private final GeometryDiscoveryEngine engine = new GeometryDiscoveryEngine();
    private final GeometryDiscoverySettings settings = GeometryDiscoverySettings.defaults(10);

    @Test
    void anchorAndOrientationConstraintsRemainApplied() {
        GeometryDiscoveryResult result = engine.relax(
                GeometryDiscoveryExamples.tetrahedron(10), settings);
        Map<Integer, DiscoveryNode> nodes = byId(result.model());
        assertEquals(new Vector3d(), nodes.get(0).position());
        assertTrue(nodes.get(1).position().x > 0);
        assertEquals(0, nodes.get(1).position().y, 0);
        assertEquals(0, nodes.get(1).position().z, 0);
    }

    @Test
    void tetrahedronConvergesToEqualEdgeLengths() {
        GeometryDiscoveryResult result = engine.relax(
                GeometryDiscoveryExamples.tetrahedron(10), settings);
        assertTrue(result.converged(), () -> result.diagnostics().toString());
        assertEquals(10, result.metrics().meanConnectedNeighborDistance(), 1.0e-5);
        assertTrue(result.metrics().standardDeviationConnectedNeighborDistance() < 1.0e-5);
    }

    @Test
    void squareCycleConvergesToEqualEdgeLengths() {
        GeometryDiscoveryResult result = engine.relax(
                GeometryDiscoveryExamples.squareCycle(10), settings);
        assertTrue(result.converged(), () -> result.diagnostics().toString());
        assertEquals(10, result.metrics().minimumConnectedNeighborDistance(), 1.0e-5);
        assertEquals(10, result.metrics().maximumConnectedNeighborDistance(), 1.0e-5);
    }

    @Test
    void collisionPreventionSeparatesUnconnectedOverlappingNodes() {
        GeometryDiscoveryModel model = new GeometryDiscoveryModel("collision", "collision",
                List.of(new DiscoveryNode(0, new Vector3d(), true),
                        new DiscoveryNode(1, new Vector3d(10, 0, 0), false),
                        new DiscoveryNode(2, new Vector3d(), false)),
                List.of(new DiscoveryConnection(0, 1), new DiscoveryConnection(1, 2)));
        GeometryDiscoveryResult result = engine.relax(model, settings);
        assertTrue(byId(result.model()).get(0).position()
                .distance(byId(result.model()).get(2).position())
                >= settings.minimumNodeSeparation() - settings.convergenceTolerance());
    }

    @Test
    void sameSeedAndSettingsAreDeterministic() {
        GeometryDiscoveryModel model = new GeometryDiscoveryModel("deterministic", "deterministic",
                List.of(new DiscoveryNode(0, new Vector3d(), true),
                        new DiscoveryNode(1, new Vector3d(), false),
                        new DiscoveryNode(2, new Vector3d(), false)),
                List.of(new DiscoveryConnection(0, 1), new DiscoveryConnection(1, 2)));
        GeometryDiscoveryResult first = engine.relax(model, settings.withRandomSeed(42));
        GeometryDiscoveryResult second = engine.relax(model, settings.withRandomSeed(42));
        for (int id = 0; id < 3; id++)
            assertEquals(byId(first.model()).get(id).position(),
                    byId(second.model()).get(id).position());
    }

    @Test
    void nonConvergedRunsAndFiniteMetricsAreReportedHonestly() {
        GeometryDiscoveryResult result = engine.relax(
                GeometryDiscoveryExamples.tetrahedron(10),
                settings.withMaximumIterations(1));
        assertFalse(result.converged());
        assertEquals(1, result.metrics().iterationCount());
        assertTrue(result.metrics().allFinite());
        assertFalse(result.diagnostics().isEmpty());
    }

    @Test
    void exportContainsEveryNodeAndReciprocalNeighbors() throws Exception {
        GeometryDiscoveryResult result = engine.relax(
                GeometryDiscoveryExamples.tetrahedron(10), settings);
        Path output = new GeometryDiscoveryExporter().export(result,
                temp.resolve("tetrahedron.toml"));
        TomlParseResult toml = Toml.parse(output);
        assertFalse(toml.hasErrors(), () -> toml.errors().toString());
        assertEquals(4, toml.getArray("nodes").size());
        Map<String, TomlTable> nodes = tablesById(toml.getArray("nodes"));
        for (TomlTable node : nodes.values()) {
            String id = node.getString("id");
            TomlArray neighbors = node.getArray("neighbors");
            assertEquals(3, neighbors.size());
            for (int i = 0; i < neighbors.size(); i++) {
                String neighbor = neighbors.getString(i);
                assertTrue(arrayContains(nodes.get(neighbor).getArray("neighbors"), id));
            }
        }
    }

    @Test
    void exporterNeverOverwritesCubicTemplate() throws Exception {
        Path template = temp.resolve("lattice/cubic-lattice.toml");
        Files.createDirectories(template.getParent());
        Files.writeString(template, "authoritative template");
        GeometryDiscoveryResult result = engine.relax(
                GeometryDiscoveryExamples.tetrahedron(10), settings);
        assertThrows(IllegalArgumentException.class,
                () -> new GeometryDiscoveryExporter().export(result, template));
        assertEquals("authoritative template", Files.readString(template));
    }

    @Test
    void runtimeCompatibilityDistinguishesCandidateShapes() {
        GeometryDiscoveryResult tetrahedron = engine.relax(
                GeometryDiscoveryExamples.tetrahedron(10), settings);
        GeometryDiscoveryResult cube = engine.relax(
                GeometryDiscoveryExamples.smallCubic(10), settings);
        assertFalse(tetrahedron.runtimeCompatibility().compatible());
        assertTrue(tetrahedron.runtimeCompatibility().reason().contains("rectangular"));
        assertTrue(cube.runtimeCompatibility().compatible());
        assertEquals(new org.joml.Vector3i(2, 2, 2),
                cube.runtimeCompatibility().dimensions());
    }

    @Test
    void compatibleCubeExportUsesRuntimeDirectionalNeighborSlots() throws Exception {
        GeometryDiscoveryResult cube = engine.relax(
                GeometryDiscoveryExamples.smallCubic(10), settings);
        Path output = new GeometryDiscoveryExporter().export(cube, temp.resolve("cube.toml"));
        LatticeModelLoader.LoadResult loaded = new LatticeModelLoader().load(output);
        assertTrue(loaded.validation().isValid(),
                () -> loaded.validation().messages().toString());
        TomlArray nodes = Toml.parse(output).getArray("nodes");
        TomlTable nodeOne = tablesById(nodes).get("1");
        assertEquals("", nodeOne.getArray("neighbors").getString(0));
        assertEquals("3", nodeOne.getArray("neighbors").getString(1));
        assertEquals("5", nodeOne.getArray("neighbors").getString(2));
        assertEquals("0", nodeOne.getArray("neighbors").getString(3));
    }

    private Map<Integer, DiscoveryNode> byId(GeometryDiscoveryModel model) {
        Map<Integer, DiscoveryNode> result = new HashMap<>();
        for (DiscoveryNode node : model.nodes()) result.put(node.id(), node);
        return result;
    }

    private Map<String, TomlTable> tablesById(TomlArray array) {
        Map<String, TomlTable> result = new HashMap<>();
        for (int i = 0; i < array.size(); i++) {
            TomlTable table = array.getTable(i);
            result.put(table.getString("id"), table);
        }
        return result;
    }

    private boolean arrayContains(TomlArray array, String expected) {
        for (int i = 0; i < array.size(); i++)
            if (expected.equals(array.getString(i))) return true;
        return false;
    }
}
