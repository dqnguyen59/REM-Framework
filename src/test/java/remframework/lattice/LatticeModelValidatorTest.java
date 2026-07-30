package remframework.lattice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LatticeModelValidatorTest {
    @TempDir Path temp;

    @Test
    void reportsMissingNeighborReferences() throws Exception {
        assertHasError(explicit("""
                [[nodes]]
                id="a"
                index=[0,0,0]
                position=[0,0,0]
                neighbors=["missing"]
                """), "missing neighbor");
    }

    @Test
    void reportsNonreciprocalNeighbors() throws Exception {
        assertHasError(explicit("""
                [[nodes]]
                id="a"
                index=[0,0,0]
                position=[0,0,0]
                neighbors=["b"]
                [[nodes]]
                id="b"
                index=[1,0,0]
                position=[10,0,0]
                neighbors=[]
                """), "Nonreciprocal");
    }

    @Test
    void reportsDuplicateNodeIds() throws Exception {
        assertHasError(explicit("""
                [[nodes]]
                id="a"
                index=[0,0,0]
                position=[0,0,0]
                neighbors=[]
                [[nodes]]
                id="a"
                index=[1,0,0]
                position=[10,0,0]
                neighbors=[]
                """), "Duplicate node ID");
    }

    @Test
    void reportsDisconnectedGraphs() throws Exception {
        assertHasError(explicit("""
                [[nodes]]
                id="a"
                index=[0,0,0]
                position=[0,0,0]
                neighbors=["b"]
                [[nodes]]
                id="b"
                index=[1,0,0]
                position=[10,0,0]
                neighbors=["a"]
                [[nodes]]
                id="c"
                index=[2,0,0]
                position=[20,0,0]
                neighbors=[]
                """), "disconnected");
    }

    @Test
    void reportsSelfDuplicatePositionAndDistanceProblems() throws Exception {
        LatticeModelLoader.LoadResult result = load(explicit("""
                [[nodes]]
                id="a"
                index=[0,0,0]
                position=[0,0,0]
                neighbors=["a","b","b"]
                [[nodes]]
                id="b"
                index=[1,0,0]
                position=[0,0,0]
                neighbors=["a"]
                """));
        List<String> messages = result.validation().messages().stream()
                .map(ValidationMessage::message).toList();
        assertTrue(messages.stream().anyMatch(m -> m.contains("references itself")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("duplicate neighbor")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("Duplicate node position")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("distance differs")));
    }

    private void assertHasError(String contents, String expected) throws Exception {
        LatticeModelLoader.LoadResult result = load(contents);
        assertFalse(result.validation().isValid());
        assertTrue(result.validation().messages().stream()
                .map(ValidationMessage::message)
                .anyMatch(message -> message.contains(expected)),
                () -> result.validation().messages().toString());
    }

    private LatticeModelLoader.LoadResult load(String contents) throws Exception {
        Path file = temp.resolve("model-" + Files.list(temp).count() + ".toml");
        Files.writeString(file, contents);
        return new LatticeModelLoader().load(file);
    }

    private String explicit(String nodes) {
        return """
                name = "Explicit"
                description = "Test explicit graph"
                formatVersion = "1.0"
                [model]
                type = "explicit"
                dimensionality = 3
                dimensions = [3,1,1]
                equilibriumDistance = 10.0
                distanceTolerance = 0.000001
                """ + nodes;
    }
}
