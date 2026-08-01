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

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Separate persistence for an unfinished New Discovery wizard.
 */
public final class GeometryDiscoveryWizardDraftStore {
    private final Path path;

    public GeometryDiscoveryWizardDraftStore(Path path) { this.path = path; }
    public Properties load() {
        Properties values = new Properties();
        if (Files.notExists(path)) return values;
        try (Reader reader = Files.newBufferedReader(path)) {
            values.load(reader);
        } catch (IOException | IllegalArgumentException ignored) {
            // A damaged draft must not prevent opening the wizard.
        }
        return values;
    }
    public void save(Properties values) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            try (Writer writer = Files.newBufferedWriter(path)) {
                values.store(writer, "REM Framework Geometry Discovery wizard draft");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save New Discovery draft.", exception);
        }
    }
    public void reset() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not reset New Discovery draft.", exception);
        }
    }
}
