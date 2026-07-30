package remframework.lattice;

import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import remframework.settings.AppSettings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LatticeManagerTest {
    @TempDir Path temp;

    @Test
    void startupCreatesDirectoryTemplateAndAlwaysRegistersSystemFirst() throws Exception {
        Path directory = temp.resolve("lattice");
        LatticeManager manager = manager(directory);
        manager.initialize();

        assertTrue(Files.isDirectory(directory));
        assertTrue(Files.isRegularFile(directory.resolve("cubic-lattice.toml")));
        assertEquals(LatticeManager.SYSTEM_CUBIC_ID, manager.descriptors().getFirst().identifier());
        assertEquals("System Cubic Lattice", manager.descriptors().getFirst().displayName());
        assertTrue(manager.find("file:cubic-lattice.toml").orElseThrow().valid());
    }

    @Test
    void templateIsOverwrittenButOtherTomlFilesAreNot() throws Exception {
        Path directory = temp.resolve("lattice");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("cubic-lattice.toml"), "old");
        Path other = directory.resolve("custom.toml");
        Files.writeString(other, validCubic("Custom"));

        manager(directory).initialize();

        String template = Files.readString(directory.resolve("cubic-lattice.toml"));
        assertTrue(template.startsWith("# =============================================================================="));
        assertTrue(template.contains("name = \"Cubic Lattice Template\""));
        assertEquals(validCubic("Custom"), Files.readString(other));
    }

    @Test
    void missingConfiguredModelFallsBackWithReason() {
        AppSettings settings = new AppSettings();
        settings.setLatticeModelIdentifierSilently("file:missing.toml");
        LatticeManager manager = new LatticeManager(temp.resolve("lattice"), settings);
        manager.initialize();
        assertEquals(LatticeManager.SYSTEM_CUBIC_ID, manager.selected().identifier());
        assertTrue(manager.lastWarning().contains("missing"));
    }

    @Test
    void malformedTomlIsVisibleAndDoesNotStopDiscovery() throws Exception {
        Path directory = temp.resolve("lattice");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("broken.toml"), "name = [");
        LatticeManager manager = manager(directory);
        manager.initialize();
        LatticeDescriptor broken = manager.find("file:broken.toml").orElseThrow();
        assertFalse(broken.valid());
        assertFalse(broken.messages().isEmpty());
        assertTrue(manager.find("file:cubic-lattice.toml").orElseThrow().valid());
    }

    @Test
    void refreshAddsAndRemovesFilesAndPreservesValidSelection() throws Exception {
        Path directory = temp.resolve("lattice");
        LatticeManager manager = manager(directory);
        manager.initialize();
        Path added = directory.resolve("added.toml");
        Files.writeString(added, validCubic("Added"));
        manager.refresh();
        assertTrue(manager.find("file:added.toml").isPresent());
        assertTrue(manager.select("file:added.toml"));
        manager.refresh();
        assertEquals("file:added.toml", manager.selected().identifier());

        Files.delete(added);
        manager.refresh();
        assertTrue(manager.find("file:added.toml").isEmpty());
        assertEquals(LatticeManager.SYSTEM_CUBIC_ID, manager.selected().identifier());
        assertTrue(manager.lastWarning().contains("missing or invalid"));
    }

    @Test
    void duplicateDisplayNamesRetainDistinctStableIdentifiers() throws Exception {
        Path directory = temp.resolve("lattice");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("a.toml"), validCubic("Duplicate"));
        Files.writeString(directory.resolve("b.toml"), validCubic("Duplicate"));
        LatticeManager manager = manager(directory);
        manager.initialize();
        assertEquals(2, manager.descriptors().stream()
                .filter(d -> d.displayName().equals("Duplicate")).count());
        assertNotEquals(manager.find("file:a.toml").orElseThrow().identifier(),
                manager.find("file:b.toml").orElseThrow().identifier());
    }

    @Test
    void builtInAndGeneratedCubicModelsAreEquivalent() {
        Path directory = temp.resolve("lattice");
        LatticeManager manager = manager(directory);
        manager.initialize();
        LatticeModel system = manager.descriptors().getFirst().model();
        LatticeModel generated = manager.find("file:cubic-lattice.toml").orElseThrow().model();
        Vector3i dimensions = new Vector3i(3, 2, 2);
        List<LatticeModel.NodeDefinition> expected = system.nodes(dimensions);
        List<LatticeModel.NodeDefinition> actual = generated.nodes(dimensions);
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).id(), actual.get(i).id());
            assertEquals(expected.get(i).index(), actual.get(i).index());
            assertEquals(expected.get(i).position(), actual.get(i).position());
            assertEquals(expected.get(i).neighbors(), actual.get(i).neighbors());
        }
    }

    private LatticeManager manager(Path directory) {
        return new LatticeManager(directory, new AppSettings());
    }

    static String validCubic(String name) {
        return """
                name = "%s"
                description = "Test model"
                formatVersion = "1.0"
                [model]
                type = "cubic"
                dimensionSource = "project"
                dimensionality = 3
                spacing = 10.0
                equilibriumDistance = 10.0
                boundary = "open"
                neighborOffsets = [[1,0,0],[0,1,0],[0,0,1],[-1,0,0],[0,-1,0],[0,0,-1]]
                """.formatted(name);
    }
}
