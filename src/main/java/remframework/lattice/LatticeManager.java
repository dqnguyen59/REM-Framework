package remframework.lattice;

import remframework.helpers.Constants;
import remframework.settings.AppSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class LatticeManager {
    public static final String SYSTEM_CUBIC_ID = "system:cubic";
    private static LatticeManager instance;

    private final Path latticeDirectory;
    private final AppSettings settings;
    private final CubicLatticeTemplateWriter templateWriter = new CubicLatticeTemplateWriter();
    private final LatticeModelLoader loader = new LatticeModelLoader();
    private List<LatticeDescriptor> descriptors = List.of();
    private LatticeDescriptor selected;
    private String lastWarning = "";

    public static synchronized LatticeManager getInstance() {
        if (instance == null)
            instance = new LatticeManager(Path.of(Constants.BASE_PATH, "lattice"),
                    AppSettings.getInstance());
        return instance;
    }

    public LatticeManager(Path latticeDirectory, AppSettings settings) {
        this.latticeDirectory = latticeDirectory.toAbsolutePath().normalize();
        this.settings = settings;
    }

    public synchronized void initialize() {
        refresh();
    }

    public synchronized void refresh() {
        String previous = selected == null ? settings.getLatticeModelIdentifier()
                : selected.identifier();
        List<LatticeDescriptor> next = new ArrayList<>();
        next.add(systemDescriptor());
        try {
            boolean created = Files.notExists(latticeDirectory);
            Files.createDirectories(latticeDirectory);
            if (created) System.out.println("Created lattice directory: " + latticeDirectory);
            Path template = templateWriter.write(latticeDirectory);
            System.out.println("Generated Cubic Lattice Template: " + template);
        } catch (IOException exception) {
            warn("Could not prepare lattice directory/template: " + exception.getMessage());
        }

        Map<Path, Path> unique = new LinkedHashMap<>();
        if (Files.isDirectory(latticeDirectory)) {
            try (var paths = Files.list(latticeDirectory)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".toml"))
                        .map(p -> p.toAbsolutePath().normalize())
                        .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .forEach(p -> unique.putIfAbsent(p, p));
            } catch (IOException exception) {
                warn("Could not scan lattice directory: " + exception.getMessage());
            }
        }
        for (Path path : unique.values()) next.add(loadDescriptor(path));
        next.subList(1, next.size()).sort(Comparator
                .comparing((LatticeDescriptor d) -> !d.valid())
                .thenComparing(d -> d.displayName().toLowerCase(Locale.ROOT))
                .thenComparing(LatticeDescriptor::identifier));
        descriptors = List.copyOf(next);
        System.out.println("Discovered " + unique.size() + " lattice model file(s).");

        Optional<LatticeDescriptor> preserved = findValid(previous);
        if (preserved.isPresent()) {
            selected = preserved.get();
        } else {
            selected = descriptors.getFirst();
            if (previous == null || previous.isBlank())
                fallback("No lattice model is configured.");
            else if (!SYSTEM_CUBIC_ID.equals(previous))
                fallback("Previously selected lattice '" + previous
                        + "' is missing or invalid after refresh.");
        }
        System.out.println("Selected Lattice Model: " + selected.uiLabel());
    }

    public synchronized boolean select(String identifier) {
        Optional<LatticeDescriptor> descriptor = find(identifier);
        if (descriptor.isEmpty()) {
            fallback("Configured lattice '" + identifier + "' is missing.");
            return false;
        }
        if (!descriptor.get().valid()) {
            fallback("Configured lattice '" + identifier + "' is invalid: "
                    + messages(descriptor.get()));
            return false;
        }
        selected = descriptor.get();
        settings.setLatticeModelIdentifier(identifier);
        lastWarning = "";
        System.out.println("Selected Lattice Model: " + selected.uiLabel());
        return true;
    }

    public synchronized void constructionFailed(LatticeDescriptor descriptor, String reason) {
        fallback("Construction of '" + descriptor.displayName() + "' failed: " + reason);
        settings.requestLatticeRebuild();
    }

    private LatticeDescriptor loadDescriptor(Path path) {
        LatticeModelLoader.LoadResult result = loader.load(path);
        String relative = latticeDirectory.relativize(path).toString().replace('\\', '/');
        String identifier = "file:" + relative;
        String name = String.valueOf(result.metadata().getOrDefault("name", path.getFileName().toString()));
        if ("null".equals(name) || name.isBlank()) name = path.getFileName().toString();
        LatticeDescriptor descriptor = new LatticeDescriptor(identifier, name,
                LatticeDescriptor.SourceType.FILE, path, result.validation().isValid(),
                result.validation().messages(), result.metadata(), result.model());
        if (descriptor.valid()) System.out.println("Registered Lattice Model: " + descriptor.uiLabel());
        else System.err.println("Invalid Lattice Model " + path + ": " + messages(descriptor));
        return descriptor;
    }

    private LatticeDescriptor systemDescriptor() {
        return new LatticeDescriptor(SYSTEM_CUBIC_ID, "System Cubic Lattice",
                LatticeDescriptor.SourceType.SYSTEM, null, true, List.of(),
                Map.of("description", "Guaranteed built-in cubic lattice."),
                SystemCubicLatticeModel.create(settings.isUseLoop()));
    }

    private Optional<LatticeDescriptor> findValid(String identifier) {
        return find(identifier).filter(LatticeDescriptor::valid);
    }

    public synchronized Optional<LatticeDescriptor> find(String identifier) {
        return descriptors.stream().filter(d -> d.identifier().equals(identifier)).findFirst();
    }

    private void fallback(String reason) {
        selected = descriptors.isEmpty() ? systemDescriptor() : descriptors.getFirst();
        settings.setLatticeModelIdentifierSilently(SYSTEM_CUBIC_ID);
        warn(reason + " Falling back to System Cubic Lattice.");
    }

    private void warn(String warning) {
        lastWarning = warning;
        System.err.println("Lattice warning: " + warning);
    }

    private String messages(LatticeDescriptor descriptor) {
        return descriptor.messages().stream().map(ValidationMessage::message)
                .reduce((a, b) -> a + "; " + b).orElse("unknown error");
    }

    public synchronized List<LatticeDescriptor> descriptors() { return descriptors; }
    public synchronized LatticeDescriptor selected() {
        return selected == null ? systemDescriptor() : selected;
    }
    public synchronized String lastWarning() { return lastWarning; }
    public Path latticeDirectory() { return latticeDirectory; }
}
